# ADR-344: Ký hiệu bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Ký hiệu bang [TAG]" trong lượt hỏi lô 52 (AskUserQuestion; mô tả kèm: bang chủ đặt ký hiệu 2–4 ký tự; hiện trước tên thành viên trong chat thế giới và khi soi trang bị). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-172](ADR-172-Guild-Rename.md) · [ADR-171](ADR-171-Inspect-Player.md) · [ADR-062](ADR-062-Achievements-And-Titles.md) |

---

## Decision

**D1 — Lưu:** cột `guild.tag VARCHAR(4)` (V178), UNIQUE `lower(tag)`; 2–4 chữ cái/chữ số (có dấu được), lưu chữ in hoa; chỉ bang chủ đặt/xoá.

**D2 — Hiện:** server gắn `ChatMessageEvent.sender_guild_tag = 9` cho tin kênh THẾ GIỚI và GIAO DỊCH (kênh bang/nhóm/riêng không cần); `InspectCharacterResponse.guild_tag = 9` khi soi. Client hiện `<TAG>` trước danh hiệu và tên. Chỉ THÊM field.

**D3 — Dây:** `SetGuildTagCommand{query_only, tag}` / `SetGuildTagResponse{outcome, tag}` **787/788**. Client `/kyhieubang [ký hiệu|xoa]` (`/guildtag`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | `guild.tag` UNIQUE (V178); field chat 9 / soi 9; 787/788 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 52): `GuildTagPort`/`Facade`, `PostgresGuildTagAdapter`, `Lot52Commands.setGuildTag`/`chatTagOf`/`withGuildTag`, `GatewayChannelHandler.chatEvent`, `GuildTagCoordinator`, `ChatCoordinator.withTitle`, `InspectCoordinator`.
2. ✅ Test: `Lot52IntegrationTest.guildTagUniqueAndReadableByMember`, `Lot52ServerTest.validatesGuildTag`.
3. LIVE344
