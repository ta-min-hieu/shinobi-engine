# ADR-413: DPS trên HUD

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "DPS trên HUD" trong lượt hỏi lô 60 (AskUserQuestion; mô tả kèm: /dpshud: dòng HUD hiện sát thương mỗi giây của mình trong trận đang đánh). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-405](ADR-405-Combat-Timer.md) · [ADR-251](ADR-251-Party-Damage-Meter.md) |

---

## Decision

**D1:** cộng sát thương các đòn/kỹ năng do chính mình gây ra (`AttackResultEvent`/`SkillResultEvent`) trong trận hiện tại — trận mở ở đòn đầu, kết thúc khi 5 giây không đánh (cùng ADR-405); DPS = tổng / số giây (tối thiểu 1). Ngoài trận để trống.

**D2:** `/dpshud`, nhớ trên máy, mặc định tắt. Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, cửa sổ trận 5 giây | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 60): `HudStatsCoordinator` (dòng `DPS`, `dps`).
2. ✅ Test: `Lot60ClientTest.dpsOnlyInsideCombat`.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
