# ADR-136: Nghi lễ đột phá cảnh giới

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-01) — chủ dự án chọn "Nghi lễ đột phá cảnh giới" trong lượt hỏi lô 25 (AskUserQuestion; mô tả kèm: VFX đột phá + thông báo toàn server + danh hiệu cảnh giới hiện trên đầu). Phần "danh hiệu trên đầu" TÁCH ra mục treo — xem D3. |
| Date | 2026-11-01 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-126](ADR-126-Heavenly-Tribulation.md) (Thiên Kiếp — đường duy nhất qua mốc cảnh giới) · [ADR-132](ADR-132-Costume-Tier-By-Realm.md) |

---

## Decision

**D1 — Thông báo toàn server**: lên cấp MỞ ĐẦU một cảnh giới (19 Trúc Cơ, 37 Tử Phủ, 55 Kim Đan, 73 Nguyên Anh, 91 Hoá
Thần — `BreakthroughHooks.realmOpenedAt`) ⇒ một dòng hệ thống song ngữ tới mọi người đang online
("Benkei đột phá Trúc Cơ! / Benkei broke through to Foundation Establishment!"), cùng khuôn thông báo bang chiến
(`ChatMessageEvent` kênh hệ thống). Móc vào hook lên cấp (`CharacterLevelSync`, sau sư đồ và bậc trang phục). Vì 18/36/…
là trần Thiên Kiếp, đột phá luôn xảy ra ngay khi thắng kiếp.

**D2 — VFX đột phá cho chính mình**: client thấy `ExperienceChangedEvent.leveled_up` mà cảnh giới của cấp mới khác cấp
trước (`CultivationRealm`, hiển thị thuần) ⇒ phát `vfx_dot_pha` (cột sáng vàng dâng lên + hai vòng hào quang + đốm
sáng bay) trên chính mình và chữ "ĐỘT PHÁ <cảnh giới>!" thay chữ lên cấp thường. Không gói mới.

**D3 — Danh hiệu cảnh giới trên đầu: CHƯA làm, tách mục.** Client hiện không vẽ chữ nào trên đầu nhân vật (không tên,
không danh hiệu), và `CharacterSpawnedEvent` không mang cấp của người khác. Làm thật cần: thêm field cấp vào gói spawn
(đổi protocol — chỉ thêm) + một bộ vẽ chữ trong không gian 3D theo camera. Đề xuất làm cùng "Khung UI tu tiên" của lô
này hoặc lô sau; người khác vẫn thấy đột phá qua dòng thông báo D1 và bậc trang phục mới (ADR-132).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Báo toàn server ở cấp mở đầu cảnh giới; VFX client tự phát khi cảnh giới đổi | Accepted |
| 2 | Danh hiệu trên đầu cần đổi protocol + bộ vẽ chữ 3D | Treo — chờ làm cùng Khung UI |

## ADR cần tạo

- Không (D3 nằm trong phạm vi lựa chọn đã duyệt; khi làm sẽ ghi vào ADR của Khung UI).

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-01: `BreakthroughHooks` (+ test), `CombatFeedbackPresenter.showLevelUp(level)`, VFX `dot_pha`
   (`build_skill_vfx.py`), chuỗi `hud.exp.breakthrough`.
2. ✅ **Kiểm sống bằng gameProbe 2026-11-01**: Benkei (Cấp 18) qua Thiên Kiếp lên Cấp 19 ⇒ Kagemaru (đang online) nhận dòng hệ thống "Benkei đột phá Trúc Cơ! / Benkei broke through to Foundation Establishment!". VFX phía client chưa xem bằng ảnh chụp.
3. ⏳ D3 — danh hiệu cảnh giới trên đầu.
