# ADR-210: Danh hiệu cộng chỉ số

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Danh hiệu cộng chỉ số" trong lượt hỏi lô 35 (AskUserQuestion; mô tả kèm: đeo danh hiệu được cộng một ít chỉ số). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-062](ADR-062-Achievements-And-Titles.md) (thành tựu + danh hiệu) · [ADR-126](ADR-126-Heavenly-Tribulation.md) (cùng khuôn cộng phẳng) · [ADR-065](ADR-065-Item-Sets.md) (đường chỉ số trang bị) |

---

## Decision

**D1 — Dữ liệu thuần** (V162): bốn cột `bonus_atk/def/res/hp` trên `achievement`. Chỉ danh hiệu ĐANG ĐEO tính (tối đa
một, luật ADR-062), nên không có chuyện gom nhiều danh hiệu cộng dồn. Số là **PLACEHOLDER** nhỏ (công +2…+10, máu
+20…+80) — đủ để có lý do chọn danh hiệu, không đủ thành trục sức mạnh. Đổi bằng SQL, khởi động lại server (bảng đọc
một lần, như bộ pháp khí).

**D2 — Chung đường chỉ số**: `TitleEffects.plus` cắm vào chuỗi cộng phẳng sẵn có (Thiên Kiếp, tẩy luyện, đồ giám, chuyển
thế, kỹ năng bang, trận pháp) — vào world, mặc/gỡ đồ, bảng nhân vật, Lực Chiến đều thấy cùng một con số. Đổi / bỏ danh
hiệu ⇒ server áp lại chỉ số ngay.

**D3 — Protocol, chỉ thêm**: `AchievementView.bonus_atk/def/res/hp = 7…10`. Không opcode mới.

**D4 — Client**: dòng thành tựu nối "(Công +4, Kháng +4)" sau tên danh hiệu, chỉ chỉ số khác 0.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cột chỉ số trên `achievement` (V162), chỉ danh hiệu đang đeo, cộng phẳng chung đường chỉ số; field 7–10 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 35): V162, `TitleBonusPort`/`Facade`, `PostgresTitleBonusAdapter`, `TitleEffects`,
   áp lại chỉ số khi đổi danh hiệu, `AchievementCoordinator.bonusSuffix`.
2. ✅ Test: `AchievementCountIntegrationTest` (đọc đúng số V162), `TitleEffectsTest`, `AchievementBonusTextTest`.
3. ✅ Kiểm sống gameProbe 2026-11-07 (V162): ĐạoHữu01 không đeo gì ⇒ máu tối đa 168, Lực Chiến 1652; `--setTitle=level_10`
   ⇒ `OK`, bảng nhân vật máu tối đa 198 (+30 đúng số V162), Lực Chiến 1682. Đã bỏ danh hiệu như lúc đầu.
