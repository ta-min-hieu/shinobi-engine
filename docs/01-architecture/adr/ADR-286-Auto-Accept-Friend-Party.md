# ADR-286: Tự nhận lời mời nhóm từ bạn bè

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Tự nhận lời mời nhóm từ bạn bè" trong lượt hỏi lô 44 (AskUserQuestion; mô tả kèm: bật thì lời mời vào nhóm từ bạn bè được tự chấp nhận). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-059](ADR-059-Party-And-Guild-Changed-Events.md) (sự kiện đổi thành viên) · [ADR-237](ADR-237-Block-Stranger-Invites.md) |

---

## Decision

**Lệch so với mô tả lúc hỏi (ghi "thuần client"):** lời mời nhóm **không có gói đẩy** tới người được mời và
`PendingPartyInviteView` không mang id người mời — client không biết ai mời, cũng không biết lúc nào có lời mời mới. Nên
làm ở **server**:

**D1 — Cột** `character.auto_accept_friend_party` (V171, mặc định tắt). `SetPartyAutoAcceptCommand{query_only, enabled}`
/ `SetPartyAutoAcceptResponse{enabled}` 1345/1346 (dải Party). Client `/tunhannhom bat|tat` (hoặc `/autoparty`).

**D2 — Ngay sau một lời mời thành công**: người được mời bật công tắc **và** người mời là **bạn** của họ ⇒ server gọi đúng
`PartyFacade.acceptInvite` (mọi luật nhóm đầy / đã có nhóm giữ nguyên), phát sự kiện đổi thành viên như đường chấp nhận
thường (ADR-059), và báo người được mời một dòng chat "Đã tự vào nhóm của bạn bè X". Người không phải bạn ⇒ vẫn là lời
mời thường.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | V171 cột công tắc; sau lời mời OK từ bạn bè ⇒ server tự chấp nhận + sự kiện đổi thành viên; 1345/1346; `/tunhannhom` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 44): `PartyAutoAcceptPort`/`PartyAutoAcceptFacade`, `PostgresPartyAutoAcceptAdapter`, `Lot44Commands.autoAcceptIfFriend`, `GatewayChannelHandler.handleInvitePartyMemberBlocking`, `PartyAutoAcceptCoordinator`.
2. ✅ Test: `Lot44IntegrationTest.storesPartyAutoAcceptSwitch`, `Lot44ServerTest.noticesAreBilingual`.
3. ✅ Kiểm sống gameProbe 2026-11-12: BaoDo01 `leaveParty` → `autoParty:on`; ĐạoHữu01 `partyInvite:BaoDo01` ⇒ OK, BaoDo01 nhận dòng "Đã tự vào nhóm của bạn bè ĐạoHữu01" và `party_member` trở lại (1,2)+(1,1). Đã trả cờ về `false`.
