# C2 — Toàn vẹn đơn đặt sân và khóa lịch

## Phạm vi sửa

- [Firestore Rules](../firestore.rules) xác minh khách hàng đang hoạt động, sân/slot hợp lệ, chủ sân, giá cơ bản, phụ phí và từng dịch vụ trong catalog. Đơn mới chỉ được ở trạng thái `pending/unpaid/upcoming`; thời gian bắt đầu phải ở tương lai theo múi giờ Việt Nam và thời gian server. Không nhận trường tùy ý, giao dịch thanh toán giả hoặc giá tự sửa.
- Booking và `pitches/{pitchId}/availability/{date}` phải được ghi cùng giao dịch. Document lịch giữ nguyên cấu trúc `date/slots`, bổ sung `lastSlotId` để Rules kiểm tra chỉ đúng một slot thay đổi. Rules dùng cả trạng thái trước và `getAfter()` để ngăn ghi booking riêng, giữ chỗ giả, ghi đè khóa, xóa lịch hoặc mở khóa của đơn khác.
- [BookingRepository](../app/src/main/java/com/example/footballbooking/data/repository/BookingRepository.java) đọc sân, slot, dịch vụ và khóa lịch bằng transaction. Khi cạnh tranh, Firestore retry rồi từ chối yêu cầu thua. Lỗi đọc không được bỏ qua để tiếp tục tạo đơn. Giá thay đổi buộc tải lại và xác nhận lại.
- Duyệt, từ chối và hủy dùng chung transaction, đọc lại đơn và khóa hiện tại. Chỉ chủ sân thực tế/admin hoạt động được duyệt hoặc từ chối đơn pending; khách chỉ hủy đơn của mình pending/approved trước giờ bắt đầu. Giá, khách hàng, sân và thanh toán không được đổi trong thao tác này. Từ chối ghi `adminNote`, giải phóng lịch; duyệt ghi map lồng đúng cấu trúc.
- `available` không còn bị hiển thị là đã đặt. Lỗi tải lịch không trả về danh sách mặc định “còn trống”. Catalog có slot nhưng tất cả bị tắt thì không quay về lịch mặc định.
- [BookingViewModel](../app/src/main/java/com/example/footballbooking/ui/customer/booking/BookingViewModel.java) chỉ cung cấp dịch vụ có thật trong catalog; bỏ dịch vụ giả khi catalog rỗng/lỗi. Sửa ánh xạ `isActive/isPeakHour` và loại các getter tiện ích của Booking khỏi dữ liệu ghi.
- [Backend](../backend/services/firestore_service.py) dùng `DocumentReference.get(transaction=tx)` để nhận snapshot đúng với SDK Python, tiêu thụ query trước khi ghi, kiểm tra blocked, giá hữu hạn và khóa lịch. Cùng hợp đồng khóa với Android.
- [BookingExpiryWorker](../app/src/main/java/com/example/footballbooking/utils/BookingExpiryWorker.java) chỉ xử lý đơn trong quyền của admin/owner, qua transaction riêng từng đơn. Đơn vừa được duyệt hoặc khóa đã đổi sẽ không bị ghi đè. Đây vẫn là helper do ứng dụng gọi, chưa phải scheduler server.

## Hợp đồng dữ liệu và giới hạn

- Mỗi đơn tối đa **5 dịch vụ**, để xác minh từng giá trong giới hạn số document mà Rules được đọc. Android/backend báo lỗi rõ khi vượt quá. Công thức tiền vẫn là `basePrice + surcharge + totalServicePrice`; chưa thay chính sách tính giá theo 90 phút (M2).
- `bookingPolicyVersion: 2` trên sân chỉ được bật sau khi kiểm tra catalog và đồng bộ khóa của các đơn hiện hữu. `useDefaultSlots: true` chỉ dành cho sân không có document timeSlots; nếu có catalog riêng thì không được dùng fallback cho ID thiếu.
- Sân cũ và **sân mới chưa được chuẩn bị** chưa nhận đơn theo policy mới. Owner không được tự bật policy hoặc đổi ownerId. Admin cần chuẩn bị sân trước khi mở nhận đơn. Đây là thay đổi vận hành cần làm cùng lúc với triển khai, không được chỉ deploy Rules rồi bỏ qua migration.
- Lịch các slot phải không chồng giờ. Đổi catalog slot yêu cầu admin tắt policy trước, kiểm tra lại đơn còn hiệu lực rồi chạy công cụ chuẩn bị lại. Client owner không được sửa timeSlots trực tiếp. UI owner hiện không có luồng quản lý timeSlots riêng.
- Khóa bảo đảm độc quyền **cùng sân/ngày/slot**. Kiểm tra khách đặt đồng thời ở sân khác vẫn là kiểm tra trước ở Android, chưa phải khóa lịch toàn cục theo khách; không tuyên bố ngăn mọi đặt trùng giữa nhiều sân.
- Đơn cũ thiếu khóa hoặc thông tin bất nhất cần sửa bằng công cụ tin cậy; client không được tự nhận quyền sửa dữ liệu cũ.
- Hủy đơn đã trả tiền không tự hoàn tiền. Chưa xử lý C3 (xác minh VNPay), quyền đọc rộng của bookings/users, FCM queue hoặc scheduler hết hạn.

