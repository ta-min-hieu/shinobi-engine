# ADR-105: Báo cáo người chơi

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-25) — chủ dự án chọn "Báo cáo người chơi" trong lượt hỏi lô 22 (AskUserQuestion; mô tả kèm: người chơi báo cáo (chat bẩn/hack/lừa đảo) kèm 20 dòng chat gần nhất; GM xem bằng /gm reports). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-25 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-052](ADR-052-In-Game-GM-Commands.md) · ADR-091 (chặn) |

---

## Decision

**D1 — Dữ liệu (V89)**: `player_report(id, reporter_id, target_id, reason, note, chat_excerpt, created_at,
handled_at, handled_by)`. `reason` ∈ {`CHAT`, `CHEAT`, `SCAM`, `OTHER`}; `note` ≤ 200 ký tự.

**D2 — Gửi**: `/report <tên> <chat|cheat|scam|other> [ghi chú]` trong ô chat. Server trích **20 tin nhắn gần nhất của
người bị báo cáo** từ `audit_log` (`CHAT_MESSAGE` — nguồn duy nhất còn giữ nội dung chat, GAMEPLAY.md §10) vào
`chat_excerpt` NGAY lúc báo cáo (janitor có xoá audit chat cũ cũng không mất bằng chứng). Không tự báo cáo mình; tối đa
5 báo cáo/ngày mỗi người; cùng người bị báo cáo chỉ 1 lần/ngày.

**D3 — GM**: `/gm reports` (GM) liệt kê 10 báo cáo chưa xử lý mới nhất; `/gm report <id>` xem trích chat; `/gm
resolve <id>` đánh dấu đã xử lý.

**D4 — Giao thức, chỉ thêm**: 806/807 `ReportPlayerCommand{target_name, reason, note}` / `ReportPlayerResponse{outcome}`
(dải Chat).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Báo cáo kèm 20 tin chat trích từ audit_log, 5/ngày, GM liệt kê + đánh dấu | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ⏳ Thi hành (lô 22 phần 1).
2. ⏳ Kiểm sống bằng gameProbe.
