package com.example.footballbooking.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.model.TimeSlot;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BookingRepository — Quản lý toàn bộ nghiệp vụ đặt sân.
 *
 * LUỒNG ĐẶT SÂN (Marketplace):
 * 1. createBooking(booking, pitch) → tạo document + gắn pitchOwnerId
 * 2. Cập nhật availability (status = pending)
 * 3. FCM queue → thông báo cho Owner/Admin có đơn mới
 * 4. Owner/Admin duyệt → bookingStatus = "approved"
 * 5. FCM notify customer
 *
 * LUỒNG HỦY ĐƠN:
 * 1. cancelBooking() → bookingStatus = "cancelled"
 * 2. Hoàn lại availability slot (status = available)
 * 3. Hoàn tiền nếu đã thanh toán (manual / VNPay refund)
 */
public class BookingRepository {

    private final FirebaseFirestore mDb;
    private ListenerRegistration bookingListListener;
    private long historyGeneration;
    private long slotsGeneration;
    private long availabilityGeneration;

    private static BookingRepository instance;

    /** A ViewModel owns this instance and its listeners. */
    public static BookingRepository createScoped() { return new BookingRepository(); }

    private BookingRepository() {
        mDb = FirebaseFirestore.getInstance();
    }

    public static synchronized BookingRepository getInstance() {
        if (instance == null) instance = new BookingRepository();
        return instance;
    }

    // ============================================================
    // TẠO ĐƠN ĐẶT SÂN
    // ============================================================

