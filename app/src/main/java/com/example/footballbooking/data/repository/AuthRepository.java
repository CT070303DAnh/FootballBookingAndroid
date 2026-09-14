package com.example.footballbooking.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.example.footballbooking.data.model.User;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * AuthRepository — Single source of truth cho Authentication.
 *
 * PATTERN: Repository pattern
 * - ViewModel gọi các method của Repository
 * - Repository giao tiếp với Firebase Auth & Firestore
 * - Kết quả trả về qua MutableLiveData<Resource<T>>
 *
 * KHÔNG dùng callback hell — mọi kết quả đều qua LiveData.
 */
public class AuthRepository {

    private final FirebaseAuth mAuth;
    private final FirebaseFirestore mDb;

    // Singleton pattern — chỉ tạo 1 instance duy nhất
    private static AuthRepository instance;

    private AuthRepository() {
        mAuth = FirebaseAuth.getInstance();
        mDb   = FirebaseFirestore.getInstance();
    }

    public static synchronized AuthRepository getInstance() {
        if (instance == null) {
            instance = new AuthRepository();
        }
        return instance;
    }

    // ==========================================================
    // ĐĂNG KÝ TÀI KHOẢN MỚI
    // ==========================================================

    /**
     * Đăng ký tài khoản mới cho Customer.
     * Flow: Firebase Auth createUser → Lưu User document vào Firestore.
     *
     * @param email       Email người dùng
     * @param password    Mật khẩu (≥ 6 ký tự)
     * @param displayName Tên hiển thị
     * @param phone       Số điện thoại
     * @param result      LiveData nhận kết quả Resource<User>
     */
    public void register(String email, String password, String displayName,
                         String phone, MutableLiveData<Resource<User>> result) {

        // 1. Báo hiệu đang loading
        result.setValue(Resource.loading(null));

        // 2. Tạo tài khoản Firebase Auth
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        result.setValue(Resource.error("Lỗi không xác định khi tạo tài khoản", null));
                        return;
                    }

                    // 3. Tạo User object với role mặc định là "customer"
                    User newUser = new User(
                            firebaseUser.getUid(),
                            email,
                            displayName,
                            phone,
                            Constants.ROLE_CUSTOMER
                    );

