# ADR-423: Lịch sử tên bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Lịch sử tên bang" trong lượt hỏi lô 62 (AskUserQuestion; mô tả kèm: /lichsutenbang [tên bang]: các tên cũ của bang kèm ngày đổi (ghi từ nay, bảng mới)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-172](ADR-172-Guild-Rename.md) · [ADR-343](ADR-343-Old-Names-Lookup.md) |

---

## Decision

**D1 — Lưu:** bảng MỚI `guild_name_history(guild_id, old_name, new_name, changed_at)` (V184). Mỗi lần đổi tên bang thành công (ADR-172) gateway ghi một dòng — tên cũ đọc ngay TRƯỚC lệnh đổi. Chỉ ghi từ lô 62 trở đi (các lần đổi trước đó không còn dữ liệu).

**D2 — Tra:** tên rỗng = bang của mình; có tên thì tìm theo tên hiện tại HOẶC tên cũ (không phân biệt hoa thường). `GetGuildNameHistoryRequest{guild_name}` / `GetGuildNameHistoryResponse{found, current_name, renames{old_name, new_name, at}}` **1212/1213**. Client `/lichsutenbang [tên]` (`/guildnames`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `guild_name_history` (V184), 1212/1213 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 62): `GuildNameHistoryPort/Facade`, `PostgresGuildNameHistoryAdapter`, `Lot62Commands.rememberRename/guildNameHistory` (bọc lệnh đổi tên trong `GatewayChannelHandler`), `SocialExtrasCoordinator`.
2. ✅ Test: `Lot62IntegrationTest.recordsGuildRenamesAndFindsByOldName`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14: (lùi mốc đổi tên + cấp tạm linh thạch) ĐạoHữu01 đổi `LinhKiemMon → KiemTienMon` ⇒ `guildNames:` trả `hiện=KiemTienMon đổi=1`, tra bằng tên CŨ `LinhKiemMon` cũng ra đúng bang; tên không tồn tại ⇒ `thấy=false`. Đã khôi phục tên, mốc đổi tên, linh thạch, nhật ký bang, sổ vàng và xoá dòng lịch sử.
