# ADR-306: Báo trước sự kiện

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Báo trước sự kiện" trong lượt hỏi lô 47 (AskUserQuestion 2026-11-12; mô tả kèm: 5 phút trước mỗi sự kiện trong lịch sự kiện tuần, server báo toàn server một dòng chat). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-224](ADR-224-Event-Calendar.md) · [ADR-225](ADR-225-World-Boss-Warning.md) |

---

## Decision

**D1 — Job 60 giây** (`EventReminder`): còn ≤ 5 phút tới một mốc trong lịch tuần ⇒ báo toàn server MỘT dòng chat song
ngữ, mỗi mốc một lần (nhớ mốc đã báo theo loại). Mốc báo: **luận võ đại hội**, **làm mới ngày**, **làm mới tuần**.
**Boss thế giới không báo ở đây** — `WorldBossScheduler` đã tự báo trước (tránh báo đôi). Không opcode, không migration.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Job 60 giây, 3 loại mốc, mỗi mốc một lần | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 47): `EventReminder`, `GatewayBootstrap.addReminders`.
2. ✅ Test: `Lot47ServerTest.eventReminderWindowAndMinutes`.
3. ⏳ Chạy theo giờ thật (mốc luận võ/reset) nên probe không ép được; phủ bằng `Lot47ServerTest.eventReminderWindowAndMinutes` (cửa sổ 5 phút, làm tròn phút, nội dung song ngữ).
