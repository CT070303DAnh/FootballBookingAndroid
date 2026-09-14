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
        String availabilityPath = Constants.COL_PITCHES + "/" + booking.getPitchId()
                + "/" + Constants.SUB_AVAILABILITY + "/" + booking.getBookingDate();

        Map<String, Object> slotUpdate = new HashMap<>();
        slotUpdate.put("status", "pending");
        slotUpdate.put("bookingId", bookingId);

        batch.set(
                mDb.document(availabilityPath + "/" + booking.getSlotId()),
                slotUpdate,
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
        if (pitch.getOwnerId() != null && !pitch.getOwnerId().isEmpty()) {
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
        String availabilityDocPath = Constants.COL_PITCHES + "/" + booking.getPitchId()
                + "/" + Constants.SUB_AVAILABILITY + "/" + booking.getBookingDate();

        Map<String, Object> slotRestore = new HashMap<>();
        slotRestore.put("status", "available");
        slotRestore.put("bookingId", null);

        batch.update(
                mDb.document(availabilityDocPath + "/" + booking.getSlotId()),
                slotRestore
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

    /**
     * Lấy tất cả timeSlots của sân, kết hợp với availability của ngày được chọn.
     * Trả về list TimeSlot với status: "available", "booked", "pending".
     */
    public void getAvailableSlots(String pitchId, String dateString,
                                  MutableLiveData<Resource<List<TimeSlot>>> result) {
        result.setValue(Resource.loading(null));

        // Lấy tất cả timeslots của sân
        mDb.collection(Constants.COL_PITCHES)
           .document(pitchId)
           .collection(Constants.SUB_TIME_SLOTS)
           .whereEqualTo("isActive", true)
           .orderBy("startTime")
           .get()
           .addOnSuccessListener(slotsSnapshot -> {
               List<TimeSlot> slots = slotsSnapshot.toObjects(TimeSlot.class);

               // Sau đó lấy availability của ngày đó
               mDb.collection(Constants.COL_PITCHES)
                  .document(pitchId)
                  .collection(Constants.SUB_AVAILABILITY)
                  .document(dateString)
                  .get()
                  .addOnSuccessListener(availDoc -> {
                      if (availDoc.exists()) {
                          Map<String, Object> availMap = availDoc.getData();
                          // Map slotId → status, cập nhật lại list
                          if (availMap != null) {
                              for (TimeSlot slot : slots) {
                                  Object slotData = availMap.get(slot.getSlotId());
                                  // Mặc định "available" nếu không có trong availability doc
                              }
                          }
                      }
                      result.setValue(Resource.success(slots));
                  })
                  .addOnFailureListener(e -> {
                      // Ngày chưa có record → tất cả slots đều available
                      result.setValue(Resource.success(slots));
                  });
           })
           .addOnFailureListener(e ->
                   result.setValue(Resource.error("Lỗi tải khung giờ: " + e.getMessage(), null))
           );
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
