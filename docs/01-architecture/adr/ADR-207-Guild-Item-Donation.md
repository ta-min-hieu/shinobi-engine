# ADR-207: Góp vật phẩm cho bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Góp vật phẩm cho bang" trong lượt hỏi lô 35 (AskUserQuestion; mô tả kèm: góp đồ thay vì chỉ góp linh thạch, quy ra cống hiến + kinh nghiệm bang). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-068](ADR-068-Guild-Level-And-Contribution.md) (cấp bang, cống hiến, góp linh thạch) · [ADR-170](ADR-170-Sell-Junk.md) (điều kiện "rời túi được") · [ADR-180](ADR-180-Guild-Log.md) (nhật ký bang) · [ADR-198](ADR-198-Guild-Bank.md) (kho bang — CẤT đồ, khác góp) |

---

## Decision

**D1 — Góp là tiêu huỷ, không phải cất.** Món góp biến mất; muốn để đồ cho cả bang dùng thì là kho bang (ADR-198). Giá trị
quy đổi = **giá thu của Tàng Bảo Các × số lượng** — cùng tỷ lệ 1 linh thạch = 1 kinh nghiệm bang = 1 cống hiến như góp
linh thạch (ADR-068), nên góp đồ không bao giờ lời hơn bán đồ rồi góp tiền, chỉ đỡ một bước (và không bị trần vàng chặn).

**D2 — Điều kiện**: giống bán ở Tàng Bảo Các (ADR-170): chưa cường hoá, không khoá, không khảm / tẩy luyện / linh thú,
không đang mặc, không đang chào giao dịch; và phải có giá thu (`shop_item.sell_price > 0`). Số lượng 1…9.999, không quá
chồng đang có.

**D3 — Một transaction**: khoá túi → khoá chồng → trừ món → cộng kinh nghiệm bang (khoá dòng bang) + cống hiến → audit
`GUILD_ITEM_DONATION` → nhật ký bang `ITEM_DONATED` (amount = số lượng, detail = mã vật phẩm; V161 nới CHECK) và
`LEVEL_UP` nếu lên cấp. Lên cấp thì báo cả bang như góp linh thạch.

**D4 — Protocol**: `DonateItemToGuildCommand{inventory_item_id, quantity}` / `DonateItemToGuildResponse{outcome,
contribution, guild_leveled_up}` 757/758; `GuildLogKind.ITEM_DONATED = 14`.

**D5 — Client**: chọn món trong túi rồi `/gopdo [số]` (hoặc `/donateitem`; không số = 1). Nhật ký bang hiện "X góp
<tên món> xN cho bang".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Góp = tiêu huỷ món, giá trị = giá thu × số lượng → kinh nghiệm bang + cống hiến; V161; 757/758; `/gopdo` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 35): `GuildItemDonationPort`/`Facade`, `PostgresGuildItemDonationAdapter`, V161,
   `Lot35Facades`/`Lot35Commands`, `GuildItemDonationCoordinator`.
2. ✅ Test: `GuildItemDonationIntegrationTest` (Postgres thật: góp một phần rồi phần còn lại, món khoá / ngoài bang /
   quá số lượng không ghi gì, cống hiến + kinh nghiệm bang + nhật ký đúng).
3. ✅ Kiểm sống gameProbe 2026-11-07 (V161): ĐạoHữu01 `donateItem:13/3` (3 Tiểu Hồi Xuân Đan, giá thu 20) ⇒ `OK`,
   cống hiến +60, kinh nghiệm bang 2200→2260, nhật ký bang `ITEM_DONATED 3 minor_healing_pill`; kiếm gỗ đang mặc ⇒
   `NOT_DONATABLE`; 99 phi đao (có 20) ⇒ `INSUFFICIENT_QUANTITY`; số lượng 0 ⇒ `INVALID_QUANTITY`. Túi, cống hiến,
   kinh nghiệm bang, nhật ký và audit đã trả về như cũ.
