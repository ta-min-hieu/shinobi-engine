# ADR-069: Nhiệm vụ bang tuần — cả bang hạ 300 yêu thú trong tuần, thưởng qua thư cho mọi thành viên

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-21) — chủ dự án chọn "Nhiệm vụ bang tuần" trong lượt hỏi lô 17 (AskUserQuestion; mô tả kèm: mục tiêu chung cả bang — giết N quái trong tuần, xong thì phát thưởng qua thư cho mọi thành viên). Câu hỏi mở theo khuyến nghị. AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-21 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [GDD §7/§8](../../13-game-design/GAME-DESIGN-DOCUMENT.md) ("nhiệm vụ bang — hợp tác") · [ADR-068](ADR-068-Guild-Level-And-Contribution.md) · [ADR-060](ADR-060-Daily-Check-In.md) (quà qua thư hệ thống) |

---

## Decision

**D1 — Mục tiêu**: thành viên bang hạ tổng cộng **300** yêu thú trong một tuần (thứ Hai 0h giờ Việt Nam →
Chủ nhật, cùng mốc giờ với điểm danh). Chỉ người ra đòn cuối được tính (phần chia của đồng đội không cộng
thêm lượt). Tham số ở `GuildWeeklyQuest`.

**D2 — Thưởng, đúng MỘT lần mỗi tuần**: lượt hạ thứ 300 (bộ đếm `UPDATE … RETURNING` dưới khoá dòng — chỉ
một lượt thấy đúng con số đó) chốt tuần: bang +**5.000** kinh nghiệm (có thể lên cấp — ADR-068), và mỗi
thành viên LÚC ĐÓ nhận thư hệ thống **2.000** linh thạch (offline vẫn nhận; vào bang sau mốc thì không).

**D3 — Dữ liệu** (V70): `guild_weekly_progress(guild_id, week_start, kills, completed_at)`. Tuần mới = dòng mới.

**D4 — Giao thức**: tiến độ nằm trong `GuildProgressView` (ADR-068); `GuildChangeReason.WEEKLY_QUEST_COMPLETED = 8`
gửi cho thành viên online + `NewMailEvent`.

**D5 — Client**: cửa sổ Bang hiện "Nhiệm vụ tuần: 123/300 yêu thú" (hoặc "đã hoàn thành").

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Nhiều nhiệm vụ tuần chọn ngẫu nhiên | Cần bảng nội dung + UI chọn; một mục tiêu đủ cho bản đầu |
| Thưởng theo cống hiến từng người | Phức tạp, dễ tranh cãi; chia đều khuyến khích hợp tác |
| Đếm cả phần chia nhóm | Một con quái thành nhiều lượt — mục tiêu mất nghĩa |

## Consequences

- Mỗi lượt hạ quái của người có bang thêm một câu UPDATE (cùng nhịp các bước thưởng khác, trên Virtual Thread).

## Revisit Trigger

- Muốn nhiệm vụ tuần đa dạng (thu thập, hầm ngục) hoặc thưởng vật phẩm.

## Technical Decisions

- `GuildProgressionPort#recordKill` trả về `WeeklyCompletion` đúng một lần; gateway (`GuildWeeklyHooks`) gửi thư + thông báo.

## ADR cần tạo

Không.

## Tài liệu liên quan

- `V70__guild_progression.sql` · `GuildWeeklyHooks` · `GuildProgressionUseCase`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-21 (lô 17).
2. ✅ Kiểm sống 2026-10-21: mỗi quái hạ được cộng vào `guild_weekly_progress` (tuần bắt đầu thứ Hai 2026-10-19); đặt `kills = 299` rồi hạ một con ⇒ 300, `completed_at` ghi, thư "Nhiệm vụ bang tuần" 2.000 + sự kiện 720; hạ thêm ⇒ không thư thứ hai.
