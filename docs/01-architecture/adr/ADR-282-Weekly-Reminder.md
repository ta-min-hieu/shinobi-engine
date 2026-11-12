# ADR-282: Nhắc nhiệm vụ tuần

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Nhắc nhiệm vụ tuần" trong lượt hỏi lô 44 (AskUserQuestion; mô tả kèm: trước reset tuần vài giờ, nhắc nhiệm vụ tuần còn chưa lĩnh). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-154](ADR-154-Weekly-Tasks.md) (nhiệm vụ tuần) · [ADR-252](ADR-252-Daily-Reminder.md) (cùng khuôn) |

---

## Decision

**D1 — Việc nền mỗi phút**, cùng khuôn ADR-252: còn ≤ **3 giờ** tới mốc reset tuần (`ResetSchedule.nextWeekly`, 0h thứ
Hai giờ VN) thì nhắc **một lần cho mỗi mốc** mọi người đang online còn việc tuần chưa lĩnh: "còn N nhiệm vụ tuần chưa
lĩnh (M đã đủ, chỉ cần lĩnh — phím E)". Không còn gì ⇒ không nhắc. Không opcode, không migration; cờ "đã nhắc" ở RAM
(server khởi động lại trong 3 giờ cuối có thể nhắc lại — chấp nhận).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Việc nền mỗi phút; ≤ 3 giờ trước reset tuần nhắc một lần/mốc người còn việc tuần chưa lĩnh | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 44): `WeeklyReminder` (job trong `GatewayBootstrap.addReminders`).
2. ✅ Test: `Lot44ServerTest.countsUnclaimedWeeklyTasks`, `noticesAreBilingual`.
3. ⚠️ Chưa kiểm sống được: việc nền theo đồng hồ thật, lúc kiểm sống còn xa mốc reset tuần — logic đếm kiểm bằng test; đường gửi chat là `sendToCharacterIfOnline` đã kiểm sống nhiều lần.
