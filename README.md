# ⚽ Football Pitch Booking AI

> Ứng dụng đặt sân bóng đá tích hợp AI tư vấn — Android (Java)

[![Android](https://img.shields.io/badge/Platform-Android-green?logo=android)](https://developer.android.com)
[![Java](https://img.shields.io/badge/Language-Java%2017-orange?logo=openjdk)](https://openjdk.org)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-yellow?logo=firebase)](https://firebase.google.com)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

---

## 📋 Mục lục

- [Giới thiệu](#-giới-thiệu)
- [Tính năng](#-tính-năng)
- [Kiến trúc](#-kiến-trúc)
- [Công nghệ sử dụng](#-công-nghệ-sử-dụng)
- [Cấu trúc dự án](#-cấu-trúc-dự-án)
- [Cài đặt & Chạy](#-cài-đặt--chạy)
- [Tài khoản test](#-tài-khoản-test)
- [API & Cấu hình](#-api--cấu-hình)
- [Screenshots](#-screenshots)
- [Đóng góp](#-đóng-góp)

---

## 🎯 Giới thiệu

**Football Pitch Booking AI** là ứng dụng Android cho phép người dùng tìm kiếm, đặt sân bóng đá trực tuyến với hệ thống **AI tư vấn thông minh**. Ứng dụng hoạt động theo mô hình **Marketplace** — nhiều chủ sân có thể đăng ký và quản lý sân của mình trên nền tảng.

### Điểm nổi bật

- 🤖 **AI Weighted Scoring** — Gợi ý sân phù hợp nhất dựa trên vị trí GPS, thời tiết real-time, đánh giá sân và lịch sử đặt
- 💳 **VNPay Sandbox** — Thanh toán trực tuyến qua cổng VNPay
- 📊 **Dashboard Analytics** — Biểu đồ doanh thu với MPAndroidChart (BarChart, PieChart, LineChart)
- 🔔 **FCM Push Notification** — Thông báo realtime khi đơn được duyệt/từ chối
- 🗺️ **Google Maps** — Xem vị trí sân trên bản đồ

---

## ✨ Tính năng

### 👤 Customer (Khách hàng)

| Tính năng | Mô tả |
|-----------|-------|
| 🔐 Đăng nhập / Đăng ký | Firebase Authentication (Email/Password) |
| 🏠 Trang chủ | Danh sách sân, tìm kiếm, skeleton loading |
| 🤖 AI Gợi ý | Weighted Scoring: 30% khoảng cách + 25% thời tiết + 25% đánh giá + 20% lịch sử |
| 📋 Chi tiết sân | Ảnh, thông tin, đánh giá, tiện ích, bản đồ |
| 📅 Đặt sân | Chọn ngày → slot → dịch vụ → xác nhận |
| 💳 Thanh toán | VNPay Sandbox (WebView + HMAC-SHA512) |
| 📜 Lịch sử đặt | Realtime listener, status chips, hủy đơn |
| 🗺️ Bản đồ | Google Maps SDK hiển thị vị trí sân |
| 👤 Hồ sơ | Thông tin cá nhân, đăng xuất |

### 🏟️ Owner (Chủ sân)

| Tính năng | Mô tả |
|-----------|-------|
| 📊 Dashboard | KPI: Tổng sân, đơn chờ, doanh thu tháng |
| ➕ CRUD Sân | Thêm/sửa/đóng sân, upload ảnh Firebase Storage |
| ✅ Duyệt đơn | Duyệt/Từ chối đơn đặt trên sân của mình |
| 📈 Doanh thu | BarChart theo ngày, filter theo tháng |
| 🔔 Thông báo | FCM khi có đơn đặt mới |

### 👑 Admin (Quản trị viên)

| Tính năng | Mô tả |
|-----------|-------|
| 📋 Quản lý đơn | Tabs: Tất cả / Chờ duyệt / Đã duyệt / Từ chối |
| 🏟️ Quản lý sân | CRUD toàn bộ sân trong hệ thống |
| 📊 Báo cáo | BarChart + PieChart + LineChart (MPAndroidChart) |
| 👥 Nhân viên | Quản lý nhân sự (đang phát triển) |
| 🎯 Match Status | Cập nhật trạng thái trận: Sắp đá → Đang đá → Kết thúc |

---

## 🏗 Kiến trúc

### MVVM Architecture

```
┌──────────────────────────────────────────────┐
│                    VIEW                       │
│  Activity / Fragment / Adapter                │
│  (ViewBinding, Material Components)           │
├──────────────────────────────────────────────┤
│                 VIEWMODEL                     │
│  LiveData<Resource<T>> + Business Logic       │
│  (Observe pattern, no Android imports)        │
├──────────────────────────────────────────────┤
│                REPOSITORY                     │
│  Single Source of Truth                       │
│  Firebase Firestore / Auth / Storage          │
│  Retrofit (Weather API)                       │
├──────────────────────────────────────────────┤
│                   MODEL                       │
│  Data classes with Firestore annotations      │
│  @DocumentId, @ServerTimestamp, GeoPoint       │
└──────────────────────────────────────────────┘
```

### Phân quyền 3 Roles (Marketplace)

```
Login → Firestore load User.role
  ├── "admin"         → AdminDashboardActivity   🔵
  ├── "owner"         → OwnerDashboardActivity   🔶
  ├── "owner_pending" → Blocked (chờ Admin duyệt)
  └── "customer"      → CustomerMainActivity     💜
```

### AI Scoring Engine

```
AI_Score = 0.30 × S_distance  +  0.25 × S_weather
         + 0.25 × S_rating    +  0.20 × S_history

S_distance = e^(-0.05 × km)        ← Exponential decay
S_weather  = WeatherData.score()    ← Clear=1.0, Rain=0.2
S_rating   = pitch.rating / 5.0
S_history  = userBookCount / max
```

---

## 🛠 Công nghệ sử dụng

| Công nghệ | Phiên bản | Mục đích |
|-----------|-----------|---------|
| **Java** | 17 | Ngôn ngữ chính |
| **Android SDK** | 24 - 36 | Target API |
| **Firebase Auth** | BOM 33.1.2 | Xác thực email/password |
| **Cloud Firestore** | BOM 33.1.2 | Database realtime |
| **Firebase Storage** | BOM 33.1.2 | Lưu trữ ảnh sân |
| **Firebase Cloud Messaging** | BOM 33.1.2 | Push notification |
| **Google Maps SDK** | 19.0.0 | Bản đồ vị trí sân |
| **Fused Location** | 21.3.0 | GPS lấy vị trí user |
| **Retrofit 2** | 2.11.0 | HTTP client (Weather API) |
| **OkHttp** | 4.12.0 | Networking + logging |
| **Glide** | 4.16.0 | Image loading + caching |
| **MPAndroidChart** | 3.1.0 | Biểu đồ doanh thu |
| **Material Design 3** | Latest | UI Components |
| **Lottie** | 6.4.0 | Animation |
| **Shimmer** | 0.5.0 | Skeleton loading |
| **CircleImageView** | 3.1.0 | Avatar tròn |
| **Navigation Component** | 2.8.0 | Điều hướng Fragment |
| **VNPay Sandbox** | 2.1.0 | Thanh toán trực tuyến |
| **OpenWeatherMap API** | 2.5 | Dữ liệu thời tiết realtime |

---

## 📁 Cấu trúc dự án

```
app/src/main/java/com/example/footballbooking/
├── data/
│   ├── model/              # Data classes (Firestore mapping)
│   │   ├── User.java
│   │   ├── Pitch.java         # + ownerId, ownerName
│   │   ├── Booking.java       # + pitchOwnerId (denormalized)
│   │   ├── TimeSlot.java
│   │   ├── Review.java
│   │   ├── Service.java
│   │   ├── Employee.java
│   │   ├── Notification.java
│   │   └── WeatherData.java   # DTO + Weather scoring
│   ├── remote/
│   │   ├── RetrofitClient.java       # Singleton Retrofit
│   │   └── api/
│   │       └── WeatherApiService.java
│   └── repository/
│       ├── AuthRepository.java       # Login / Register / Profile
│       ├── PitchRepository.java      # CRUD sân (realtime)
│       ├── BookingRepository.java    # Đặt sân (WriteBatch atomic)
│       ├── WeatherRepository.java    # OpenWeather + cache 10min
│       ├── AdminRepository.java      # Admin: all pitches/bookings
│       └── OwnerRepository.java      # Owner: filtered by ownerId
│
├── ui/
│   ├── auth/
│   │   ├── SplashActivity.java
│   │   ├── LoginActivity.java       # Role-based routing
│   │   ├── RegisterActivity.java
│   │   └── AuthViewModel.java
│   ├── customer/
│   │   ├── CustomerMainActivity.java   # BottomNav + NavController
│   │   ├── home/                       # HomeFragment + ViewModel
│   │   ├── pitch/                      # PitchDetail + Map
│   │   ├── booking/                    # Book + History
│   │   ├── ai/                         # AI Suggestion
│   │   ├── payment/                    # VNPay WebView
│   │   └── profile/                    # ProfileFragment
│   ├── owner/                  ← NEW (Marketplace)
│   │   ├── OwnerDashboardActivity.java
│   │   ├── OwnerViewModel.java
│   │   ├── pitch/              # CRUD sân của mình
│   │   ├── booking/            # Duyệt đơn sân mình
│   │   ├── report/             # Doanh thu sân mình
│   │   └── profile/            # Hồ sơ chủ sân
│   ├── admin/
│   │   ├── AdminDashboardActivity.java
│   │   ├── AdminViewModel.java
│   │   ├── booking/            # Quản lý tất cả đơn
│   │   ├── pitch/              # Quản lý tất cả sân
│   │   ├── report/             # Báo cáo toàn hệ thống
│   │   └── staff/              # Quản lý nhân viên
│   └── common/adapter/
│       ├── PitchAdapter.java
│       ├── BookingAdapter.java
│       ├── TimeSlotAdapter.java
│       ├── AiPitchAdapter.java
│       ├── AdminBookingAdapter.java
│       └── AdminPitchAdapter.java
│
├── service/
│   └── MyFirebaseMessagingService.java   # FCM handling
│
└── utils/
    ├── Constants.java          # Tất cả hằng số
    ├── Resource.java           # Resource<T> wrapper
    ├── AiScoringEngine.java    # Weighted Scoring Algorithm
    └── FirestoreSeeder.java    # Test data generator
```

---

## 🚀 Cài đặt & Chạy

### Yêu cầu

- Android Studio Hedgehog (2023.1.1) trở lên
- JDK 17
- Android SDK 36
- Tài khoản Firebase (Blaze plan nếu dùng Cloud Functions)

### Bước 1: Clone dự án

```bash
git clone https://github.com/your-username/football-booking-ai.git
cd football-booking-ai
```

### Bước 2: Cấu hình Firebase

> 📖 **Xem hướng dẫn chi tiết từng bước:** [`docs/FIREBASE_SETUP_GUIDE.md`](docs/FIREBASE_SETUP_GUIDE.md)

1. Tạo project trên [Firebase Console](https://console.firebase.google.com)
2. Thêm Android app với package name: `com.example.footballbooking`
3. Download `google-services.json` → đặt vào thư mục `app/` (cùng cấp với `app/build.gradle.kts`)
4. Vào **Authentication** (trong Project shortcuts) → Bật provider **Email/Password**
5. Vào **Databases & Storage** → Tạo **Firestore** database (location: `asia-southeast1`, chọn Test mode)
6. Copy nội dung file `firestore.rules` dán vào tab **Rules** trên Firestore và nhấn **Publish**
7. *(Tùy chọn)* **Firebase Storage**: Nếu dùng gói Spark miễn phí, có thể bỏ qua bước này vì app hỗ trợ load ảnh từ URL trực tiếp qua Glide
8. **Cloud Messaging** (trong mục DevOps & Engagement): Đã tự động kích hoạt API V1

### Bước 3: Cấu hình API Keys

Tạo file `local.properties` (nếu chưa có) và thêm:

```properties
# Google Maps
GOOGLE_MAPS_API_KEY=your_google_maps_api_key

# OpenWeatherMap (https://openweathermap.org/api)
OPENWEATHER_API_KEY=your_openweather_api_key

# VNPay Sandbox (https://sandbox.vnpayment.vn/merchantv2)
VNPAY_HASH_SECRET=your_vnpay_hash_secret
```

### Bước 4: Deploy Firestore Rules

```bash
# Cài Firebase CLI
npm install -g firebase-tools

# Login
firebase login

# Deploy rules + indexes
firebase deploy --only firestore
```

Hoặc copy nội dung file `firestore.rules` vào Firebase Console → Firestore → Rules.

### Bước 5: Seed dữ liệu test

Mở `SplashActivity.java`, thêm vào `onCreate()`:

```java
// Chỉ gọi 1 LẦN DUY NHẤT, sau đó xóa dòng này
FirestoreSeeder.seedAll();
```

### Bước 6: Build & Run

```bash
# Sync Gradle
File → Sync Project with Gradle Files

# Run
Shift + F10 (hoặc ▶️ Run)
```

---

## 🧪 Tài khoản test

| Role | Email | Mật khẩu | Dashboard |
|------|-------|----------|-----------|
| 👑 Admin | `admin@test.com` | `123456` | Quản lý toàn hệ thống |
| 🏟️ Chủ sân | `owner@test.com` | `123456` | Quản lý sân của mình |
| ⚽ Khách hàng | `customer@test.com` | `123456` | Đặt sân, AI gợi ý |

### Thẻ test VNPay Sandbox

| Thông tin | Giá trị |
|-----------|---------|
| Số thẻ | `9704198526191432198` |
| Tên chủ thẻ | `NGUYEN VAN A` |
| Ngày hết hạn | `07/15` |
| OTP | `123456` |

---

## ⚙️ API & Cấu hình

### OpenWeatherMap API

```
GET https://api.openweathermap.org/data/2.5/weather
    ?lat={lat}&lon={lon}
    &appid={API_KEY}
    &units=metric&lang=vi
```

Cache: 10 phút | Dùng cho AI Scoring Engine

### VNPay Sandbox

```
POST https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
    ?vnp_TmnCode={TMN_CODE}
    &vnp_Amount={amount × 100}
    &vnp_SecureHash={HMAC-SHA512}
    ...
```

Callback: `vnpay://payment_result?vnp_ResponseCode=00`

### Firestore Collections

| Collection | Mô tả |
|-----------|-------|
| `users` | Thông tin người dùng (3 roles) |
| `pitches` | Danh sách sân bóng |
| `pitches/{id}/timeSlots` | Khung giờ của sân |
| `pitches/{id}/availability` | Trạng thái slot theo ngày |
| `bookings` | Đơn đặt sân |
| `reviews` | Đánh giá sân |
| `services` | Dịch vụ kèm theo |
| `employees` | Nhân viên |
| `fcm_queue` | Hàng đợi gửi notification |

---

## 📸 Screenshots

> _Thêm screenshots tại đây sau khi build thành công._

| Customer | Owner | Admin |
|----------|-------|-------|
| Home + AI | Dashboard | Booking Management |
| Booking Flow | Pitch CRUD | Revenue Report |
| Payment | Revenue | Status Charts |

---

## 📈 Thống kê dự án

| Metric | Giá trị |
|--------|---------|
| Java files | 58 |
| Layout XML | 29 |
| Drawable XML | 12 |
| Navigation graphs | 3 |
| Menu XML | 3 |
| **Tổng source files** | **105** |
| Packages | 26 |
| Firestore collections | 9 |
| Composite indexes | 7 |
| Roles | 3 (customer, owner, admin) |

---

## 🤝 Đóng góp

1. Fork dự án
2. Tạo feature branch (`git checkout -b feature/TenTinhNang`)
3. Commit (`git commit -m 'Thêm tính năng XYZ'`)
4. Push (`git push origin feature/TenTinhNang`)
5. Tạo Pull Request

---

## 📄 License

Dự án này được phát triển cho mục đích học tập và đồ án tốt nghiệp.

```
MIT License — Copyright (c) 2025
```

---

<p align="center">
  Made with ❤️ for Vietnamese football lovers ⚽
</p>
