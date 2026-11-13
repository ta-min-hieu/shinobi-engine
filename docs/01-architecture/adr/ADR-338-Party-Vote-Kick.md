# ADR-338: Biểu quyết đuổi khỏi nhóm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Biểu quyết đuổi khỏi nhóm" trong lượt hỏi lô 51 (AskUserQuestion; mô tả kèm: thành viên khởi xướng bỏ phiếu đuổi một người; quá nửa đồng ý trong 60 giây thì người đó rời nhóm). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-059](ADR-059-Party-And-Guild-Changed-Events.md) · [ADR-191](ADR-191-Party-Leader-Transfer.md) |

---

## Decision

**D1 — Luật:** người được bầu = mọi thành viên trừ người bị đuổi; người khởi xướng tính một phiếu đồng ý; cần `số người được bầu / 2 + 1` phiếu đồng ý trong 60 giây. Không đuổi được trưởng nhóm (trưởng nhóm đã có quyền kick), không tự đuổi mình, mỗi nhóm một cuộc bầu, mỗi người một phiếu.

**D2 — Thực thi:** thông qua ⇒ gọi đúng đường kick hiện có dưới tên trưởng nhóm (`PartyFacade.kickMember`) + báo đổi thành viên (ADR-059) + `VoteKickEvent{passed}` cho mọi người. Trạng thái bầu chỉ ở RAM (`PartyVoteKicks`, dọn lười khi hết hạn) — server khởi động lại thì cuộc bầu mất, chấp nhận được.

**D3 — Dây:** `StartVoteKickCommand{target_name}` / `StartVoteKickResponse` **1349/1350**, `VoteKickEvent` **1351**, `CastVoteKickCommand{yes}` / `CastVoteKickResponse` **1352/1353**. Client `/duoi <tên>`, `/bophieu co|khong`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Quá nửa người được bầu trong 60 giây; RAM; kick qua đường trưởng nhóm; 1349–1353 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 51): `PartyVoteKicks`, `Lot51Commands.startVoteKick`/`castVoteKick`, `VoteKickCoordinator`.
2. ✅ Test: `Lot51ServerTest.neededIsMoreThanHalf`/`votePassesWithMajorityOnce`/`voteExpires`.
3. ✅ Kiểm sống gameProbe 2026-11-13 (hai probe): ĐạoHữu01 (thành viên) bầu đuổi BaoDo01 (trưởng nhóm) ⇒ TARGET_IS_LEADER; `castVote` khi chưa có cuộc bầu ⇒ NO_VOTE; BaoDo01 bầu đuổi ĐạoHữu01 trong nhóm hai người ⇒ PASSED 1/1, ĐạoHữu01 nhận `PartyChangedEvent` bị đuổi. Đã mời lại vào nhóm, trả vai trò như cũ.
