# ADR-222: Kênh chat giao dịch

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Kênh chat giao dịch" trong lượt hỏi lô 36 (AskUserQuestion; mô tả kèm: kênh riêng cho mua bán, tách khỏi kênh thế giới; ai cũng bật/tắt xem được). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-110](ADR-110-Sworn-Brotherhood.md) (kênh kết nghĩa — cách thêm kênh) · [ADR-152](ADR-152-Broadcast-Talisman.md) (kênh loa) |

---

## Decision

**D1 — Kênh mới `CHAT_CHANNEL_TRADE = 8`**: phát toàn server như Thế giới, cùng kiểm duyệt (cấm chat, từ cấm, độ dài)
và cùng khoe vật phẩm (ADR-188). Không lưu gì mới (nhật ký chat ghi tên kênh như mọi kênh).

**D2 — Bật / tắt xem là việc của client**: ẩn kênh thì tin tới không vào khung chat; mặc định hiện. (ADR-229 sẽ mở rộng
cho mọi kênh và nhớ theo máy.)

**D3 — Client**: gửi bằng `/gd <nội dung>` (hoặc `/trade`), hoặc xoay nút kênh: Thế giới → **Giao dịch** → Khu vực →
Nhóm. `/kenhgd tat|bat` (`/tradechat off|on`) ẩn / hiện.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Kênh TRADE toàn server, cùng kiểm duyệt; ẩn / hiện ở client; `/gd`, `/kenhgd` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 36): `ChatChannel.TRADE` (proto + domain), định tuyến ở `routeChatMessage`,
   `ChatController` (tiền tố `/gd`, ẩn kênh), `TradeChannelCoordinator`.
2. ✅ Test: `TradeChannelTest` (tiền tố, ẩn kênh).
3. ✅ Kiểm sống gameProbe 2026-11-08: ĐạoHữu01 gửi `CHAT_CHANNEL_TRADE` "Bán 10 Tiểu Hồi Xuân Đan" ⇒ chính mình và một người
   chơi khác đang online (MoiBangThu) cùng nhận `CHAT_MESSAGE_EVENT channel=CHAT_CHANNEL_TRADE`.
