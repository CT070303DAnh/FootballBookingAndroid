# 🔥 Hướng dẫn cấu hình Firebase (Giao diện mới nhất) — Chi tiết từng bước

> Dành cho dự án **Football Pitch Booking AI** (Android / Java)  
> *Đã cập nhật theo giao diện Firebase Console mới nhất (Product Categories / Project Shortcuts).*

---

## 🧭 Tổng quan giao diện Firebase Console mới

Trên thanh điều hướng bên trái của Firebase Console hiện tại:
- 🔍 **Search for products**: Thanh tìm kiếm nhanh mọi dịch vụ.
- ⚙️ **Settings** (Bánh răng): Menu thả xuống gồm `General`, `Usage and billing`, `Service accounts`...
- 📌 **Project shortcuts**: Lối tắt đến các dịch vụ chính (`Authentication`, `Firestore`, `Storage`...).
- 📂 **Product categories**:
  - **Databases & Storage** ➔ Firestore, Storage, Realtime Database.
  - **DevOps & Engagement** ➔ Cloud Messaging (FCM), Crashlytics...
  - **Security** ➔ App Check, Security Rules.
  - **Analytics** ➔ Dashboard, Events...

---

## 📋 Bảng tổng hợp các dịch vụ cần cấu hình

| # | Dịch vụ | Mục đích | Bắt buộc? | Vị trí menu mới |
|---|---------|----------|-----------|-----------------|
| 1 | **Authentication** | Đăng ký / Đăng nhập (Email + Password) | ✅ **Bắt buộc** | `Project shortcuts` hoặc `Search` |
| 2 | **Cloud Firestore** | Database lưu trữ User, Sân, Đơn đặt... | ✅ **Bắt buộc** | `Databases & Storage` ➔ `Firestore` |
| 3 | **Cloud Messaging (FCM)** | Thông báo đẩy (Push Notification) | ✅ Khuyên dùng | `DevOps & Engagement` ➔ `Cloud Messaging` |
| 4 | **Firebase Storage** | Upload file ảnh sân, avatar | ⚠️ **Tùy chọn** (Miễn phí nếu dùng link ảnh online) | `Databases & Storage` ➔ `Storage` |

---

## BƯỚC 1: Tạo Firebase Project & Thêm App Android

