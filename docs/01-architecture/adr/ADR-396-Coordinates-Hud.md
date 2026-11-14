# ADR-396: Toạ độ trên HUD

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Toạ độ trên HUD" trong lượt hỏi lô 58 (AskUserQuestion; mô tả kèm: /toadohud bật/tắt dòng toạ độ nhân vật luôn hiện trên HUD). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-270](ADR-270-Coordinates-Command.md) |

---

## Decision

**D1:** dòng nhỏ góc dưới trái "Toạ độ (m): N" — cùng nguồn với `/toado` (ADR-270, hoành độ làm tròn về mét); chỉ dựng lại chữ khi số đổi. Mặc định tắt; `/toadohud` (`/coordshud`) bật/tắt, nhớ trên máy.

**D2:** thêm cơ chế chung `CombatHud.addCounterLine` (một dòng số trên HUD: tiền tố i18n + nguồn số, âm = để trống) để các lô sau dùng lại.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, `addCounterLine` dùng chung | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 58): `HudCounterLine`, `HudInfoLines.addCounter`, `CoordinatesCoordinator.meters`, `ChatViewCoordinator`.
2. ✅ Test: kiểm bằng mắt; logic dùng chung `appendClock` đã có test.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
