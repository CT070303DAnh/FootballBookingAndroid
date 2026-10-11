# C3 — Xác minh thanh toán VNPay tại backend

## Thay đổi

- [Cấu hình Android](../app/build.gradle.kts) không còn đọc hoặc sinh `BuildConfig.VNPAY_HASH_SECRET`. Secret chỉ có trong môi trường backend; không có giá trị secret thật được thêm vào mã nguồn.
- [PaymentActivity](../app/src/main/java/com/example/footballbooking/ui/customer/payment/PaymentActivity.java) chỉ gửi bookingId và Firebase ID token tới [API thanh toán](../backend/api/payments.py). Bỏ ký URL và ghi paymentStatus từ client; bỏ extra_amount ở các điểm mở màn hình.
- [Payment service](../backend/services/payment_service.py) kiểm tra token đã bị thu hồi, hồ sơ đang hoạt động, chủ đơn, trạng thái đơn và giờ thi đấu. Số tiền lấy từ Firestore, phải là VND nguyên dương và hữu hạn; không chấp nhận amount/customerId/returnUrl do client gửi. URL ký HMAC-SHA512 trên đúng query UTF-8 đã URL-encode và sắp xếp.
- Mỗi đơn có một `paymentSessionId` trỏ tới `payment_sessions/{paymentRef}`. Hai yêu cầu mở cùng lúc dùng transaction và nhận lại cùng phiên, không tạo hai mã thu tiền. Mã tham chiếu ngẫu nhiên 32 ký tự; URL hết hạn tối đa sau 15 phút hoặc trước giờ thi đấu.
- Browser return **không** ghi thanh toán. Backend kiểm tra chữ ký rồi chuyển về `vnpay://payment_result?payment_ref=...`; Activity hỗ trợ mở nguội không có extras, onNewIntent và khôi phục booking/ref sau tái tạo. Callback tự chế chỉ kích hoạt API kiểm tra có xác thực, không thể tạo thành công.
- Chỉ IPN hợp lệ mới ghi nhận tiền: đối chiếu chữ ký constant-time, merchant, reference, số tiền (VND × 100), cả ResponseCode và TransactionStatus, mã giao dịch ngân hàng và PayDate. Trùng query parameter hoặc currency khác VND bị từ chối.
- Ghi `payment_sessions`, `vnpay_transactions/{transactionNo}` và booking trong cùng transaction. IPN lặp trả `02`; một transactionNo không thể thanh toán hai đơn. Lỗi ghi trả `99` yêu cầu VNPay gửi lại, không xác nhận trước commit. Client mọi role bị Rules chặn ghi/đọc các collection thanh toán và chặn sửa trường thanh toán của booking.
- Activity chỉ báo thành công khi API xác nhận trạng thái session và booking cùng `paid`/cùng transactionNo. Callback đến trước IPN hiển thị chờ, kiểm tra lại tối đa khoảng 20 giây rồi cho kiểm tra thủ công. Lỗi mạng không hiển thị thành công hoặc tự tạo một giao dịch khác.
- Tiền về sau hủy/từ chối, sau hạn thanh toán hoặc sau kết quả thất bại trước đó được ghi nhận nhưng đánh dấu `paymentReviewRequired`, hiển thị `review`. Không đổi bookingStatus và không chiếm lại slot đã trả. Đơn bị hủy sau khi thanh toán cũng hiển thị review khi kiểm tra qua API.
- Bản release chỉ gọi payment API qua HTTPS. Payment HTTP client không log body/token và không tự theo redirect. WebView tắt file/content access và mixed content; URL khởi tạo chỉ tới gateway VNPay HTTPS. Các bước ngân hàng HTTPS vẫn chạy trong WebView.

## Cấu hình bắt buộc trước khi sử dụng

Trong môi trường backend, theo [mẫu cấu hình](../backend/.env.example):

```dotenv
VNPAY_TMN_CODE=<merchant do VNPay cấp>
VNPAY_HASH_SECRET=<secret chỉ có ở server>
VNPAY_GATEWAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=https://your-api.example.com/payments/vnpay/return
```

Đăng ký IPN URL công khai HTTPS `https://your-api.example.com/payments/vnpay/ipn` với VNPay.
Merchant, secret và gateway phải cùng môi trường. Production gateway được hỗ trợ là
`https://pay.vnpay.vn/vpcpay.html`; chỉ đổi sau khi hoàn tất kiểm thử/chứng nhận merchant.
Không cấu hình secret thì API trả lỗi 503, không dùng placeholder hoặc ký bằng khóa rỗng.

