# ADR-382: Sao chép tên vào clipboard

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Sao chép tên vào clipboard" trong lượt hỏi lô 56 (AskUserQuestion; mô tả kèm: /saochep [tên]: chép tên vào clipboard; bỏ trống ⇒ người nhắn riêng gần nhất). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-247](ADR-247-Quick-Reply.md) |

---

## Decision

**D1:** `/saochep <tên>` (`/copyname`) chép đúng chữ gõ; bỏ trống ⇒ người KHÁC nhắn riêng gần nhất (nghe `ChatMessageEvent` kênh riêng như `/r`, ADR-247). Ghi qua `Gdx.app.getClipboard()`.

**D2:** không đổi protocol; chưa ai nhắn ⇒ in hướng dẫn.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, clipboard libGDX | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 56): `ChatToolsCoordinator`.
2. ✅ Test: kiểm bằng mắt.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
