# Rà soát đặt sân, quyền đọc và báo cáo (ngoài chatbot)

## Build

Gradle đã chạy được bằng JetBrains JDK 21 trong cache máy. `assembleDebug` thành công; không cần đổi Gradle, Android Gradle Plugin hoặc thư viện. Tình trạng chờ daemon trong các đợt trước không còn tái hiện ở lần kiểm tra này.

## Đặt sân và khôi phục lựa chọn

- [BookingViewModel](../app/src/main/java/com/example/footballbooking/ui/customer/booking/BookingViewModel.java) theo dõi danh sách slot mới nhất: bỏ slot khi đang tải/lỗi, thay bằng đối tượng mới khi vẫn khả dụng, xóa lựa chọn khi slot đã bị chiếm. Click từ dòng dữ liệu cũ bị bỏ qua.
- Ngày, ID slot và ID dịch vụ được lưu trong SavedStateHandle. Khi tạo lại ViewModel, chỉ khôi phục ID rồi đối chiếu dữ liệu mới; không tin giá/khả dụng đã lưu. Ngày đã qua trở về hôm nay. Sự kiện chọn lại cùng ngày không xóa dịch vụ.
- ViewModel không gửi thêm yêu cầu nếu đang tạo đơn hoặc đã thành công. Chỉ xác nhận khi lịch sân đã đạt policy v2 và dịch vụ tải thành công.
- [BookingActivity](../app/src/main/java/com/example/footballbooking/ui/customer/booking/BookingActivity.java) xóa các dòng giờ cũ khi tải/lỗi; cập nhật lại lựa chọn và tổng tiền. Hộp thoại xác nhận đối chiếu ngày, slot, tổng tiền và điều kiện xác nhận trước khi gửi.
- Calendar chọn ngày theo Asia/Ho_Chi_Minh. Ghi chú và radio thanh toán tiếp tục dùng cơ chế lưu view state của Android.

Đây không phải cơ chế exactly-once xuyên process death. Nếu app bị đóng hoặc mất kết nối trong lúc tạo đơn, cần kiểm tra lịch sử trước khi đặt lại. Transaction C2 vẫn bảo vệ khóa lịch khỏi tạo hai đơn chiếm cùng slot.

## Quyền truy cập

- [Rules](../firestore.rules): hồ sơ chỉ người sở hữu hoặc admin đọc; chỉ admin liệt kê hồ sơ. Cho phép chủ tài khoản đọc hồ sơ của mình khi bị khóa để luồng xác thực biết trạng thái bị khóa.
- Đơn đặt chỉ customer của đơn, owner sở hữu đơn hoặc admin đang hoạt động được đọc. Không cho customer truy vấn toàn bộ collection.
- Tài khoản bị khóa không đọc lịch sân/dịch vụ/review, ghi dịch vụ/review, sửa hồ sơ hoặc truy cập notification/history qua client. Các vai trò owner/customer kiểm tra cả trạng thái active.
- [BookingRepository](../app/src/main/java/com/example/footballbooking/data/repository/BookingRepository.java) theo dõi trực tiếp availability, không tải đơn chứa tên/số điện thoại khách để tính giờ trống.

**Điều kiện triển khai:** cập nhật Android cùng Rules và hoàn thành kiểm tra/migration availability C2 trước khi mở đặt sân policy v2. Dữ liệu cũ chưa migration không được coi là lịch có thể đặt. Chưa deploy Rules hoặc sửa dữ liệu Firebase thật trong đợt này. SDK Admin phía backend không bị ràng buộc bởi Rules này.

## Báo cáo

[RevenuePolicy](../app/src/main/java/com/example/footballbooking/utils/RevenuePolicy.java) dùng chung cho admin/owner: chỉ cộng đơn `paid` và `approved`, đúng tháng đá, số tiền hữu hạn không âm. Đây là doanh thu đơn hợp lệ theo tháng đá, không phải báo cáo dòng tiền theo ngày ngân hàng chuyển tiền; đơn hủy/hoàn tiền không được cộng.

Query báo cáo lấy đủ trạng thái trong tháng để tổng số đơn và biểu đồ trạng thái không chỉ có đơn approved. Callback báo cáo cũ không ghi đè tháng mới. Khoảng `YYYY-MM-01` đến `YYYY-MM-31` vẫn bao phủ đúng các chuỗi ngày hợp lệ của tháng, kể cả tháng ngắn hơn 31 ngày.

## Kiểm chứng và giới hạn

- Build debug, unit tests và instrumentation tests được chạy bằng Gradle.
- 4 unit test mới cho doanh thu; thêm 2 instrumentation test kiểm tra ViewModel trên emulator (khôi phục/đổi trạng thái slot và lựa chọn dịch vụ). Sửa assertion application ID bị cũ trong test mẫu.
- 60/60 kiểm thử Rules đạt trên Firestore Emulator, gồm 3 test mới về quyền đọc/query và blocked owner. Không gọi Firestore production.
- Kiểm thử ViewModel không thay thế thao tác UI đầy đủ. Chưa thực hiện đăng nhập bằng tài khoản thật, VNPay thật, duyệt chủ sân qua UI hoặc toàn bộ luồng trên nhiều thiết bị.

Kiểm tra nghiệm thu còn cần: đổi ngày nhanh trên mạng chậm; slot bị người khác đặt khi hộp thoại đang mở; xoay màn hình; hệ thống hủy process khi app nền; đổi tháng báo cáo nhanh; đăng nhập customer/owner/admin/blocked để kiểm tra UI tương ứng. Thanh toán tiền mặt chỉ được cộng sau khi trạng thái paid được ghi nhận qua cơ chế tin cậy; đợt này không bổ sung quyền tự đánh dấu paid cho Android.
