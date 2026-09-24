package com.example.footballbooking.utils;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FirestoreSeeder — Tạo dữ liệu mẫu để test ứng dụng.
 *
 * CÁCH DÙNG:
 * Gọi 1 lần duy nhất khi cần seed dữ liệu (ví dụ từ SplashActivity):
 * FirestoreSeeder.seedAll();
 *
 * SAU KHI SEED:
 * - 3 tài khoản: admin / owner / customer
 * - 5 sân bóng (thuộc owner)
 * - Time slots cho mỗi sân (7h → 22h, mỗi slot 1.5h)
 * - 3 dịch vụ kèm theo
 *
 * ⚠️ XÓA FILE NÀY TRƯỚC KHI DEPLOY PRODUCTION!
 */
public class FirestoreSeeder {

    private static final String TAG = "Seeder";
    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ===== TÀI KHOẢN TEST =====
    // Tất cả dùng chung mật khẩu: 123456
    // Admin: admin@test.com
    // Owner: owner@test.com
    // Customer: customer@test.com

    public static void seedAll() {
        Log.d(TAG, "========= SEEDING DATA =========");
        seedUsers(ownerList -> {
            loginAs("admin@test.com", () -> {
                seedPitches(ownerList, () -> {
                    seedServices(() -> {
                        seedTimeSlots(() -> {
                            loginAs("customer@test.com", () -> {
                                seedBookings();
                                Log.d(TAG, "========= SEED COMPLETE =========");
                            });
                        });
                    });
                });
            });
        });
    }

