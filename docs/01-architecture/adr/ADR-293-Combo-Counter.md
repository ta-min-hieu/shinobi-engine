# ADR-293: Đếm combo đòn đánh

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Đếm combo đòn đánh" trong lượt hỏi lô 45 (AskUserQuestion; mô tả kèm: hiện "Liên kích ×N" khi đánh trúng liên tục). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-283](ADR-283-Fps-Counter.md) (cùng khuôn công tắc Preferences) |

---

## Decision

**D1 — Thuần client, chỉ hiển thị.** `ComboCounter`: mỗi đòn TRÚNG do chính mình gây ra trong vòng 3 giây kể từ đòn
trước cộng một, quá hạn đếm lại từ 1. Từ đòn thứ 3, `CombatFeedbackPresenter` bay chữ "Liên kích xN" trên đầu nhân vật.
Né không tính, không đứt chuỗi. Không ảnh hưởng sát thương (server vẫn quyết mọi thứ).

**D2 — `/combo` bật/tắt** (mặc định bật), nhớ trong Preferences `hud.combo`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | `ComboCounter` 3 giây, hiện từ đòn 3; `/combo` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 45): `ComboCounter`, `ComboCoordinator`, `CombatFeedbackPresenter.useCombo`.
2. ✅ Test: `Lot45ClientTest.comboCountsWithinWindowAndResets`.
3. ⏳ Thuần client — probe headless không vẽ UI; phủ bằng test đơn vị + biên dịch, cần kiểm tay trên client thật.
