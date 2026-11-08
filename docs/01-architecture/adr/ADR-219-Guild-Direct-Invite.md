# ADR-219: Mời thẳng vào bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Mời thẳng vào bang" trong lượt hỏi lô 36 (AskUserQuestion; mô tả kèm: bang chủ / phó mời một người theo tên, người được mời bấm đồng ý là vào bang, không cần gửi đơn). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-184](ADR-184-Guild-Vice-Leader.md) (phó bang chủ duyệt đơn) · [ADR-185](ADR-185-Guild-Recruitment-Board.md) (chiêu mộ) |

---

## Decision

**D1 — Lời mời sống trong RAM**, 5 phút, mỗi người được mời giữ một lời mời gần nhất. Không bảng mới: mất khi server
khởi động lại là chấp nhận được (chỉ là lời mời). Bảng lời mời dùng chung cả server (lớp lệnh dựng theo từng kết nối,
nên bảng khoá theo registry như `LeaderboardCache`).

**D2 — Ai mời được ai**: bang chủ hoặc phó bang chủ; người được mời phải **đang online** và chưa ở bang nào.

**D3 — Đồng ý = đường cũ**: server tự "xin vào" nhân danh người được mời rồi "duyệt" nhân danh người mời, qua đúng
`GuildFacade.sendJoinRequest` / `approveJoinRequest` sẵn có — mọi luật cũ giữ nguyên (bang đầy theo cấp, người mời còn
quyền duyệt không, đã ở bang khác), nhật ký bang + báo cả bang như khi duyệt đơn.

**D4 — Protocol**: `InviteToGuildCommand{target_name}` / `Response{outcome}` 761/762, `GuildInviteEvent{guild_name,
inviter_name}` 763, `RespondGuildInviteCommand{accept}` / `Response{outcome, guild_name}` 764/765.

**D5 — Client**: `/moibang <tên>` (`/ginvite`); người được mời thấy dòng chat, gõ `/vaobang` (`/gaccept`) hoặc
`/tuchoibang` (`/gdecline`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lời mời RAM 5 phút, đồng ý đi đường xin vào + duyệt sẵn có; 761–765; `/moibang`, `/vaobang` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 36): `GuildInviteCommands`, `GuildInviteCoordinator`.
2. ✅ Kiểm sống gameProbe 2026-11-08: tạo nhân vật thử MoiBangThu (giữ online). BaoDo01 (thành viên thường) mời ⇒
   `NOT_AUTHORIZED`; ĐạoHữu01 (bang chủ) mời tên không có ⇒ `TARGET_NOT_FOUND`, mời BaoDo01 đang offline ⇒
   `TARGET_NOT_FOUND`, mời MoiBangThu ⇒ `OK` và MoiBangThu nhận `GUILD_INVITE_EVENT` (763); MoiBangThu `guildRespond:yes`
   ⇒ `JOINED bang=LinhKiemMon` (DB: `guild_member` MEMBER), trả lời lần hai ⇒ `NO_INVITE`. Đã gỡ nhân vật thử khỏi bang
   và đặt hẹn xoá (ADR-205) cho nó.
