# ADR-163: Túi phúc

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-03) — chủ dự án chọn "Túi phúc" trong lượt hỏi lô 29 (AskUserQuestion; mô tả kèm: vật phẩm mở ra quà ngẫu nhiên theo bảng tỉ lệ (server quay), rơi từ boss/hoạt động; có thể xem trước bảng quà). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-03 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-151](ADR-151-Lucky-Wheel.md) (cùng cách rút theo trọng số ở server) · [ADR-159](ADR-159-Item-Lock.md) D3 (túi đã khoá vẫn MỞ được — "dùng") |

---

## Decision

**D1 — Dữ liệu (V131)**: vật phẩm `lucky_bag` (Túi Phúc, chồng 99) + bảng `lucky_bag_reward(bag_item_template_id,
reward_gold | reward_item_template_id + reward_quantity, weight, sort_order)` — mỗi quà là vàng HOẶC một chồng đồ (CHECK).
Bảng mặc định 10 quà, tổng trọng số 100: 3 đan hồi máu 25%, 800 vàng 30%, phù bảo hộ 15%, 2 phù truyền tống 10%, Linh
Ngọc bậc 1 ×4 loại 10%, Mộc Linh Ngọc Nhị phẩm 5%, 5.000 vàng 5%. Nguồn: boss Bí Cảnh 25% một túi, boss thế giới chắc
chắn 1–2 túi. Mọi số là PLACEHOLDER (Điều 27). Thêm túi loại khác = thêm dòng, không cần code.

**D2 — Mở túi**: lệnh RIÊNG (không đi đường "Dùng" chung — đường đó chỉ trừ món rồi trả hiệu ứng, sẽ nuốt túi mà không
cấp quà). Một transaction: khoá túi đồ → đọc bảng quà theo thứ tự → trừ một túi (đường "dùng": túi khoá vẫn mở được) →
server rút theo trọng số (`SplittableRandom` ở `LuckyBagFacade`, như vòng quay) → cấp quà (vàng qua `GoldLedger`, đồ gộp
chồng/mở dòng) → audit `LUCKY_BAG_OPENED`. Túi đầy hoặc vàng chạm trần ⇒ rollback, túi còn nguyên.

**D3 — Giao thức, chỉ thêm**: `GetLuckyBagsRequest/Response` (536/537 — mỗi loại túi: số đang có + bảng quà kèm trọng
số), `OpenLuckyBagCommand/Response` (538/539; OK / NOT_A_BAG / NO_BAG / BAG_FULL / GOLD_FULL; kèm quà vừa rút và bảng
sau lần mở).

**D4 — Client**: cửa sổ `/tuiphuc` (hoặc `/luckybag`): dòng túi "Túi Phúc (đang có N)" + nút Mở, dưới là từng quà với %
(trọng số / tổng). Icon `lucky_bag` là nợ art (ghim vào `ITEMS_KNOWN_TO_LACK_AN_ICON`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Túi phúc = bảng quà theo trọng số trong DB, server rút, lệnh mở riêng một transaction, % công khai; 536–539 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-03 (lô 29): V131, `LuckyBagPort`/`LuckyBagFacade`, `PostgresLuckyBagAdapter`, `LuckyBagCommands`
   536–539, `LuckyBagCoordinator` `/tuiphuc`.
2. ✅ Test Postgres thật: `LuckyBagIntegrationTest` (2 ô: quà đồ + quà vàng đúng ô rút, trừ túi, hết túi ⇒ NO_BAG; bảng
   10 quà tổng 100 + đếm túi + món không phải túi ⇒ NOT_A_BAG).
3. ✅ Kiểm sống gameProbe 2026-11-03 (`--lot21=luckyBags,openBag:86×4`): 3 túi ⇒ phù bảo hộ, Hoả Linh Ngọc, Thổ Linh
   Ngọc; lần 4 ⇒ `NO_BAG`; ba dòng audit `LUCKY_BAG_OPENED`.
4. Chờ chủ dự án: bảng quà + tỉ lệ rơi túi (PLACEHOLDER); duyệt chữ `lucky_bag.*`; icon Túi Phúc.
