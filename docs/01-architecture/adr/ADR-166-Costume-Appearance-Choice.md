# ADR-166: Huyễn hình trang phục

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-03) — chủ dự án chọn "Huyễn hình trang bị" trong lượt hỏi lô 29, rồi khi được báo trang bị KHÔNG vẽ lên nhân vật (ngoại hình chỉ theo linh căn + bậc trang phục theo cảnh giới, ADR-132) đã chọn hướng **"Huyễn hình trang phục"** (AskUserQuestion; mô tả kèm: cho người chơi chọn hiển thị một bậc trang phục ĐÃ mở — bậc cảnh giới hiện tại hoặc thấp hơn — mọi người thấy ngay qua gói đổi ngoại hình sẵn có; tốn vàng). Các hướng không chọn: vẽ vũ khí lên model (việc lớn, cần ADR rendering + art), bỏ tính năng, chỉ đổi icon trong túi. |
| Date | 2026-11-03 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-132](ADR-132-Costume-Tier-By-Realm.md) (bậc trang phục theo cảnh giới) · [ADR-045](ADR-045-Appearance-Changed-Event.md) (gói đổi ngoại hình 307) |

---

## Decision

**D1 — Dữ liệu (V134)**: `character_costume_choice(character_id PK, costume_tier 1–3, chosen_at)`; không có dòng = tự
theo cảnh giới. Phí **2.000 vàng** mỗi lần chọn một bậc cụ thể; trở về "theo cảnh giới" miễn phí (xoá dòng).
PLACEHOLDER (Điều 27). Một transaction: trừ phí (`GoldLedger`) + ghi + audit `COSTUME_CHOSEN`.

**D2 — Chỉ bậc ĐÃ mở**: bậc chọn phải ≤ bậc theo cảnh giới hiện tại (ADR-132: B2 từ Cấp 19, B3 từ Cấp 37), nếu không
⇒ `TIER_LOCKED`. Bậc HIỆN = min(bậc chọn, bậc đã mở) — trùng sinh tụt cấp không làm lộ bậc chưa mở. Chọn đúng bậc đang
mở coi như "theo cảnh giới".

**D3 — Một chỗ áp cho mọi đường**: `ConnectedCharacterRegistry` áp bậc đã chọn (`CostumeChoiceHooks`) MỖI lần ghi
ngoại hình (`register` lúc vào world/trùng sinh, `updateAppearance` lúc lên cấp/đổi hệ) — không nơi dựng ngoại hình nào
phải tự nhớ. Bậc đã chọn nhớ trong RAM (`CostumeFacade`, đọc DB một lần mỗi nhân vật). `CostumeTierHooks` so ngoại hình
cũ với ngoại hình ĐÃ ÁP để quyết phát 307. Đổi xong, gateway chạy lại đúng đường lên cấp ⇒ người đang nhìn thấy nhận
`CharacterAppearanceChangedEvent` (307), người tới sau nhận `CharacterSpawnedEvent` mang bậc mới.

**D4 — Giao thức, chỉ thêm**: `SetCostumeCommand{CostumeTier tier}` (UNSPECIFIED = theo cảnh giới) /
`SetCostumeResponse{outcome, shown_tier, fee}` — 245/246. Outcome: OK / TIER_LOCKED / NOT_ENOUGH_GOLD.

**D5 — Client**: lệnh chat `/huyenhinh 1|2|3|tudong` (hoặc `/costume 1|2|3|auto`); kết quả một dòng hệ thống. Model mới
tải qua đường 307/303 sẵn có — không đổi phía render.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Chọn hiển thị bậc trang phục đã mở (2.000 vàng, về tự động miễn phí), áp ở registry mỗi lần ghi ngoại hình; 245/246 | Accepted |

## ADR cần tạo

- Không. (Vẽ trang bị lên model — nếu muốn sau này — là ADR rendering riêng.)

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-03 (lô 29): V134, `CostumePort`/`CostumeFacade` (feature-character), `PostgresCostumeAdapter`,
   `CostumeChoiceHooks` (registry), `CostumeCommands` 245/246, `CostumeCoordinator` `/huyenhinh`.
2. ✅ Test: `CostumeChoiceHooksTest` (min(chọn, đã mở); không chọn/chưa biết giữ nguyên), `CostumeIntegrationTest`
   (Postgres thật: chọn trừ phí + nhớ; về tự động miễn phí + xoá; thiếu vàng không đổi gì).
3. ✅ Kiểm sống gameProbe 2026-11-03 (hai nhân vật cạnh nhau): ĐạoHữu01 Cấp 20 (mở tới B2) chọn B3 ⇒ `TIER_LOCKED`; chọn
   B1 ⇒ OK, −2.000 vàng, BaoDo01 đang đứng cạnh nhận 307; vào lại world ⇒ BaoDo01 thấy spawn `COSTUME_TIER_B1`;
   `/huyenhinh tudong` ⇒ miễn phí, về B2, lại 307.
4. Chờ chủ dự án: phí 2.000 vàng (PLACEHOLDER); có muốn thêm "Huyễn Hình Phù" thay cho vàng không.