## Chuẩn bị dữ liệu và triển khai (chưa thực hiện trên cloud)

Công cụ [prepare_booking_policy.py](../backend/tools/prepare_booking_policy.py) mặc định **chỉ đọc**. Không có credential trong mã nguồn và bắt buộc chỉ rõ project.

1. Sao lưu dữ liệu và lên khoảng bảo trì; dừng mọi writer cũ, gồm Android cũ, backend chatbot cũ và tác vụ Admin SDK khác. Admin SDK bỏ qua Rules nên chỉ deploy Rules không chặn được backend cũ.
2. Chạy dry-run từ thư mục backend với credential quản trị do người vận hành cung cấp:

   ```powershell
   .\.venv\Scripts\python.exe -m tools.prepare_booking_policy --project PROJECT_ID
   ```

3. Xử lý thủ công các lỗi: nhiều đơn còn hiệu lực cùng slot, khung giờ chồng nhau, owner sai, slot đã đổi khi còn đơn tương lai, khóa thủ công/mồ côi hoặc trạng thái không hiểu được. Công cụ không tự chọn đơn thắng và không âm thầm xóa khóa thủ công.
4. Sau khi xem kết quả và trong thời gian đã dừng writer, chạy lại với `--apply`. Công cụ tắt policy trước, kiểm tra toàn bộ dữ liệu, xây lại availability từ pending/approved, dọn khóa còn lại của đơn đã hủy/từ chối và mới bật version 2. Lỗi giữa chừng để sân chưa hoàn tất ở trạng thái không nhận đơn mới; rà soát rồi chạy lại. Không dùng lệnh này khi còn traffic ghi.
5. Triển khai Rules, backend và bản Android tương thích trong cùng khoảng bảo trì; không cho bản cũ ghi lại lịch. Kiểm tra đăng ký/đặt/duyệt/hủy/từ chối, dịch vụ và hai thiết bị tranh cùng slot rồi mới mở traffic.
6. Khi đổi catalog hoặc chuẩn bị sân mới, lặp lại quá trình kiểm tra; không tự gán version 2 để bỏ qua lỗi.

Chưa chạy dry-run lẫn `--apply` trên Firebase thật, chưa deploy Rules/backend, chưa sửa dữ liệu cloud.

## Kiểm chứng

- Firestore Emulator: **54/54** test C1+C2 đạt. Gồm giá/owner giả, slot sai hoặc tắt, tài khoản blocked, thiếu một vế của giao dịch, chiếm/xóa/sửa nhiều khóa, hủy kèm sửa giá, hủy cũ sau khi đặt lại, cạnh tranh transaction và giới hạn 5 dịch vụ trên ngày đã có khóa khác.
- Backend: **25/25** test unit/integration đạt; SDK Python thật chạy trên emulator xác minh tạo booking/khóa lịch và hai transaction tranh cùng slot chỉ có một đơn thành công.
- Biên dịch Android bị chặn trước khi compile source: toolchain yêu cầu JetBrains JDK 21 nhưng URL Foojay trả HTTP 400. Chưa xác minh APK hay thao tác UI trên thiết bị; cần chạy lại build khi sửa môi trường JDK.
- `git diff --check` đạt; không commit hay deploy trong đợt sửa này.
- Cách chạy test: [hướng dẫn emulator](../tests/firestore/README.md). Không chạy Node và Python integration song song trên cùng demo database.
