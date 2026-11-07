# ADR-181: Bảng xếp hạng bang — thêm số thành viên và tổng cống hiến

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Bảng xếp hạng bang" trong lượt hỏi lô 31 (mô tả kèm: thêm bảng bang hội vào Bia Anh Danh, xếp theo cấp bang và tổng cống hiến). ⚠️ Lúc làm mới thấy **bảng bang ĐÃ CÓ từ ADR-068** (AI liệt kê nhầm là tính năng mới) ⇒ hỏi lại; chủ dự án chọn "**Thêm cột cho bảng**" (AskUserQuestion 2026-11-07): giữ cách xếp, mỗi dòng hiện thêm số thành viên và tổng cống hiến. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-057](ADR-057-Leaderboards.md) (Bia Anh Danh) · [ADR-068](ADR-068-Guild-Level-And-Contribution.md) (cấp bang, cống hiến, bảng bang) |

---

## Decision

**D1 — Giữ cách xếp ADR-068**: kinh nghiệm bang giảm dần (cấp đi theo kinh nghiệm), cache 60 giây như mọi bảng.

**D2 — Hai cột mới**: số thành viên hiện tại và **tổng cống hiến của thành viên HIỆN TẠI** (`SUM(guild_member.contribution)`
cùng câu truy vấn xếp hạng) — người đã rời bang thì cống hiến của họ không còn tính; khác kinh nghiệm bang (giữ lại vĩnh
viễn và có cả thưởng nhiệm vụ tuần).

**D3 — Giao thức, chỉ thêm**: `LeaderboardEntry.member_count = 7`, `total_contribution = 8` — chỉ có giá trị ở
`LEADERBOARD_KIND_GUILD`, 0 ở bảng khác. Không opcode mới, không migration.

**D4 — Client**: dòng bảng bang "1. LinhKiemMon — bang cấp 1, 2000 kinh nghiệm bang, 2 thành viên, 2000 cống hiến".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Giữ xếp hạng ADR-068, thêm số thành viên + tổng cống hiến thành viên hiện tại vào `LeaderboardEntry` (7, 8) | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 31): `GuildRank.totalContribution`, `LeaderboardCache.GuildStats`, hai field proto, chuỗi
   `leaderboard.row.guild` vi/en.
2. ✅ Test: `GuildProgressionIntegrationTest` (tổng cống hiến từng bang trên Postgres thật).
3. ✅ Kiểm sống gameProbe 2026-11-07 (`--leaderboards=guild`): "#1 LinhKiemMon Lv1 2000 tv=2 cốngHiến=2000" — khớp DB
   (BaoDo01 góp 2.000, bang chủ 0).