    /** Read the shared day document inside a transaction; competing bookings retry. */
    public void createBooking(Booking booking, Pitch pitch,
                              MutableLiveData<Resource<Booking>> result) {
        if (booking == null || pitch == null || pitch.getPitchId() == null
                || booking.getBookingDate() == null || booking.getSlotId() == null
                || booking.getCustomerId() == null || booking.getServices() == null
                || booking.getStartTime() == null || booking.getEndTime() == null) {
            result.setValue(Resource.error("Dữ liệu đặt sân không hợp lệ", null));
            return;
        }
        if (booking.getServices().size() > 5) {
            result.setValue(Resource.error("Mỗi đơn hỗ trợ tối đa 5 dịch vụ", null));
            return;
        }
        result.setValue(Resource.loading(null));
        // Preserve the existing cross-pitch duplicate check; a failed read stops booking.
        mDb.collection(Constants.COL_BOOKINGS)
                .whereEqualTo("customerId", booking.getCustomerId())
                .whereEqualTo("bookingDate", booking.getBookingDate()).get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot) {
                        if (isOccupied(doc.getString("bookingStatus"))
                                && (booking.getSlotId().equals(doc.getString("slotId"))
                                || booking.getStartTime().equals(doc.getString("startTime")))) {
                            result.setValue(Resource.error("Bạn đã đặt sân vào khung giờ này rồi", null));
                            return;
                        }
                    }
                    createBookingTransaction(booking, pitch.getPitchId(), result);
                })
                .addOnFailureListener(e -> result.setValue(Resource.error("Không thể kiểm tra lịch: " + e.getMessage(), null)));
    }

    private void createBookingTransaction(Booking booking, String pitchId,
                                          MutableLiveData<Resource<Booking>> result) {
        com.google.firebase.firestore.DocumentReference bookingRef = mDb.collection(Constants.COL_BOOKINGS).document();
        com.google.firebase.firestore.DocumentReference pitchRef = mDb.collection(Constants.COL_PITCHES).document(pitchId);
        com.google.firebase.firestore.DocumentReference dayRef = pitchRef.collection(Constants.SUB_AVAILABILITY)
                .document(booking.getBookingDate());
        com.google.firebase.firestore.DocumentReference notificationRef = mDb.collection("fcm_queue").document();
        mDb.runTransaction(tx -> {
            if (!startsInFuture(booking)) throw new IllegalStateException("Khung giờ đã bắt đầu, không thể đặt");
            com.google.firebase.firestore.DocumentSnapshot pitchDoc = tx.get(pitchRef);
            com.google.firebase.firestore.DocumentSnapshot slotDoc = tx.get(pitchRef.collection(Constants.SUB_TIME_SLOTS).document(booking.getSlotId()));
            com.google.firebase.firestore.DocumentSnapshot day = tx.get(dayRef);
            java.util.List<com.google.firebase.firestore.DocumentSnapshot> services = new java.util.ArrayList<>();
            for (Map<String, Object> service : booking.getServices()) {
                services.add(tx.get(mDb.collection(Constants.COL_SERVICES).document((String) service.get("serviceId"))));
            }
            Pitch currentPitch = pitchDoc.toObject(Pitch.class);
            if (currentPitch == null || !currentPitch.isApprovedForDiscovery()
                    || !Constants.PITCH_AVAILABLE.equals(currentPitch.getStatus())) {
                throw new IllegalStateException("Sân không còn khả dụng");
            }
            if (currentPitch.getBookingPolicyVersion() != 2) {
                throw new IllegalStateException("Lịch sân đang chờ kiểm tra dữ liệu. Vui lòng thử lại sau");
            }
            TimeSlot slot = slotDoc.exists() ? slotDoc.toObject(TimeSlot.class) : null;
            if (!slotDoc.exists() && currentPitch.getUseDefaultSlots()) {
                for (TimeSlot candidate : getDefaultTimeSlots()) {
                    if (candidate.getSlotId().equals(booking.getSlotId())) slot = candidate;
                }
            }
            if (slot == null || !slot.isActive()
                    || !slot.getStartTime().equals(booking.getStartTime())
                    || !slot.getEndTime().equals(booking.getEndTime())) {
                throw new IllegalStateException("Khung giờ đã thay đổi. Vui lòng chọn lại");
            }
            if (!"available".equals(slotState(day, booking.getSlotId()).getOrDefault("status", "available"))) {
                throw new IllegalStateException("Khung giờ vừa được người khác đặt");
            }
            double serviceTotal = 0;
            for (int i = 0; i < services.size(); i++) {
                com.google.firebase.firestore.DocumentSnapshot service = services.get(i);
                Map<String, Object> selected = booking.getServices().get(i);
                Double price = service.getDouble("price");
                if (!service.exists() || !Boolean.TRUE.equals(service.getBoolean("isActive")) || price == null
                        || !Double.isFinite(price) || price < 0
                        || !(selected.get("price") instanceof Number)
                        || price.doubleValue() != ((Number) selected.get("price")).doubleValue()
                        || !java.util.Objects.equals(service.getString("name"), selected.get("serviceName"))) {
                    throw new IllegalStateException("Dịch vụ hoặc giá đã thay đổi. Vui lòng chọn lại");
                }
                serviceTotal += price;
            }
            double surcharge = slot.isPeakHour() ? slot.getSurcharge() : 0;
            double total = currentPitch.getBasePrice() + surcharge + serviceTotal;
            if (!Double.isFinite(total) || currentPitch.getBasePrice() < 0 || surcharge < 0
                    || booking.getBasePrice() != currentPitch.getBasePrice()
                    || booking.getSurcharge() != surcharge || booking.getTotalServicePrice() != serviceTotal
                    || booking.getTotalAmount() != total) {
                throw new IllegalStateException("Giá đã thay đổi. Vui lòng tải lại trước khi xác nhận");
            }
            booking.setBookingId(bookingRef.getId());
            booking.setPitchId(pitchId);
            booking.setPitchOwnerId(currentPitch.getOwnerId());
            booking.setPitchName(currentPitch.getName());
            booking.setPitchAddress(currentPitch.getAddress());
            booking.setPitchType(currentPitch.getType());
            booking.setBookingStatus(Constants.STATUS_PENDING);
            booking.setMatchStatus(Constants.MATCH_UPCOMING);
            booking.setPaymentStatus(Constants.PAYMENT_UNPAID);
            booking.setVnpayTransactionId(null);
            booking.setPayosTransactionId(null);
            booking.setAdminNote(null);
            booking.setCreatedAt(null);
            booking.setUpdatedAt(null);
            tx.set(bookingRef, booking);
            tx.set(dayRef, availabilityUpdate(booking, "pending", bookingRef.getId()), com.google.firebase.firestore.SetOptions.merge());
            tx.set(mDb.collection(Constants.COL_USERS).document(booking.getCustomerId())
                    .collection(Constants.SUB_BOOKING_HISTORY).document(bookingRef.getId()),
                    java.util.Collections.singletonMap("bookingRef", bookingRef.getId()));
            Map<String, Object> fcm = new HashMap<>();
            fcm.put("type", "new_booking");
            fcm.put("bookingId", bookingRef.getId());
            fcm.put("customerId", booking.getCustomerId());
            fcm.put("customerName", booking.getCustomerName());
            fcm.put("pitchName", booking.getPitchName());
            fcm.put("time", booking.getStartTime());
            fcm.put("date", booking.getBookingDate());
            fcm.put("ownerId", booking.getPitchOwnerId());
            fcm.put("processed", false);
            fcm.put("createdAt", FieldValue.serverTimestamp());
            tx.set(notificationRef, fcm);
            return booking;
        }).addOnSuccessListener(value -> result.setValue(Resource.success(value)))
          .addOnFailureListener(e -> result.setValue(Resource.error("Đặt sân thất bại: " + e.getMessage(), null)));
    }

    public void createBooking(Booking booking, MutableLiveData<Resource<Booking>> result) {
        Pitch pitch = new Pitch();
        pitch.setPitchId(booking != null ? booking.getPitchId() : null);
        createBooking(booking, pitch, result);
    }

    private static boolean isOccupied(String status) {
        return "pending".equals(status) || "approved".equals(status)
                || "booked".equals(status) || "reserved".equals(status);
    }

    private static boolean startsInFuture(Booking booking) {
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US);
        format.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        format.setLenient(false);
        try {
            java.util.Date start = format.parse(booking.getBookingDate() + " " + booking.getStartTime());
            return start != null && start.after(new java.util.Date());
        } catch (java.text.ParseException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> slotState(com.google.firebase.firestore.DocumentSnapshot day, String slotId) {
        Object slots = day.get("slots");
        Object value = slots instanceof Map ? ((Map<?, ?>) slots).get(slotId) : null;
        return value instanceof Map ? (Map<String, Object>) value : java.util.Collections.emptyMap();
    }

    private static Map<String, Object> availabilityUpdate(Booking booking, String status, String bookingId) {
        Map<String, Object> state = new HashMap<>();
        state.put("status", status);
        state.put("bookingId", bookingId);
        Map<String, Object> update = new HashMap<>();
        update.put("date", booking.getBookingDate());
        update.put("lastSlotId", booking.getSlotId());
        update.put("slots", java.util.Collections.singletonMap(booking.getSlotId(), state));
        return update;
    }

    /** Re-read the booking and lock so stale approval/cancellation cannot release a newer booking. */
    public com.google.android.gms.tasks.Task<Void> transitionBooking(String bookingId, String target, String reason) {
        com.google.firebase.firestore.DocumentReference ref = mDb.collection(Constants.COL_BOOKINGS).document(bookingId);
        com.google.firebase.firestore.DocumentReference notificationRef = mDb.collection("fcm_queue").document();
        return mDb.runTransaction(tx -> {
            Booking current = tx.get(ref).toObject(Booking.class);
            if (current == null) throw new IllegalStateException("Không tìm thấy đơn đặt sân");
            current.setBookingId(bookingId);
            com.google.firebase.firestore.DocumentReference dayRef = mDb.collection(Constants.COL_PITCHES)
                    .document(current.getPitchId()).collection(Constants.SUB_AVAILABILITY).document(current.getBookingDate());
            Map<String, Object> lock = slotState(tx.get(dayRef), current.getSlotId());
            boolean cancelling = Constants.STATUS_CANCELLED.equals(target);
            if (cancelling && !startsInFuture(current)) throw new IllegalStateException("Không thể hủy sau giờ thi đấu");
            if ((!Constants.STATUS_PENDING.equals(current.getBookingStatus())
                    && !(cancelling && Constants.STATUS_APPROVED.equals(current.getBookingStatus())))
                    || !bookingId.equals(lock.get("bookingId")) || !isOccupied((String) lock.get("status"))) {
                throw new IllegalStateException("Đơn hoặc lịch sân đã thay đổi. Vui lòng tải lại");
            }
            boolean approved = Constants.STATUS_APPROVED.equals(target);
            if (!approved && !cancelling && !Constants.STATUS_REJECTED.equals(target)) {
                throw new IllegalArgumentException("Trạng thái không hợp lệ");
            }
            Map<String, Object> update = new HashMap<>();
            update.put("bookingStatus", target);
            update.put("updatedAt", FieldValue.serverTimestamp());
            if (Constants.STATUS_REJECTED.equals(target)) update.put("adminNote", reason == null ? "" : reason);
            tx.update(ref, update);
            tx.set(dayRef, availabilityUpdate(current, approved ? "booked" : "available", approved ? bookingId : null),
                    com.google.firebase.firestore.SetOptions.merge());
            if (approved) {
                Map<String, Object> fcm = new HashMap<>();
                fcm.put("type", Constants.NOTIF_BOOKING_APPROVED);
                fcm.put("bookingId", bookingId);
                fcm.put("customerId", current.getCustomerId());
                fcm.put("pitchName", current.getPitchName());
                fcm.put("time", current.getStartTime());
                fcm.put("processed", false);
                fcm.put("createdAt", FieldValue.serverTimestamp());
                tx.set(notificationRef, fcm);
            }
            return null;
        });
    }

    // ============================================================
    // LẤY LỊCH SỬ ĐẶT SÂN (Realtime)
    // ============================================================

    /**
     * Lắng nghe realtime lịch sử đặt sân của user hiện tại.
     * Sort theo thời gian tạo giảm dần (mới nhất lên đầu).
     */
    public void getBookingsByCustomer(String customerId,
                                      MutableLiveData<Resource<List<Booking>>> result) {
        result.setValue(Resource.loading(null));
        detachBookingListListener();
        final long generation = historyGeneration;

        bookingListListener = mDb.collection(Constants.COL_BOOKINGS)
                .whereEqualTo("customerId", customerId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (generation != historyGeneration) return;
                    if (error != null) {
                        result.setValue(Resource.error(error.getMessage(), null));
                        return;
                    }
                    List<Booking> bookings = snapshots != null
                            ? snapshots.toObjects(Booking.class) : null;
                    result.setValue(Resource.success(bookings));
                });
    }

    // ============================================================
    // HỦY ĐƠN ĐẶT SÂN
    // ============================================================

    /**
     * Hủy đơn đặt sân.
     * CHỈ cho phép hủy khi bookingStatus = "pending" hoặc "approved"
     * VÀ thời gian hiện tại trước giờ thi đấu.
     * Kiểm tra điều kiện này ở ViewModel trước khi gọi.
     */
    public void cancelBooking(Booking booking,
                              MutableLiveData<Resource<Boolean>> result) {
        if (booking == null || booking.getBookingId() == null || booking.getPitchId() == null || booking.getBookingDate() == null) {
            result.setValue(Resource.error("Dữ liệu đơn hàng không hợp lệ", false));
            return;
        }
        result.setValue(Resource.loading(null));

        transitionBooking(booking.getBookingId(), Constants.STATUS_CANCELLED, null)
                .addOnSuccessListener(unused -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(Resource.error("Hủy đơn thất bại: " + e.getMessage(), false)));
    }

    // ============================================================
    // LẤY TIME SLOTS KHẢ DỤNG CỦA SÂN THEO NGÀY
    // ============================================================

    public static List<TimeSlot> getDefaultTimeSlots() {
        List<TimeSlot> list = new java.util.ArrayList<>();
        list.add(new TimeSlot("slot_1", "06:00", "07:30", false, 0));
        list.add(new TimeSlot("slot_2", "07:30", "09:00", false, 0));
        list.add(new TimeSlot("slot_3", "09:00", "10:30", false, 0));
        list.add(new TimeSlot("slot_4", "14:00", "15:30", false, 0));
        list.add(new TimeSlot("slot_5", "15:30", "17:00", false, 0));
        list.add(new TimeSlot("slot_6", "17:00", "18:30", true, 30000));
        list.add(new TimeSlot("slot_7", "18:30", "20:00", true, 50000));
        list.add(new TimeSlot("slot_8", "20:00", "21:30", true, 50000));
        list.add(new TimeSlot("slot_9", "21:30", "23:00", false, 0));
        return list;
    }

    private ListenerRegistration slotsListener;

    public void detachSlotsListener() {
        slotsGeneration++;
        availabilityGeneration++;
        if (slotsListener != null) {
            slotsListener.remove();
            slotsListener = null;
        }
    }

    /**
     * Lấy tất cả timeSlots của sân, kết hợp với các đơn đặt trong ngày.
     * Nếu sân chưa có timeSlots riêng trong subcollection, tự động dùng bộ khung giờ chuẩn.
     * Lắng nghe Realtime (addSnapshotListener) và đọc cả availability document để cập nhật tức thì khi có người đặt.
     */
    public void getAvailableSlots(String pitchId, String dateString,
                                  MutableLiveData<Resource<List<TimeSlot>>> result) {
        if (pitchId == null || dateString == null) {
            result.setValue(Resource.error("Tham số không hợp lệ", null));
            return;
        }
        result.setValue(Resource.loading(null));

        detachSlotsListener();
        final long generation = slotsGeneration;

        // 1. Lấy danh sách khung giờ từ subcollection timeSlots của sân
        mDb.collection(Constants.COL_PITCHES)
           .document(pitchId)
           .collection(Constants.SUB_TIME_SLOTS)
           .get()
           .addOnSuccessListener(slotsSnapshot -> {
               if (generation != slotsGeneration) return;
               // Luôn dùng catalog slot cố định 90 phút. Dữ liệu Firestore chỉ
               // được phép bật/tắt từng slot và thay surcharge, không được đổi
               // start/end hoặc tạo khung giờ ngoài catalog.
               List<TimeSlot> slots = new java.util.ArrayList<>();
               List<TimeSlot> fixedSlots = getDefaultTimeSlots();
               if (slotsSnapshot == null || slotsSnapshot.isEmpty()) {
                   slots = fixedSlots;
               } else {
                   for (TimeSlot fixedSlot : fixedSlots) {
                       com.google.firebase.firestore.DocumentSnapshot doc = null;
                       for (com.google.firebase.firestore.DocumentSnapshot candidate : slotsSnapshot) {
                           if (fixedSlot.getSlotId().equals(candidate.getId())) {
                               doc = candidate;
                               break;
                           }
                       }
                       if (doc == null || !Boolean.TRUE.equals(doc.getBoolean("isActive"))) continue;
                       String start = doc.getString("startTime");
                       String end = doc.getString("endTime");
                       if (!fixedSlot.getStartTime().equals(start) || !fixedSlot.getEndTime().equals(end)) continue;

                       TimeSlot configured = doc.toObject(TimeSlot.class);
                       if (configured == null) configured = fixedSlot;
                       configured.setSlotId(fixedSlot.getSlotId());
                       configured.setStartTime(fixedSlot.getStartTime());
                       configured.setEndTime(fixedSlot.getEndTime());
                       if (!doc.contains("surcharge")) configured.setSurcharge(fixedSlot.getSurcharge());
                       slots.add(configured);
                   }
               }

               // Sắp xếp theo startTime tăng dần
               java.util.Collections.sort(slots, (a, b) -> {
                   if (a.getStartTime() == null || b.getStartTime() == null) return 0;
                   return a.getStartTime().compareTo(b.getStartTime());
               });

               final List<TimeSlot> baseSlots = slots;

               // Availability contains slot locks without exposing customer bookings.
               slotsListener = mDb.collection(Constants.COL_PITCHES).document(pitchId)
                   .collection(Constants.SUB_AVAILABILITY).document(dateString)
                   .addSnapshotListener((availDoc, error) -> {
                       if (generation != slotsGeneration) return;
                       if (error != null) {
                           result.setValue(Resource.error("Cannot load availability", null));
                           return;
                       }
                       java.util.Set<String> bookedSlotIds = new java.util.HashSet<>();
                       java.util.Set<String> bookedStartTimes = new java.util.HashSet<>();
                             // b. Lấy slot đã đặt từ subcollection availability
                             if (availDoc != null && availDoc.exists()) {
                                 Object slotsObj = availDoc.get("slots");
                                 if (slotsObj instanceof Map) {
                                     @SuppressWarnings("unchecked")
                                     Map<String, Object> slotsMap = (Map<String, Object>) slotsObj;
                                     for (Map.Entry<String, Object> entry : slotsMap.entrySet()) {
                                         String sId = entry.getKey();
                                         if (entry.getValue() instanceof Map<?, ?> slotData) {
                                             String st = (String) slotData.get("status");
                                             if (isOccupied(st)) {
                                                 if (sId != null && !sId.trim().isEmpty()) {
                                                     bookedSlotIds.add(sId.trim());
                                                 }
                                             }
                                         }
                                     }
                                 }
                             }

                             // c. Đánh dấu trạng thái cho từng slot
                             List<TimeSlot> resultSlots = new java.util.ArrayList<>();
                             java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
                             java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);
                             dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
                             timeFormat.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
                             java.util.Date now = new java.util.Date();
                             String todayStr = dateFormat.format(now);
                             String currentTimeStr = timeFormat.format(now);
                             boolean isToday = dateString.equals(todayStr);
                             boolean isPastDate = dateString.compareTo(todayStr) < 0;

                             for (TimeSlot baseSlot : baseSlots) {
                                 TimeSlot slot = new TimeSlot(
                                         baseSlot.getSlotId(),
                                         baseSlot.getStartTime(),
                                         baseSlot.getEndTime(),
                                         baseSlot.isPeakHour(),
                                         baseSlot.getSurcharge()
                                 );
                                 slot.setActive(baseSlot.isActive());

                                 boolean isBooked = (slot.getSlotId() != null && bookedSlotIds.contains(slot.getSlotId().trim()))
                                         || (slot.getStartTime() != null && bookedStartTimes.contains(slot.getStartTime().trim()))
                                         || "booked".equalsIgnoreCase(baseSlot.getStatus())
                                         || "pending".equalsIgnoreCase(baseSlot.getStatus());

                                 String slotCompareTime = slot.getStartTime();
                                 boolean isPastSlot = isPastDate || (isToday && slotCompareTime != null
                                         && slotCompareTime.compareTo(currentTimeStr) <= 0);

                                 if (isBooked) {
                                     slot.setStatus("booked");
                                     slot.setAvailable(false);
                                 } else if (isPastSlot) {
                                     slot.setStatus("past");
                                     slot.setAvailable(false);
                                 } else {
                                     slot.setStatus("available");
                                     slot.setAvailable(true);
                                 }
                                 resultSlots.add(slot);
                             }

                             result.setValue(Resource.success(resultSlots));
                  });
           })
           .addOnFailureListener(e -> {
               if (generation != slotsGeneration) return;
               result.setValue(Resource.error("Không thể tải khung giờ: " + e.getMessage(), null));
           });
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    public void detachBookingListListener() {
        historyGeneration++;
        if (bookingListListener != null) {
            bookingListListener.remove();
            bookingListListener = null;
        }
    }
}
