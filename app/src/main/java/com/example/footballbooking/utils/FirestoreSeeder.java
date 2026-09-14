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
 *   FirestoreSeeder.seedAll();
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
    // Admin:    admin@test.com
    // Owner:    owner@test.com
    // Customer: customer@test.com

    public static void seedAll() {
        Log.d(TAG, "========= SEEDING DATA =========");
        seedUsers();
        seedPitches();
        seedServices();
        seedTimeSlots();
        Log.d(TAG, "========= SEED COMPLETE =========");
    }

    // ============================================================
    // USERS
    // ============================================================
    private static void seedUsers() {
        FirebaseAuth auth = FirebaseAuth.getInstance();

        // 1. Admin
        createUser(auth, "admin@test.com", "123456", user -> {
            user.put("displayName", "Admin System");
            user.put("phoneNumber", "0900 000 000");
            user.put("role", Constants.ROLE_ADMIN);
            user.put("avatarUrl", "");
            db.collection(Constants.COL_USERS).document((String) user.get("uid")).set(user);
            Log.d(TAG, "✅ Admin created: admin@test.com / 123456");
        });

        // 2. Owner
        createUser(auth, "owner@test.com", "123456", user -> {
            user.put("displayName", "Nguyễn Văn Bình");
            user.put("phoneNumber", "0912 345 678");
            user.put("role", Constants.ROLE_OWNER);
            user.put("avatarUrl", "");
            db.collection(Constants.COL_USERS).document((String) user.get("uid")).set(user);
            Log.d(TAG, "✅ Owner created: owner@test.com / 123456");
        });

        // 3. Customer
        createUser(auth, "customer@test.com", "123456", user -> {
            user.put("displayName", "Trần Minh Anh");
            user.put("phoneNumber", "0987 654 321");
            user.put("role", Constants.ROLE_CUSTOMER);
            user.put("avatarUrl", "");
            db.collection(Constants.COL_USERS).document((String) user.get("uid")).set(user);
            Log.d(TAG, "✅ Customer created: customer@test.com / 123456");
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
                            .addOnFailureListener(signInErr ->
                                    Log.e(TAG, "SignIn error for " + email + ": " + signInErr.getMessage()));
                });
    }

    // ============================================================
    // PITCHES
    // ============================================================
    private static void seedPitches() {
        // Lấy UID của owner sau khi tạo xong
        // (Do Firebase Auth async, ta dùng approach khác: tạo trực tiếp với ownerId giả định)
        // Trong thực tế, nên lấy UID từ callback ở trên.

        String[][] pitchData = {
                // {name, address, type, price, lat, lng, description}
                {"Sân Mỹ Đình A", "Đường Lê Đức Thọ, Nam Từ Liêm, Hà Nội", "7", "200000",
                        "21.0285", "105.7823", "Sân cỏ nhân tạo chất lượng cao, hệ thống đèn LED chiếu sáng tốt."},
                {"Sân Thống Nhất B", "138 Đào Duy Từ, Quận 10, TP.HCM", "5", "150000",
                        "10.7769", "106.6603", "Sân mini 5 người, mặt cỏ đẹp, có quán nước và bãi đỗ xe."},
                {"Sân Hàng Đẫy Sport", "Trịnh Hoài Đức, Đống Đa, Hà Nội", "11", "500000",
                        "21.0239", "105.8296", "Sân 11 người tiêu chuẩn, phù hợp giải đấu phong trào."},
                {"Green Football Club", "45 Nguyễn Hữu Thọ, Quận 7, TP.HCM", "7", "250000",
                        "10.7324", "106.7222", "Sân cỏ nhân tạo thế hệ 4, mái che toàn sân, phòng thay đồ tiện nghi."},
                {"Sân Tân Phú Center", "Lũy Bán Bích, Tân Phú, TP.HCM", "5", "120000",
                        "10.7941", "106.6298", "Sân mini giá rẻ, mở cửa từ 6h sáng đến 23h đêm."},
        };

        String[][] amenitiesData = {
                {"🅿️ Bãi đỗ xe", "💡 Đèn đêm", "💧 Nước uống"},
                {"🅿️ Bãi đỗ xe", "💧 Nước uống"},
                {"🅿️ Bãi đỗ xe", "🚿 Phòng tắm", "💡 Đèn đêm", "💧 Nước uống"},
                {"🅿️ Bãi đỗ xe", "🚿 Phòng tắm", "💡 Đèn đêm", "🏠 Mái che", "💧 Nước uống"},
                {"💡 Đèn đêm", "💧 Nước uống"},
        };

        for (int i = 0; i < pitchData.length; i++) {
            String[] p = pitchData[i];
            String pitchId = "pitch_" + (i + 1);

            Map<String, Object> pitch = new HashMap<>();
            pitch.put("pitchId", pitchId);
            pitch.put("name", p[0]);
            pitch.put("address", p[1]);
            pitch.put("type", p[2]);
            pitch.put("basePrice", Double.parseDouble(p[3]));
            pitch.put("location", new GeoPoint(
                    Double.parseDouble(p[4]), Double.parseDouble(p[5])));
            pitch.put("description", p[6]);
            pitch.put("status", Constants.PITCH_AVAILABLE);
            pitch.put("rating", 4.0f + (float)(Math.random() * 1.0));
            pitch.put("totalReviews", (int)(Math.random() * 50) + 5);
            pitch.put("imageUrls", new ArrayList<>());
            pitch.put("amenities", Arrays.asList(amenitiesData[i]));
            // Owner sẽ được gắn khi owner đăng nhập lần đầu
            pitch.put("ownerId", "");  // Cập nhật sau khi seed users
            pitch.put("ownerName", "Nguyễn Văn Bình");
            pitch.put("ownerPhone", "0912 345 678");
            pitch.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            pitch.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

            db.collection(Constants.COL_PITCHES).document(pitchId).set(pitch)
                    .addOnSuccessListener(v -> Log.d(TAG, "✅ Pitch: " + p[0]))
                    .addOnFailureListener(e -> Log.e(TAG, "❌ Pitch error: " + e.getMessage()));
        }
    }

    // ============================================================
    // SERVICES (dịch vụ kèm theo)
    // ============================================================
    private static void seedServices() {
        String[][] services = {
                {"Thuê bóng", "30000", "Bóng Nike Premier League chính hãng"},
                {"Áo đấu (bộ 10)", "100000", "Bộ 10 áo đấu 2 màu phân biệt"},
                {"Nước uống (thùng 12)", "50000", "Thùng 12 chai nước khoáng 500ml"},
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

            db.collection(Constants.COL_SERVICES).document(serviceId).set(svc)
                    .addOnSuccessListener(v -> Log.d(TAG, "✅ Service: " + s[0]))
                    .addOnFailureListener(e -> Log.e(TAG, "❌ Service error: " + e.getMessage()));
        }
    }

    // ============================================================
    // TIME SLOTS (khung giờ cho mỗi sân)
    // ============================================================
    private static void seedTimeSlots() {
        // Tạo slots: 07:00 → 22:00, mỗi slot 1.5 giờ
        String[][] slots = {
                {"slot_1",  "07:00", "08:30", "0"},    // 0 = không phụ phí
                {"slot_2",  "08:30", "10:00", "0"},
                {"slot_3",  "10:00", "11:30", "0"},
                {"slot_4",  "14:00", "15:30", "0"},
                {"slot_5",  "15:30", "17:00", "0"},
                {"slot_6",  "17:00", "18:30", "30000"}, // Giờ cao điểm
                {"slot_7",  "18:30", "20:00", "50000"}, // Giờ VÀNG
                {"slot_8",  "20:00", "21:30", "50000"}, // Giờ VÀNG
                {"slot_9",  "21:30", "23:00", "20000"},
        };

        for (int pitchIdx = 1; pitchIdx <= 5; pitchIdx++) {
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
            Log.d(TAG, "✅ TimeSlots for " + pitchId + " (" + slots.length + " slots)");
        }
    }
}