### 1.1. Tạo Project mới
1. Truy cập **[https://console.firebase.google.com](https://console.firebase.google.com)**.
2. Click **"Add project"** (hoặc "Thêm dự án").
3. Nhập tên project (ví dụ: `AndroidBookingFootballAI`).
4. **Google Analytics**: Có thể Bật (Enable) hoặc Tắt tùy ý ➔ Click **"Create project"** ➔ Đợi hoàn tất.

### 1.2. Đăng ký Android App & Download `google-services.json`
1. Tại trang chủ project, click vào biểu tượng **Android** (🤖).
2. Điền thông tin ứng dụng:
   - **Android package name**: `com.example.footballbooking` *(⚠️ Bắt buộc phải nhập chính xác tên này)*.
   - **App nickname**: `Football Booking AI`.
   - **Debug signing certificate SHA-1**: *(Tùy chọn, cần nếu dùng Google Sign-In hoặc Maps)*.
3. Click **"Register app"**.
4. Click **"Download google-services.json"** ➔ Lưu file về máy.
5. **Copy file `google-services.json` vừa tải vào thư mục `app/` của dự án:**
   ```
   D:\AndroidAdvance\app\google-services.json
   ```
   *(Kiểm tra đúng đường dẫn: file nằm cùng cấp với `app/build.gradle.kts`)*.
6. Trên màn hình Firebase Console, bấm **"Next"** qua các bước hướng dẫn SDK (vì code dự án đã tích hợp sẵn) ➔ bấm **"Continue to console"**.

> 💡 **Mẹo:** Nếu sau này cần tải lại file `google-services.json` hoặc thêm SHA-1:  
> Bấm vào **Settings** (⚙️) ở menu trái ➔ chọn **General** ➔ cuộn xuống mục **"Your apps"**.

---

## BƯỚC 2: Bật Authentication (Đăng nhập Email/Mật khẩu)

1. Ở menu bên trái, tìm trong **Project shortcuts** chọn **Authentication** (hoặc gõ `Authentication` vào thanh **Search for products**).
2. Click nút **"Get started"**.
3. Tại tab **"Sign-in method"**:
   - Chọn mục **"Email/Password"**.
   - Bật công tắc dòng đầu tiên: **Enable (Email/Password)**.
   - Dòng thứ 2 *(Email link - passwordless sign-in)*: **Để Tắt**.
   - Bấm **"Save"**.

> ✅ **Hoàn thành:** Trạng thái của provider **Email/Password** hiển thị là **Enabled**.

---

## BƯỚC 3: Tạo Cloud Firestore Database

1. Ở menu bên trái, vào **Databases & Storage** ➔ chọn **Firestore** (hoặc bấm vào **Firestore** trong mục **Project shortcuts**).
2. Click nút **"Create database"**.
3. **Database ID & Location**:
   - Database ID: giữ nguyên `(default)`.
   - **Location**: Chọn **`asia-southeast1` (Singapore)** hoặc `asia-east2` (Hồng Kông) để có tốc độ truy vấn nhanh nhất tại Việt Nam.
   - Bấm **"Next"**.
4. **Security rules**:
   - Chọn **"Start in test mode"** (để dễ dàng test và seed dữ liệu ban đầu).
   - Bấm **"Create"** (hoặc "Enable") và đợi 15-30 giây để hệ thống tạo xong Database.

### Cập nhật Rules bảo mật (Sau khi tạo xong Firestore):
1. Chuyển sang tab **"Rules"** trên trang Firestore.
2. Sao chép toàn bộ nội dung từ file [firestore.rules](file:///D:/AndroidAdvance/firestore.rules) trong thư mục gốc của dự án.
3. Dán vào trình soạn thảo Rules trên Firebase Console và bấm **"Publish"**.

---

## BƯỚC 4: Về Firebase Storage (Lưu trữ ảnh)

Khi vào menu **Databases & Storage** ➔ **Storage**, bạn sẽ thấy thông báo:  
*"Storage requires a billing account (Blaze). Upgrade your project now..."*

### 👉 Bạn nên chọn phương án nào?

#### **Phương án 1 (Khuyên dùng - Hoàn toàn MIỄN PHÍ, KHÔNG cần thẻ Visa):**
- **BỎ QUA bước bật Storage này!** Bạn cứ giữ nguyên gói **Spark ($0/month)**.
- Ứng dụng đặt sân bóng đã được tích hợp thư viện **Glide** tải ảnh cực mượt từ **đường link ảnh online trực tiếp (Image URL)**.
- Dữ liệu mẫu (Seeder) và hệ thống hiển thị sân bóng, banner, avatar mặc định đều dùng URL online trực tiếp, nên bạn **không cần bật Storage** app vẫn chạy hoàn hảo 100%!

#### **Phương án 2 (Nếu bạn CÓ thẻ Visa / Mastercard):**
1. Bấm nút cam **"Upgrade project"** ➔ Chọn gói **Blaze**.
2. Nhập thông tin thẻ thanh toán (Google sẽ tặng $300 credit dùng thử).
3. Quay lại mục **Storage** ➔ Bấm **"Get started"** ➔ Chọn **Start in test mode** ➔ Chọn Location (`asia-southeast1`) ➔ Bấm **"Done"**.

---

## BƯỚC 5: Kiểm tra Cloud Messaging (FCM API V1)

Dùng để gửi thông báo trạng thái đặt sân (Duyệt/Hủy/Đơn mới).

1. Ở menu bên trái, vào **Product categories** ➔ **DevOps & Engagement** ➔ chọn **Cloud Messaging** (hoặc gõ `Cloud Messaging` trên thanh tìm kiếm).
2. Kiểm tra trạng thái API:
   - Bấm vào icon **Settings** (⚙️) ở menu trái ➔ chọn **General**.
   - Hoặc vào mục **Service accounts** để xem thông tin Firebase Admin SDK.
   - Mặc định, các project Firebase tạo mới đều đã tự động kích hoạt **Firebase Cloud Messaging API (V1)**.

---

## BƯỚC 6: Kiểm tra tổng thể & Khởi chạy ứng dụng

### 6.1. Kiểm tra vị trí file
Đảm bảo file cấu hình đã được đặt đúng vị trí:
```
D:\AndroidAdvance\app\google-services.json
```

### 6.2. Tạo dữ liệu mẫu để trải nghiệm (Seeder)
Để có sẵn các sân bóng đẹp mắt, lịch thi đấu, dịch vụ và tài khoản test:
1. Mở file [SplashActivity.java](file:///D:/AndroidAdvance/app/src/main/java/com/example/footballbooking/ui/auth/SplashActivity.java).
2. Thêm dòng sau vào trong hàm `onCreate()`:
   ```java
   FirestoreSeeder.seedAll();
   ```
3. Chạy app một lần để dữ liệu tự động đồng bộ lên Cloud Firestore.
4. Sau khi dữ liệu đã lên Firestore, bạn có thể xóa dòng `FirestoreSeeder.seedAll();` đi.

### 6.3. Tài khoản test có sẵn sau khi Seed:
| Vai trò | Email đăng nhập | Mật khẩu | Màn hình tương ứng |
|---------|-----------------|----------|-------------------|
| **Quản trị viên (Admin)** | `admin@test.com` | `123456` | Admin Dashboard (Quản lý toàn diện, biểu đồ) |
| **Chủ sân (Owner)** | `owner@test.com` | `123456` | Owner Dashboard (Quản lý sân, duyệt đơn) |
| **Khách đặt sân (Customer)** | `customer@test.com` | `123456` | Customer App (Trang chủ, Bản đồ, AI Gợi ý, Hồ sơ) |

---

## ❓ Câu hỏi & Lỗi thường gặp

### 1. `google-services.json is missing` khi Build:
- **Khắc phục**: File phải nằm chính xác trong thư mục `app/` (`D:\AndroidAdvance\app\google-services.json`), không được đặt ở thư mục gốc của project.

### 2. Không tạo được Storage vì bắt nhập thẻ Visa:
- **Khắc phục**: Không cần tạo Storage. Ứng dụng hỗ trợ hiển thị ảnh qua URL trực tiếp, giữ nguyên gói Spark $0 hoàn toàn bình thường.

### 3. Lỗi `PERMISSION_DENIED` khi đọc ghi Firestore:
- **Khắc phục**: Vào **Firestore** ➔ Tab **Rules** ➔ Kiểm tra xem đã dán nội dung từ [firestore.rules](file:///D:/AndroidAdvance/firestore.rules) và bấm **Publish** chưa.
