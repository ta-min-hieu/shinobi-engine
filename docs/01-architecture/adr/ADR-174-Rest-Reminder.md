# ADR-174: Nhắc nghỉ ngơi

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-04) — chủ dự án chọn "Nhắc nghỉ ngơi" trong lượt hỏi lô 30 (AskUserQuestion). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-04 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-143](ADR-143-Display-Settings.md) (`/caidat`, công tắc lưu trên máy) · [ADR-156](ADR-156-Auto-Potion.md) (cùng nhịp `update` mỗi khung) |

---

## Decision

**D1 — Nhịp**: chơi liên tục **2 giờ** (tính từ lúc vào thế giới, đồng hồ đơn điệu của client) thì in một dòng hệ thống
nhắc nghỉ; sau đó **mỗi giờ** nhắc lại, kèm số giờ đã chơi. Chỉ nhắc — KHÔNG chặn chơi, không giảm thưởng (không phải
chống nghiện kiểu cắt EXP). Số giờ là PLACEHOLDER (Điều 27).

**D2 — Thuần client**: không opcode, không bảng, không gửi gì lên server — đây là tiện ích sức khoẻ cho người ngồi trước
máy, không phải dữ liệu gameplay. Vào lại thế giới (relog, đổi nhân vật) thì đếm lại từ đầu.

**D3 — Công tắc**: `DisplaySettings.Option.REST_REMINDER` trong `/caidat`, mặc định **BẬT**, lưu trên máy như các công tắc
ADR-143. Tắt thì im lặng nhưng vẫn giữ nhịp — bật lại thì nhắc ở mốc giờ kế tiếp, không dồn.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Nhắc sau 2 giờ rồi mỗi giờ, dòng hệ thống chat, thuần client, công tắc `/caidat` mặc định bật | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-04 (lô 30): `RestReminder` (gọi mỗi khung từ `WorldScreen.syncCombatHud`), công tắc
   `REST_REMINDER`, chuỗi `rest_reminder.notice` vi/en.
2. ✅ Test: `RestReminderTest` (chưa tới 2 giờ thì im; đúng 2 giờ nhắc một lần; mỗi giờ sau nhắc tiếp; tắt thì im và giữ
   nhịp). Không kiểm sống bằng gameProbe — probe không chạy client đồ hoạ và không ai chờ 2 giờ; logic nằm trọn trong lớp
   đã test bằng đồng hồ giả.
3. Chờ chủ dự án: mốc 2 giờ / mỗi giờ; có muốn thêm bảng nhắc giữa màn hình thay vì dòng chat không.
