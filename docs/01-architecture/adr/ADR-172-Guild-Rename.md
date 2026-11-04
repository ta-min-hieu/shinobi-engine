# ADR-172: Đổi tên bang hội

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-04) — chủ dự án chọn "Đổi tên bang hội" trong lượt hỏi lô 30 (AskUserQuestion; mô tả kèm: bang chủ đổi tên bang (tốn vàng quỹ bang, hồi chiêu 30 ngày), báo cả bang). AI soạn văn bản và chọn chi tiết sau khi được chọn. ⚠️ Lệch mô tả lúc hỏi: **dự án không có quỹ vàng bang** (góp bang ADR-068 chỉ thành kinh nghiệm bang + cống hiến) ⇒ phí trừ vào vàng của CHÍNH bang chủ (D2). |
| Date | 2026-11-04 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-059](ADR-059-Party-And-Guild-Changed-Events.md) (event bang 720) · [ADR-068](ADR-068-Guild-Level-And-Contribution.md) · luật tên bang `GuildName` (2026-09-29) |

---

## Decision

**D1 — Ai, luật tên**: chỉ **bang chủ**. Tên mới chịu ĐÚNG luật tạo bang (`GuildName`: 2–16 ký tự chữ/số/`_`, NFC) và chỉ
mục duy nhất không phân biệt hoa thường `guild_name_lower_key` (kiểm trước, chỉ mục là chốt cuối).

**D2 — Phí, hồi chiêu (V138)**: **20.000 vàng** của bang chủ (`GoldLedger`, `gold.reason.guild_rename`, vào sổ vàng
ADR-169); **30 ngày** một lần — cột `guild.renamed_at`. PLACEHOLDER (Điều 27). Một transaction: kiểm bang chủ → khoá dòng
bang → hồi chiêu → tên trùng → trừ phí → đổi tên → audit `GUILD_RENAMED` (tên cũ → mới).

**D3 — Báo cả bang**: thành công ⇒ mọi thành viên đang online (trừ người đổi) nhận `GuildChangedEvent` lý do mới
`GUILD_CHANGE_REASON_RENAMED = 9` ⇒ dòng hệ thống "Bang vừa được bang chủ đổi tên".

**D4 — Giao thức, chỉ thêm**: `RenameGuildCommand{new_name}` / `RenameGuildResponse{outcome, name,
next_allowed_at_epoch_ms, fee}` — 741/742.

**D5 — Client**: lệnh chat `/doitenbang <tên mới>` (hoặc `/guildrename`); kết quả một dòng hệ thống (hồi chiêu kèm ngày giờ
được đổi lại).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bang chủ đổi tên bang, luật tên như tạo bang, 20.000 vàng của bang chủ, 30 ngày, báo 720 RENAMED; 741/742 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-04 (lô 30): V138, `GuildRenamePort`/`GuildRenameFacade`, `PostgresGuildRenameAdapter`,
   `GuildRenameCommands` 741/742, `GuildChangeReason.RENAMED`, `GuildRenameCoordinator` `/doitenbang`.
2. ✅ Test Postgres thật: `GuildRenameIntegrationTest` (đổi được + trừ vàng; hồi chiêu đúng 30 ngày rồi đổi lại được;
   thành viên thường / không bang / tên trùng hoa-thường / thiếu vàng đều từ chối, tên không đổi).
3. ✅ Kiểm sống gameProbe 2026-11-04: tên "a" ⇒ `INVALID_NAME`; bang chủ ĐạoHữu01 ⇒ `OK` LinhKiemMon, −20.000 vàng; lần hai
   ⇒ `ON_COOLDOWN`; thành viên BaoDo01 ⇒ `NOT_LEADER` và nhận event 720.
4. Chờ chủ dự án: phí 20.000 vàng / 30 ngày (PLACEHOLDER); có muốn làm quỹ vàng bang không.
