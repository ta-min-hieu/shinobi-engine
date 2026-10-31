# ADR-120: Luyện khí

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-30) — chủ dự án chọn "Luyện khí" trong lượt hỏi lô 23 (AskUserQuestion; mô tả kèm: lò ở NPC thợ rèn; quặng/cá (ADR-110 theo lượt hỏi, thực là ADR-107) + linh thạch ⇒ vũ khí/giáp theo công thức; server tung phẩm chất thường/tốt/hiếm). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-31 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-079](ADR-079-Alchemy-Crafting.md) (luyện đan — dùng lại module, bảng, lò) · [ADR-107](ADR-107-Fishing-And-Mining.md) (quặng/cá) · [ADR-111](ADR-111-Equipment-Refinement.md) (dòng tẩy luyện) |

---

## Decision

**D1 — Dùng lại luyện đan, không module mới**: `feature-crafting` thêm loại công thức (`RecipeKind` ALCHEMY/FORGE, cột
`crafting_recipe.kind`, V100). Luyện khí đi CÙNG đường một-transaction của ADR-079 D3 (khoá túi → tiêu nguyên liệu → trừ
linh thạch → cấp món → audit `FORGE_ITEM`), chỉ khác bước cấp. Lệnh luyện đan từ chối mã luyện khí và ngược lại.

**D2 — Lò ở trưởng lão Chu Hoành** (cùng lò đan, kiểm `NpcProximity` ở server). ⚠️ **Lệch mô tả lượt hỏi**: thế giới
CHƯA có NPC thợ rèn (thêm NPC cần model + điểm đặt + thoại — việc art/nội dung). Khi có thợ rèn chỉ cần đổi mã NPC kiểm
gần. Client mở cửa sổ bằng lệnh chat `/luyenkhi`.

**D3 — Phẩm chất do server tung** trước transaction: thường 70% / tốt 25% / hiếm 5% (`ForgeQuality`) ⇒ món ra lò có sẵn
0 / 1 / 2 **dòng tẩy luyện** (luật dòng của ADR-111, `RefinementPolicy.seedLines`, ngẫu nhiên ở server). Không có cột
"phẩm chất" riêng — phẩm chất CHÍNH LÀ số dòng gieo sẵn, người chơi có thể tẩy lại như mọi món. Lần luyện bị từ chối
không tốn gì và không lộ phẩm chất đã tung.

**D4 — Nội dung bản đầu** (đề xuất cân bằng, Lv20 — giữa bộ Ngoại Môn Lv15 và Trúc Cơ Lv30): **Linh Thiết Kiếm** (công
27) = 8 Linh Khoáng Thạch + 2 Yêu Đan + 600 linh thạch; **Linh Thiết Bào** (thủ 14, kháng 9, máu 30) = 6 Linh Khoáng Thạch + 4 Linh Ngư
+ 500 linh thạch. Mọi con số là dữ liệu SQL.

**D5 — Giao thức, chỉ thêm**: 1814/1815 `ListForgeRecipesRequest/Response` (dùng lại `RecipeView`), 1816/1817
`ForgeItemCommand/Response` (dùng lại `CraftItemOutcome`, thêm enum `ForgeQuality`). `protocolVersion` không tăng.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Luyện khí là loại công thức của feature-crafting, lò ở trưởng lão, phẩm chất 70/25/5 ⇒ 0/1/2 dòng tẩy luyện | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-31 (lô 23 phần 2b): `ForgeQuality` + `RecipeKind` (có test), `CraftItemUseCase.forge`,
   `PostgresCraftingWorkshopAdapter.grantForged`, V100, `CraftingCommands.listForge/forge`, client `ForgeCoordinator`,
   bước probe `forgeList`/`forge:<mã>`, test Postgres trong `Lot23IntegrationTest`.
2. ✅ **Kiểm sống bằng gameProbe 2026-10-31** (DB dev lên V100): `forgeList` trả 2 công thức; gửi mã luyện đan vào lệnh
   luyện khí ⇒ UNKNOWN_RECIPE; Kagemaru túi đầy ⇒ BAG_FULL ×3, linh thạch và Linh Khoáng Thạch không đổi (rollback);
   tanthua44 Lv15 ⇒ LEVEL_TOO_LOW; hạ tạm cấp công thức về 15 (khởi động lại vì công thức được đệm) ⇒ luyện 2 lần OK
   (−600 linh thạch mỗi lần, gói 209 `gold.reason.craft`), cả hai phẩm chất thường và đúng 0 dòng tẩy luyện trong DB, lần
   ba ⇒ MISSING_MATERIALS; đã trả cấp công thức về 20. Phẩm chất tốt/hiếm (1/2 dòng) chỉ kiểm qua test Postgres (10 lần
   luyện, số dòng khớp phẩm chất).
3. ⚠️ Chủ dự án xem: NPC thợ rèn (D2); hai món chưa có icon (nợ ở `ContentReferenceIntegrationTest`).
