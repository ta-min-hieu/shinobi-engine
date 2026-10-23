# CLAUDE.md — feature-trade

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-trade/**`.
> ⚠️ **Hệ thống nguy hiểm nhất dự án** (GAMEPLAY.md §8) — mọi thay đổi ở đây PHẢI giữ nguyên tính
> nguyên tử của `ConfirmTradeUseCase`, không "tối ưu" bớt bước xác thực lại.

## Cấu trúc (Clean Arch)

```
api/            TradeFacade (+ RequestResult/AcceptResult/CancelResult/SetOfferResult/
                 ConfirmResult/TradePhase/OfferItemInput/OfferItemView/TradeStatusView — type
                 RIÊNG, không lộ TradeStatus/UseCase.Result domain ra ngoài)
application/    RequestTradeUseCase · AcceptTradeRequestUseCase · CancelTradeUseCase
                SetTradeOfferUseCase · ConfirmTradeUseCase · GetTradeStatusUseCase
                TradePorts (gộp 5 Port cho ConfirmTradeUseCase) · TradeUseCases (gộp 6 UseCase)
                TradeExecutionAbortedException (package-private, rollback nội bộ)
domain/         TradeSession (danh tính) · TradeSessionSnapshot (toàn bộ trạng thái, đọc thuần
                LẪN FOR UPDATE) · TradeOfferItem · OfferedItemInput · OwnedItemSnapshot
                TradeStatus (enum) · TradeSessionPort · TradeInventoryPort · TradeGoldPort
                TradeAuditPort · IdempotencyPort · IdempotencyKeyReusedException
                TradeParticipantBusyException
                Transaction · TransactionRunner (bản khai riêng, không dùng chung feature khác)
```

Chiều: `api → application → domain`. **`domain/` không import gì bên ngoài** — adapter thật
(`PostgresTrade*Adapter`) nằm ở `server-database`.

## Quyết định MVP (2026-07-24, Phase 7 lát cắt 3)

3 quyết định chốt cùng chủ dự án qua `AskUserQuestion` trước khi code (GAMEPLAY.md §8 chỉ liệt 5
yêu cầu, KHÔNG có thiết kế nào — mọi cơ chế bên dưới là quyết định mới):

1. **KHÔNG khoá item lúc đặt lời chào** — không có cột `locked` trên `inventory_item`, không sửa
   `EquipItemUseCase`/`EnhanceItemUseCase` (feature-inventory)/`SellItemUseCase` (feature-shop) để
   từ chối item đang chào. Thay vào đó, `ConfirmTradeUseCase` **xác thực lại TOÀN BỘ lời chào NGAY
   LÚC THI HÀNH** (khi cả 2 bên đã confirm) — khoá dòng `FOR UPDATE`, kiểm còn đúng chủ + đủ số
   lượng/gold, trong CÙNG 1 transaction với chuyển gold/item. Nếu ai đã bán/gửi/trang bị mất item
   đã chào giữa chừng, giao dịch thất bại SẠCH (`OFFER_INVALID`, không mất gì, không có bên nào
   nhận được gì) — KHÔNG có đường nào tạo ra duplicate item dù không khoá trước, vì mọi transfer
   đều khoá dòng thật lúc thi hành. ⚠️ Nhánh "trang bị mất" chỉ được THI HÀNH từ 2026-09-24
   (`TransferOutcome.ITEM_EQUIPPED`): trước đó `transferItem` chỉ kiểm chủ + số lượng, nên món đang
   mặc bị đổi chủ trong khi ô trang bị người đưa vẫn trỏ vào nó — người đưa giữ chỉ số trong World,
   người nhận mặc được ngay. Đánh đổi: trải nghiệm kém hơn 1 chút (biết lời chào hỏng ở bước
   cuối thay vì ngay lúc chào), đổi lấy KHÔNG đụng 2 module đã xong (feature-inventory/feature-shop)
   — chọn vì rủi ro/chi phí thấp hơn nhiều, đúng thứ tự ưu tiên CLAUDE.md §8 (Correctness trước,
   không đổi Correctness lấy Elegance/UX polish).
2. **Cho phép tách stack** — 1 item chào có thể là 1 PHẦN của 1 dòng `inventory_item` (VD 3/10
   kunai). `TradeInventoryPort.transferItem` tự tách: nếu `quantity` bằng đúng số lượng dòng, đổi
   nguyên `character_id` của dòng; nếu nhỏ hơn, giảm `quantity` dòng gốc + tạo dòng MỚI cho người
   nhận. **Gộp chồng** (chủ dự án ĐẢO quyết định ban đầu ngày 2026-09-24): bản 2026-07-24 ghi
   "KHÔNG gộp vào stack có sẵn của người nhận" vì lúc đó không đường cấp đồ nào khác tự gộp; nay
   rơi đồ, shop, quest, thư đều gộp (`InventoryStackMerge`) nên giao dịch gộp theo. Đồ CHƯA cường
   hoá gộp vào chồng sẵn có còn chỗ (`max_stack`) của người nhận TRƯỚC — nguyên dòng thì xoá dòng
   nguồn, tách thì giảm dòng nguồn; không chồng nào nhận được mới đổi chủ/tạo dòng như cũ. Đồ đã
   cường hoá không bao giờ gộp.
   **Sức chứa túi** kiểm SAU mọi lượt chuyển (`TradeInventoryPort#exceedsCapacity`, sửa
   2026-09-24): trước đó chuyển NGUYÊN DÒNG bỏ qua hẳn kiểm, túi 20 dòng nhận thành 21. Kiểm ở
   cuối chứ không từng lượt để đổi 1-lấy-1 giữa hai túi đều đầy vẫn hợp lệ. ⚠️ Bản 2026-09-24 mới
   bỏ kiểm giữa chừng cho chuyển NGUYÊN DÒNG; chuyển TÁCH CHỒNG vẫn đếm dòng người nhận ngay giữa
   giao dịch, và vì lời chào của requester chạy trước nên một vụ đổi hợp lệ (requester tách chồng vào
   túi đầy, target trả lại nguyên dòng) bị `OFFER_INVALID` trong khi đảo vai thì thành công. **Sửa
   2026-10-03** (`7ca8e2f`): kiểm giữa chừng bị gỡ hẳn, outcome `RECIPIENT_INVENTORY_FULL` của
   `TradeInventoryPort` cũng gỡ; chỉ còn `exceedsCapacity` ở cuối. Test: `TradeIntegrationTest`.
   **Ngoại lệ HẸP (chủ dự án quyết 2026-09-29): món đang chào KHÔNG được cường hoá.** Lỗi thật:
   `trade_offer_item` không lưu `enhance_level`, xác thực lúc thi hành chỉ kiểm chủ/số lượng/đang
   mặc ⇒ người đưa cường hoá SAU KHI người kia đã xác nhận, giao dịch thi hành với món khác giá trị.
   `EnhanceItemUseCase` (feature-inventory) nay hỏi `TradeOfferPort` sau khoá túi đồ và trả
   `ITEM_IN_TRADE` (gateway → `ErrorResponse` `error.item_in_trade`, không đổi protocol/schema).
   Không cần phụ thuộc Gradle giữa hai module: port khai ở feature-inventory, adapter
   (`PostgresInventoryTradeOfferAdapter`) đọc `trade_offer_item` ở server-database. Để hai bên xếp
   hàng, `PostgresTradeSessionAdapter.replaceOffer` lấy `CharacterInventoryLock` của người chào sau
   khi khoá dòng phiên (DATABASE.md §5.1). Bán/gửi thư/mặc KHÔNG bị chặn — chúng không đổi giá trị
   món, chỉ làm giao dịch hỏng sạch lúc thi hành như cũ. Test: `EnhanceWhileInTradeIntegrationTest`.
3. **`idempotencyKey` (ADR-025) chỉ ở `ConfirmTradeCommand`** — Command duy nhất có thể kích hoạt
   hiệu ứng thật (chuyển gold/item). `RequestTrade`/`AcceptTradeRequest`/`Cancel`/`SetTradeOffer`
   không cần — tự nhiên idempotent (gọi lại vô hại) hoặc không có hậu quả kinh tế.

## Quyết định thiết kế khác (suy ra từ 3 quyết định trên, không cần hỏi thêm)

- **KHÔNG escrow gold/item lúc đặt lời chào** — hệ quả trực tiếp của quyết định 1: nếu không khoá
  item thì cũng không escrow gold (nhất quán, tránh 1 nửa cơ chế "khoá phần này nhưng không khoá
  phần kia"). Gold/item CHỈ thật sự bị đụng tới ở đúng 1 chỗ: `ConfirmTradeUseCase` lúc cả 2 confirm.
- **2-phase confirm = sửa lời chào RESET CẢ 2 cờ confirmed** (`replaceOffer` luôn set lại
  `requester_confirmed=false, target_confirmed=false`) — chống lừa đảo đổi lời chào SAU KHI đối
  phương đã confirm (đúng nghĩa GAMEPLAY.md §8 "2-phase confirm... chống lừa đảo"). Không có khái
  niệm "sửa xong tự giữ nguyên confirm của người kia".
- **`CancelTradeUseCase` dùng chung cho từ chối (PENDING_ACCEPT) lẫn huỷ giữa chừng (OPEN)** —
  không tách 2 Command riêng (khác feature-guild tách Leave/Disband) vì cả 2 trường hợp đều là
  "1 trong 2 bên rút lui, không escrow gì để hoàn trả" — cùng 1 hành động, không cần phân biệt.
- **"1 nhân vật tối đa 1 phiên trade active"** — KHÔNG có UNIQUE constraint DB cứng (khác
  `guild_member.character_id`/`party_member.character_id`; một người có thể ở cột requester HOẶC
  target nên UNIQUE không diễn đạt được). Bản 2026-07-24 chỉ kiểm ở tầng application
  (`findActiveSessionOfCharacter` trước khi tạo mới) và coi race 2 phiên là "hiếm, vô hại". ⚠️
  **Đo lại 2026-10-02 (`EconomyConcurrencyIntegrationTest`): A mời T đúng lúc B mời T thì T kẹt
  trong HAI phiên 10/10 lần**, và `GetTradeStatusUseCase` chỉ thấy một — vẫn không mất tài sản
  (thi hành xác thực lại), nhưng không hiếm. Nay `PostgresTradeSessionAdapter#createRequest` kiểm lại
  trong CÙNG giao dịch với INSERT, sau khoá tư vấn theo TỪNG nhân vật (không gian tên riêng,
  không chạm khoá túi đồ), ném `TradeParticipantBusyException` nếu một bên đã bận. Không đổi
  schema/protocol. ⏳ **Chờ chủ dự án duyệt** việc đảo quyết định cũ này.
- **`trade_offer_item.item_id` KHÔNG có FK** tới `inventory_item` — tham chiếu MỀM có chủ đích
  (xem comment migration V16): có FK sẽ CHẶN việc bán/gửi thư 1 item đang được chào trong 1 trade
  không liên quan (vi phạm FK), sai hoàn toàn với triết lý "không khoá trước".
  ⚠️ Hệ quả của tham chiếu mềm: lời chào **không xác thực sở hữu** lúc đặt, nên trước 2026-10-03
  `GetTradeStatusUseCase` tra loại + cấp cường hoá của BẤT KỲ `inventory_item` id nào được chào —
  lộ đồ người lạ theo id, và cho đối phương thấy một món +15 không bao giờ chuyển được. **Sửa
  2026-10-03** (`11ed42a`): món không còn thuộc người chào hiển thị như món đã biến mất
  (`itemTemplateId=0`). Xác thực thật vẫn chỉ ở lúc thi hành.
- **Hình dạng lời chào kiểm ở biên dây** (2026-10-03, `EconomyCommandShape` ở `server-gateway`):
  vàng âm hoặc dòng có `itemId`/`quantity` ≤ 0 bị từ chối bằng **400 `error.invalid_payload`** trên
  event loop. Trước đó chúng tới `OfferedItemInput`, ném `IllegalArgumentException` và rơi vào lưới
  cuối của gateway thành 500 kèm stack trace mức ERROR mỗi gói. Nghiệp vụ (đủ vàng, còn sở hữu) vẫn
  là việc của use case.
- **Không có `DisbandTrade`/kick riêng** — trade luôn đúng 2 người, `CancelTradeUseCase` đã đủ.
- **`offer_version` (ADR-075, 2026-10-23)**: mỗi `replaceOffer` tăng `trade_session.offer_version`; xác nhận mang
  phiên bản client đang hiển thị, lệch ⇒ `OFFER_JUST_CHANGED`, ném trong transaction để khoá chống trùng rollback
  theo. `0` = client cũ. Khoá 3 giây (`TradeOfferChanges`) vẫn giữ.
- **Phiên sót lúc khởi động (README dòng 40(iv), 2026-10-23)**: `TradeFacade#cancelLeftoverSessions` xoá mọi phiên
  trước khi mở cổng game — chỉ gọi lúc chưa ai online.

## Cứng

✅ `ConfirmTradeUseCase` là NƠI DUY NHẤT chạm gold/item thật — mọi UseCase khác chỉ đổi
   `trade_session`/`trade_offer_item` (Ý ĐỊNH), không đổi `inventory_item`/`character_stat`
✅ Xác thực lại (khoá dòng + kiểm sở hữu/số lượng/gold) PHẢI chạy lại Ở ĐÚNG lúc thi hành, không
   được tin dữ liệu đã kiểm lúc đặt lời chào (đã có thể lỗi thời)
✅ Port là interface trong `domain/`; Adapter implement ở `server-database`
✅ Value Object = `record`, validate trong compact constructor
✅ Module khác gọi qua `TradeFacade`, không đọc bảng `trade_session`/`trade_offer_item` trực tiếp
✅ Ghi audit (`TRADE_EXECUTED`) cho CẢ 2 bên khi thi hành thành công — từ 2026-10-03 (`85ac060`)
   mỗi dòng ghi `goldGiven`/`goldReceived` + `itemsGiven`/`itemsReceived` (`itemId` dòng NGUỒN +
   `quantity`). Phiên và lời chào bị xoá ngay sau thi hành nên dòng audit là dấu vết DUY NHẤT; bản cũ
   chỉ có `sessionId`/`counterpartId`. Từ 2026-10-14 (chủ dự án chọn, README dòng 25(iii)) mỗi món
   còn ghi `itemTemplateId` + `enhanceLevel`, đọc từ dòng nguồn TRONG transaction TRƯỚC khi chuyển
   (`TradeInventoryPort#findItemById(Transaction, long)`) — sau chuyển dòng đó có thể đã bị xoá/gộp

❌ SQL/JDBC/Redis client trong module này (Điều 23 — enforce bằng ArchUnit)
❌ `domain/` biết PostgreSQL
❌ Thêm cột `locked`/escrow vào `inventory_item`/`character_stat` — vi phạm quyết định 1 ở trên
❌ Bỏ `CharacterInventoryLock` trong `replaceOffer` — mở lại lỗi "cường hoá sau khi đã chào" (2026-09-29)
❌ `feature-trade` phụ thuộc Gradle vào `feature-inventory`/`feature-shop`/`feature-character`

## Test

`domain/` test không mock (`TradeSession`/`TradeOfferItem`/`OfferedItemInput`/`OwnedItemSnapshot`
validate compact constructor). `application/` test với **Fake Port dùng chung** (cùng quyết định
feature-guild/feature-party — nhiều Port, tránh lặp nested Fake mỗi file). Postgres thật
(`server-database/.../trade/TradeIntegrationTest`) verify: xác thực lại phát hiện item đã bán mất
giữa chừng → `OFFER_INVALID`, tách stack đúng, rollback TOÀN BỘ (gold lẫn item) khi 1 chân thất
bại, `idempotencyKey` chặn thi hành 2 lần. `EconomyConcurrencyIntegrationTest` (2026-10-03) đua
thi hành với bán món đang chào và với bên kia huỷ, mỗi kiểu 10 vòng, đo GIÁ TRỊ CUỐI: đúng một bên
thắng mỗi vòng, vàng/vật phẩm bảo toàn — không có bug, test giữ lại làm hàng rào.
