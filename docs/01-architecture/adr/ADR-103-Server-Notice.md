# ADR-103: Thông báo server khi đăng nhập (MOTD)

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-25) — chủ dự án chọn "Thông báo server khi đăng nhập" trong lượt hỏi lô 22 (AskUserQuestion; mô tả kèm: ADMIN đặt thông báo (/gm motd), người chơi thấy khung thông báo lúc vào world + một dòng hệ thống; ít migration, 1–2 opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-25 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-052](ADR-052-In-Game-GM-Commands.md) (lệnh GM) |

---

## Decision

**D1 — Dữ liệu (V89)**: bảng `server_notice` đúng MỘT dòng (`id = 1`): `body` (≤ 300 ký tự), `updated_at`,
`updated_by` (nhân vật ADMIN). Rỗng/không có dòng = không có thông báo.

**D2 — Lệnh**: `/gm motd <chữ>` (ADMIN) đặt thông báo và phát ngay cho mọi người đang online; `/gm motd off` xoá. Đi qua
đường audit write-ahead của ADR-052 như mọi lệnh GM.

**D3 — Giao thức, chỉ thêm**: `OPCODE_SERVER_NOTICE_EVENT = 7` (dải System) `ServerNoticeEvent{body}` — gửi NGAY SAU
`SelectCharacterResponse` khi có thông báo, và cho mọi người online lúc ADMIN đặt mới.

**D4 — Client**: nhận gói ⇒ một dòng hệ thống `[Thông báo] …` trong chat + mở cửa sổ thông báo (đóng bằng ESC). Chữ do
ADMIN gõ là nội dung người dùng, không phải khoá i18n (cùng ngoại lệ với `/gm announce`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Một dòng `server_notice`, `/gm motd`, opcode 7 lúc vào world + khi đặt mới | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ⏳ Thi hành (lô 22 phần 1).
2. ⏳ Kiểm sống bằng gameProbe.
