# ADR-408: Lịch sử chat thế giới

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Lịch sử chat thế giới" trong lượt hỏi lô 60 (AskUserQuestion; mô tả kèm: /lichsuthegioi: xem 20 tin kênh thế giới gần nhất). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-401](ADR-401-Guild-Chat-History.md) · [ADR-304](ADR-304-Guild-Chat-Backlog.md) |

---

## Decision

**D1:** mỗi tin kênh thế giới vừa phát được giữ trong RAM (dùng lại lớp bộ nhớ tin bang của ADR-304, một khoá chung, 50 tin); `/lichsuthegioi` phát lại tối đa **20** tin gần nhất cho riêng người hỏi, kèm giờ `[HH:mm UTC]`. Không lưu DB — khởi động lại server là rỗng. Kênh giao dịch không giữ.

**D2 — Dây:** `GetWorldChatHistoryRequest{}` / `GetWorldChatHistoryResponse{count}` **822/823**; tin đi trước bằng `ChatMessageEvent` kênh thế giới. Client `/lichsuthegioi` (`/worldhistory`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lệnh 822/823, ≤ 20 tin từ RAM | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 60): `registry.worldChat`, ghi trong `broadcastServerWide`, `Lot60Commands.worldChatHistory/worldReplay`, `LookupToolsCoordinator`.
2. ✅ Test: `Lot60ServerTest.worldReplayGoesToWorldChannelWithTime`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14: ĐạoHữu01 chat kênh thế giới rồi `worldHistory:` ⇒ `tin=1`.
