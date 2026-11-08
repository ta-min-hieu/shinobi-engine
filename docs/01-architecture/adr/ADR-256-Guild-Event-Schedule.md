# ADR-256: Lịch hoạt động bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Lịch hoạt động bang" trong lượt hỏi lô 41 (AskUserQuestion; mô tả kèm: bang chủ/phó hẹn giờ hoạt động (giờ + nội dung), cả bang được nhắc trước 10 phút). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-184](ADR-184-Guild-Vice-Leader.md) · [ADR-248](ADR-248-Guild-Poll.md) · [ADR-252](ADR-252-Daily-Reminder.md) (cùng khuôn việc nền) |

---

## Decision

**D1 — Bảng** `guild_event(guild_id, created_by, title ≤ 60, starts_at, reminded, created_at)` (V170). Bang chủ / phó hẹn
một buổi trong **1 phút – 7 ngày** tới (lọc từ cấm như chat), mỗi bang tối đa **5 buổi sắp tới** (khoá dòng bang khi
tạo để không vượt trần), sĩ quan huỷ được. Mọi thành viên xem danh sách.

**D2 — Nhắc:** việc nền mỗi phút (`GuildEventReminder`) tìm buổi bắt đầu trong **10 phút** tới chưa nhắc, gửi một dòng
chat hệ thống song ngữ cho mọi thành viên đang online rồi đặt `reminded` — mỗi buổi nhắc đúng một lần, kể cả server
khởi động lại (cờ ở DB). Buổi đã qua giờ tự rơi khỏi danh sách (không xoá dòng — giữ làm dấu vết).

**D3 — Protocol** 776–781 (dải Guild): tạo `{title, starts_at_epoch_seconds}` / `{outcome}`, huỷ `{event_id}` /
`{outcome}`, liệt kê `{}` / `{repeated GuildEventView}`. Client `/lichbang` xem; `/lichbang them <HH:mm> <nội dung>`
hẹn theo **giờ máy mình** (đã qua thì là ngày mai — client đổi sang epoch, server không cần biết múi giờ);
`/lichbang huy <số>` theo danh sách vừa xem.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | V170 `guild_event`; sĩ quan hẹn 1 phút–7 ngày, tối đa 5 buổi; nhắc 10 phút trước một lần (cờ DB); 776–781; `/lichbang` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 41): `GuildEventPort`/`GuildEventFacade`, `PostgresGuildEventAdapter`, `GuildEventReminder`, `Lot41Commands`, `GuildEventCoordinator`.
2. ✅ Test: `GuildEventFacadeTest`, `GuildEventReminderTest`, `Lot41IntegrationTest.officersScheduleCancelAndRemindersFireOnce`, `Lot41CoordinatorsTest` (HH:mm hôm nay/ngày mai).
3. ✅ Kiểm sống gameProbe 2026-11-08: ĐạoHữu01 hẹn "Đi bí cảnh" sau 30 phút ⇒ `OK`, giờ đã qua ⇒ `INVALID`; BaoDo01 thấy lịch (+29 phút), tự hẹn ⇒ `NOT_OFFICER`. Hẹn "Nhắc thử" sau 5 phút ⇒ ~14 giây sau nhận chat `Lịch bang: "Nhắc thử" bắt đầu sau 5 phút`, cột `reminded = true`. Fuzz opcode gateway bắt được lỗi giờ hẹn khổng lồ (`Instant.ofEpochSecond` ném ⇒ 500) — đã chặn trước thành `INVALID`. Dữ liệu thử đã xoá.
