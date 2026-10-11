# Đồng bộ lựa chọn dịch vụ đặt sân

## Lỗi và cách sửa

[BookingActivity](../app/src/main/java/com/example/footballbooking/ui/customer/booking/BookingActivity.java) trước đây không gán lại trạng thái checkbox khi bind dòng. RecyclerView có thể tái sử dụng checkbox đã chọn cho dịch vụ khác. Đổi ngày đã xóa lựa chọn trong ViewModel nhưng chưa cập nhật checkbox.

Adapter hiện đọc lựa chọn từ ViewModel mỗi lần bind và sau mỗi lần bấm. Đổi ngày yêu cầu bind lại các dòng. Không giữ một bộ lựa chọn riêng trong adapter.

[BookingViewModel](../app/src/main/java/com/example/footballbooking/ui/customer/booking/BookingViewModel.java) nhận diện dịch vụ bằng serviceId thay vì tham chiếu Java. Khi Firestore trả về đối tượng mới, chọn lại không thêm trùng và bỏ chọn vẫn xóa đúng dịch vụ. Khi tải lại danh sách thành công, lựa chọn được đối chiếu với danh sách mới: cập nhật giá hiện hành, loại bỏ dịch vụ không còn trong danh sách, tính lại tổng trước khi báo thành công cho UI.

Mỗi lần tải dịch vụ có số phiên bản. Phản hồi cũ hoặc phản hồi sau khi ViewModel được giải phóng không cập nhật state. Khi tải lỗi, giữ lựa chọn và dữ liệu trước đó; transaction tạo booking vẫn kiểm tra dịch vụ/giá phía dữ liệu như bản sửa C2.

## Kiểm tra

- Đã kiểm tra nơi khởi tạo adapter và sự tương thích của Predicate/removeIf với minSdk 24.
- `git diff --check` đạt.
- Chưa build/chạy UI Android do vướng Gradle daemon/toolchain đã ghi nhận trước đó. Chưa có kiểm thử tự động riêng cho đợt này.

## Test thủ công cần chạy

1. Chọn một dịch vụ, cuộn ra khỏi màn hình rồi quay lại: chỉ đúng dịch vụ đó được tick, tổng tiền không đổi do cuộn.
2. Đổi ngày: tất cả checkbox bỏ chọn; chọn khung giờ mới không cộng lại phí dịch vụ cũ.
3. Xoay màn hình sau khi chọn dịch vụ: checkbox và phí dịch vụ cùng phản ánh lựa chọn trong ViewModel.
4. Tải lại dịch vụ rồi bỏ chọn một mục đã chọn: mục được xóa đúng, không có phí lặp.
5. Trong môi trường thử nghiệm, đổi giá hoặc vô hiệu hóa dịch vụ rồi tải lại: tổng tiền dùng giá mới hoặc bỏ dịch vụ đã ngừng hoạt động.

Đợt này sửa lựa chọn dịch vụ. Việc khôi phục ngày/khung giờ trên giao diện sau xoay màn hình và lưu state sau process death vẫn cần xử lý riêng.
