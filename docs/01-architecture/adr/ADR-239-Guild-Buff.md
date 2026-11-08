# ADR-239: Buff bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Buff bang" trong lượt hỏi lô 39 (AskUserQuestion; mô tả kèm: bang chủ tiêu quỹ bang (kinh nghiệm bang) để bật +EXP cho mọi thành viên trong 1 giờ). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-068](ADR-068-Guild-Level-And-Contribution.md) (cấp + kinh nghiệm bang) · [ADR-085](ADR-085-Guild-Skills.md) (Ngộ Tính) · [ADR-164](ADR-164-Double-Exp-Pill.md) (x2 EXP) |

---

## Decision

**D1 — Giá: 1.000 kinh nghiệm bang, +20% EXP đánh quái, 1 giờ** (PLACEHOLDER cân bằng — Điều 27, hằng số ở
`GuildBuffFacade`). `ActivateGuildBuffCommand{query_only}` / `ActivateGuildBuffResponse{outcome, seconds_left,
guild_exp_left, percent, cost}` 768/769 (dải Guild). `query_only` = ai trong bang cũng hỏi được còn bao lâu.

**D2 — Ai, khi nào.** Chỉ **bang chủ** (đúng mô tả lúc hỏi). Buff đang chạy ⇒ `ALREADY_ACTIVE` (không cộng dồn, không
gia hạn). **Không được làm bang tụt cấp**: kinh nghiệm sau khi trừ phải ≥ mốc của cấp hiện tại
(`GuildLevelTable.expToReach`), nếu không ⇒ `NOT_ENOUGH_EXP`. Khoá dòng `guild FOR UPDATE`, trừ + đặt hạn trong một
giao dịch, audit `GUILD_BUFF`. Cột mới `guild.exp_buff_until` (V168).

**D3 — Áp vào đâu.** EXP đánh quái, sau quẻ bói (ADR-149) và **trước** đan x2 (ADR-164 vẫn là hệ số cuối). Hạn đọc qua
bộ đệm 30 giây theo nhân vật — hạ quái không chạm DB mỗi lần; bật xong xoá đệm. Người vào bang giữa chừng được hưởng
trong ≤ 30 giây; người rời bang mất trong ≤ 30 giây.

**D4 — Báo.** Bật xong mọi thành viên đang online nhận một dòng chat hệ thống song ngữ. Client `/buffbang` (hoặc
`/guildbuff`), `/buffbang ?` xem còn bao lâu.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 768/769; bang chủ tiêu 1.000 kinh nghiệm bang, +20% EXP 1 giờ, không tụt cấp; cột `guild.exp_buff_until` (V168); đệm 30 giây; `/buffbang` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 39): `GuildBuffPort`/`GuildBuffFacade`, `GuildLevelTable.expToReach`, `PostgresGuildBuffAdapter`, `Lot39Commands.guildBuff`, `GatewayBootstrap.fortuneBoosted`, `GuildBuffCoordinator`.
2. ✅ Test: `GuildBuffFacadeTest` (+% khi còn hạn, đệm, mốc cấp), `Lot39IntegrationTest.guildBuffChargesGuildExpOnceAndNeverDropsALevel`, `Lot39CoordinatorsTest`.
3. ✅ Kiểm sống gameProbe 2026-11-08: BaoDo01 (thành viên) ⇒ `NOT_LEADER`; ĐạoHữu01 (bang chủ) ⇒ `OK giây=3600`, kinh nghiệm bang 2200 → 1200; bật lần nữa ⇒ `ALREADY_ACTIVE`; `?` ⇒ `STATUS giây=3599`. Hạ hình nộm luyện công (20 EXP gốc) ⇒ nhận **24** EXP (+20%). Dữ liệu thử đã hoàn lại.
