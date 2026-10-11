# Hoàn thiện vòng đời VNPay

Luồng khi chưa cấu hình được IPN được cập nhật tại
[VNPay không IPN](VNPAY_WITHOUT_IPN.md): Return URL xác minh dữ liệu rồi
kích hoạt QueryDR; chỉ phản hồi QueryDR đã xác minh mới có thể ghi paid.

## Thành phần

- [payment_service.py](../backend/services/payment_service.py): tạo/reuse phiên, xác minh IPN, settlement dùng chung trong Firestore transaction. Chỉ báo thành công bình thường khi đơn còn hợp lệ và giữ đúng khóa sân. Tiền về khi khóa mất hoặc đơn hủy được lưu paid nhưng trả trạng thái review.
- [payment_reconciliation.py](../backend/services/payment_reconciliation.py): QueryDR chính thức, POST JSON, chữ ký HMAC-SHA512 theo chuỗi phân cách `|`, kiểm tra chữ ký phản hồi và merchant/ref/amount/type. Không dùng chữ ký URL cho QueryDR.
- [payment_maintenance.py](../backend/services/payment_maintenance.py): hết hạn giữ chỗ online bằng transaction; không ghi đè khóa của đơn khác hoặc hủy đơn đã paid.
- [PaymentActivity](../app/src/main/java/com/example/footballbooking/ui/customer/payment/PaymentActivity.java): kiểm tra trạng thái nội bộ, đối soát theo yêu cầu, tiếp tục cùng phiên còn hiệu lực, báo lỗi WebView/HTTP/SSL; không bỏ qua SSL.

Tài liệu giao thức đối chiếu: <https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr&refund.html>.

## Chính sách áp dụng

1. Đơn chọn VNPay được giữ tối đa 15 phút từ `createdAt` phía Firestore, không quá giờ bắt đầu trận. Tạo URL muộn không gia hạn thời gian này.
2. Phiên cũ tạo trước thay đổi được giữ đến `expiresAt` đã cấp để không hủy trước thời hạn đã hứa.
3. Tác vụ hết hạn chỉ xử lý booking có phương thức `vnpay`, còn pending/approved và unpaid. Tiền mặt/pay_later không bị tác vụ này hủy. Chuyển từ tiền mặt/pay_later sang trả VNPay không tự đổi chính sách giữ chỗ trước khi thanh toán thành công.
4. Hết hạn không chứng minh chưa chuyển tiền. Tác vụ nhả khóa và đánh dấu booking cancelled; giữ nguyên lịch sử phiên thanh toán. IPN/QueryDR thành công về sau ghi nhận tiền với review, không khôi phục booking hoặc giành lại slot.
5. Chủ sân duyệt và payment là hai trạng thái riêng. Vẫn cho thanh toán trước khi duyệt. Không tự động hoàn tiền khi từ chối/hủy.
6. Tiếp tục thanh toán chỉ mở lại **cùng reference còn pending**. Phiên failed/expired không bị thay bằng reference mới tự động. Khách kiểm tra/đối soát trước khi hủy/chọn lại booking; không tuyên bố đã có luồng trả lại tiền tự động hoặc tự retry phiên thất bại.

## API và bảo mật

- `POST /payments/vnpay/sessions`: bookingId + Firebase token, số tiền lấy từ server.
- `GET /payments/vnpay/sessions/{ref}`: đọc trạng thái, không gọi ngân hàng trong mỗi lần polling.
- `POST /payments/vnpay/sessions/{ref}/reconcile`: xác thực quyền sở hữu, đối soát QueryDR. Giới hạn một lần/phút/phiên bằng Firestore transaction, kể cả nhiều backend process. Phiên mới chờ ít nhất 60 giây trước query đầu tiên.
- `/ipn` xác thực chữ ký trước settlement; `/return` kích hoạt QueryDR sau khi xác minh callback. Không dùng dữ liệu Return URL để trực tiếp ghi paid.
- ResponseCode QueryDR = 00 chỉ có nghĩa truy vấn thành công. TransactionStatus = 00 mới là thanh toán thành công; 02 là lỗi giao dịch. Các trạng thái khác, not-found, rate-limit và timeout không tự ghi failed/paid.
- Chỉ gọi endpoint VNPay cố định theo môi trường, không theo URL người dùng đưa vào. Không tự retry POST ngân hàng trong request. Không lưu/log secret hoặc ID token.
- `VNPAY_SERVER_IP` là địa chỉ máy chủ đối soát; cấu hình trusted proxy của Uvicorn phải chính xác nếu dùng proxy. Không tin tùy ý header X-Forwarded-For.

