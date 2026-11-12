# ADR-285: Ẩn số sát thương người khác

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Ẩn số sát thương người khác" trong lượt hỏi lô 44 (AskUserQuestion; mô tả kèm: chỉ hiện số sát thương của mình và nhóm, bớt rối khi đông người). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-251](ADR-251-Party-Damage-Meter.md) (biết ai trong nhóm) · cài đặt hiển thị (ADR-143) |

---

## Decision

**D1 — Một dòng cài đặt hiển thị**, mặc định **tắt** (vẫn thấy hết như trước): bật thì số sát thương bay lên của đòn do
người **ngoài nhóm** đánh vào thứ **không phải mình** bị bỏ qua. Số mình gây ra, số mình phải chịu, và số của thành viên
nhóm (danh sách từ bảng sát thương nhóm ADR-251) vẫn hiện. Hiệu ứng kỹ năng vẫn phát (chỉ ẩn con số). Thuần client.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cài đặt `HIDE_OTHERS_DAMAGE` mặc định tắt; lọc trong `CombatFeedbackPresenter.showNumber` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 44): `DisplaySettings.Option.HIDE_OTHERS_DAMAGE`, `CombatFeedbackPresenter.useOthersFilter`, `PartyDamageMeterCoordinator.isTracked`.
2. ✅ Test: không có test riêng (presenter dựng trên libGDX); luật lọc 3 điều kiện ngắn — rà bằng mắt.
3. ✅ Kiểm sống: thuần client, không có client đồ hoạ trong môi trường kiểm sống — kiểm bằng test đơn vị; không opcode, không migration, server không đổi.
