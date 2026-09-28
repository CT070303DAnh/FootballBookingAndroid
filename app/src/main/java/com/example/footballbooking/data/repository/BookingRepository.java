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
import com.google.firebase.firestore.WriteBatch;

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

    private static BookingRepository instance;

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

    /**
     * Tạo booking mới với Firestore WriteBatch (atomic).
     * Đảm bảo availability được cập nhật đồng thời với booking.
     *
     * QUAN TRỌNG: Luôn truyền Pitch object để lấy ownerId.
     * pitchOwnerId được denormalize vào Booking để Owner có thể query
     * đơn đặt trên sân của mình mà không cần JOIN.
     *
     * @param booking  Object đã được điền đầy đủ từ BookingViewModel
     * @param pitch    Pitch object để lấy ownerId, ownerName
     * @param result   LiveData nhận kết quả
     */
    public void createBooking(Booking booking, Pitch pitch,
                              MutableLiveData<Resource<Booking>> result) {
        if (booking == null || pitch == null || pitch.getPitchId() == null || booking.getBookingDate() == null) {
            result.setValue(Resource.error("Dữ liệu đặt sân không hợp lệ", null));
            return;
        }
        result.setValue(Resource.loading(null));

        // 1. Kiểm tra ngày/giờ xem đã quá hạn chưa (chỉ quá hạn khi endTime đã trôi qua)
        String todayStr = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
        String currentTimeStr = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date());
        boolean isPastDate = booking.getBookingDate().compareTo(todayStr) < 0;
        String compareTime = booking.getEndTime() != null ? booking.getEndTime() : booking.getStartTime();
        boolean isPastTime = booking.getBookingDate().equals(todayStr)
                && compareTime != null
                && compareTime.compareTo(currentTimeStr) <= 0;

        if (isPastDate || isPastTime) {
            result.setValue(Resource.error("Khung giờ này đã quá thời gian, không thể đặt!", null));
            return;
        }

        // 2. Kiểm tra xem chính người dùng này đã đặt khung giờ này trong ngày chưa (tránh trùng đơn)
        if (booking.getCustomerId() != null && !booking.getCustomerId().isEmpty()) {
            mDb.collection(Constants.COL_BOOKINGS)
                    .whereEqualTo("customerId", booking.getCustomerId())
                    .whereEqualTo("bookingDate", booking.getBookingDate())
                    .get()
                    .addOnSuccessListener(userSnapshot -> {
                        boolean userAlreadyBooked = false;
                        if (userSnapshot != null) {
                            for (com.google.firebase.firestore.DocumentSnapshot doc : userSnapshot.getDocuments()) {
                                String status = doc.getString("bookingStatus");
                                String docSlotId = doc.getString("slotId");
                                String docStartTime = doc.getString("startTime");

                                boolean isSameSlot = (booking.getSlotId() != null && booking.getSlotId().equals(docSlotId))
                                        || (booking.getStartTime() != null && booking.getStartTime().equals(docStartTime));

                                if (isSameSlot && status != null
                                        && !Constants.STATUS_CANCELLED.equals(status)
                                        && !Constants.STATUS_REJECTED.equals(status)) {
                                    userAlreadyBooked = true;
                                    break;
                                }
                            }
                        }

                        if (userAlreadyBooked) {
                            result.setValue(Resource.error("Bạn đã đặt sân vào khung giờ này rồi! Không thể tạo đơn trùng lặp.", null));
                            return;
                        }

                        // Tiếp tục kiểm tra xem sân có bị người khác đặt không
                        checkPitchSlotAndExecuteBatch(booking, pitch, result);
                    })
                    .addOnFailureListener(e -> {
                        // Nếu lỗi query đơn của user, vẫn chuyển sang bước kiểm tra sân
                        checkPitchSlotAndExecuteBatch(booking, pitch, result);
                    });
        } else {
            checkPitchSlotAndExecuteBatch(booking, pitch, result);
        }
    }

    private void checkPitchSlotAndExecuteBatch(Booking booking, Pitch pitch,
                                                MutableLiveData<Resource<Booking>> result) {
        mDb.collection(Constants.COL_BOOKINGS)
                .whereEqualTo("pitchId", pitch.getPitchId())
                .whereEqualTo("bookingDate", booking.getBookingDate())
                .get()
                .addOnSuccessListener(snapshot -> {
                    boolean isBooked = false;
                    if (snapshot != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                            String status = doc.getString("bookingStatus");
                            String docSlotId = doc.getString("slotId");
                            String docStartTime = doc.getString("startTime");

                            boolean isSameSlot = (booking.getSlotId() != null && booking.getSlotId().equals(docSlotId))
                                    || (booking.getStartTime() != null && booking.getStartTime().equals(docStartTime));

                            if (isSameSlot && status != null
                                    && !Constants.STATUS_CANCELLED.equals(status)
                                    && !Constants.STATUS_REJECTED.equals(status)) {
                                isBooked = true;
                                break;
                            }
                        }
                    }

                    if (isBooked) {
                        result.setValue(Resource.error("Khung giờ này đã có người khác đặt. Vui lòng chọn khung giờ khác!", null));
                        return;
                    }

                    // Nếu hợp lệ -> thực hiện WriteBatch
                    executeBookingWriteBatch(booking, pitch, result);
                })
                .addOnFailureListener(e -> {
                    // Nếu lỗi query (ví dụ mất mạng/timeout), vẫn tiến hành đặt sân
                    executeBookingWriteBatch(booking, pitch, result);
                });
    }

    private void executeBookingWriteBatch(Booking booking, Pitch pitch,
                                          MutableLiveData<Resource<Booking>> result) {
        // Auto-generate bookingId
        String bookingId = mDb.collection(Constants.COL_BOOKINGS).document().getId();
        booking.setBookingId(bookingId);
        booking.setBookingStatus(Constants.STATUS_PENDING);
        booking.setMatchStatus(Constants.MATCH_UPCOMING);
        booking.setPaymentStatus(Constants.PAYMENT_UNPAID);

        // ★ GẮN OWNER ID — chìa khóa cho Marketplace model
        booking.setPitchOwnerId(pitch.getOwnerId());

        // Dùng WriteBatch để ghi nhiều document cùng lúc (atomic)
        WriteBatch batch = mDb.batch();

        // 1. Tạo booking document
        batch.set(mDb.collection(Constants.COL_BOOKINGS).document(bookingId), booking);

        // 2. Cập nhật availability của sân (khóa slot lại, trạng thái "pending")
        Map<String, Object> slotDetails = new HashMap<>();
        slotDetails.put("status", Constants.STATUS_PENDING);
        slotDetails.put("bookingId", bookingId);

        Map<String, Object> slotsMap = new HashMap<>();
        slotsMap.put(booking.getSlotId(), slotDetails);

        Map<String, Object> dateSlotUpdate = new HashMap<>();
        dateSlotUpdate.put("date", booking.getBookingDate());
        dateSlotUpdate.put("slots", slotsMap);

        batch.set(
                mDb.collection(Constants.COL_PITCHES)
                   .document(pitch.getPitchId())
                   .collection(Constants.SUB_AVAILABILITY)
                   .document(booking.getBookingDate()),
                dateSlotUpdate,
                com.google.firebase.firestore.SetOptions.merge()
        );

        // 3. Lưu bookingId vào bookingHistory của user
        if (booking.getCustomerId() != null) {
            Map<String, String> historyRef = new HashMap<>();
            historyRef.put("bookingRef", bookingId);
            batch.set(
                    mDb.collection(Constants.COL_USERS)
                       .document(booking.getCustomerId())
                       .collection(Constants.SUB_BOOKING_HISTORY)
                       .document(bookingId),
                    historyRef
            );
        }

        // 4. ★ FCM queue — thông báo cho Owner có đơn mới
        if (pitch != null && pitch.getOwnerId() != null && !pitch.getOwnerId().isEmpty()) {
            Map<String, Object> fcm = new HashMap<>();
            fcm.put("type", "new_booking");
            fcm.put("bookingId", bookingId);
            fcm.put("customerId", booking.getCustomerId());
            fcm.put("customerName", booking.getCustomerName());
            fcm.put("pitchName", booking.getPitchName());
            fcm.put("time", booking.getStartTime());
            fcm.put("date", booking.getBookingDate());
            fcm.put("ownerId", pitch.getOwnerId());
            fcm.put("processed", false);
            fcm.put("createdAt", FieldValue.serverTimestamp());
            batch.set(mDb.collection("fcm_queue").document(), fcm);
        }

        // Commit tất cả cùng lúc
        batch.commit()
                .addOnSuccessListener(unused ->
                        result.setValue(Resource.success(booking))
                )
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(
                                "Đặt sân thất bại: " + e.getMessage(), null))
                );
    }

    /**
     * Overload cho backward compatibility — nếu không cần pitch info.
     * Sẽ KHÔNG gắn pitchOwnerId (chỉ dùng khi Admin tự tạo đơn).
     */
    public void createBooking(Booking booking,
                              MutableLiveData<Resource<Booking>> result) {
        Pitch dummyPitch = new Pitch();
        dummyPitch.setOwnerId("");
        createBooking(booking, dummyPitch, result);
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

        bookingListListener = mDb.collection(Constants.COL_BOOKINGS)
                .whereEqualTo("customerId", customerId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
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

        WriteBatch batch = mDb.batch();

        // 1. Cập nhật bookingStatus = cancelled
        Map<String, Object> bookingUpdate = new HashMap<>();
        bookingUpdate.put("bookingStatus", Constants.STATUS_CANCELLED);

        batch.update(
                mDb.collection(Constants.COL_BOOKINGS).document(booking.getBookingId()),
                bookingUpdate
        );

        // 2. Trả slot về "available"
        Map<String, Object> slotDetails = new HashMap<>();
        slotDetails.put("status", "available");
        slotDetails.put("bookingId", null);

        Map<String, Object> slotsMap = new HashMap<>();
        slotsMap.put(booking.getSlotId(), slotDetails);

        Map<String, Object> slotRestore = new HashMap<>();
        slotRestore.put("date", booking.getBookingDate());
        slotRestore.put("slots", slotsMap);

        batch.set(
                mDb.collection(Constants.COL_PITCHES)
                   .document(booking.getPitchId())
                   .collection(Constants.SUB_AVAILABILITY)
                   .document(booking.getBookingDate()),
                slotRestore,
                com.google.firebase.firestore.SetOptions.merge()
        );

        batch.commit()
                .addOnSuccessListener(unused ->
                        result.setValue(Resource.success(true))
                )
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(
                                "Hủy đơn thất bại: " + e.getMessage(), false))
                );
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

        // 1. Lấy danh sách khung giờ từ subcollection timeSlots của sân
        mDb.collection(Constants.COL_PITCHES)
           .document(pitchId)
           .collection(Constants.SUB_TIME_SLOTS)
           .whereEqualTo("isActive", true)
           .get()
           .addOnSuccessListener(slotsSnapshot -> {
               List<TimeSlot> slots = new java.util.ArrayList<>();
               if (slotsSnapshot != null && !slotsSnapshot.isEmpty()) {
                   slots = slotsSnapshot.toObjects(TimeSlot.class);
                   for (int i = 0; i < slots.size(); i++) {
                       if (slots.get(i).getSlotId() == null) {
                           slots.get(i).setSlotId(slotsSnapshot.getDocuments().get(i).getId());
                       }
                   }
               }

               // Fallback: nếu chưa thiết lập khung giờ riêng, dùng khung giờ chuẩn
               if (slots.isEmpty()) {
                   slots = getDefaultTimeSlots();
               }

               // Sắp xếp theo startTime tăng dần
               java.util.Collections.sort(slots, (a, b) -> {
                   if (a.getStartTime() == null || b.getStartTime() == null) return 0;
                   return a.getStartTime().compareTo(b.getStartTime());
               });

               final List<TimeSlot> baseSlots = slots;

               // 2. Lắng nghe Realtime các đơn đặt (bookings) của sân trong ngày dateString
               slotsListener = mDb.collection(Constants.COL_BOOKINGS)
                  .whereEqualTo("pitchId", pitchId)
                  .whereEqualTo("bookingDate", dateString)
                  .addSnapshotListener((bookingsSnapshot, error) -> {
                      if (error != null) {
                          android.util.Log.e("BookingRepository", "Lỗi snapshot bookings: " + error.getMessage());
                      }

                      // Đọc đồng thời subcollection availability của sân trong ngày dateString
                      mDb.collection(Constants.COL_PITCHES)
                         .document(pitchId)
                         .collection(Constants.SUB_AVAILABILITY)
                         .document(dateString)
                         .get()
                         .addOnCompleteListener(availTask -> {
                             java.util.Set<String> bookedSlotIds = new java.util.HashSet<>();
                             java.util.Set<String> bookedStartTimes = new java.util.HashSet<>();

                             // a. Lấy slot đã đặt từ COL_BOOKINGS
                             if (bookingsSnapshot != null && !bookingsSnapshot.isEmpty()) {
                                 for (com.google.firebase.firestore.DocumentSnapshot doc : bookingsSnapshot.getDocuments()) {
                                     String status = doc.getString("bookingStatus");
                                     if (!Constants.STATUS_CANCELLED.equals(status)
                                             && !Constants.STATUS_REJECTED.equals(status)) {
                                         String slotId = doc.getString("slotId");
                                         String startTime = doc.getString("startTime");
                                         if (slotId != null && !slotId.trim().isEmpty()) bookedSlotIds.add(slotId.trim());
                                         if (startTime != null && !startTime.trim().isEmpty()) bookedStartTimes.add(startTime.trim());
                                     }
                                 }
                             }

                             // b. Lấy slot đã đặt từ subcollection availability
                             if (availTask.isSuccessful() && availTask.getResult() != null && availTask.getResult().exists()) {
                                 com.google.firebase.firestore.DocumentSnapshot availDoc = availTask.getResult();
                                 Object slotsObj = availDoc.get("slots");
                                 if (slotsObj instanceof Map) {
                                     @SuppressWarnings("unchecked")
                                     Map<String, Object> slotsMap = (Map<String, Object>) slotsObj;
                                     for (Map.Entry<String, Object> entry : slotsMap.entrySet()) {
                                         String sId = entry.getKey();
                                         if (entry.getValue() instanceof Map<?, ?> slotData) {
                                             String st = (String) slotData.get("status");
                                             if (st != null && !Constants.STATUS_CANCELLED.equals(st)
                                                     && !Constants.STATUS_REJECTED.equals(st)) {
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
                             String todayStr = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                     .format(new java.util.Date());
                             String currentTimeStr = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                     .format(new java.util.Date());
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

                                 String slotCompareTime = slot.getEndTime() != null ? slot.getEndTime() : slot.getStartTime();
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
                  });
           })
           .addOnFailureListener(e -> {
               List<TimeSlot> defaultSlots = getDefaultTimeSlots();
               result.setValue(Resource.success(defaultSlots));
           });
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    public void detachBookingListListener() {
        if (bookingListListener != null) {
            bookingListListener.remove();
            bookingListListener = null;
        }
    }
}
