# ADR-122: Hỏi đáp tông môn

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-30) — chủ dự án chọn "Hỏi đáp tông môn" trong lượt hỏi lô 23 (AskUserQuestion; mô tả kèm: giờ cố định, câu hỏi lore trong kênh thế giới, đúng trước nhận thưởng; ngân hàng câu hỏi là i18n). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-31 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [STORY.md](../../13-game-design/STORY.md) §1–§4 (nguồn câu hỏi) · ADR-106 (lịch hoạt động) |

---

## Decision

**D1 — Giờ cố định, giờ Việt Nam**: mặc định một phiên lúc **20:00** mỗi ngày; vận hành đổi bằng biến môi trường
`KAGE_QUIZ_TIMES` (`"HH:mm,HH:mm"`, hỏng ⇒ dùng mặc định). Mỗi phiên **10 câu**, mỗi câu mở **40 giây**, cách nhau 50
giây; câu rút ngẫu nhiên không lặp trong phiên (ngẫu nhiên ở server).

**D2 — Trả lời bằng kênh THẾ GIỚI như chat thường**: tin nhắn vẫn phát đi bình thường (qua mọi luật chat: khoá chat,
giới hạn tần suất, lọc từ), SAU khi phát mới đem chấm. So sánh sau khi chuẩn hoá (bỏ dấu tiếng Việt kể cả đ, chữ thường,
chỉ giữ chữ/số) với danh sách cách trả lời được chấp nhận — "Thanh Hư Tông", "thanh hu tong", "THANH HƯ" đều đúng. Người
đúng **đầu tiên** thắng (máy trạng thái `SectQuiz` trong RAM, `synchronized`); hết 40 giây không ai đúng thì lộ đáp án.

**D3 — Thưởng qua thư**: 100 linh thạch, khoá chống trùng tất định theo phiên + câu (`quiz:<ngày>T<giờ>:<câu>`) — chết
giữa chừng gửi lại cũng không thành hai thư. Thư thay vì cộng thẳng: người thắng có thể đang ở bất cứ đâu, và thư là
đường cấp thưởng đã có audit + idempotency.

**D4 — Ngân hàng câu hỏi**: bảng `quiz_question` (V102) — khoá i18n câu hỏi + đáp án hiển thị, cột `accepted` là các
cách trả lời (đã chuẩn hoá, `|`). 12 câu bản đầu rút từ phần CÔNG KHAI của STORY.md §1–§4 (tông môn, tông chủ, đại lục,
cảnh giới, thế lực, trưởng lão Chu Hoành, Đại Kiếp, tông phong) — không câu nào hé bí mật trục chính. Câu hỏi do AI
soạn, **chủ dự án duyệt lại**.

**D5 — Giao thức, chỉ thêm**: 808 `QuizQuestionEvent`, 809 `QuizRevealEvent` (phát cho mọi người online). Client hiện
thành dòng hệ thống trong khung chat, không có cửa sổ riêng.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Phiên giờ cố định (env), trả lời qua kênh thế giới, chuẩn hoá bỏ dấu, người đúng đầu tiên nhận thư 100 linh thạch | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-31 (lô 23 phần 2d): feature-chat `QuizRules` / `QuizQuestion` / `QuizPort` / `SectQuiz` (có
   test); `PostgresQuizAdapter` (V102); gateway `QuizHooks` (nhịp 2 giây trong `ExpansionJobs` + móc vào nhánh phát
   chat thế giới); client `QuizCoordinator`; bước probe `quiz:<giây>:<khoá>=<đáp án>;...`.
2. ✅ **Kiểm sống bằng gameProbe 2026-10-31**: server bật với `KAGE_QUIZ_TIMES=13:35`, Benkei chờ với bảng đáp án 8/12
   câu: câu 0 (Huyết Sa) ⇒ probe gõ "huyet sa" ⇒ gói 809 thắng=Benkei 100 linh thạch + gói thư mới, thư #42 "Hỏi đáp
   tông môn" 100 linh thạch trong DB; câu 1 (Đại Kiếp) và câu 2 (tông phong) không trả lời ⇒ hết 40 giây lộ đáp án,
   thắng=-. Đáp án có dấu truyền qua dòng lệnh PowerShell bị hỏng mã ký tự nên không kiểm sống được — phần bỏ dấu kiểm
   bằng unit test.
3. ⚠️ Chủ dự án xem: 12 câu hỏi (D4); giờ phiên mặc định 20:00 (D1); thưởng 100 linh thạch/câu.
