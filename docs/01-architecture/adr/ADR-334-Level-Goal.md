# ADR-334: Mục tiêu cấp độ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Mục tiêu cấp độ /muctieu" trong lượt hỏi lô 50 (AskUserQuestion; mô tả kèm: đặt cấp muốn đạt; HUD hiện % tiến độ và EXP còn thiếu tới cấp đó). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-272](ADR-272-Exp-Per-Hour.md) · [ADR-034](ADR-034-Manual-Attack-And-Experience.md) |

---

## Decision

**D1 — Đặt:** `/muctieu <cấp>` (`/goal`), cấp phải lớn hơn cấp hiện tại và ≤ 100; `/muctieu xoa` bỏ. Nhớ trên máy.

**D2 — HUD:** dòng "Mục tiêu cấp N: còn K cấp (X% cấp này)" góc dưới trái, cập nhật mỗi `ExperienceChangedEvent` từ `level`, `exp_into_level`, `exp_for_next_level` server gửi. Client KHÔNG biết đường cong EXP nên không hiện tổng EXP còn thiếu tới cấp đích (khác mô tả lúc hỏi) — hiện số cấp còn lại và % cấp đang đi; ước thời gian đã có ở `/exph` (ADR-272).

**D3 — Tới nơi:** một dòng chúc mừng rồi tự bỏ mục tiêu.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Mục tiêu lưu máy; HUD "còn K cấp, X% cấp này" từ số server; `/muctieu`; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 50): `LevelGoalCoordinator`, `CombatHud.setLevelGoal` (`HudInfoLines`).
2. ✅ Test: `Lot50ClientTest.goalPercentClamps`.
3. ⏳ Thuần client — cần chạy client thật để nhìn dòng HUD.
