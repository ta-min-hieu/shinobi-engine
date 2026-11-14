# ADR-425: Câu chào khi vào game

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Câu chào khi vào game" trong lượt hỏi lô 62 (AskUserQuestion; mô tả kèm: /cauchao <câu>: khi mình vào game, bạn bè đang online thấy câu chào đó). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-311](ADR-311-Friend-Presence-Chat-Line.md) · [ADR-335](ADR-335-Status-Message.md) |

---

## Decision

**D1 — Lưu:** bảng MỚI `login_greeting(character_id PK, message ≤ 60)` (V184). Làm sạch: một dòng, bỏ ký tự điều khiển, ≤ 60 ký tự, không từ cấm (không hợp lệ ⇒ từ chối, giữ câu cũ); rỗng = xoá.

**D2 — Phát:** mỗi lần vào world, nếu có câu chào và KHÔNG đang ẩn (ADR-383) ⇒ bạn bè online nhận dòng hệ thống `Tên: "câu chào"`. `SetLoginGreetingCommand{message, query_only}` / `SetLoginGreetingResponse{accepted, message}` **1166/1167**. Client `/cauchao <câu>|xoa` (`/greeting`), bỏ trống ⇒ hỏi.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `login_greeting` (V184), 1166/1167, tôn trọng chế độ ẩn | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 62): `LoginGreetingPort/Facade`, `PostgresLoginGreetingAdapter`, `Lot62Commands.setLoginGreeting/onEnteredWorld`, `SocialExtrasCoordinator`.
2. ✅ Test: `Lot62IntegrationTest.storesAndClearsGreeting`, `Lot62ServerTest.cleansGreeting`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14: ĐạoHữu01 đặt câu chào rồi vào game lại khi BaoDo01 (bạn bè) đang online ⇒ BaoDo01 nhận dòng `ĐạoHữu01: "chào chư vị đồng đạo"`. Đã xoá câu chào sau khi kiểm.
