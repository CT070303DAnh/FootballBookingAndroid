# Đợt sửa 1 — C1: Ngăn tự nâng quyền

## Vấn đề và thay đổi

Trước đây, user được sửa toàn bộ document hồ sơ của mình. Vì rules cũng đọc role
từ document này, một request trực tiếp có thể đổi customer thành admin. Hai nhánh
khôi phục hồ sơ Android còn suy luận role từ chuỗi email.

Code hoàn chỉnh sau sửa nằm tại:

- [firestore.rules](../firestore.rules): chỉ cho tự tạo customer/owner_pending với
  UID đúng và status active. Self-update chỉ được đổi displayName, phoneNumber,
  avatarUrl, fcmToken và updatedAt. Việc thêm/xóa trường cũng bị kiểm tra.
- [AuthRepository.java](../app/src/main/java/com/example/footballbooking/data/repository/AuthRepository.java):
  hai đường đọc hồ sơ dùng cùng cơ chế, đọc từ server; nếu thiếu thì chỉ khôi phục
  customer. Không lấy quyền từ email, không điền số điện thoại giả, không báo thành
  công trước khi ghi xong. Transaction đọc lại trước khi tạo để không ghi đè hồ sơ
  vừa được tạo hoặc duyệt. Kết quả cũ không được trả như một phiên đăng nhập mới.

Admin active vẫn được đổi role/status hợp lệ. Admin blocked không được sử dụng
quyền admin. UID/email/createdAt không thể bị sửa bằng các đường cập nhật này.
Hồ sơ admin cũ chưa có status vẫn được nhận là active để tương thích dữ liệu cũ;
nên chuẩn hóa status bằng công cụ quản trị tin cậy khi rà soát dữ liệu.

## Ảnh hưởng đến các luồng khác

- Đăng ký customer và owner_pending hiện tại vẫn hợp lệ.
- Cập nhật FCM token vẫn hợp lệ.
- Khóa/mở khóa user trong AdminRepository vẫn hợp lệ.
- Khôi phục hồ sơ giờ cần mạng. Mất mạng trả lỗi và không tự tạo hồ sơ từ cache.
- Khi hồ sơ bị thiếu, email có chữ admin/owner cũng chỉ nhận customer. Muốn khôi
  phục đặc quyền phải đối chiếu và cấp lại từ phía quản trị tin cậy.
- C1 không sửa H11: màn hình duyệt owner_pending và cách Splash xử lý role này
  vẫn cần một đợt sửa tiếp theo. Test chứng minh rules cho phép admin duyệt, không
  có nghĩa UI đã có nút duyệt.
- Seeder Android cũ tự ghi role admin/owner sẽ bị rules mới từ chối. Không bật
  lại seeder này với rules đã siết, và không nới rules để chạy seeder. Dùng một
  dự án test/Emulator hoặc công cụ quản trị tin cậy để tạo dữ liệu có đặc quyền.

## Áp dụng lên Firebase

Thay đổi trong repository **không tự triển khai rules** và không sửa dữ liệu cloud.

1. Sao lưu rules đang triển khai và xác định đúng Firebase project.
2. Rà soát danh sách tài khoản đang có role admin/owner. Rules mới không tự phát
   hiện hoặc hạ quyền những hồ sơ đã bị nâng quyền trước đó.
3. Admin đầu tiên: đăng ký tài khoản bình thường, xác minh UID trong Firebase
   Authentication, rồi dùng Firebase Console hoặc Admin SDK ở môi trường tin cậy
   để cấp role admin và status active cho đúng document users/{UID}.
4. Đưa nội dung root firestore.rules đã review lên Firebase Console, kiểm tra
   biên dịch rồi Publish. Không dùng firebase.test.json để deploy.

C2/C3 và các quyền đọc rộng từ audit vẫn chưa được xử lý trong đợt C1. Không coi
việc áp dụng bản sửa này là đã hoàn tất bảo mật toàn hệ thống.

## Kiểm thử

Test tự động và cách chạy: [tests/firestore](../tests/firestore/README.md).
Mọi fixture admin trong test được tạo bằng quyền bypass **chỉ trong Emulator**.

Kiểm tra thủ công trên Firebase project thử nghiệm sau khi áp dụng rules:

1. Đăng ký khách mới: hồ sơ customer/active được tạo, đăng nhập bình thường.
2. Đăng ký chủ sân: hồ sơ owner_pending/active được tạo, chưa được tự nâng owner.
3. Với client customer, thử cập nhật role, uid, status hoặc xóa role: đều phải bị
   PERMISSION_DENIED. Không dùng Console để thử thao tác bị cấm, vì Console có
   quyền quản trị và không mô phỏng quyền client.
4. Cập nhật tên/điện thoại/avatar hoặc fcmToken bằng đúng user: phải thành công.
5. Admin khóa rồi mở khóa một tài khoản thường: vẫn thành công.
6. Tài khoản Firebase Auth thử nghiệm chưa có hồ sơ, email chứa admin/owner:
   đăng nhập phải khôi phục customer, không có đặc quyền.
7. Tắt mạng lúc đọc/khôi phục hồ sơ: nhận lỗi, không có thông báo khôi phục thành công.
8. Thử hai request khôi phục đồng thời: một hồ sơ hợp lệ; không ghi đè role/status
   nếu document đã tồn tại khi transaction đọc lại.

Build Android cần chạy lại ở môi trường có JDK theo cấu hình Gradle. Test rules
không thay thế kiểm tra compile hoặc kiểm thử giao diện Android.

## Kết quả kiểm chứng ngày 09/10/2026

- `npm test`: 20/20 test đạt trên Firestore Emulator; không gọi Firebase thật.
- `git diff --check`: đạt.
- `gradlew.bat :app:compileDebugJavaWithJavac --offline --console=plain`: chưa đến
  bước compile Java. Gradle yêu cầu JetBrains JDK 21 nhưng URL Foojay tải toolchain
  trả HTTP 400. Chưa xác nhận build Android đạt.
- Rules chưa được triển khai lên Firebase thật.
