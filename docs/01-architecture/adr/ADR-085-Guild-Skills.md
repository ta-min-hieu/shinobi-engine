# ADR-085: Kỹ năng bang — buff thụ động cho mọi thành viên

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Kỹ năng bang" trong lượt hỏi lô 20 (AskUserQuestion; mô tả kèm: cấp bang mở buff thụ động cho mọi thành viên — +EXP%, +máu tối đa, +vàng rơi; bang chủ chọn nâng bằng kinh nghiệm bang). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-068](ADR-068-Guild-Level-And-Contribution.md) · [ADR-077](ADR-077-Mental-Arts.md) (ống chỉ số chung) |

---

## Decision

**D1 — Ba kỹ năng, bậc 0–4** (`GuildSkill`, tham số cân bằng): **Ngộ Tính** +5% kinh nghiệm đánh quái/bậc · **Cường
Thể** +15 máu tối đa/bậc · **Hộ Thể** +3 thủ/bậc.

> Lượt hỏi ghi "+vàng rơi": quái trong game KHÔNG rơi vàng (nguồn vàng là bán đồ/thưởng), nên không có gì để nhân.
> Thay bằng **Hộ Thể** (+thủ). Lượt hỏi cũng ghi "nâng bằng kinh nghiệm bang": tiêu kinh nghiệm bang sẽ làm tụt cấp
> bang (kinh nghiệm là thước cấp, ADR-068 D1), nên dùng **điểm kỹ năng bang = cấp bang × 2** (không lưu; đã tiêu =
> tổng bậc) — cấp bang vẫn là thứ mở khoá, đúng tinh thần.

**D2 — Chỉ bang chủ nâng**; kiểm dưới khoá dòng `guild` (V81 `guild_skill`, vắng dòng = bậc 0). Audit `GUILD_SKILL_UPGRADE`.

**D3 — Hiệu lực**: máu/thủ đi chung ống chỉ số trang bị + tâm pháp (`SkillRankSource#withMentalArts`) ⇒ bảng nhân vật,
Lực Chiến, trận đánh đều thấy; Ngộ Tính nhân EXP đánh quái SAU hệ số sự kiện (ADR-067), không chạm thưởng quest. Nâng
xong ⇒ tính lại chỉ số mọi thành viên đang online ngay. Vào/rời bang thì áp ở lần tính chỉ số kế (đăng nhập, mặc đồ…).

**D4 — Giao thức, chỉ thêm**: 727/728 `ListGuildSkillsRequest/Response`, 729/730 `UpgradeGuildSkillCommand/Response`.

**D5 — Client**: cửa sổ Bang thêm nút "Kỹ năng" (hàng thứ hai) mở `ReportPanel`; nút "Nâng" chỉ bày cho bang chủ còn điểm.

## Consequences

- Rời bang vẫn giữ buff tới lần tính chỉ số kế — chấp nhận ở bản đầu (không có đường nào khai thác được tài sản).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 3 kỹ năng × 4 bậc, điểm = cấp bang × 2, bang chủ nâng, ống chỉ số chung, 727–730 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `V81__guild_skills.sql` · `PostgresGuildSkillAdapter` · `GuildSkillEffects` · `GuildSkillCommands`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 20).
2. ⏳ Kiểm sống bằng gameProbe.