    private static void loginAs(String email, Runnable onSuccess) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, "123456")
                .addOnSuccessListener(authResult -> {
                    Log.d(TAG, "Switched user to " + email);
                    onSuccess.run();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Switch user failed for " + email + ": " + e.getMessage()));
    }

    // ============================================================
    // USERS
    // ============================================================
    private static void seedUsers(java.util.function.Consumer<List<Map<String, Object>>> onComplete) {
        FirebaseAuth auth = FirebaseAuth.getInstance();

        String[][] users = {
                { "admin@test.com", "Admin System", Constants.ROLE_ADMIN, "0900 000 000" },
                { "owner1@test.com", "Nguyễn Văn Bình", Constants.ROLE_OWNER, "0912 345 678" },
                { "owner2@test.com", "Trần Quang Đại", Constants.ROLE_OWNER, "0988 111 222" },
                { "owner3@test.com", "Lê Minh Phát", Constants.ROLE_OWNER, "0977 333 444" },
                { "customer@test.com", "Trần Minh Anh", Constants.ROLE_CUSTOMER, "0987 654 321" }
        };

        List<Map<String, Object>> ownerList = new ArrayList<>();
        seedUserSequentially(auth, users, 0, ownerList, onComplete);
    }

    private static void seedUserSequentially(FirebaseAuth auth, String[][] users, int index, 
                                             List<Map<String, Object>> ownerList,
                                             java.util.function.Consumer<List<Map<String, Object>>> onComplete) {
        if (index >= users.length) {
            onComplete.accept(ownerList);
            return;
        }

        String[] u = users[index];
        createUser(auth, u[0], "123456", userMap -> {
            String uid = (String) userMap.get("uid");
            if (uid != null) {
                userMap.put("displayName", u[1]);
                userMap.put("role", u[2]);
                userMap.put("phoneNumber", u[3]);
                userMap.put("avatarUrl", "");

                if (Constants.ROLE_OWNER.equals(u[2])) {
                    ownerList.add(userMap);
                }

                db.collection(Constants.COL_USERS).document(uid).set(userMap).addOnCompleteListener(task -> {
                    seedUserSequentially(auth, users, index + 1, ownerList, onComplete);
                });
            } else {
                seedUserSequentially(auth, users, index + 1, ownerList, onComplete);
            }
        });
    }

    private static void createUser(FirebaseAuth auth, String email, String password,
            java.util.function.Consumer<Map<String, Object>> onSuccess) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    if (result.getUser() != null) {
                        Map<String, Object> user = new HashMap<>();
                        user.put("uid", result.getUser().getUid());
                        user.put("email", email);
                        onSuccess.accept(user);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "User " + email + " already exists in Auth, syncing Firestore doc...");
                    auth.signInWithEmailAndPassword(email, password)
                            .addOnSuccessListener(result -> {
                                if (result.getUser() != null) {
                                    Map<String, Object> user = new HashMap<>();
                                    user.put("uid", result.getUser().getUid());
                                    user.put("email", email);
                                    onSuccess.accept(user);
                                }
                            })
                            .addOnFailureListener(signInErr -> {
                                Log.e(TAG, "SignIn error for " + email + ": " + signInErr.getMessage());
                                onSuccess.accept(new HashMap<>()); // Pass empty to continue
                            });
                });
    }

    // ============================================================
    // PITCHES
    // ============================================================
    private static void seedPitches(List<Map<String, Object>> owners, Runnable onComplete) {
        if (owners.isEmpty()) {
            onComplete.run();
            return;
        }

        String[][] pitchData = {
                            { "Sân Mỹ Đình A", "Đường Lê Đức Thọ, Nam Từ Liêm, Hà Nội", "7", "200000", "21.0285",
                                    "105.7823", "Sân cỏ nhân tạo chất lượng cao." },
                            { "Sân VSA2", "Lê Trọng Tấn, Thanh Xuân, Hà Nội", "7", "250000", "21.0044", "105.8082",
                                    "Sân trung tâm quận Thanh Xuân." },
                            { "Sân Cường Quốc", "Hoàng Cầu, Đống Đa, Hà Nội", "5", "150000", "21.0315", "105.8256",
                                    "Mặt cỏ mới nâng cấp." },
                            { "Sân Bách Khoa", "Tạ Quang Bửu, Hai Bà Trưng, Hà Nội", "11", "500000", "21.0063",
                                    "105.8427", "Sân vận động sinh viên lớn nhất." },
                            { "Sân Thủy Lợi", "Chùa Bộc, Đống Đa, Hà Nội", "7", "220000", "21.0069", "105.8249",
                                    "Sân chất lượng cao, thường xuyên tổ chức giải." },
                            { "Sân An Dương", "An Dương, Tây Hồ, Hà Nội", "5", "180000", "21.0475", "105.8444",
                                    "Sân mát mẻ gần hồ Tây." },
                            { "Sân Phúc Xá", "Phúc Xá, Ba Đình, Hà Nội", "7", "200000", "21.0425", "105.8458",
                                    "Không gian thoáng, giá cả hợp lý." },
                            { "Sân Hoàng Mai", "Đền Lừ, Hoàng Mai, Hà Nội", "7", "160000", "20.9856", "105.8425",
                                    "Khu dân cư đông đúc, nhộn nhịp." },
                            { "Sân Viettel", "Trường Chinh, Đống Đa, Hà Nội", "11", "600000", "20.9984", "105.8197",
                                    "Sân cỏ thật tiêu chuẩn thi đấu." },
                            { "Sân Không Quân", "Lê Trọng Tấn, Thanh Xuân, Hà Nội", "7", "220000", "20.9995",
                                    "105.8286", "Hệ thống chiếu sáng đạt chuẩn." },
                            { "Sân Khương Đình", "Khương Đình, Thanh Xuân, Hà Nội", "5", "120000", "20.9897",
                                    "105.8132", "Sân mini phù hợp sinh viên." },
                            { "Sân Lĩnh Nam", "Lĩnh Nam, Hoàng Mai, Hà Nội", "7", "180000", "20.9831", "105.8753",
                                    "Bãi đỗ xe siêu rộng rãi." },
                            { "Sân Trần Thái Tông", "Trần Thái Tông, Cầu Giấy, Hà Nội", "5", "150000", "21.0278",
                                    "105.7925", "Mặt cỏ êm, cao su đầy đủ." },
                            { "Sân Dịch Vọng", "Dịch Vọng, Cầu Giấy, Hà Nội", "7", "200000", "21.0392", "105.7915",
                                    "Gần khu ăn uống sầm uất." },
                            { "Sân Nghĩa Tân", "Nghĩa Tân, Cầu Giấy, Hà Nội", "5", "140000", "21.0461", "105.7932",
                                    "Giá cực kỳ ưu đãi buổi sáng." },
                            { "Sân Mai Dịch", "Mai Dịch, Cầu Giấy, Hà Nội", "7", "190000", "21.0378", "105.7758",
                                    "Gần nhiều trường đại học." },
                            { "Sân Quan Hoa", "Nguyễn Đình Hoàn, Cầu Giấy, Hà Nội", "5", "150000", "21.0354",
                                    "105.8021", "Mát mẻ, gần bờ sông Tô Lịch." },
                            { "Sân Tôn Thất Tùng", "Tôn Thất Tùng, Đống Đa, Hà Nội", "7", "230000", "21.0028",
                                    "105.8291", "Gần đại học Y Hà Nội." },
                            { "Sân Tây Sơn", "Tây Sơn, Đống Đa, Hà Nội", "5", "160000", "21.0112", "105.8234",
                                    "Trung tâm thành phố, tiện đi lại." },
                            { "Sân Láng", "Đường Láng, Đống Đa, Hà Nội", "7", "210000", "21.0187", "105.8087",
                                    "Sân sạch sẽ, có bảo vệ trông xe." },
                            { "Sân Yên Hòa", "Yên Hòa, Cầu Giấy, Hà Nội", "5", "170000", "21.0225", "105.7954",
                                    "Mới thay cỏ nhân tạo năm ngoái." },
                            { "Sân Trung Kính", "Trung Kính, Cầu Giấy, Hà Nội", "7", "250000", "21.0189", "105.7931",
                                    "Có khán đài nhỏ cho khán giả." },
                            { "Sân Mễ Trì", "Mễ Trì Thượng, Nam Từ Liêm, Hà Nội", "5", "130000", "21.0145", "105.7821",
                                    "Chất lượng dịch vụ tốt." },
                            { "Sân Lê Đức Thọ", "Lê Đức Thọ, Nam Từ Liêm, Hà Nội", "11", "550000", "21.0321",
                                    "105.7681", "Sân kích thước lớn, thoáng mát." },
                            { "Sân Mỹ Đình B", "Phú Đô, Nam Từ Liêm, Hà Nội", "7", "190000", "21.0232", "105.7635",
                                    "Gần sân vận động quốc gia." },
                            { "Sân Cổ Nhuế", "Cổ Nhuế, Bắc Từ Liêm, Hà Nội", "5", "140000", "21.0542", "105.7794",
                                    "Mật độ cao su dày, hạn chế chấn thương." },
                            { "Sân Xuân Đỉnh", "Xuân Đỉnh, Bắc Từ Liêm, Hà Nội", "7", "200000", "21.0631", "105.7915",
                                    "Không gian xung quanh yên tĩnh." },
                            { "Sân Kim Mã", "Kim Mã, Ba Đình, Hà Nội", "5", "180000", "21.0304", "105.8242",
                                    "Khu trung tâm, nhịp sống hiện đại." },
                            { "Sân Đội Cấn", "Đội Cấn, Ba Đình, Hà Nội", "7", "240000", "21.0356", "105.8221",
                                    "Lưới rào bao quanh kiên cố." },
                            { "Sân Bách Thảo", "Hoàng Hoa Thám, Ba Đình, Hà Nội", "5", "170000", "21.0401", "105.8283",
                                    "Cây xanh rợp bóng mát xung quanh." }
                    };

                    String[][] allAmenities = {
                            { "🅿️ Bãi đỗ xe", "💡 Đèn đêm", "💧 Nước uống" },
                            { "🅿️ Bãi đỗ xe", "💧 Nước uống" },
                            { "🅿️ Bãi đỗ xe", "🚿 Phòng tắm", "💡 Đèn đêm", "💧 Nước uống" },
                            { "🅿️ Bãi đỗ xe", "🚿 Phòng tắm", "💡 Đèn đêm", "🏠 Mái che", "💧 Nước uống" },
                            { "💡 Đèn đêm", "💧 Nước uống" }
                    };

                    java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);

                    for (int i = 0; i < pitchData.length; i++) {
                        String[] p = pitchData[i];
                        String pitchId = "pitch_" + (i + 1);

                        // Chia đều sân cho các owners
                        Map<String, Object> owner = owners.get(i % owners.size());

                        Map<String, Object> pitch = new HashMap<>();
                        pitch.put("pitchId", pitchId);
                        pitch.put("name", p[0]);
                        pitch.put("address", p[1]);
                        pitch.put("type", p[2]);
                        pitch.put("basePrice", Double.parseDouble(p[3]));
                        pitch.put("location", new GeoPoint(Double.parseDouble(p[4]), Double.parseDouble(p[5])));
                        pitch.put("description", p[6]);
                        pitch.put("status", Constants.PITCH_AVAILABLE);
                        pitch.put("rating", 4.0f + (float) (Math.random() * 1.0));
                        pitch.put("totalReviews", (int) (Math.random() * 50) + 5);
                        pitch.put("imageUrls", new ArrayList<>(java.util.Collections.singletonList(
                                "https://images.unsplash.com/photo-1518605368461-1e1e1fd51d20?q=80&w=800")));
                        pitch.put("amenities", Arrays.asList(allAmenities[i % allAmenities.length]));
                        pitch.put("ownerId", owner.get("uid"));
                        pitch.put("ownerName", owner.get("displayName"));
                        pitch.put("ownerPhone", owner.get("phoneNumber"));
                        pitch.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                        pitch.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

                        db.collection(Constants.COL_PITCHES).document(pitchId).set(pitch)
                                .addOnCompleteListener(task -> {
                                    if (count.incrementAndGet() == pitchData.length) {
                                        onComplete.run();
                                    }
                                });
                    }
    }

    // ============================================================
    // SERVICES (dịch vụ kèm theo)
    // ============================================================
    private static void seedServices(Runnable onComplete) {
        String[][] services = {
                { "Thuê bóng", "30000", "Bóng Nike Premier League chính hãng" },
                { "Áo đấu (bộ 10)", "100000", "Bộ 10 áo đấu 2 màu phân biệt" },
                { "Nước uống (thùng 12)", "50000", "Thùng 12 chai nước khoáng 500ml" },
        };

        for (int i = 0; i < services.length; i++) {
            String[] s = services[i];
            String serviceId = "service_" + (i + 1);

            Map<String, Object> svc = new HashMap<>();
            svc.put("serviceId", serviceId);
            svc.put("name", s[0]);
            svc.put("price", Double.parseDouble(s[1]));
            svc.put("description", s[2]);
            svc.put("isActive", true);

            db.collection(Constants.COL_SERVICES).document(serviceId).set(svc);
        }
        onComplete.run();
    }

    // ============================================================
    // TIME SLOTS (khung giờ cho mỗi sân)
    // ============================================================
    private static void seedTimeSlots(Runnable onComplete) {
        String[][] slots = {
                { "slot_1", "07:00", "08:30", "0" },
                { "slot_2", "08:30", "10:00", "0" },
                { "slot_3", "10:00", "11:30", "0" },
                { "slot_4", "14:00", "15:30", "0" },
                { "slot_5", "15:30", "17:00", "0" },
                { "slot_6", "17:00", "18:30", "30000" },
                { "slot_7", "18:30", "20:00", "50000" },
                { "slot_8", "20:00", "21:30", "50000" },
                { "slot_9", "21:30", "23:00", "20000" },
        };

        for (int pitchIdx = 1; pitchIdx <= 30; pitchIdx++) {
            String pitchId = "pitch_" + pitchIdx;
            for (String[] slot : slots) {
                Map<String, Object> slotData = new HashMap<>();
                slotData.put("slotId", slot[0]);
                slotData.put("startTime", slot[1]);
                slotData.put("endTime", slot[2]);
                slotData.put("surcharge", Double.parseDouble(slot[3]));
                slotData.put("isActive", true);
                slotData.put("label", slot[1] + " - " + slot[2]);

                db.collection(Constants.COL_PITCHES)
                        .document(pitchId)
                        .collection(Constants.SUB_TIME_SLOTS)
                        .document(slot[0])
                        .set(slotData);
            }
        }
        onComplete.run();
    }

    // ============================================================
    // BOOKINGS
    // ============================================================
    public static void seedBookings() {
        Log.d(TAG, "========= SEEDING BOOKINGS =========");
        db.collection(Constants.COL_USERS).whereEqualTo("role", Constants.ROLE_CUSTOMER).get()
                .addOnSuccessListener(customerSnap -> {
                    if (customerSnap.isEmpty())
                        return;
                    String customerId = customerSnap.getDocuments().get(0).getId();
                    String customerName = customerSnap.getDocuments().get(0).getString("displayName");
                    String customerPhone = customerSnap.getDocuments().get(0).getString("phoneNumber");

                    db.collection(Constants.COL_USERS).whereEqualTo("role", Constants.ROLE_OWNER).get()
                            .addOnSuccessListener(ownerSnap -> {
                                if (ownerSnap.isEmpty())
                                    return;
                                String ownerId = ownerSnap.getDocuments().get(0).getId();

                                db.collection(Constants.COL_PITCHES).whereEqualTo("ownerId", ownerId).limit(1).get()
                                        .addOnSuccessListener(pitchSnap -> {
                                            if (pitchSnap.isEmpty())
                                                return;
                                            String pitchId = pitchSnap.getDocuments().get(0).getId();
                                            String pitchName = pitchSnap.getDocuments().get(0).getString("name");
                                            String pitchAddress = pitchSnap.getDocuments().get(0).getString("address");

                                            // Booking 1: Pending (Tiền mặt)
                                            Map<String, Object> b1 = new HashMap<>();
                                            String bId1 = "booking_1_" + System.currentTimeMillis();
                                            b1.put("bookingId", bId1);
                                            b1.put("customerId", customerId);
                                            b1.put("customerName", customerName);
                                            b1.put("customerPhone", customerPhone);
                                            b1.put("pitchId", pitchId);
                                            b1.put("pitchName", pitchName);
                                            b1.put("pitchAddress", pitchAddress);
                                            b1.put("pitchType", "5");
                                            b1.put("pitchOwnerId", ownerId);
                                            // Lấy ngày mai
                                            java.util.Calendar cal = java.util.Calendar.getInstance();
                                            cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
                                            String tomorrow = new java.text.SimpleDateFormat("yyyy-MM-dd",
                                                    java.util.Locale.getDefault()).format(cal.getTime());
                                            b1.put("bookingDate", tomorrow);
                                            b1.put("slotId", "slot_6");
                                            b1.put("startTime", "17:00");
                                            b1.put("endTime", "18:30");
                                            b1.put("basePrice", 200000.0);
                                            b1.put("surcharge", 30000.0);
                                            b1.put("totalServicePrice", 0.0);
                                            b1.put("totalAmount", 230000.0);
                                            b1.put("paymentStatus", Constants.PAYMENT_UNPAID);
                                            b1.put("paymentMethod", Constants.METHOD_CASH);
                                            b1.put("bookingStatus", Constants.STATUS_PENDING);
                                            b1.put("matchStatus", Constants.MATCH_UPCOMING);
                                            b1.put("createdAt",
                                                    com.google.firebase.firestore.FieldValue.serverTimestamp());
                                            b1.put("updatedAt",
                                                    com.google.firebase.firestore.FieldValue.serverTimestamp());
                                            db.collection(Constants.COL_BOOKINGS).document(bId1).set(b1);

                                            // Booking 2: Approved (VNPay)
                                            Map<String, Object> b2 = new HashMap<>(b1);
                                            String bId2 = "booking_2_" + System.currentTimeMillis();
                                            cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
                                            String nextTomorrow = new java.text.SimpleDateFormat("yyyy-MM-dd",
                                                    java.util.Locale.getDefault()).format(cal.getTime());
                                            b2.put("bookingId", bId2);
                                            b2.put("bookingDate", nextTomorrow);
                                            b2.put("paymentMethod", Constants.METHOD_VNPAY);
                                            b2.put("paymentStatus", Constants.PAYMENT_PAID);
                                            b2.put("bookingStatus", Constants.STATUS_APPROVED);
                                            db.collection(Constants.COL_BOOKINGS).document(bId2).set(b2);

                                            Log.d(TAG, "✅ Bookings seeded successfully!");
                                        });
                            });
                });
    }
}
