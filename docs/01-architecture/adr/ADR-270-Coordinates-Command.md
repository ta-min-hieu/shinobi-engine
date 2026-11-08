# ADR-270: Toạ độ hiện tại /toado

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Toạ độ hiện tại /toado" trong lượt hỏi lô 42–43 (AskUserQuestion; mô tả kèm: In bản đồ + toạ độ x,y của mình, có thể dán vào chat). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-141](ADR-141-Zone-Map-HUD-And-Wasteland-Waypoints.md) (vùng) · [ADR-250](ADR-250-Location-Bookmarks.md) (vị trí yêu thích) |

---

## Decision

**D1 — Thuần client.** `/toado` (hoặc `/coords`) in hoành độ đang đứng (mét, làm tròn — thế giới đi theo trục X, kiểu
ADR-033) kèm trạng thái vùng (thường / an toàn / PvP, ADR-141). Mô tả lúc chọn ghi "x,y + bản đồ": thế giới chỉ có trục
X có nghĩa với người chơi và client không có tên bản đồ ngoài trạng thái vùng, nên in đúng những gì có thật.

**D2 —** `/toado gui` (`send`) gửi dòng đó vào kênh chat đang chọn như một tin thường (server vẫn kiểm duyệt, giới hạn
tốc độ).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | hoành độ + trạng thái vùng; `/toado gui` gửi vào kênh đang chọn | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 42): `CoordinatesCoordinator`.
2. ✅ Test: `Lot42ClientTest.coordinatesRoundAndNameZones`.
