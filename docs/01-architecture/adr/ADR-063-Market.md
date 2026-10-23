# ADR-063: Phường thị — treo bán vật phẩm lấy linh thạch, phí 5%, hết hạn trả qua thư

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-18) — chủ dự án chọn "Phường thị (đấu giá)" trong lượt hỏi lô 16 (AskUserQuestion; mô tả kèm: treo bán vật phẩm lấy linh thạch, thu 5% phí làm gold sink, hết hạn trả qua thư). Câu hỏi mở theo khuyến nghị. AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-18 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [GDD §10](../../13-game-design/GAME-DESIGN-DOCUMENT.md) (gold sink) · [ADR-025](ADR-025-Idempotency.md) · feature-trade (giao dịch trực tiếp) |

---

## Context

Giao dịch trực tiếp (feature-trade) đòi hai người cùng online, đứng nói chuyện với nhau. Người bán đồ
thừa cho người lạ không có chỗ nào; tiền chỉ chảy giữa người chơi và cửa hàng hệ thống. GDD §10 đòi một
gold sink ở "phí đấu giá".

## Decision

**D1 — Giá cố định, không trả giá** (Q1 theo khuyến nghị — "đấu giá" theo nghĩa phường thị, không phải
đặt giá cao hơn): người bán treo một chồng (hoặc một phần chồng) với giá tổng; ai bấm Mua trước thì được.
Đấu giá tăng dần cần giữ vàng của người trả giá, hoàn tiền khi bị trả cao hơn, chốt theo giờ — để sau nếu cần.

**D2 — Ký quỹ đồ khi treo.** Treo = chuyển món khỏi túi vào `market_listing` (V65) trong một transaction
dưới khoá túi đồ: không bán được món đang mặc / đang chào giao dịch; món treo không còn trong túi nên
không thể bán hai nơi. **Chỉ món CHƯA cường hoá** (`ENHANCED_NOT_ALLOWED`) — cùng luật thư đính kèm đang
có (`PostgresMailInventoryGatewayAdapter#consumeItem`: thư không chở được `enhance_level`). Đồ cường hoá vẫn
mua bán được qua giao dịch trực tiếp. Lý do: D4 trả món hết hạn bằng thư; cho treo đồ +7 rồi trả về +0 là
xoá công cường hoá. (Phát hiện khi thiết kế — mô tả lúc hỏi chủ dự án chưa nhắc chuyện này.)

**D3 — Mua là MỘT transaction:** khoá dòng treo (`FOR UPDATE`, còn `ACTIVE`) → khoá túi người mua (không
mua của chính mình) → trừ vàng người mua → cộng `giá − phí` cho người bán (offline vẫn nhận, qua
`GoldLedger` ⇒ `GoldChangedEvent`) → thêm món vào túi người mua (đầy ⇒ rollback, `BAG_FULL`) → `SOLD` →
audit `MARKET_BUY`/`MARKET_SALE`. Phí = `floor(giá × 5%)`, tối thiểu 1 — linh thạch đó BIẾN MẤT (sink).
Người bán online nhận thêm `ListingSoldEvent`.

**D4 — Huỷ / hết hạn.** Người bán huỷ ⇒ món về túi (đầy ⇒ từ chối). Treo tối đa **48 giờ**; một tác vụ nền
mỗi phút (1) chuyển dòng quá hạn `ACTIVE → EXPIRED` (compare-and-set — từ đó không ai mua được), rồi (2) gửi
**thư hệ thống** kèm món cho người bán với khoá chống trùng tất định theo id dòng, và đánh dấu `returned`.
Chết giữa (1) và (2) thì lượt sau gửi lại đúng khoá — không bao giờ hai thư, không bao giờ mất món.

**D5 — Giới hạn** (`MarketPolicy`, tham số mã — Q2 theo khuyến nghị): tối đa 10 dòng đang treo mỗi nhân
vật; giá 1..10.000.000; xem 50 dòng mới nhất (lọc theo mã vật phẩm tuỳ chọn). Mở ở MỌI nơi bằng phím **O**
(Q3 theo khuyến nghị — bảng tin chung, không cần đứng ở NPC).

**D6 — Idempotency** (ADR-025): `CreateListingCommand` và `BuyListingCommand` mang `idempotency_key` (dời đồ
+ vàng). Huỷ thì không (trạng thái tuyệt đối — huỷ lần hai trả `NOT_FOUND`).

**D7 — Giao thức: dải Shop/Auction 1006–1014** (đã dành cho "Auction" ở NETWORK.md §4), chỉ thêm,
`protocolVersion` không tăng: 1006/1007 `ListMarketRequest`/`Response`, 1008/1009 `CreateListingCommand`/
`Response`, 1010/1011 `BuyListingCommand`/`Response`, 1012/1013 `CancelListingCommand`/`Response`, 1014
`ListingSoldEvent`.

**D8 — Module mới `feature-market`**: domain + use case + facade; adapter ở server-database đụng
`inventory_item`/`character_stat`/`market_listing` trong cùng transaction (cùng cách feature-trade).

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Đấu giá tăng dần | Giữ vàng nhiều người, hoàn tiền, chốt theo giờ — gấp 3 khối lượng; giá cố định đủ cho Closed Beta |
| Không ký quỹ, kiểm lúc mua | Món có thể đã bán/giao dịch/cường hoá chỗ khác — người mua trả tiền cho thứ không còn |
| Trả món hết hạn thẳng vào túi | Túi đầy hoặc người bán offline thì mất / phải giữ hàng đợi; thư có sẵn mọi thứ |
| Phí treo trả trước | Phạt cả người không bán được; phí khi bán đủ làm sink |

## Consequences

- Một bảng mới. Đồ cường hoá chưa lên phường thị được (chờ thư chở được `enhance_level`).
- Vàng biến mất 5% mỗi giao dịch phường thị — theo dõi `kage.gold.total`.

## Revisit Trigger

- Cần đấu giá tăng dần / mua theo đơn giá một phần chồng.
- Thư chở được `enhance_level` (đổi `mail_item` + `MailAttachmentView`) ⇒ mở phường thị cho đồ cường hoá.
- Thao túng giá giữa hai tài khoản (chuyển vàng trá hình) ⇒ trần giá theo cấp hoặc báo cáo bất thường.

## Technical Decisions

- Khoá: dòng treo → túi người mua → (vàng qua `GoldLedger`). Hết hạn: `UPDATE ... SET status='EXPIRED' WHERE status='ACTIVE' AND expires_at <= now() RETURNING` — chỉ một tác vụ thắng mỗi dòng.

## ADR cần tạo

Không.

## Tài liệu liên quan

- `market.proto` · `V65__market.sql` · `feature-market` · `MarketCoordinator`
- Test: `MarketPolicyTest`, `MarketDatabaseTest`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-18 (lô 16).
2. ✅ Kiểm sống 2026-10-21 bằng gameProbe (`--listOnMarket/--buyFromMarket`): Kagemaru treo Hộ Thủ Tạp Dịch giá 500; tanthua44 mua ⇒ người mua −500, người bán +475 (500 − 5%), dòng `SOLD`. Lượt hai tài khoản (2026-10-23): Benkei treo Mũ Tạp Dịch 300 rồi đứng online, Kagemaru mua ⇒ Benkei +285 kèm 1014 `LISTING_SOLD_EVENT`.
