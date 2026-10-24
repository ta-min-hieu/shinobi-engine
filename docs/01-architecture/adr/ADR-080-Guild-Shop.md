# ADR-080: Cửa hàng bang — đổi điểm cống hiến lấy vật phẩm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Cửa hàng bang" trong lượt hỏi lô 19 (AskUserQuestion; mô tả kèm: đổi điểm cống hiến ADR-068 lấy vật phẩm; hàng mở theo cấp bang; opcode mới, migration bảng hàng). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-068](ADR-068-Guild-Level-And-Contribution.md) · feature-guild |

---

## Context

Cống hiến (ADR-068) chỉ là con số trên danh sách thành viên — góp linh thạch không đem lại gì cho chính người góp.

## Decision

**D1 — Cống hiến TÍCH LUỸ giữ nguyên, thêm cột ĐÃ TIÊU** (V77 `guild_member.contribution_spent`): điểm tiêu được =
`contribution − contribution_spent`. Danh sách thành viên vẫn hiện tổng đã góp (thước đo công lao không tụt khi mua).
Rời bang mất cả hai (dòng `guild_member` bị xoá — như hiện nay).

**D2 — Hàng là DỮ LIỆU** (V77 `guild_shop_item`: món, giá cống hiến, cấp bang yêu cầu). Bản đầu: Tiểu Hồi Xuân Đan
(cấp 1), Bùa hộ cường hoá (cấp 2), Linh Ngọc các hệ (cấp 3), Linh Thú Đan (cấp 4), Tẩy Căn Đan (cấp 5).

**D3 — Một lần mua = một transaction**: khoá dòng thành viên → kiểm cấp bang → kiểm điểm tiêu được → cấp món vào túi
(túi đầy ⇒ `BAG_FULL`) → cộng `contribution_spent` → audit `GUILD_SHOP_BUY`. Port RIÊNG `GuildShopPort` (adapter `PostgresGuildShopAdapter`
cấp món bằng các mảnh SQL túi đồ dùng chung) — feature-guild không import feature-inventory.

**D4 — Giao thức, chỉ thêm**: 723/724 `ListGuildShopRequest/Response`, 725/726 `BuyGuildShopItemCommand/Response`
(`BuyGuildShopItemOutcome`). Điểm tiêu được + cấp bang đi trong `ListGuildShopResponse` (không thêm field vào
`GuildProgressView`).

**D5 — Client**: cửa sổ Bang (G) thêm nút "Cửa hàng bang" mở `ReportPanel` danh sách hàng + nút "Đổi".

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Trừ thẳng vào `contribution` | Danh sách thành viên/xếp hạng bang tụt khi người ta mua — sai nghĩa "công lao" |
| Tiền tệ bang riêng | Thêm một nguồn và một cột nữa cho cùng ý nghĩa |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | contribution_spent, hàng DB theo cấp bang, một transaction, dải 723–726 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `V77__guild_shop.sql` · `GuildShopUseCase`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 19).
2. ✅ Kiểm sống 2026-10-24 (gameProbe `--donateToGuild=5000 --guildShopBuy=20,23,46`): cống hiến 5.000; đổi Tiểu Hồi Xuân Đan (200) `OK` ⇒ còn 4.800, `contribution` vẫn 5.000, `contribution_spent` 200; món #23 không bán ⇒ `UNKNOWN_ITEM`; Linh Thú Đan (bang cấp 4) ⇒ `GUILD_LEVEL_TOO_LOW`.
