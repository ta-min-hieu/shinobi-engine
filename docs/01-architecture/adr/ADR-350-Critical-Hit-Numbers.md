# ADR-350: Số chí mạng nổi bật

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Số chí mạng nổi bật" trong lượt hỏi lô 52 (AskUserQuestion; mô tả kèm: số sát thương chí mạng to hơn, màu khác và có dấu '!' (hiện chưa phân biệt)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) · [ADR-307](ADR-307-Colorblind-Mode.md) |

---

## Decision

**D1 — Hiện:** đòn có `is_crit` (đã có trong `AttackResultEvent`/`SkillResultEvent`) ⇒ số "-N!" to gấp 1,4 lần; mình gây ra thì màu cam, mình chịu thì giữ màu "bị trúng" (kể cả màu chế độ mù màu ADR-307).

**D2 — Không cấp phát:** `FloatingCombatText` thêm hệ số phóng cho từng mục trong pool; đặt cỡ chữ trước khi vẽ mục đó và trả cỡ chữ gốc sau vòng vẽ (font dùng chung với HUD). Không đổi protocol, không công tắc.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Số chí mạng "-N!" to 1,4 lần, cam; phóng theo mục trong pool; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 52): `FloatingCombatText.show(..., scale)`/`CRIT`, `CombatFeedbackPresenter.nextIsCrit`.
2. ✅ Test: đường vẽ là libGDX — kiểm bằng client thật; logic chọn màu nằm cạnh số thường đã có test hiện số.
3. ⏳ Thuần client — cần chạy client thật để nhìn số.
