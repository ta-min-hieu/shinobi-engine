# ADR-336: Bảng rao vặt

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Bảng rao vặt" trong lượt hỏi lô 51 (AskUserQuestion; mô tả kèm: đăng tin ngắn 'cần mua/bán X' lưu 24 giờ trên bảng chung; mọi người xem danh sách và nhắn riêng người đăng). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-222](ADR-222-Trade-Chat-Channel.md) · [ADR-063](ADR-063-Market.md) |

---

## Decision

**D1 — Một tin mỗi người:** bảng `bulletin_post(character_id PK, body VARCHAR(100), posted_at)` (V177); đăng tin mới thay tin cũ; rỗng = gỡ. Tin sống 24 giờ (lọc lúc đọc). Đăng lại phải cách 1 phút (chống spam). Kiểm nội dung như chat.

**D2 — Xem:** 20 tin còn hạn mới nhất, kèm tên, giờ đăng, đang online không. Không có giao dịch qua bảng — mua bán vẫn qua nhắn riêng / giao dịch / phường thị (khác kênh giao dịch ADR-222: kênh trôi theo chat, bảng giữ tin 24 giờ).

**D3 — Dây:** `PostBulletinCommand{body}` / `PostBulletinResponse{outcome}` **816/817**; `ListBulletinRequest{}` / `ListBulletinResponse{repeated BulletinPost}` **818/819**. Client `/raovat [chữ|xoa]` (`/bulletin`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `bulletin_post` (V177), một tin/người, 24 giờ, cách 1 phút; 816–819 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 51): `BulletinPort`/`Facade` (feature-chat), `PostgresBulletinAdapter`, `Lot51Commands.postBulletin`/`listBulletin`, `BulletinCoordinator`.
2. ✅ Test: `Lot51IntegrationTest.bulletinOnePostPerCharacterNewestFirst`.
3. LIVE336
