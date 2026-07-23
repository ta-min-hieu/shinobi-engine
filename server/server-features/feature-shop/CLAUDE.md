# CLAUDE.md — feature-shop

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-shop/**`.

## Cấu trúc (Clean Arch)

```
api/            ShopFacade
application/    ListShopItemsUseCase · BuyItemUseCase · SellItemUseCase
                ShopPorts (gộp 4 Port, tránh vượt 4 tham số — Checkstyle)
                ShopTransactionAbortedException (package-private, rollback nội bộ)
domain/         ShopListing (VO) · OwnedItem (VO)
                ShopCatalogPort · GoldPort · InventoryGatewayPort · AuditPort
                Transaction · TransactionRunner
```

Chiều: `api → application → domain`. **`domain/` không import gì bên ngoài** — adapter thật
(`PostgresShop*Adapter`) nằm ở **`server-database`**, cùng lý do đã ghi ở feature-account/CLAUDE.md.

## Quyết định MVP chốt cùng chủ dự án (2026-07-23)

Trước khi code, 3 câu hỏi "chưa chốt" đã hỏi qua `AskUserQuestion` (CLAUDE.md §4 — gold/schema/
protocol chưa có thiết kế nào trong docs gốc):

1. **Danh mục lưu ở bảng `shop_item`** (migration V8), KHÔNG phải bảng tra cứu tĩnh trong code
   (khác `EnhancementTable`) — catalog có thể vài chục/trăm item, sửa giá không nên cần build lại.
2. **Có bán lại (sell) ở MVP này** — `sell_price` là cột RIÊNG mỗi dòng `shop_item` (không phải %
   cố định của `buy_price`) — tự cân bằng từng item, không áp 1 công thức chung.
3. **Shop TOÀN CỤC, không gắn NPC/vị trí** — Phase 6 mới có NPC/Dialogue; lúc đó mới thêm ràng
   buộc "đứng gần NPC mới mua được" (không phải thiếu sót, chờ đúng lúc có khái niệm NPC).

## Vì sao là module RIÊNG (không gộp vào feature-inventory như cường hoá)

Cường hoá (Phase 5 lát cắt 4-5) nằm TRONG `feature-inventory` vì nó thao tác trực tiếp trên 1 item
đã có trong túi (`enhance_level`), không cần khái niệm catalog/giá. Mua/bán khác: cần 1 bounded
context riêng (`shop_item`, giá, danh mục) — và NETWORK.md §4 đã chừa dải opcode riêng (1000-1099
"Shop / Auction") từ Phase 3, xác nhận ý định ban đầu là module tách biệt.

## Kiến trúc: Y HỆT lý do `GoldPort` của feature-inventory (cường hoá)

Mua/bán cần đổi gold + đổi item NGUYÊN TỬ trong CÙNG 1 transaction (DATABASE.md §5 — "trừ tiền
nhưng không nhận item = mất tiền"). `InventoryFacade`/`CharacterFacade` không nhận `Transaction`
chia sẻ giữa 2 feature (mỗi feature tự định nghĩa `Transaction`/`TransactionRunner` riêng), nên
`feature-shop` tự khai báo **bản sao** `GoldPort` (thêm `creditGold` cho bán, khác `GoldPort` của
feature-inventory chỉ có `deductGold`) và **`InventoryGatewayPort` mới** (không có tương đương ở
feature-inventory — cường hoá không cần thêm/bớt cả 1 item, chỉ đổi field trên item đã có).

**`InventoryGatewayPort`'s adapter KHÔNG tái dùng `Inventory` aggregate của feature-inventory** —
viết SQL riêng cho `inventory_item` (đếm số dòng + INSERT cho mua; `SELECT ... FOR UPDATE` +
UPDATE/DELETE cho bán). Lý do: bắc cầu 2 loại `Transaction` khác nhau (feature-shop's vs
feature-inventory's, dù cả 2 cùng bọc 1 `java.sql.Connection` ở tầng JDBC) phức tạp hơn nhiều so
với trùng lặp 2-3 câu SQL nhỏ. `DEFAULT_CAPACITY = 20` bị TRÙNG LẶP với hằng số cùng tên ở
`PostgresInventoryAdapter` (feature-inventory) — cả 2 đều là PLACEHOLDER (GDD chưa chốt sức chứa
túi đồ thật), đổi thì phải đổi cả 2 chỗ cho tới khi có nguồn chung.

## Thứ tự thao tác trong transaction — vì sao Buy và Sell khác nhau

- **`BuyItemUseCase`**: trừ gold TRƯỚC (rẻ — 1 UPDATE có điều kiện, no-op nếu không đủ) → cộng
  item. Nếu túi đầy, `grantItem` trả `false` → ném `ShopTransactionAbortedException` để buộc
  `JdbcTransactionRunner` rollback CẢ phần gold đã trừ (`JdbcTransactionRunner` chỉ rollback khi
  action ném exception, return thường sẽ COMMIT — không thể chỉ "return sớm" ở nhánh này).
- **`SellItemUseCase`**: bớt item TRƯỚC (điều kiện, khoá dòng bằng `SELECT ... FOR UPDATE` chống
  race 2 request bán cùng lúc) → cộng gold. `creditGold` không có nhánh thất bại (luôn là 1 UPDATE
  vô điều kiện) nên KHÔNG cần rollback thủ công — thứ tự này tránh hẳn nhu cầu dùng exception-để-
  rollback ở đường bán, đơn giản hơn đường mua.

## Cứng

✅ Quy tắc validate (giá âm, quantity ≤ 0) ở `domain/`/đầu `UseCase`, không ở Facade
✅ Port là interface trong `domain/`; Adapter implement ở `server-database`
✅ Value Object = `record`, validate trong compact constructor (`ShopListing`, `OwnedItem`)
✅ Module khác gọi qua `ShopFacade`, không đọc bảng `shop_item`/`inventory_item` trực tiếp

❌ SQL/JDBC/Redis client trong module này (Điều 23 — enforce bằng ArchUnit)
❌ `domain/` biết PostgreSQL

## Test

`domain/` test **không mock**. `application/` test với **Fake Port** (không Mockito) — verify cả
nhánh rollback (`shouldRollbackAndReturnInventoryFull_whenGrantFails`) bằng cách kiểm tra
`goldPort.deductCalled == true` nhưng `auditPort.loggedAction == null` (audit không ghi cho giao
dịch bị rollback). Postgres thật (`server-database/.../shop/ShopIntegrationTest`) verify atomicity
end-to-end: rollback gold khi túi đầy lúc mua, khoá dòng khi bán. Socket thật
(`GatewayServerIntegrationTest`) verify dây opcode dispatch, không lặp lại nghiệp vụ đã phủ ở tầng
dưới.
