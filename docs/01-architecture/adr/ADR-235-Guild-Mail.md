# ADR-235: Thư toàn bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Thư toàn bang" trong lượt hỏi lô 38 (AskUserQuestion; mô tả kèm: bang chủ/phó gửi một thư tới mọi thành viên, có giới hạn mỗi ngày). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-184](ADR-184-Guild-Vice-Leader.md) (phó bang chủ) · [ADR-179](ADR-179-Guild-Notice.md) (thông báo bang) · [ADR-212](ADR-212-Stranger-Mail-Block.md) |

---

## Decision

**D1 — Gửi = nhiều thư thường.** `SendGuildMailCommand{subject, body}` / `SendGuildMailResponse{outcome, recipients,
remaining_today}` 766/767 (dải Guild). Server gửi cho **mọi thành viên khác** một thư qua đúng `MailFacade.sendMail` —
cùng kiểm độ dài (tiêu đề 64, nội dung 1024), cùng lọc từ cấm, cùng audit thư; không vàng, không đồ nên **không tốn
bưu phí**. Ai đang online nhận `NewMailEvent` như thư thường. Người cùng bang không phải "người lạ" nên chặn thư người lạ
(ADR-212) không áp dụng.

**D2 — Ai được gửi:** bang chủ hoặc phó bang chủ (ADR-184). Người khác ⇒ `NOT_OFFICER`; không có bang ⇒ `NOT_IN_GUILD`.

**D3 — Trần: 3 đợt mỗi bang mỗi ngày** (theo mốc reset ngày `DailyReset.ZONE`, PLACEHOLDER cân bằng — Điều 27). Đếm
trên audit `GUILD_MAIL` (`{guildId, recipients}`, chỉ mục một phần V167) — vừa là hạn mức vừa là dấu vết cho CSKH khi
có người kêu bị spam. Trần tính theo **bang** (không theo người) để bang chủ + nhiều phó không nhân số đợt. Hai sĩ quan
gửi đúng cùng lúc có thể vượt trần 1 đợt — chấp nhận (không có hại về kinh tế, chỉ là thư).

**D4 — Client** `/thubang <tiêu đề> | <nội dung>` (hoặc `/guildmail`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 766/767 gửi thư thường tới mọi thành viên khác; bang chủ/phó; 3 đợt/bang/ngày đếm trên audit `GUILD_MAIL`; `/thubang` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 38): `GuildMailQuotaPort`/`GuildMailFacade`, `PostgresGuildMailQuotaAdapter`, `GuildMailCommands`, `GuildMailCoordinator`.
2. ✅ Test: `Lot38bIntegrationTest.countsGuildMailBatchesPerGuildSince`, `Lot38bCoordinatorsTest` (tách tiêu đề | nội dung).
3. ✅ Kiểm sống gameProbe 2026-11-08: ĐạoHữu01 (bang chủ) `guildMail` ⇒ `OK nhận=1 cònLại=2` (BaoDo01 nhận thư); BaoDo01 (thành viên thường) ⇒ `NOT_OFFICER`. Thư/audit thử đã xoá.
