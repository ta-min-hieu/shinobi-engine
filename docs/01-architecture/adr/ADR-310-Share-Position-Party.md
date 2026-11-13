# ADR-310: Chia sẻ vị trí vào nhóm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Chia sẻ vị trí vào nhóm" trong lượt hỏi lô 47 (AskUserQuestion 2026-11-12; mô tả kèm: /vitri: gửi tọa độ + tên vùng hiện tại của mình vào kênh nhóm). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-270](ADR-270-Coordinates-Command.md) · [ADR-250](ADR-250-Location-Bookmarks.md) |

---

## Decision

**D1 — Thuần client, mở rộng ADR-270.** `/chiasevitri` (hoặc `/sharepos`) gửi dòng tọa độ + trạng thái vùng y như
`/toado gui`, nhưng LUÔN vào **kênh nhóm** (không phụ thuộc kênh đang chọn). Không ở nhóm thì server từ chối như mọi tin
kênh nhóm.

**D2 — Đổi tên lệnh so với mô tả lúc hỏi:** `/vitri` đã là lệnh "vị trí yêu thích" (ADR-250) nên dùng `/chiasevitri`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | `/chiasevitri` gửi tọa độ vào kênh nhóm | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 47): `CoordinatesCoordinator`.
2. ✅ Test: biên dịch + kiểm tay (UI).
3. ⏳ Kiểm sống gameProbe.
