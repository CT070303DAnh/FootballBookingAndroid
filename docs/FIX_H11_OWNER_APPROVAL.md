# H11 — Duyệt chủ sân và điều hướng tài khoản

- Login và Splash dùng chung [RoleDestination](../app/src/main/java/com/example/footballbooking/ui/auth/RoleDestination.java). Chỉ customer, owner và admin có dashboard; owner_pending quay về đăng nhập với thông báo chờ duyệt. Role thiếu/lạ bị từ chối thay vì rơi vào customer.
- Pending/role không hợp lệ được đăng xuất trước khi hiển thị thông báo. Đăng ký thành công cũng kết thúc phiên trước khi về Login, phù hợp luồng yêu cầu đăng nhập lại. Bỏ lời hứa duyệt trong 24 giờ vì chưa có cam kết thời gian thực tế.
- [SplashActivity](../app/src/main/java/com/example/footballbooking/ui/auth/SplashActivity.java) hủy callback/animation khi bị hủy và chặn điều hướng lặp. Lỗi kiểm tra hồ sơ được chuyển sang Login để người dùng biết lý do, không chỉ lỗi bị khóa.
- Admin có nút “Duyệt chủ sân” với hộp thoại xác nhận, chỉ bật cho owner_pending đang active. ViewModel giữ trạng thái đang xử lý và chặn bấm gửi lặp. Kết quả thao tác duyệt tách khỏi thao tác admin khác, được tiêu thụ sau khi hiển thị.
- [AdminRepository](../app/src/main/java/com/example/footballbooking/data/repository/AdminRepository.java) đọc lại hồ sơ bằng transaction, chỉ đổi owner_pending/active sang owner. Hồ sơ vừa bị khóa hoặc vừa được người khác duyệt sẽ báo lỗi. Không tự mở khóa, không ghi đè thông tin cá nhân. UID lấy từ document ID khi tải danh sách, tránh cập nhật nhầm theo trường uid cũ.
- Không đổi Rules: quy tắc C1 đã cho admin active cập nhật role/status và cấm customer/owner tự nâng quyền. Không deploy hay thay đổi tài khoản Firebase thật.

## Kiểm chứng

- 10/10 trường hợp định tuyến vai trò đạt khi compile/run bằng `javac/java` độc lập.
- Layout XML parse thành công; `git diff --check` đạt.
- Chưa xác minh giao diện trên thiết bị hoặc transaction duyệt qua Android runtime.
- Lệnh `:app:compileDebugJavaWithJavac --offline` chỉ in “Starting a Gradle Daemon” rồi không tiến triển qua nhiều lần kiểm tra; đã dừng lượt build này. Chưa tới compile source, không kết luận build đạt. Các lượt C1–C3 trước gặp lỗi tải toolchain JDK 21 HTTP 400.

Test định tuyến độc lập Java:

```powershell
javac -encoding UTF-8 -d tests/auth/classes app/src/main/java/com/example/footballbooking/ui/auth/RoleDestination.java tests/auth/RoleDestinationCheck.java
java -cp tests/auth/classes RoleDestinationCheck
```

Cần kiểm tra UI khi môi trường Android build hoạt động:

1. Đăng ký chủ sân: trở về Login, không giữ phiên đăng nhập.
2. Đăng nhập pending và mở lại app bằng phiên pending cũ: không vào customer/owner dashboard.
3. Admin duyệt tài khoản active: hàng chuyển thành OWNER sau khi commit; người đó đăng nhập lại vào owner dashboard.
4. Tài khoản pending bị khóa: nút duyệt bị vô hiệu; khóa từ thiết bị khác trong lúc hộp thoại đang mở phải khiến transaction thất bại.
5. Hai admin cùng duyệt: chỉ thao tác đầu thành công; thao tác sau không ghi đè hồ sơ.
6. Xoay/tái tạo màn hình hoặc rời Splash trong lúc chờ: không mở dashboard lặp hay chạy callback sau destroy.
7. Customer/owner/admin hợp lệ tiếp tục vào đúng màn hình; role null/lạ không nhận quyền customer mặc định.

Giới hạn: đợt này sửa H11 và callback Splash liên quan; chưa triển khai cơ chế theo dõi thu hồi quyền tức thời ở mọi dashboard, chưa sửa toàn bộ lỗi lifecycle của các Fragment khác. Việc duyệt chủ sân không tự chuẩn bị policy lịch C2 cho sân mới.
