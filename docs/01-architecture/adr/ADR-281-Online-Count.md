# ADR-281: Đếm người online

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Đếm người online" trong lượt hỏi lô 44 (AskUserQuestion; mô tả kèm: /online: số người đang online, chia theo linh căn / cảnh giới). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-038](ADR-038-Theme-Pivot-Tu-Tien.md) (cảnh giới) · [ADR-066](ADR-066-Faction-Change.md) (linh căn) |

---

## Decision

**D1 — Chỉ con số, không tên.** `GetOnlineCountRequest{}` / `GetOnlineCountResponse{total, repeated FactionCount{faction,
count}, repeated int32 by_realm}` 269/270 (dải Character). Đếm trên danh sách nhân vật đang online của registry (gồm cả
người trong hầm ngục/đấu trường); linh căn chưa chọn ⇒ `NONE`; cảnh giới theo bảng cấp của `BreakthroughHooks` (18 cấp
mỗi cảnh giới, Hoá Thần từ cấp 91) — cùng bảng client hiển thị. Client `/online`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 269/270 đếm tổng + linh căn + cảnh giới, không tên; `/online` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 44): `Lot44Commands.onlineCount`, `OnlineCountCoordinator`.
2. ✅ Test: `Lot44ServerTest.mapsLevelsToSixRealms`, `Lot44ClientTest`.
3. ✅ Kiểm sống gameProbe 2026-11-12 (`onlineCount`, đổi tên bước vì `online` đã là bước thưởng online lô 21): tổng=2, linh căn HOA:2, cảnh giới [1, 1, 0, 0, 0, 0].
