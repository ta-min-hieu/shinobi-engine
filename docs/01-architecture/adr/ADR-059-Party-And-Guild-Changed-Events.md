# ADR-059: Event "nhóm/bang vừa đổi" — `PartyChangedEvent` (1318) + `GuildChangedEvent` (720)

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-18) — chủ dự án chọn dòng "40(iii): event nhóm/bang" trong lượt hỏi lô 15 (AskUserQuestion; mô tả kèm: báo ngay khi bị kick / nhóm-bang giải tán / đổi trưởng nhóm, opcode event mới, client tự cập nhật). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-18 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [docs/README.md dòng 40(iii), 40(i)](../../README.md) · [ADR-045](ADR-045-Appearance-Changed-Event.md) (khuôn "thêm event đẩy, `protocolVersion` không tăng") |

---

## Context

`party.proto`/`guild.proto` chỉ có Command/Response — KHÔNG có `*Event` nào. Người bị trưởng nhóm kick,
bang bị giải tán, trưởng nhóm tự chuyển quyền khi rớt mạng (dòng 40(i), 2026-10-11): người còn lại chỉ
thấy thay đổi ở lần tự tải lại danh sách. Cửa sổ Nhóm/Bang đang mở thì đứng yên với dữ liệu sai.

## Decision

1. **Một event cho mỗi miền, mang LÝ DO, không mang danh sách**:
   ```proto
   enum PartyChangeReason { PARTY_CHANGE_REASON_UNSPECIFIED = 0; MEMBER_JOINED = 1; MEMBER_LEFT = 2;
                            MEMBER_KICKED = 3; YOU_WERE_KICKED = 4; LEADER_CHANGED = 5; }
   message PartyChangedEvent { PartyChangeReason reason = 1; int64 subject_character_id = 2; }
   enum GuildChangeReason { GUILD_CHANGE_REASON_UNSPECIFIED = 0; MEMBER_JOINED = 1; MEMBER_LEFT = 2;
                            MEMBER_KICKED = 3; YOU_WERE_KICKED = 4; DISBANDED = 5; YOU_WERE_ACCEPTED = 6; }
   message GuildChangedEvent { GuildChangeReason reason = 1; int64 subject_character_id = 2; }
   ```
   Opcode `OPCODE_PARTY_CHANGED_EVENT = 1318`, `OPCODE_GUILD_CHANGED_EVENT = 720`. Chỉ thêm —
   `protocolVersion` không tăng. (Tên giá trị enum thật có tiền tố theo luật proto, vd
   `PARTY_CHANGE_REASON_MEMBER_JOINED`.)
2. Event là **lời nhắc "dữ liệu của bạn đã cũ"**: client nói một câu theo lý do rồi tự hỏi lại
   `GetMyParty`/`ListPartyMembers` (bang tương tự) — không suy danh sách từ event, cùng luật "hỏi lại
   sau mọi lệnh đổi trạng thái" đã dùng cho thư/bang.
3. **Gateway phát SAU khi lệnh thành công**, tới mọi thành viên ONLINE bị ảnh hưởng (trừ chính người ra
   lệnh — họ đã có Response): danh sách lấy TRƯỚC lệnh (rời/kick/giải tán) hoặc SAU lệnh (vào nhóm/bang).
   Người bị kick nhận `YOU_WERE_KICKED`; người được duyệt vào bang nhận `YOU_WERE_ACCEPTED`. Chuyển quyền
   trưởng nhóm khi rớt mạng (40(i)) phát `LEADER_CHANGED` cho người còn online.
4. Offline thì không gửi gì — lần vào world sau đã tự hỏi lại.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Event mang trọn danh sách thành viên | Trùng `ListMembersResponse`; hai nguồn sự thật dễ lệch |
| Mỗi lý do một opcode | 10+ opcode cho cùng một việc "làm mới đi" |

## Consequences

- Cửa sổ Nhóm/Bang tự đúng khi người khác đổi nó; người bị kick biết ngay.
- Vài lần đọc DB thêm mỗi lệnh đổi thành viên (lấy danh sách người nhận) — lệnh hiếm.

## Revisit Trigger

- Nhóm/bang lớn (trăm người) ⇒ cân nhắc gộp event.

## Technical Decisions

| # | Quyết định |
|---|---|
| 1 | 1318/720, event mang lý do + người liên quan |
| 2 | Client hỏi lại danh sách khi nhận event |
| 3 | Chỉ gửi thành viên online, trừ người ra lệnh |

## ADR cần tạo

Không.

## Tài liệu liên quan

[feature-party](../../../server/server-features/feature-party/) · [feature-guild](../../../server/server-features/feature-guild/) · [NETWORK.md](../../05-server/NETWORK.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành (2026-10-18) — `MembershipNotices` (gateway, phát sau lệnh vào/rời/kick/giải tán/duyệt + chuyển quyền trưởng nhóm lúc rớt mạng), `MembershipNoticeCoordinator` (client: một dòng chat hệ thống + hỏi lại danh sách). ✅ Kiểm sống 2026-10-23 bằng hai gameProbe (hai tài khoản): Benkei (trưởng nhóm, online) mời tanthua44, tanthua44 nhận ⇒ Benkei nhận 1318 `PARTY_CHANGED_EVENT`; góp bang/xong nhiệm vụ bang ⇒ 720 (ADR-068/069). Dòng chat hệ thống phía client chưa nhìn bằng giao diện.
