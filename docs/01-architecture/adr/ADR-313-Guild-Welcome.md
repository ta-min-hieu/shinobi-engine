# ADR-313: Lời chào thành viên mới

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Lời chào thành viên mới" trong lượt hỏi lô 48 (AskUserQuestion; mô tả kèm: bang chủ đặt lời chào; ai vừa vào bang tự nhận lời chào đó qua chat và thư). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-179](ADR-179-Guild-Notice.md) · [ADR-059](ADR-059-Party-And-Guild-Changed-Events.md) |

---

## Decision

**D1 — Cột `guild.welcome_message`** (V174, `VARCHAR(200)`, rỗng = không chào). Bang chủ / phó đặt; ≤ 200 ký tự,
không ký tự điều khiển, không từ cấm (cùng bộ lọc chat).

**D2 — Gửi:** mọi đường vào bang đều đi qua `MembershipNotices.guildMembershipChanged` ⇒ người vừa vào nhận một dòng
chat kênh bang `[Tên bang] <lời chào>` (nếu online) và một thư hệ thống. Việc phụ: lỗi dừng ở `GuildWelcomeHooks`.

**D3 — Dây:** `SetGuildWelcomeCommand{query_only, message}` / `SetGuildWelcomeResponse{outcome, message}` **783/784**.
Client `/loichao` (xem), `/loichao <chữ>` (đặt), `/loichao xoa`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cột `welcome_message`; gửi chat + thư khi vào bang; 783/784; `/loichao` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 48): `GuildWelcomePort`/`Facade`, `PostgresGuildWelcomeAdapter`, `GuildWelcomeHooks`, `Lot48Commands.setGuildWelcome`, `GuildWelcomeCoordinator`.
2. ✅ Test: `Lot48ServerTest`.
3. ✅ Kiểm sống gameProbe 2026-11-13: `welcome:?` ⇒ rỗng; đặt "Chào mừng đạo hữu đến bang!" ⇒ OK; hỏi lại ⇒ đúng lời chào. Đường gửi lúc vào bang phủ bằng `Lot48ServerTest` (chưa có bước probe rời/vào bang). Đã trả lời chào về rỗng.
