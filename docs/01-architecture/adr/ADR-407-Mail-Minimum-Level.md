# ADR-407: Cấp tối thiểu gửi thư

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Cấp tối thiểu gửi thư" trong lượt hỏi lô 60 (AskUserQuestion; mô tả kèm: Chống thư rác: nhân vật dưới cấp N (mặc định 5, cấu hình biến môi trường) không gửi thư cho người không phải bạn bè). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-368](ADR-368-World-Chat-Minimum-Level.md) · [ADR-212](ADR-212-Stranger-Mail-Block.md) |

---

## Decision

**D1:** gửi thư người-chơi-tới-người-chơi (`SendMailCommand`) mà người gửi dưới cấp `KAGE_MAIL_MIN_LEVEL` (mặc định **5**, `0` = tắt; cùng khuôn ADR-368) và người nhận KHÔNG phải bạn bè ⇒ từ chối, không tạo thư, không ký quỹ. Thư bạn bè / nhóm / bang / hệ thống không đổi. Kiểm sau chặn thư người lạ (ADR-212).

**D2 — Dây:** CHỈ THÊM `SEND_MAIL_OUTCOME_LEVEL_TOO_LOW = 11`; client báo "cần đạt cấp tối thiểu". Test gateway đặt biến = 0 để giữ hồ sơ test cũ. ⚠️ Đo sức chứa có gửi thư thì cũng đặt biến này = 0.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Outcome 11 chỉ thêm, `KAGE_MAIL_MIN_LEVEL` mặc định 5 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 60): `Lot60Commands.mayMail`, kiểm trong `GatewayChannelHandler` sau ADR-212, `Lot55Commands.parseMinLevel(raw, fallback, variable)`, `MailController` (bảng thông điệp chuyển sang `Map.ofEntries`).
2. ✅ Test: `Lot60ServerTest.parsesMailMinLevelWithOwnFallback`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14 (server mặc định cấp 5): PhuTanThu (cấp 1, không phải bạn) gửi thư cho BaoDo01 ⇒ `SEND_MAIL_OUTCOME_LEVEL_TOO_LOW`, không thư nào được tạo.
