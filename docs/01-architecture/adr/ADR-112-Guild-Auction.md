# ADR-112: Đấu giá bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-25) — chủ dự án chọn "Đấu giá bang" trong lượt hỏi lô 22 (AskUserQuestion; mô tả kèm: đồ rơi từ boss thế giới/Bí Cảnh của bang đưa lên đấu giá nội bộ 30 phút, thành viên trả giá bằng linh thạch, tiền chia đều cho người tham gia; migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-25 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | ADR-081 (boss thế giới) · ADR-099 (Bí Cảnh) · [ADR-102](ADR-102-Guild-War.md) (luồng nền + chốt nguyên tử) |

---

## Decision

**D1 — Nguồn lô (V91)**: mỗi lần boss Bí Cảnh chết, nếu người ra đòn cuối có bang ⇒ một **lô đấu giá** cho bang đó: vật
phẩm gieo từ `guild_auction_drop` (tham số: mã vật phẩm, số lượng, trọng số). Người tham gia = các thành viên CÙNG BANG
có mặt trong Bí Cảnh lúc boss chết. Boss thế giới để bản sau (sổ sát thương ADR-081 đã chia thưởng riêng).

**D2 — Trả giá**: lô mở **30 phút**, giá khởi điểm 100, mỗi lần trả phải hơn giá hiện tại ít nhất **10%**. Vàng người
trả bị TRỪ NGAY (giữ chỗ); người bị trả vượt được hoàn qua thư. Chỉ thành viên bang đó trả giá được. Một giao dịch: khoá
dòng lô → kiểm còn mở, đủ mức → trừ vàng người mới → hàng đợi hoàn tiền người cũ.

**D3 — Chốt**: hết giờ ⇒ chốt nguyên tử một lần (khoá dòng lô): có người trả ⇒ thư vật phẩm cho người thắng, tiền chia
đều (làm tròn xuống) cho người tham gia qua thư; không ai trả ⇒ thư vật phẩm cho người ra đòn cuối. Hàng đợi thư bền
(cùng khuôn bang chiến), khoá chống trùng theo (lô, người, loại thư).

**D4 — Giao thức, chỉ thêm**: 737/738 `ListGuildAuctionsRequest`/`Response{lots[]}`, 739/740
`BidGuildAuctionCommand{lot_id, amount}`/`Response{outcome}`.

**D5 — Client**: nút "Đấu giá" trong cửa sổ Bang (G) mở bảng lô: vật phẩm, giá hiện tại, người dẫn, còn bao lâu, ô số tiền
+ nút Trả giá.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lô từ boss Bí Cảnh, 30 phút, +10%, giữ chỗ vàng, chia đều người tham gia, nút trong cửa sổ Bang | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ⏳ Thi hành (lô 22 phần 3).
2. ⏳ Kiểm sống bằng gameProbe.
