# Sửa thời tiết trong gợi ý AI

## Nguyên nhân và thay đổi

Cache trước đây chỉ lưu một kết quả, không kiểm tra tọa độ. Khi người dùng đổi vị trí trong 10 phút, AI có thể dùng thời tiết nơi cũ. Request cũ trả về muộn cũng có thể ghi đè request mới.

- [WeatherRepository](../app/src/main/java/com/example/footballbooking/data/repository/WeatherRepository.java) dùng khóa gồm tọa độ chính xác hoặc thành phố fallback. Cache giữ tối đa một kết quả, hết hạn sau 10 phút bằng đồng hồ monotonic, không phụ thuộc việc chỉnh giờ thiết bị.
- Mỗi ViewModel sở hữu repository riêng. Yêu cầu mới hủy request trước và tăng số phiên bản; callback chỉ cập nhật cache/LiveData nếu vẫn thuộc phiên bản hiện tại. Kiểm tra được thực hiện lúc giao kết quả trên main thread để chặn cả callback đã xếp hàng.
- Refresh xóa cache và vô hiệu request đang chạy. ViewModel được giải phóng sẽ hủy request và callback chờ.
- [AiViewModel](../app/src/main/java/com/example/footballbooking/ui/customer/ai/AiViewModel.java) xóa thời tiết cũ khi tải lại, chờ weather thành công hoặc lỗi trước khi tính điểm. Nếu API lỗi, giữ fallback điểm thời tiết trung lập như trước.

Không thêm thư viện, không thay đổi công thức xếp hạng hay giao diện. Cache theo tọa độ chính xác ưu tiên tính đúng; GPS dao động nhỏ có thể phát sinh request mới.

## Kiểm chứng

Chạy `python tests/lifecycle/check_weather_requests.py`: **12/12 kiểm tra đạt**. Script biên dịch repository thực bằng javac với các lớp giả lập Retrofit, LiveData và main loop; không gọi API thật. Bao gồm cache cùng vị trí, đổi vị trí, fallback thành phố, hủy request cũ, callback thành công/lỗi đến muộn, cleanup, refresh, hết hạn và độc lập giữa hai owner.

`git diff --check` đạt. Chưa xác minh build/UI Android; môi trường Gradle ở các đợt trước bị vướng daemon/toolchain. Kiểm tra độc lập không thay thế build ứng dụng hoặc kiểm thử tích hợp ViewModel.

## Test trên thiết bị

1. Mở gợi ý AI tại vị trí A rồi đổi GPS sang B: thời tiết phải tải lại, không lấy cache A.
2. Tắt quyền GPS để dùng Hà Nội, sau đó bật GPS: kết quả phải theo tọa độ mới.
3. Giả lập mạng chậm và refresh liên tục: kết quả cũ không được ghi đè kết quả mới.
4. Mất mạng: gợi ý vẫn xuất hiện sau khi yêu cầu thời tiết báo lỗi, dùng điểm trung lập.
5. Thoát màn hình trong lúc tải rồi mở lại: không nhận kết quả của ViewModel đã bị giải phóng.
