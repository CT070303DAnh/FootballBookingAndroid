# Chatbot — Xác nhận đúng yêu cầu và chặn gửi chồng nhau

## Thay đổi

- Mỗi thẻ BOOKING_CONFIRMATION có `confirmationId` ngẫu nhiên và `expiresAt`. Android gửi lại chính ID của thẻ được bấm; không dùng câu “Xác nhận đặt sân” làm định danh yêu cầu.
- Backend đối chiếu ID với pending của đúng UID và conversation. Thẻ cũ, sai cuộc trò chuyện, sai người dùng hoặc hết hạn không tạo booking. Thẻ cũ không xóa pending mới.
- Câu xác nhận không kèm ID (kể cả “OK”) chỉ nhắc bấm thẻ, không tự chọn pending mới nhất. Bản Android cũ sẽ không xác nhận được booking qua chatbot; cần cập nhật Android cùng backend.
- ViewModel chặn gửi khi đang xử lý; TextWatcher không bật lại nút gửi khi người dùng tiếp tục gõ. Nút xác nhận cũ bị vô hiệu hóa, và ViewModel kiểm tra ID lần nữa trước khi gửi. Conversation ID được tạo trước request đầu tiên.
- Backend dùng cờ busy dưới lock theo phiên: request thứ hai trong cùng cuộc trò chuyện được báo đang xử lý; không đồng thời thay pending/history hay gọi tạo booking. Cuộc trò chuyện khác vẫn được xử lý độc lập. Cờ luôn được nhả trong finally, phiên busy không bị dọn giữa chừng.
- Lưu tối đa 10 kết quả xác nhận thành công trong phiên. Gửi lại cùng ID trả lại kết quả cũ, không gọi tạo booking lần nữa và không ảnh hưởng pending mới.
- Repository kiểm tra Firebase UID trước gửi và trước giao kết quả thành công về UI. Fragment tháo adapter khi view bị hủy.

## Kiểm chứng

Kết quả đợt sửa: 43 test backend đạt, 14 test tích hợp cần Firestore Emulator được bỏ qua (tổng 57). Có 9 test hồi quy mới cho xác nhận và API. `git diff --check` đạt. Chưa xác minh build hoặc chạy UI Android.

Chạy từ backend:

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

Test bổ sung kiểm tra ID thẻ cũ, lời xác nhận không ID, xác nhận lặp, hết hạn, khác UID/conversation, request đồng thời và nhả busy sau exception. API test kiểm tra chuyển đúng ID với UID đã xác thực và từ chối ID sai định dạng.

Không thay đổi Rules hoặc transaction tạo booking C2 trong đợt này. Không gọi LLM/VNPay/Firebase thật; test dùng mock. Chưa xác minh build/UI Android vì môi trường Gradle ở các đợt trước chưa qua khởi động daemon/toolchain JDK 21.

## Giới hạn vận hành

Session và cache kết quả vẫn ở RAM của một backend process. Sau restart, request với ID cũ bị từ chối, không tự dựng lại pending hay tạo đơn. Cần chạy một worker hoặc sticky routing cho hội thoại; nếu muốn nhiều worker/replica phải chuyển session/lock/idempotency sang kho dùng chung. Không tuyên bố bảo đảm exactly-once xuyên restart.

Lỗi mạng sau khi gửi xác nhận không chứng minh booking chưa được tạo. Người dùng cần kiểm tra lịch sử trước khi lập yêu cầu mới. UI vô hiệu thẻ đã gửi thay vì tự thử tạo đơn khác. Cache hết hạn sau 30 phút không hoạt động; thẻ xác nhận hết hạn sau 5 phút theo server. Khi process Android mất, lịch sử chat chưa được khôi phục tự động — thuộc nhóm state/process-death khác.

Kiểm tra thiết bị cần làm: tạo hai thẻ liên tiếp rồi bấm thẻ cũ; bấm nhanh xác nhận hai lần; gõ tiếp khi đang loading; chuyển tab lúc request đang chạy; đợi thẻ hết hạn; mất mạng ngay sau bấm xác nhận và kiểm tra lịch sử booking.
