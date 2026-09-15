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
        result.setValue(Resource.loading(null));

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
        Map<String, Object> dateSlotUpdate = new HashMap<>();
        dateSlotUpdate.put("date", booking.getBookingDate());
        dateSlotUpdate.put("slots." + booking.getSlotId() + ".status", Constants.STATUS_PENDING);
        dateSlotUpdate.put("slots." + booking.getSlotId() + ".bookingId", bookingId);

        batch.set(
                mDb.collection(Constants.COL_PITCHES)
                   .document(booking.getPitchId())
                   .collection(Constants.SUB_AVAILABILITY)
                   .document(booking.getBookingDate()),
                dateSlotUpdate,
                com.google.firebase.firestore.SetOptions.merge()
        );

        // 3. Lưu bookingId vào bookingHistory của user
        Map<String, String> historyRef = new HashMap<>();
        historyRef.put("bookingRef", bookingId);
        batch.set(
                mDb.collection(Constants.COL_USERS)
                   .document(booking.getCustomerId())
                   .collection(Constants.SUB_BOOKING_HISTORY)
                   .document(bookingId),
                historyRef
        );

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
        Map<String, Object> slotRestore = new HashMap<>();
        slotRestore.put("slots." + booking.getSlotId() + ".status", "available");
        slotRestore.put("slots." + booking.getSlotId() + ".bookingId", null);

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

    /**
     * Lấy tất cả timeSlots của sân, kết hợp với các đơn đặt trong ngày.
     * Nếu sân chưa có timeSlots riêng trong subcollection, tự động dùng bộ khung giờ chuẩn.
     * Đánh dấu chính xác slot nào "Còn trống", "Đã đặt", hoặc "Đã qua".
     */
    public void getAvailableSlots(String pitchId, String dateString,
                                  MutableLiveData<Resource<List<TimeSlot>>> result) {
        result.setValue(Resource.loading(null));

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

               final List<TimeSlot> finalSlots = slots;

               // 2. Query trực tiếp các đơn đặt (bookings) của sân trong ngày dateString
               mDb.collection(Constants.COL_BOOKINGS)
                  .whereEqualTo("pitchId", pitchId)
                  .whereEqualTo("bookingDate", dateString)
                  .get()
                  .addOnSuccessListener(bookingsSnapshot -> {
                      java.util.Set<String> bookedSlotIds = new java.util.HashSet<>();
                      java.util.Set<String> bookedStartTimes = new java.util.HashSet<>();

                      if (bookingsSnapshot != null) {
                          for (com.google.firebase.firestore.DocumentSnapshot doc : bookingsSnapshot.getDocuments()) {
                              String status = doc.getString("bookingStatus");
                              // Coi như đã đặt nếu trạng thái là pending hoặc approved
                              if (Constants.STATUS_PENDING.equals(status)
                                      || Constants.STATUS_APPROVED.equals(status)) {
                                  String slotId = doc.getString("slotId");
                                  String startTime = doc.getString("startTime");
                                  if (slotId != null) bookedSlotIds.add(slotId);
                                  if (startTime != null) bookedStartTimes.add(startTime);
                              }
                          }
                      }

                      // 3. Kiểm tra giờ đã qua nếu là ngày hôm nay
                      String todayStr = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                              .format(new java.util.Date());
                      String currentTimeStr = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                              .format(new java.util.Date());
                      boolean isToday = dateString.equals(todayStr);

                      for (TimeSlot slot : finalSlots) {
                          boolean isBooked = (slot.getSlotId() != null && bookedSlotIds.contains(slot.getSlotId()))
                                  || (slot.getStartTime() != null && bookedStartTimes.contains(slot.getStartTime()));

                          if (isBooked) {
                              slot.setStatus("booked");
                              slot.setAvailable(false);
                          } else if (isToday && slot.getEndTime() != null
                                  && slot.getEndTime().compareTo(currentTimeStr) <= 0) {
                              slot.setStatus("past");
                              slot.setAvailable(false);
                          } else {
                              slot.setStatus("available");
                              slot.setAvailable(true);
                          }
                      }

                      result.setValue(Resource.success(finalSlots));
                  })
                  .addOnFailureListener(e -> {
                      // Nếu lỗi query bookings, vẫn trả về danh sách slot cho user
                      result.setValue(Resource.success(finalSlots));
                  });
           })
           .addOnFailureListener(e -> {
               // Fallback nếu không đọc được subcollection
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
