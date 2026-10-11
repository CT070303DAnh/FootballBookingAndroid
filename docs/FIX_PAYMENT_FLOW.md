# Kiểm tra luồng xác nhận và thanh toán

## Lỗi tìm thấy và sửa

- Hộp thoại xác nhận đặt sân dùng đồng thời message và single-choice list. Android không bảo đảm hiển thị cả hai; người dùng khó biết phương thức thực sự được gửi. Giữ lựa chọn radio trên màn hình đặt sân, chụp phương thức đó khi mở hộp thoại và hiển thị rõ trong phần tóm tắt.
- Điều kiện xác nhận trước đây trả boolean và chỉ báo lỗi chung; gọi ViewModel khi chưa đủ điều kiện còn có thể kết thúc im lặng. Nay báo rõ đang gửi, đơn đã tạo, thiếu sân/hồ sơ, lịch chưa đạt policy v2, đang tải/lỗi dịch vụ hoặc slot không khả dụng. Không bỏ kiểm tra khóa lịch để cho phép đặt vào dữ liệu chưa được chuẩn bị.
- Lịch sử trước đây ghi tất cả đơn unpaid là “Thanh toán sau”, kể cả tiền mặt. Nay hiển thị đúng phương thức của đơn.
- Callback VNPay tới lúc request đang chạy trước đây có thể bị bỏ qua do cờ busy. Nay ghi nhận yêu cầu kiểm tra và thực hiện sau callback mạng đang chạy.

## Ba luồng

1. **Tiền mặt:** tạo booking pending/unpaid và khóa slot trong transaction, hiển thị đặt sân thành công, không gọi VNPay. Chọn tiền mặt không có nghĩa đã thu tiền.
2. **Thanh toán sau:** tạo booking pending/unpaid giống tiền mặt; người dùng có thể mở lịch sử để chọn VNPay sau.
3. **VNPay:** tạo booking trước, sau đó backend tạo/reuse phiên thanh toán; ngân hàng gọi IPN đã ký; Android đọc trạng thái backend. Browser return không tự chuyển đơn sang paid. Mất mạng/hết hạn hoặc tiền đến sau khi đơn hủy cần kiểm tra/đối soát, không tự tạo phiên khác để tránh trả hai lần.

## Những điều chưa thể khẳng định

Chưa nhận được nội dung lỗi cụ thể từ thiết bị người dùng, nên chưa chứng minh nguyên nhân duy nhất của ca tiền mặt được báo. Nếu thông báo là lịch chưa được kiểm tra, cần thực hiện quy trình migration C2 bằng công cụ tin cậy; sửa nút không thay thế bước chuẩn bị dữ liệu. Nếu PERMISSION_DENIED, cần đối chiếu Rules đang deploy với bản local. Đợt này không deploy Rules, sửa dữ liệu production hoặc gọi ngân hàng thật.

Luồng ghi nhận thu tiền mặt bằng người có thẩm quyền chưa được bổ sung: client vẫn không được tự sửa paymentStatus. Đây là phần nghiệp vụ riêng cần endpoint tin cậy và xác nhận đã thu tiền, không nên đánh dấu paid ngay khi khách chọn tiền mặt.

## Kiểm thử

Kết quả: build debug đạt, 4/4 instrumentation test đạt, 63/63 Rules test đạt. Backend payment 23/23 đạt ở lần chạy lại; lần đầu có một test IPN đồng thời hết 5 lần retry transaction trên emulator. Chưa coi kiểm thử concurrency hoàn toàn ổn định và chưa kiểm thử ngân hàng thật.

- Build debug và kiểm thử Android Emulator, gồm test mới xác nhận cả cash/vnpay/pay_later đều trả lỗi rõ khi thiếu dữ liệu thay vì kết thúc im lặng.
- Thêm kiểm thử Rules cho cả ba phương thức: tạo đơn unpaid và khóa slot đúng.
- Bộ backend payment kiểm tra chữ ký, amount, authentication, callback giả, idempotency và đối soát; test tích hợp chỉ chạy trên Firestore Emulator.

Sau khi cài APK mới, chọn ngày/giờ, chọn tiền mặt, kiểm tra dòng phương thức trong hộp thoại rồi xác nhận. Nếu vẫn lỗi, ghi lại nguyên văn thông báo. Không thử trả tiền thật để kiểm chứng lỗi tạo booking.
