# ADR-068: Cấp bang + cống hiến — góp linh thạch nâng cấp bang, cấp bang nới sĩ số, bảng xếp hạng bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-21) — chủ dự án chọn "Cấp bang + đóng góp" trong lượt hỏi lô 17 (AskUserQuestion; mô tả kèm: thành viên nộp linh thạch ⇒ điểm cống hiến; bang lên cấp ⇒ tăng sĩ số tối đa; xếp hạng bang ở Bia Anh Danh). Câu hỏi mở theo khuyến nghị. AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-21 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [GDD §7](../../13-game-design/GAME-DESIGN-DOCUMENT.md) ("Bang hội: cấp bang, kho, nhiệm vụ tuần") · [ADR-057](ADR-057-Leaderboards.md) · [ADR-069](ADR-069-Guild-Weekly-Quest.md) |

---

## Context

Bang hội chỉ có tên, bang chủ, thành viên (trần 50 — quyết định 2026-10-11). Không có gì để cả bang cùng
cày, không có chỗ tiêu linh thạch chung.

## Decision

**D1 — Cấp bang 1–5 theo kinh nghiệm bang** (`GuildLevelTable`): mốc tích luỹ 0 / 10.000 / 30.000 / 70.000 /
150.000. Sĩ số tối đa = 50 + 10 × (cấp − 1) — cấp 1 giữ đúng trần 50 đã chốt.

**D2 — Cống hiến**: thành viên góp linh thạch theo bội số **1.000** (1.000–100.000 mỗi lần). Mỗi linh thạch góp
= 1 kinh nghiệm bang + 1 điểm cống hiến của người góp. Linh thạch biến mất (gold sink — Q1 theo khuyến nghị:
không có quỹ bang để rút ra, tránh bang chủ ôm quỹ). Có khoá chống trùng (ADR-025) và audit `GUILD_DONATION`.

**D3 — Dữ liệu** (V70): `guild.level`, `guild.exp`, `guild_member.contribution`. Một transaction: khoá chống
trùng → khoá dòng bang → trừ vàng (GoldLedger, báo GoldChangedEvent) → cộng kinh nghiệm + tính lại cấp →
cộng cống hiến → audit.

**D4 — Giao thức, chỉ thêm**: 721/722 `DonateToGuildCommand/Response`; `GetMyGuildResponse.progress`
(`GuildProgressView`: cấp, kinh nghiệm, mốc kế, sĩ số tối đa, cống hiến của mình, tiến độ nhiệm vụ tuần);
`GuildMemberView.contribution`; `GuildChangeReason.LEVEL_UP = 7`; `LEADERBOARD_KIND_GUILD = 4` (dùng lại `LeaderboardEntry`: `name` là TÊN BANG, `level` là cấp bang,
`value` là kinh nghiệm bang, `character_id` là id bang chủ; hạng của mình = hạng bang mình).

**D5 — Client**: cửa sổ Bang (G) thêm dòng cấp/kinh nghiệm/cống hiến và nút "Góp 1.000"; danh sách thành
viên kèm cống hiến; Bia Anh Danh thêm thẻ "Bang hội".

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Quỹ bang rút được | Mở đường bang chủ ôm quỹ / rửa vàng; GDD cần gold sink |
| Góp vật phẩm | Cần định giá từng món; linh thạch đủ cho bản đầu |
| Cấp bang mở kỹ năng bang | Chưa có hệ buff bang; để sau |

## Consequences

- Linh thạch góp vào bang là sink thật. Trần 50 cũ chỉ còn đúng ở cấp 1.

## Revisit Trigger

- Kho bang, buff bang theo cấp, cửa hàng đổi điểm cống hiến.

## Technical Decisions

- `GuildLevelTable`, `GuildStanding`, `GuildProgressionPort` (feature-guild domain), `GuildProgressionUseCase`,
  `GuildProgressionFacade`; adapter `PostgresGuildProgressionAdapter`.

## ADR cần tạo

Không.

## Tài liệu liên quan

- `V70__guild_progression.sql` · `GuildCoordinator` · `LeaderboardCache`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-21 (lô 17).
2. ✅ Kiểm sống 2026-10-21 bằng gameProbe (`--donateToGuild`): góp 1.000 ⇒ exp bang 1.000/10.000, cống hiến 1.000; góp thêm 9.000 ⇒ cấp 2, sĩ số tối đa 60, sự kiện 720. Thẻ Bang ở Bia Anh Danh chưa nhìn bằng client.
