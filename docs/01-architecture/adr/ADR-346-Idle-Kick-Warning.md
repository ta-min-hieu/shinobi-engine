# ADR-346: Cảnh báo sắp bị ngắt khi treo máy

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Cảnh báo sắp bị ngắt" trong lượt hỏi lô 52 (AskUserQuestion; mô tả kèm: treo máy còn 1 phút nữa là bị tự ngắt (ADR-214) thì server gửi trước một dòng cảnh báo). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-214](ADR-214-Idle-Disconnect.md) |

---

## Decision

**D1 — Khi nào:** cùng nhịp kiểm của `IdleKick` (ADR-214): đã im tới mốc `thời hạn − 1 phút` mà chưa báo ⇒ một dòng hệ thống "còn khoảng 1 phút nữa sẽ tự ngắt"; mọi gói của người chơi (trừ PING) xoá cờ đã báo. Nhịp kiểm ≤ 60 giây nên lời báo tới trong phút cuối.

**D2 — Thời hạn ngắn** (< 2 phút, chỉ dùng khi thử) thì không báo trước. Không opcode mới, không cấu hình mới.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cờ đã báo trong `IdleKick`, báo ở mốc thời hạn − 1 phút; không opcode | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 52): `IdleKick.isWarnDue`/`checkNow`/`notice`.
2. ✅ Test: `Lot52ServerTest.warnsOnceAMinuteBeforeKick`/`noWarningForVeryShortTimeouts`.
3. ⏳ Chưa kiểm sống (cần treo 29 phút với cấu hình mặc định); phủ bằng `Lot52ServerTest.warnsOnceAMinuteBeforeKick`.
