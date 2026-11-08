# ADR-229: Lọc kênh chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Lọc kênh chat" trong lượt hỏi lô 37 (AskUserQuestion; mô tả kèm: bật/tắt hiển thị từng kênh chat ở client, lưu theo máy). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-222](ADR-222-Trade-Chat-Channel.md) (kênh giao dịch — ẩn / hiện trong phiên) |

---

## Decision

**D1 — Client thuần, không đổi protocol**: tin của kênh đã ẩn vẫn tới (server không biết), chỉ không thêm vào khung
chat. Dòng hệ thống không bao giờ bị ẩn. Áp cho Thế giới, Giao dịch, Khu vực, Bang, Nhóm, Kết nghĩa, Loa.

**D2 — Lưu theo máy**: danh sách kênh ẩn ghi vào Preferences libGDX (cùng tệp ngôn ngữ / phím tắt), nạp lại khi vào
world. `/kenhgd` của ADR-222 vẫn là ẩn / hiện trong phiên; `/an gd` là lưu lâu.

**D3 — Client**: `/an <kênh>`, `/hien <kênh>` (`/hide`, `/show`), kênh gõ không dấu (thegioi, gd, khuvuc, bang, nhom,
ketnghia, loa — hoặc tên tiếng Anh); `/kenh` (`/channels`) liệt kê trạng thái.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Ẩn / hiện theo kênh ở client, lưu Preferences; `/an`, `/hien`, `/kenh` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 37): `ChatFilterCoordinator`, `ChatController.setChannelVisible` (dùng chung với ADR-222).
2. ✅ Test: `ChatFilterTest` (đọc chuỗi lưu), `TradeChannelTest` (tin kênh ẩn không vào khung chat).
3. Kiểm sống: client thuần — môi trường kiểm sống không chạy client đồ hoạ; kiểm bằng biên dịch + bộ test client.
