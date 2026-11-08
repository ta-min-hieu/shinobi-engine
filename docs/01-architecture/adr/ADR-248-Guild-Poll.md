# ADR-248: Bình chọn trong bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Bình chọn trong bang" trong lượt hỏi lô 40 (AskUserQuestion; mô tả kèm: bang chủ/phó mở một cuộc bỏ phiếu 2–4 lựa chọn, thành viên bỏ phiếu, ai cũng xem được kết quả). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-184](ADR-184-Guild-Vice-Leader.md) (phó bang chủ) · [ADR-179](ADR-179-Guild-Notice.md) (thông báo bang) |

---

## Decision

**D1 — Một cuộc mở mỗi bang.** Bảng `guild_poll(guild_id, created_by, question, options varchar[], created_at,
closes_at)` + `guild_poll_vote(poll_id, character_id, option_index)` (V169). Bang chủ / phó mở cuộc mới (câu hỏi ≤ 80
ký tự, 2–4 lựa chọn ≤ 32 ký tự, lọc từ cấm như chat) — **mở cuộc mới là đóng cuộc đang mở**; mỗi cuộc mở 24 giờ. Khoá
dòng bang khi tạo để hai sĩ quan tạo cùng lúc không thành hai cuộc mở.

**D2 — Bỏ phiếu:** mỗi thành viên một phiếu cho cuộc đang mở, bỏ lại là **đổi phiếu**. Cuộc đóng rồi thì không bỏ
được nhưng vẫn xem được kết quả (cuộc gần nhất). Phiếu công khai theo số đếm, không công khai ai chọn gì.

**D3 — Protocol** 770–775 (dải Guild): `CreateGuildPollCommand{question, options}` / `{outcome}`,
`VoteGuildPollCommand{option_index}` / `{outcome}`, `GetGuildPollRequest{}` / `{has_poll, question, options, votes,
my_vote, seconds_left}`. Mở xong cả bang online nhận một dòng chat hệ thống. Client `/binhchon` (hoặc `/poll`) xem,
`/binhchon <số>` bỏ phiếu, `/binhchon tao <câu hỏi> | a | b …` mở cuộc.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | V169 `guild_poll` + `guild_poll_vote`; một cuộc mở/bang, 24 giờ, đổi phiếu được; 770–775; `/binhchon` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 40): `GuildPollPort`/`GuildPollFacade`, `PostgresGuildPollAdapter`, `Lot40Commands`, `GuildPollCoordinator`.
2. ✅ Test: `GuildPollFacadeTest`, `Lot40IntegrationTest.officersOpenPollsMembersVoteAndChangeVotes`, `Lot40CoordinatorsTest`.
3. ✅ Kiểm sống gameProbe 2026-11-08: BaoDo01 (thành viên) xem ⇒ chưa có cuộc nào, mở ⇒ `NOT_OFFICER`; ĐạoHữu01 mở "Đi bí cảnh tối nay?" (Có/Không/Mai) ⇒ `OK` + dòng chat báo cả bang, một lựa chọn ⇒ `INVALID`; bỏ phiếu 0 ⇒ phiếu `[1,0,0]`, còn 86399 giây; BaoDo01 chọn số 5 ⇒ `BAD_OPTION`, chọn 2 ⇒ `[1,0,1]`, `củaTôi=2`. Dữ liệu thử đã xoá.
