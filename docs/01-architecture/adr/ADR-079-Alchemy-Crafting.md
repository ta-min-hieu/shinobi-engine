# ADR-079: Luyện đan — chế tạo theo công thức ở lò đan của trưởng lão

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Luyện đan (chế tạo)" trong lượt hỏi lô 19 (AskUserQuestion; mô tả kèm: module mới feature-crafting, công thức data-driven — nguyên liệu rơi từ quái + vàng ⇒ đan dược/Linh Ngọc, NPC lò đan ở tông môn, migration + opcode mới). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | GDD §8 (nhiệm vụ ngày "luyện đan"), §10 (gold sink) · [ADR-061](ADR-061-Sect-Storage.md) (mở cửa sổ qua hội thoại + kiểm đứng gần NPC) · [ADR-078](ADR-078-Pet-Evolution.md) |

---

## Context

Mọi vật phẩm hiện đến từ ba nguồn: cửa hàng, rơi đồ, thưởng quest/thư. Không có chỗ biến đồ rơi "vô dụng" thành thứ
dùng được, và không có nguyên liệu nào. Nhiệm vụ ngày đã nhắc "luyện đan" nhưng không có cơ chế.

## Decision

**D1 — Module MỚI `feature-crafting`** (`dev.kage.crafting`, Clean Arch như feature-checkin): `Recipe`/`RecipeMaterial`
(domain), `CraftItemUseCase`, `CraftingFacade`. Không import feature khác; chạm túi đồ/vàng/audit qua port
`CraftingWorkshopPort` hiện thực ở server-database (cùng khuôn `InventoryGatewayPort` của feature-mail).

**D2 — Công thức là DỮ LIỆU** (V76): `crafting_recipe` (mã, món ra, số lượng ra, linh thạch, cấp yêu cầu) +
`crafting_recipe_material` (nguyên liệu × số lượng). Bản đầu 5 công thức: Tiểu Hồi Xuân Đan, Linh Thú Đan, và Hoả /
Thuỷ / Mộc Linh Ngọc. Nguyên liệu MỚI: **Linh Thảo** (`spirit_herb`) và **Yêu Đan** (`demon_core`), thêm vào các bảng
rơi đồ đang có. Mọi con số là tham số SQL.

**D3 — Một lần luyện = một transaction**: khoá túi đồ → trừ từng nguyên liệu (thiếu ⇒ `MISSING_MATERIALS`; phần đang
chào trong giao dịch không tính) → trừ linh thạch qua `GoldLedger` (`gold.reason.craft`) → cấp món ra (túi đầy ⇒
`BAG_FULL`) → audit `CRAFT_ITEM`. Mọi nhánh từ chối rollback trọn. Không idempotency key (cùng lý do kho đồ ADR-061
D4): lặp lệnh chỉ luyện thêm một lần bằng tài sản của chính mình, không tạo hay nhân đôi gì.

**D4 — Ở đâu**: lò đan của trưởng lão Chu Hoành (`town_elder`) — lựa chọn thoại `open_crafting` mở cửa sổ; lệnh luyện
kiểm đứng gần ông ở server (`NpcProximity`, Điều 15). Cấp nhân vật đọc từ DB.

**D5 — Giao thức, dải MỚI 1800–1899, chỉ thêm**: 1800/1801 `ListRecipesRequest/Response`, 1802/1803
`CraftItemCommand/Response` (`CraftItemOutcome`). `protocolVersion` không tăng.

**D6 — Client**: cửa sổ "Lò đan" (`ReportPanel`): mỗi dòng "món ra ×n — nguyên liệu, linh thạch, cấp" + nút "Luyện".
Tên món tra catalog vật phẩm (ADR-035).

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Đặt trong feature-inventory | Gọn hơn nhưng lượt hỏi chọn module riêng; inventory đã to |
| Tỉ lệ thất bại khi luyện | Thêm RNG + bảo hiểm như khảm ngọc; bản đầu chắc chắn thành |
| Luyện nhiều mẻ một lệnh | Bấm nhiều lần đủ dùng; số lượng là chỗ dễ tràn số |

## Consequences

- Linh thạch luyện đan là gold sink mới; Linh Ngọc và Linh Thú Đan có nguồn thứ hai ngoài cửa hàng.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Module feature-crafting, công thức DB, một transaction, lò ở town_elder, dải 1800 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [REPOSITORY-STRUCTURE.md](../REPOSITORY-STRUCTURE.md) · `V76__alchemy_crafting.sql`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 19).
2. ✅ Kiểm sống 2026-10-24 (gameProbe `--craftRecipes`): đứng xa ⇒ `TOO_FAR`; đứng cạnh trưởng lão (x=−5): túi đủ 20 ô ⇒ Linh Thú Đan `BAG_FULL`, hoàn lại trọn (Yêu Đan 15, vàng không trừ); Tiểu Hồi Xuân Đan `OK` (3 → 6 viên, −2 Linh Thảo, −10 vàng, gộp chồng nên túi đầy vẫn được); bán bớt rồi luyện Linh Thú Đan `OK` (−10 Yêu Đan, −10 Linh Thảo, −3.000 vàng).
