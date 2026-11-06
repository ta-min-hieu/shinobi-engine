# ADR-179: Thông báo bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-06) — chủ dự án chọn "Thông báo bang" trong lượt hỏi lô 31 (AskUserQuestion; mô tả kèm: bang chủ ghi một dòng thông báo, thành viên thấy khi vào game hoặc mở bảng bang). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-06 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-059](ADR-059-Party-And-Guild-Changed-Events.md) (event bang 720) · [ADR-172](ADR-172-Guild-Rename.md) (cùng khuôn "bang chủ đổi, báo cả bang") |

---

## Decision

**D1 — Ai, nội dung**: chỉ **bang chủ** (dự án chỉ có hai vai LEADER/MEMBER). Một dòng, gộp khoảng trắng, tối đa **100 ký
tự**, qua đúng bộ lọc từ cấm của chat (gateway áp — `feature-guild` không phụ thuộc `feature-chat`). Rỗng = xoá.

**D2 — Lưu (V142)**: `guild.notice VARCHAR(160) NOT NULL DEFAULT ''` + `notice_updated_at`. Mỗi lần ghi một dòng
`audit_log` `GUILD_NOTICE_SET` (độ dài, không chép nội dung — nội dung đã ở chính bảng bang).

**D3 — Hiển thị**: `GetMyGuildResponse.notice = 6` (chỉ thêm) ⇒ mọi lần client hỏi lại bang đều mang theo. Client: lúc vào
game hỏi bang một lần và in "[Thông báo bang] …" vào chat; in lại mỗi khi nó đổi; cửa sổ bang luôn hiện thông báo ở
đầu khối tiến độ. Bang chủ đổi ⇒ thành viên đang online nhận `GuildChangedEvent` lý do mới
`GUILD_CHANGE_REASON_NOTICE_CHANGED = 10` ⇒ client hỏi lại bang ⇒ thấy thông báo mới.

**D4 — Giao thức, chỉ thêm**: `SetGuildNoticeCommand{notice}` / `SetGuildNoticeResponse{outcome, notice}` 743/744.

**D5 — Client**: lệnh chat `/thongbaobang <nội dung>` (hoặc `/guildnotice`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bang chủ ghi ≤ 100 ký tự qua bộ lọc chat, V142, `GetMyGuildResponse.notice`, 743/744, 720 NOTICE_CHANGED | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-06 (lô 31): V142, `GuildNoticePort`/`GuildNoticeFacade`, `PostgresGuildNoticeAdapter`,
   `GuildNoticeCommands` 743/744, `GuildNoticeCoordinator` `/thongbaobang`, dòng thông báo trong cửa sổ bang.
2. ✅ Test: `GuildNoticeIntegrationTest` (Postgres thật), `GuildNoticeFacadeTest`, `GuildNoticeCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-06 (bước `guildNotice:<nội dung>`, `myGuild`): 101 ký tự ⇒ `TOO_LONG`; bang chủ
   ĐạoHữu01 đăng "Tối nay 8 giờ   đi Bí Cảnh" ⇒ `OK`, lưu đã gộp khoảng trắng; thành viên BaoDo01 hỏi bang ⇒ thấy đúng
   thông báo; BaoDo01 tự đăng ⇒ `NOT_LEADER`. (Đường event 720 tới thành viên online đã kiểm ở ADR-172, cùng mã.)