                    // 4. Lưu thông tin User vào Firestore
                    saveUserToFirestore(newUser, result);
                })
                .addOnFailureListener(e -> {
                    // Chuyển đổi lỗi Firebase sang tiếng Việt
                    result.setValue(Resource.error(mapAuthError(e.getMessage()), null));
                });
    }

    // ==========================================================
    // ĐĂNG NHẬP
    // ==========================================================

    /**
     * Đăng nhập bằng email và password.
     * Sau khi auth thành công, fetch User document từ Firestore để lấy role.
     *
     * @param result LiveData nhận kết quả Resource<User> (có đầy đủ role)
     */
    public void login(String email, String password,
                      MutableLiveData<Resource<User>> result) {

        result.setValue(Resource.loading(null));

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        result.setValue(Resource.error("Lỗi đăng nhập", null));
                        return;
                    }
                    // Fetch đầy đủ thông tin user (bao gồm role) từ Firestore
                    fetchUserFromFirestore(firebaseUser.getUid(), result);
                })
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(mapAuthError(e.getMessage()), null))
                );
    }

    // ==========================================================
    // KIỂM TRA SESSION HIỆN TẠI
    // ==========================================================

    /**
     * Kiểm tra xem đã có user đăng nhập chưa.
     * Dùng trong SplashActivity để auto-navigate.
     *
     * @param result LiveData nhận kết quả Resource<User> hoặc null nếu chưa đăng nhập
     */
    public void checkCurrentUser(MutableLiveData<Resource<User>> result) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            // Chưa đăng nhập → trả về null data với SUCCESS (không phải ERROR)
            result.setValue(Resource.success(null));
            return;
        }
        // Đã đăng nhập → fetch data từ Firestore
        result.setValue(Resource.loading(null));
        fetchUserFromFirestore(firebaseUser.getUid(), result);
    }

    // ==========================================================
    // ĐĂNG XUẤT
    // ==========================================================

    public void logout() {
        mAuth.signOut();
    }

    // ==========================================================
    // PRIVATE HELPERS
    // ==========================================================

    /** Lưu User document vào Firestore collection "users" */
    private void saveUserToFirestore(User user,
                                     MutableLiveData<Resource<User>> result) {
        mDb.collection(Constants.COL_USERS)
                .document(user.getUid())
                .set(user)
                .addOnSuccessListener(unused ->
                        result.setValue(Resource.success(user))
                )
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(
                                "Tạo tài khoản thành công nhưng không lưu được thông tin: " + e.getMessage(),
                                null))
                );
    }

    /** Fetch User document từ Firestore theo UID */
    private void fetchUserFromFirestore(String uid,
                                        MutableLiveData<Resource<User>> result) {
        mDb.collection(Constants.COL_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        result.setValue(Resource.success(user));
                    } else {
                        // Tự động khôi phục nếu user đã có trong Firebase Auth nhưng chưa có profile Firestore
                        FirebaseUser currentFirebaseUser = mAuth.getCurrentUser();
                        if (currentFirebaseUser != null && uid.equals(currentFirebaseUser.getUid())) {
                            String email = currentFirebaseUser.getEmail() != null ? currentFirebaseUser.getEmail() : "";
                            String role = Constants.ROLE_CUSTOMER;
                            String name = "Khách hàng";
                            if (email.contains("admin")) {
                                role = Constants.ROLE_ADMIN;
                                name = "Admin System";
                            } else if (email.contains("owner")) {
                                role = Constants.ROLE_OWNER;
                                name = "Chủ sân";
                            }
                            User fallbackUser = new User(uid, email, name, "0900 000 000", role);
                            mDb.collection(Constants.COL_USERS).document(uid).set(fallbackUser)
                                    .addOnSuccessListener(v -> result.setValue(Resource.success(fallbackUser)))
                                    .addOnFailureListener(e -> result.setValue(
                                            Resource.error("Lỗi tạo hồ sơ Firestore: " + e.getMessage(), null)));
                        } else {
                            result.setValue(Resource.error("Không tìm thấy thông tin tài khoản", null));
                        }
                    }
                })
                .addOnFailureListener(e ->
                        result.setValue(Resource.error("Lỗi kết nối: " + e.getMessage(), null))
                );
    }

    /**
     * Map lỗi Firebase Auth sang tiếng Việt thân thiện.
     * Firebase trả về error code dạng: "The email address is already in use..."
     */
    private String mapAuthError(String errorMessage) {
        if (errorMessage == null) return "Lỗi không xác định";
        if (errorMessage.contains("email address is already in use"))
            return "Email này đã được sử dụng";
        if (errorMessage.contains("badly formatted"))
            return "Email không đúng định dạng";
        if (errorMessage.contains("password is invalid") || errorMessage.contains("wrong-password"))
            return "Mật khẩu không chính xác";
        if (errorMessage.contains("no user record") || errorMessage.contains("user-not-found"))
            return "Tài khoản không tồn tại";
        if (errorMessage.contains("network"))
            return "Lỗi kết nối mạng";
        if (errorMessage.contains("too-many-requests"))
            return "Quá nhiều yêu cầu. Vui lòng thử lại sau";
        return "Đăng nhập thất bại. Vui lòng thử lại";
    }

    /**
     * Lấy thông tin User hiện tại qua callback (dùng cho Activity/Service không có ViewModel).
     * Ví dụ: OwnerDashboardActivity cần User để init OwnerViewModel.
     *
     * @param callback Nhận Resource<User> — gọi trên main thread.
     */
    public void getCurrentUserProfile(
            java.util.function.Consumer<Resource<User>> callback) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            callback.accept(Resource.error("Chưa đăng nhập", null));
            return;
        }
        mDb.collection(Constants.COL_USERS)
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        callback.accept(Resource.success(doc.toObject(User.class)));
                    } else {
                        String email = firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "";
                        String role = Constants.ROLE_CUSTOMER;
                        String name = "Khách hàng";
                        if (email.contains("admin")) {
                            role = Constants.ROLE_ADMIN;
                            name = "Admin System";
                        } else if (email.contains("owner")) {
                            role = Constants.ROLE_OWNER;
                            name = "Chủ sân";
                        }
                        User fallback = new User(firebaseUser.getUid(), email, name, "0900 000 000", role);
                        mDb.collection(Constants.COL_USERS).document(firebaseUser.getUid()).set(fallback);
                        callback.accept(Resource.success(fallback));
                    }
                })
                .addOnFailureListener(e ->
                        callback.accept(Resource.error(e.getMessage(), null)));
    }

    /**
     * Đăng ký tài khoản Chủ sân.
     * Role = "owner_pending" — cần Admin duyệt trước khi đăng nhập được Dashboard.
     */
    public void registerOwner(String email, String password, String displayName,
                              String phoneNumber,
                              MutableLiveData<Resource<User>> result) {
        result.setValue(Resource.loading(null));
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        result.setValue(Resource.error("Đăng ký thất bại", null));
                        return;
                    }
                    User owner = new User();
                    owner.setUid(firebaseUser.getUid());
                    owner.setEmail(email);
                    owner.setDisplayName(displayName);
                    owner.setPhoneNumber(phoneNumber);
                    owner.setRole(Constants.ROLE_OWNER_PENDING); // Chờ Admin duyệt

                    mDb.collection(Constants.COL_USERS)
                            .document(firebaseUser.getUid())
                            .set(owner)
                            .addOnSuccessListener(v -> result.setValue(Resource.success(owner)))
                            .addOnFailureListener(e -> result.setValue(
                                    Resource.error(e.getMessage(), null)));
                })
                .addOnFailureListener(e -> result.setValue(
                        Resource.error(mapAuthError(e.getMessage()), null)));
    }
}

