# ADR-204: Lọc nhặt đồ (tự bán đồ rác)

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Lọc nhặt đồ" trong lượt hỏi lô 34 (AskUserQuestion; mô tả kèm: chọn ngưỡng — đồ rơi dưới mức đó tự đi thẳng vào bán vặt thay vì chiếm ô túi). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-170](ADR-170-Sell-Junk.md) (cờ `junk`, bán nhanh đồ rác) · [ADR-150](ADR-150-Group-Loot-Rolls.md) (tách đồ rơi) |

---

## Lệch so với mô tả lúc hỏi

Mô tả nói "chọn ngưỡng". Vật phẩm **không có thang phẩm chất** để đặt ngưỡng; thứ duy nhất phân loại "đồ bỏ được" là cờ
`item_template.junk` của ADR-170 (đã có chủ dự án duyệt danh sách). Nên "ngưỡng" thành một công tắc: **bật = đồ rác tự
bán**. Khi game có phẩm chất vật phẩm, thêm ngưỡng thật là một ADR mới.

## Decision

**D1 — Công tắc theo nhân vật**: V159 `character.auto_sell_junk` (mặc định TẮT — đồ rác còn là vật nộp nhiệm vụ).
`SetAutoSellJunkCommand{enabled, query_only}` / `SetAutoSellJunkResponse{enabled}` 556/557.

**D2 — Lúc chia đồ rơi**: dùng đường tách sẵn có của `GrantLootUseCase` (ADR-150): chồng nào là rác **có giá thu** ở
Tàng Bảo Các và người hạ đã bật ⇒ không vào túi, bán ngay theo giá thu × số lượng, một transaction qua `GoldLedger`
(`gold.reason.auto_sell_junk`, hiện ở sổ vàng) + audit `LOOT_AUTO_SOLD`, rồi một dòng chat "Tự bán N món rác: +X linh
thạch". Công tắc chỉ được đọc từ DB khi thật sự có món rác rơi (tối đa một lần mỗi lượt chia). Vàng chạm trần ⇒ không
bán, món vào túi như thường — **không bao giờ mất đồ**. Món rác không có giá thu ⇒ vẫn vào túi.

**D3 — Client**: lệnh chat `/tubanrac bat|tat` (hoặc `/autosell on|off`); không kèm gì = xem trạng thái.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Công tắc `auto_sell_junk` (V159), tách rác lúc chia đồ rơi, bán theo giá thu qua `GoldLedger`, 556/557, `/tubanrac` | Accepted |

## ADR cần tạo

- Khi có phẩm chất vật phẩm: ngưỡng lọc thật.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 34): V159, `AutoSellJunkPort`/`Facade`, `PostgresAutoSellJunkAdapter`, `AutoSellHooks`,
   `LootRollHooks.grantLoot`, `Lot34Commands.setAutoSellJunk`, `AutoSellCoordinator`.
2. ✅ Test: `AutoSellJunkIntegrationTest` (Postgres thật: công tắc, danh mục, bán cộng vàng, chạm trần không đổi gì).
3. ✅ Kiểm sống gameProbe 2026-11-07 (V159): `autoSell:?` ⇒ `false`, `autoSell:on` ⇒ `true`; ĐạoHữu01 hạ Hình Nhân Gỗ ⇒
   rơi 1 phi đao + 1 sắt vụn, KHÔNG vào túi, dòng chat "Tự bán 2 món rác: +14 linh thạch", vàng +14, audit `LOOT_AUTO_SOLD`
   đúng hai món. Công tắc, vàng và vị trí đã trả về như cũ.
4. ✅ Test bundle bắt được chữ "…" không có trong font tiếng Việt — đã đổi thành "...".