## Cấu hình và triển khai

Android: có thể đặt `PAYMENT_BASE_URL=https://your-api.example.com/` trong local properties hoặc Gradle property. Nếu chưa đặt, tạm dùng giá trị backend cũ để tương thích. Bản release yêu cầu HTTPS. Khóa VNPay không nằm trong Android.

Backend: đặt TMN code, hash secret, return URL theo mẫu môi trường hiện có và `VNPAY_SERVER_IP`. Không commit giá trị thật. Đăng ký IPN URL với VNPay và kiểm tra HTTPS công khai.

Chạy backend với access log tắt hoặc bộ lọc query string ở cả Uvicorn/reverse proxy để không ghi toàn bộ callback có chữ ký:

```powershell
.\.venv\Scripts\python.exe -m uvicorn main:app --host 0.0.0.0 --port 8000 --no-access-log
```

Từ thư mục backend, kiểm tra số đơn cũ có thể bị hết hạn trước khi triển khai:

```powershell
.\.venv\Scripts\python.exe -m tools.maintain_payments --project YOUR_PROJECT_ID
```

Sau khi duyệt kết quả, cấu hình Task Scheduler/cron/cloud job gọi mỗi phút, working directory là backend:

```powershell
.\.venv\Scripts\python.exe -m tools.maintain_payments --project YOUR_PROJECT_ID --apply --reconcile
```

Lệnh kiểm tra project khớp credential, đối soát phiên pending/failed rồi nhả hold quá hạn. Các phiên không đối soát được được đếm để vận hành kiểm tra lại; không giả định lỗi mạng là chưa thu tiền. Tác vụ cần cảnh báo khi exit khác 0; nếu lỗi hạ tầng, lần chạy sau phải retry. Với dữ liệu lớn cần phân trang, index và hàng đợi thay vì quét như bản đồ án.

**Chưa cài lịch chạy, chưa deploy backend/Rules, chưa thay cấu hình merchant và chưa chạy lệnh apply trên Firebase thật trong đợt này.** Do đó, tự nhả slot chỉ hoạt động khi người vận hành triển khai tác vụ. Cần xử lý các đơn VNPay unpaid cũ trước khi bật, vì chúng có thể đã vượt hạn 15 phút.

## Kiểm thử và phạm vi

Kết quả đợt này: `assembleDebug` thành công; 5 unit test Android đạt; 6 instrumentation test trên Android Emulator đạt; 39 test backend payment (có Firestore Emulator) đạt; `git diff --check` đạt. Emulator lúc khởi động có cảnh báo tạo thư mục additional_test_output nhưng test report kết thúc thành công, không có test fail/skip.

Các test mock QueryDR kiểm tra chuỗi ký độc lập, phản hồi giả, sai amount/ref/merchant/type, timeout và phản hồi 91/94. Test Firestore Emulator kiểm tra lease truy vấn, thiếu IPN, nhả đúng slot, giữ đơn cash/pay_later, IPN đến sau release và chạy đồng thời IPN/expiry. Android có test entry thiếu booking và deep link giả, ngoài các test lựa chọn hiện có.

Chưa thay thế thử nghiệm Sandbox xuyên suốt bằng tài khoản merchant thực. Cần chạy thử thanh toán thành công, hủy ngân hàng, tắt app sau trả tiền, chặn IPN rồi QueryDR phục hồi, và tiền về sau hết hạn. Không nhận tiền thật trong quá trình kiểm thử.

Các chức năng ngoài đợt này: tự hoàn tiền, xác nhận thu tiền mặt có phân quyền, màn hình quản trị đối soát và Android App Links. WebView chưa hỗ trợ chuyển sang app ngân hàng; thông báo rõ khi gặp liên kết không được hỗ trợ. Người dùng vẫn có thể thanh toán trên trang web hỗ trợ hoặc quay lại kiểm tra trạng thái.
