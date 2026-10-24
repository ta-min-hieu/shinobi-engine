# ADR-084: Tặng quà bạn bè và thân mật

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Tặng quà bạn bè" trong lượt hỏi lô 20 (AskUserQuestion; mô tả kèm: mỗi ngày tặng mỗi bạn 1 lần, tốn 100 vàng ⇒ điểm thân mật; mốc thân mật thưởng qua thư; migration + opcode). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | feature-friend · [ADR-074](ADR-074-Withdraw-Friend-Request.md) · GDD §10 (gold sink) |

---

## Decision

**D1 — Thân mật là MỘT con số của cặp bạn** (V80 `friendship.intimacy`; dòng `friendship` vốn một dòng mỗi cặp).

**D2 — Tặng quà**: chỉ bạn đã chấp nhận; mỗi người tặng mỗi người nhận **một lần mỗi ngày** (giờ Việt Nam,
`friend_gift_log` PK chặn cả hai lệnh song song); người tặng mất **100 linh thạch** (đốt, `gold.reason.friend_gift`);
thân mật cặp **+10**. Một transaction trong adapter: khoá dòng `friendship` → nhật ký quà → GoldLedger → cộng thân mật →
audit `FRIEND_GIFT`.

**D3 — Mốc 100 / 300 / 600**: chạm mốc ⇒ THƯ HỆ THỐNG cho CẢ HAI người (linh thạch = mốc × 5). Gateway gửi thư
(feature-friend không biết thư); người nhận quà được báo một dòng hệ thống nếu đang online.

**D4 — Giao thức, chỉ thêm**: 1116/1117 `SendFriendGiftCommand{target_name}` / `SendFriendGiftResponse{outcome,
intimacy, milestone}`; `FriendView.intimacy = 4`.

**D5 — Client**: cửa sổ bạn bè thêm nút "Tặng quà" (tên đang gõ, như "Rút mời"); thân mật hiện cạnh trạng thái.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Quà là vật phẩm chọn từ túi | Cần chọn món + định giá; vàng đủ cho bản đầu |
| Thân mật riêng mỗi chiều | Hai con số cho một quan hệ — khó đọc, mốc thưởng lệch nhau |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Thân mật theo cặp, 1 quà/ngày/người, 100 vàng, mốc thư cho cả hai, 1116/1117 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `V80__friend_gifts.sql` · `PostgresFriendGiftAdapter` · `FriendGiftCommands`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 20).
2. ✅ Kiểm sống 2026-10-24 (`--lot20=friendGift:Benkei` ×2): lần 1 `OK` thân mật 10, vàng người tặng −100; lần 2 cùng ngày `ALREADY_GIFTED_TODAY`. Đặt thân mật 95 + xoá nhật ký ⇒ tặng `OK` thân mật 105 mốc 100 ⇒ thư hệ thống 500 linh thạch cho CẢ HAI (Benkei lĩnh được).
