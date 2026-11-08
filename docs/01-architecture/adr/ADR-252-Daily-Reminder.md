# ADR-252: Nhắc việc ngày chưa xong

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Nhắc việc ngày chưa xong" trong lượt hỏi lô 40 (AskUserQuestion; mô tả kèm: trước giờ reset 1 tiếng, nhắc điểm danh / nhiệm vụ ngày còn thiếu). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-055](ADR-055-Daily-Quests.md) (nhiệm vụ ngày) · [ADR-060](ADR-060-Daily-Check-In.md) (điểm danh) · [ADR-200](ADR-200-Reset-Timers.md) (mốc reset) |

---

## Decision

**D1 — Việc nền mỗi phút** (`ExpansionJobs`): còn ≤ **1 giờ** tới mốc reset ngày (`ResetSchedule.nextDaily`) thì nhắc
**một lần cho mỗi mốc** mọi người đang online còn nhiệm vụ ngày chưa lĩnh: một dòng chat hệ thống song ngữ "còn N
nhiệm vụ ngày chưa lĩnh (M đã xong, chỉ cần lĩnh)". Không còn gì ⇒ không nhắc.

**D2 — Đếm gì.** Nhiệm vụ ngày trong danh mục (ADR-228) đã **mở khoá** (không tiên quyết, hoặc tiên quyết đã lĩnh) mà
hôm nay chưa lĩnh. **Lệch so với mô tả lúc hỏi:** không nhắc điểm danh — điểm danh đã **tự nhận lúc vào world**
(ADR-060), người đang online không bao giờ thiếu. Không opcode, không migration. Server khởi động lại trong giờ cuối thì
có thể nhắc lần nữa (cờ "đã nhắc" ở RAM) — chấp nhận.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Việc nền mỗi phút; ≤ 1 giờ trước reset nhắc một lần/mốc người online còn nhiệm vụ ngày chưa lĩnh | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 40): `DailyReminder` (job trong `GatewayBootstrap.expansionJobs`).
2. ✅ Test: `DailyReminderTest`.
3. ⚠️ Chưa kiểm sống được: việc nền chạy theo đồng hồ thật, lúc kiểm sống còn xa mốc reset và chưa có cờ dời giờ — logic đếm kiểm bằng `DailyReminderTest`; đường gửi chat là đường `sendToCharacterIfOnline` đã kiểm sống ở các lô trước.
