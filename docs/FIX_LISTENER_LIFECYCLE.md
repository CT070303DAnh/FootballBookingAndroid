# Listener riêng theo ViewModel và callback sau khi đóng view

## Đã sửa

- [PitchRepository](../app/src/main/java/com/example/footballbooking/data/repository/PitchRepository.java) và [BookingRepository](../app/src/main/java/com/example/footballbooking/data/repository/BookingRepository.java) có factory `createScoped()`. PitchViewModel, AiViewModel và BookingViewModel sở hữu instance riêng; gọi cleanup không hủy listener của ViewModel khác. Home/Map vẫn chia sẻ PitchViewModel thuộc CustomerMainActivity như trước.
- Singleton còn được dùng cho thao tác một lần như đọc chi tiết sân hoặc transitionBooking, không được dùng cho đăng ký listener trong các ViewModel kể trên.
- Listener/list search có số thế hệ yêu cầu. Callback đã vào hàng đợi nhưng đến sau remove/reload bị bỏ qua. Tìm kiếm dừng listener danh sách trước đó; phản hồi tìm kiếm cũ không ghi đè tìm kiếm mới, lọc loại sân mới hoặc trạng thái sau cleanup. Kết quả tìm kiếm không trả sân đóng/bảo trì.
- Tải slot kiểm tra thế hệ tại cả bước tải catalog, đăng ký listener bookings và nhận availability. Đổi ngày/đóng ViewModel không để callback cũ đăng ký lại listener hoặc đưa lịch ngày cũ lên UI. Mỗi lần đọc availability cũng có thứ tự riêng, tránh phản hồi cũ ghi đè snapshot booking mới.
- [HomeFragment](../app/src/main/java/com/example/footballbooking/ui/customer/home/HomeFragment.java) hủy runnable debounce, dừng shimmer, tháo adapter khi view bị hủy. Callback GPS kiểm tra đúng binding đã khởi tạo yêu cầu, không chỉ kiểm tra binding khác null.
- Map kiểm tra binding trước onMapReady/GPS callback và bỏ listeners/reference của GoogleMap khi view bị hủy. AI bỏ GPS callback thuộc view cũ. Callback hoàn tất kiểm tra đơn hết hạn ở admin chỉ đăng ký observer khi đúng view vẫn còn tồn tại.
- AiViewModel giữ tham chiếu observer để remove trong onCleared. Tính điểm chạy bằng executor một luồng, dùng bản sao list/map/location và mã thế hệ; kết quả cũ không ghi đè kết quả mới hoặc lỗi tải sân. Cleanup hủy task/executor và callback chính.

## Kiểm chứng

```powershell
python tests/lifecycle/check_pitch_listeners.py
```

**12/12 kiểm tra hồi quy đạt**, compile mã PitchRepository thực bằng javac với test double cho Firebase/LiveData. Kiểm tra Home và AI không hủy nhau, callback còn trong hàng đợi sau cleanup, tìm kiếm A/B trả ngược thứ tự, lỗi tìm kiếm cũ và thay listener lọc. Đây không phải test tích hợp Android/Firebase thật.

`git diff --check` đạt. Build Gradle được thử nhưng chưa qua “Starting a Gradle Daemon”, chưa xác minh compile toàn ứng dụng hoặc UI trên thiết bị.

Kịch bản cần kiểm tra thiết bị: mở Home → AI → quay Home, mở/đóng chi tiết rồi đổi sân, mở lịch sử cùng AI, đổi ngày đặt nhanh khi mạng chậm, gõ tìm kiếm rồi chuyển tab, rời Map trước khi GPS/onMapReady trả về, rời màn admin trong lúc kiểm tra hết hạn.

## Giới hạn

Đợt này xử lý xung đột listener và callback ở các đường nêu trên, không tuyên bố đã sửa mọi lỗi lifecycle trong toàn ứng dụng. Cache thời tiết theo tọa độ, thứ tự phản hồi HTTP thời tiết khi refresh liên tiếp, filter kết hợp search và các vấn đề chatbot vẫn cần đợt riêng. Không thay Rules/backend hoặc dữ liệu Firebase thật.