Android dùng `CHATBOT_BASE_URL` để gọi backend hiện tại. Firebase Admin credential chỉ đặt
trên backend. Reverse proxy cần truyền địa chỉ IP qua cấu hình proxy tin cậy của Uvicorn;
không tin tùy ý header X-Forwarded-For từ Internet. Tắt/che query string trong access log
của gateway/reverse proxy khi xử lý return/IPN; không log signed URL hay Authorization.

Nếu secret từng được nhúng vào APK đã phát hành/chia sẻ, phải **thu hồi/đổi secret tại VNPay**
trước khi mở luồng mới. Xóa dòng secret cũ khỏi cấu hình Android do người vận hành quản lý,
clean/rebuild APK và ngừng dùng APK cũ. Xóa BuildConfig trong mã nguồn không thu hồi được
secret đã nằm trong APK hoặc cache build cũ. Lượt sửa này không đọc hay in giá trị secret cũ.

Triển khai backend, Rules C1–C3 và Android tương thích cùng quy trình chuyển đổi C2.
Đã có tài liệu [chuẩn bị khóa lịch](FIX_C2_BOOKING_INTEGRITY.md). Chưa deploy hay gọi
VNPay merchant thật trong lượt sửa này.

## Đối soát và giới hạn còn lại

- Không tự tạo phiên thứ hai khi phiên cũ hết hạn hoặc thất bại. Đây là lựa chọn tránh thu
  tiền hai lần khi IPN đến muộn. Người vận hành cần kiểm tra cổng merchant/query ngân hàng
  trước khi cho thanh toán lại. Chưa có API tự động querydr, refund hoặc UI quản trị đối soát.
- Nếu cần cho thử lại sau đối soát, lưu hồ sơ phiên cũ, xử lý tiền đã nhận/hoàn tiền trước,
  rồi thực hiện quy trình quản trị tin cậy. Không chỉ xóa paymentSessionId để bỏ qua phiên cũ:
  IPN đến muộn của phiên đó sẽ không còn khớp đơn.
- IPN hợp lệ nhưng số tiền trên booking đã bị thay đổi qua Admin SDK trả `04`, không tự coi
  đơn là đã thanh toán; phải điều tra và đối soát. Admin SDK bỏ qua Rules nên phải bảo vệ
  credential và hạn chế writer server khác.
- Không tự hoàn tiền khi hủy, không xác minh lại lịch sử paid cũ và không coi dữ liệu paid
  cũ thiếu phiên xác minh là bằng chứng VNPay mới. Booking đã được ghi paid không được mở
  giao dịch thu tiền mới.
- Kết quả emulator dùng secret giả. Vẫn cần merchant sandbox thật để kiểm tra redirect,
  IPN public HTTPS, trình duyệt/WebView, thẻ thử, mạng gián đoạn và thiết bị Android.
- Broad-read bookings/users và FCM queue từ audit cũ không thuộc đợt C3.

## Kiểm thử

Các bộ test nằm tại [test chữ ký/API](../backend/tests/test_payments.py),
[test thanh toán SDK thật](../backend/tests/test_payment_emulator.py) và
[test Rules](../tests/firestore/booking.rules.test.mjs). Xem
[hướng dẫn chạy emulator](../tests/firestore/README.md); chạy Node và Python lần lượt.

- **57/57** test Rules C1–C3 đạt. Lượt hồi quy phát hiện ca 5 dịch vụ vượt hạn mức
  đọc; đã bỏ lần đọc trạng thái khóa dư thừa tại booking-create. Điều kiện khóa cũ
  vẫn được kiểm tra ở ghi availability, booking kiểm tra liên kết khóa bằng getAfter.
- **48/48** test backend đạt, không skip khi kết nối emulator. Bao gồm phiên thanh toán
  tạo đồng thời, IPN đồng thời/lặp, chữ ký/số tiền/reference sai, giao dịch ngân hàng
  dùng lại, tài khoản khác/blocked, hết hạn và tiền về sau khi hủy.
- `git diff --check` đạt. Rà soát Android không còn secret VNPay, hàm HMAC,
  xử lý ResponseCode thành công tại client hoặc extra_amount.
- Thử lại `:app:compileDebugJavaWithJavac` bị chặn trước compile vì tải toolchain
  JetBrains JDK 21 trả HTTP 400. Chưa kiểm thử UI trên thiết bị hay merchant thật.

Android build vẫn cần xác minh khi môi trường có JetBrains JDK 21; lỗi tải toolchain HTTP 400
đã chặn những lượt build trước. Không coi test backend/Rules là chứng nhận APK hoặc tích hợp
merchant VNPay thật.
