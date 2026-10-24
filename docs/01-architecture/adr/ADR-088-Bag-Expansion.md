# ADR-088: Mở rộng túi đồ — Túi Càn Khôn

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Mở rộng túi đồ" trong lượt hỏi lô 20 (AskUserQuestion; mô tả kèm: vật phẩm "Túi Càn Khôn" +5 ô mỗi lần, trần 40 ô; cột sức chứa theo nhân vật; đụng nhiều chỗ đang hardcode 20 ô). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-046](ADR-046-Health-Recovery.md) D5 (đường dùng vật phẩm) · [ADR-078](ADR-078-Pet-Evolution.md) (cùng khuôn hiệu ứng) |

---

## Context

Sức chứa túi là hằng số `20` chép rời ở BỐN chỗ (túi đồ, `InventoryStackMerge` — thư/cửa hàng/lò đan/thưởng,
quest, giao dịch), mỗi chỗ ghi "PLACEHOLDER trùng … — đổi thì phải đổi ở mọi chỗ cho tới khi có nguồn chung".

## Decision

**D1 — Nguồn chung** (V83 `character_stat.bag_capacity INT DEFAULT 20 CHECK 20..40`; helper `BagCapacity` ở
server-database). Bốn hằng số cũ bị xoá, mọi chỗ đọc sức chứa của ĐÚNG nhân vật. Đồ đang mặc vẫn không chiếm ô.

**D2 — Túi Càn Khôn** (`bag_expansion_pouch`, cửa hàng 25.000 — tham số): đi đường "dùng vật phẩm" có sẵn với hiệu ứng
mới `EXPAND_BAG` (`use_value` = 5 ô). Mở rộng là một UPDATE có điều kiện (không vượt 40) trong cùng giao dịch, TRƯỚC khi
trừ túi; chạm trần ⇒ `error.bag_at_max`, túi không bị trừ.

**D3 — Giao thức, chỉ thêm**: `ITEM_USE_EFFECT_EXPAND_BAG = 3`. Client đã đọc `ListInventoryResponse.capacity` từ server
nên tự hiện sức chứa mới.

## Consequences

- `InventoryPort#expandCapacity` (mặc định `false` cho fake). Mọi test hiện có chạy ở sức chứa 20 như cũ.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cột sức chứa theo nhân vật, helper chung, Túi Càn Khôn +5 tới 40 qua đường dùng vật phẩm | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `V83__bag_expansion.sql` · `BagCapacity` · `UseItemUseCase`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 20).
2. ⏳ Kiểm sống bằng gameProbe.
