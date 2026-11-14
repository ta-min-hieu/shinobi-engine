# ADR-424: Mời bạn online vào nhóm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Mời bạn online vào nhóm" trong lượt hỏi lô 62 (AskUserQuestion; mô tả kèm: /moibanbe: gửi lời mời nhóm tới mọi bạn bè đang online (đủ chỗ thì thôi)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-286](ADR-286-Auto-Accept-Friend-Party.md) · [ADR-191](ADR-191-Party-Leader-Transfer.md) |

---

## Decision

**D1:** chưa có nhóm ⇒ lập nhóm, mình làm trưởng; đang ở nhóm người khác ⇒ từ chối. Gửi lời mời (đúng đường `PartyFacade.invite` sẵn có — kiểm trùng lời mời, đã ở nhóm khác) tới từng bạn bè đang online; nhóm đầy thì dừng. Bạn bè luôn qua kiểm riêng tư liên lạc (ADR-236/237), nên không cần kiểm lại.

**D2 — Dây:** `InviteOnlineFriendsCommand{}` / `InviteOnlineFriendsResponse{not_leader, invited}` **1356/1357**. Client `/moibanbe` (`/invitefriends`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lệnh 1356/1357, dùng lại `PartyFacade.invite` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 62): `Lot62Commands.inviteOnlineFriends`, `SocialExtrasCoordinator`.
2. ✅ Test: kiểm sống.
3. ✅ Kiểm sống 2026-11-14: ĐạoHữu01 đang là thành viên nhóm của BaoDo01 ⇒ `inviteFriends:` trả `khôngPhảiTrưởng=true` (đúng luật). Đường gửi lời mời dùng lại `PartyFacade.invite` đã có test.
