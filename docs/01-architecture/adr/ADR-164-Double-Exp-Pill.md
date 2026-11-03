# ADR-164: Đan x2 kinh nghiệm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-03) — chủ dự án chọn "Đan x2 kinh nghiệm" trong lượt hỏi lô 29 (AskUserQuestion; mô tả kèm: vật phẩm dùng được: nhân đôi EXP đánh quái trong 30 phút (cộng dồn thời gian, hiện đồng hồ trên HUD)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-03 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-088](ADR-088-Bag-Expansion.md) (cùng khuôn: hiệu ứng ghi TRONG giao dịch dùng vật phẩm) · [ADR-149](ADR-149-Daily-Fortune.md) (chuỗi hệ số EXP đánh quái) · [ADR-067](ADR-067-Double-Events.md) (x2 sự kiện toàn server) |

---

## Decision

**D1 — Dữ liệu (V132)**: bảng `character_exp_buff(character_id PK, expires_at)`; vật phẩm `double_exp_pill` (Song Bội Ngộ
Đạo Đan, chồng 20, `use_effect = DOUBLE_EXP`, `use_value = 30` phút). Nguồn: cửa hàng tông môn 3.000 vàng, boss thế giới
50%. PLACEHOLDER (Điều 27).

**D2 — Dùng đan**: đi nút **Dùng** chung (`UseItemCommand` 512). Hiệu ứng mới `ItemUseEffect.DOUBLE_EXP`; `UseItemUseCase`
ghi hạn TRONG cùng transaction trừ viên đan (`InventoryPort.extendDoubleExp`, cùng khuôn Túi Càn Khôn): hạn mới =
max(hạn cũ, bây giờ) + 30 phút — **cộng dồn**; vượt **trần 300 phút** còn lại ⇒ outcome mới `EXP_BUFF_AT_MAX`
(`error.exp_buff_at_max`), không trừ viên đan.

**D3 — Nhân EXP**: chuỗi hệ số EXP đánh quái của gateway (sự kiện x2 → bang → đạo lữ → kết nghĩa → quẻ bói) thêm mắt
cuối `ExpBuffFacade.boosted` (feature-character): còn hạn ⇒ ×2. Hạn nhớ trong RAM (đọc DB một lần mỗi nhân vật, như quẻ
bói); dùng đan xong gateway `refresh`. Chỉ EXP đánh quái — EXP nhiệm vụ/bế quan/quét Bí Cảnh không nhân.

**D4 — Giao thức, chỉ thêm**: `ItemUseEffect.ITEM_USE_EFFECT_DOUBLE_EXP = 4`; event `ExpBuffChangedEvent{seconds_left}`
(244) gửi khi vừa dùng đan và khi vào world mà còn hạn.

**D5 — Client**: dòng HUD góc trên-phải "x2 EXP tới HH:mm (còn N phút)" (`ExpBuffCoordinator`), tự gỡ khi hết giờ
(libGDX `Timer`). Chỉ hiển thị — tính từ lúc nhận gói, không tin đồng hồ client cho gameplay.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Đan dùng qua nút Dùng chung, hạn ghi trong giao dịch dùng, dồn tới trần 300 phút, ×2 EXP đánh quái ở cuối chuỗi; event 244 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-03 (lô 29): V132, `ItemUseEffect.DOUBLE_EXP`, `InventoryPort.extendDoubleExp`, `ExpBuffPort`/
   `ExpBuffFacade`, `PostgresExpBuffAdapter`, `ExpBuffCommands` (244), `ExpBuffCoordinator` + `HudBanners.setExpBuff`.
2. ✅ Test: `ExpBuffFacadeTest` (×2 chỉ trong hạn, đọc DB một lần, refresh), `DoubleExpIntegrationTest` (Postgres thật: dồn
   thời gian, viên thứ 11 vượt trần 300 phút bị từ chối), `ExpBuffCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-03: dùng đan ⇒ `EXP_BUFF_CHANGED_EVENT` (244), hạn còn 30 phút trong DB; hạ hình nộm luyện
   công: không đan +20 EXP, có đan +40 EXP (20241 → 20281); vào lại world ⇒ nhận lại 244.
4. Chờ chủ dự án: thời lượng/trần/giá/tỉ lệ rơi (PLACEHOLDER); tên "Song Bội Ngộ Đạo Đan"; icon đan (nợ art).
