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
   đều khoá dòng thật lúc thi hành. Đánh đổi: trải nghiệm kém hơn 1 chút (biết lời chào hỏng ở bước
   cuối thay vì ngay lúc chào), đổi lấy KHÔNG đụng 2 module đã xong (feature-inventory/feature-shop)
   — chọn vì rủi ro/chi phí thấp hơn nhiều, đúng thứ tự ưu tiên CLAUDE.md §8 (Correctness trước,
   không đổi Correctness lấy Elegance/UX polish).
2. **Cho phép tách stack** — 1 item chào có thể là 1 PHẦN của 1 dòng `inventory_item` (VD 3/10
   kunai). `TradeInventoryPort.transferItem` tự tách: nếu `quantity` bằng đúng số lượng dòng, đổi
   nguyên `character_id` của dòng; nếu nhỏ hơn, giảm `quantity` dòng gốc + tạo dòng MỚI cho người
   nhận (KHÔNG gộp vào stack có sẵn của người nhận — hệ thống hiện tại không tự gộp stack ở bất kỳ
   đường cấp item nào khác, giữ nhất quán).
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
- **"1 nhân vật tối đa 1 phiên trade active"** kiểm ở tầng application (`findActiveSessionOfCharacter`
  trước khi tạo mới), KHÔNG có UNIQUE constraint DB cứng (khác `guild_member.character_id`/
  `party_member.character_id`) — race hiếm tạo 2 phiên cùng lúc chỉ là bug UX (1 trong 2 phiên sẽ
  thi hành trước, phiên kia thất bại sạch lúc thi hành do item/gold đã dùng ở phiên trước), KHÔNG
  phải lỗ hổng duplicate — chấp nhận được, tránh thêm ràng buộc phức tạp cho 1 race vô hại.
- **`trade_offer_item.item_id` KHÔNG có FK** tới `inventory_item` — tham chiếu MỀM có chủ đích
  (xem comment migration V16): có FK sẽ CHẶN việc bán/gửi thư 1 item đang được chào trong 1 trade
  không liên quan (vi phạm FK), sai hoàn toàn với triết lý "không khoá trước".
- **Không có `DisbandTrade`/kick riêng** — trade luôn đúng 2 người, `CancelTradeUseCase` đã đủ.

## Cứng

✅ `ConfirmTradeUseCase` là NƠI DUY NHẤT chạm gold/item thật — mọi UseCase khác chỉ đổi
   `trade_session`/`trade_offer_item` (Ý ĐỊNH), không đổi `inventory_item`/`character_stat`
✅ Xác thực lại (khoá dòng + kiểm sở hữu/số lượng/gold) PHẢI chạy lại Ở ĐÚNG lúc thi hành, không
   được tin dữ liệu đã kiểm lúc đặt lời chào (đã có thể lỗi thời)
✅ Port là interface trong `domain/`; Adapter implement ở `server-database`
✅ Value Object = `record`, validate trong compact constructor
✅ Module khác gọi qua `TradeFacade`, không đọc bảng `trade_session`/`trade_offer_item` trực tiếp
✅ Ghi audit (`TRADE_EXECUTED`) cho CẢ 2 bên khi thi hành thành công

❌ SQL/JDBC/Redis client trong module này (Điều 23 — enforce bằng ArchUnit)
❌ `domain/` biết PostgreSQL
❌ Thêm cột `locked`/escrow vào `inventory_item`/`character_stat` — vi phạm quyết định 1 ở trên
❌ `feature-trade` phụ thuộc Gradle vào `feature-inventory`/`feature-shop`/`feature-character`

## Test

`domain/` test không mock (`TradeSession`/`TradeOfferItem`/`OfferedItemInput`/`OwnedItemSnapshot`
validate compact constructor). `application/` test với **Fake Port dùng chung** (cùng quyết định
feature-guild/feature-party — nhiều Port, tránh lặp nested Fake mỗi file). Postgres thật
(`server-database/.../trade/TradeIntegrationTest`) verify: xác thực lại phát hiện item đã bán mất
giữa chừng → `OFFER_INVALID`, tách stack đúng, rollback TOÀN BỘ (gold lẫn item) khi 1 chân thất
bại, `idempotencyKey` chặn thi hành 2 lần.
