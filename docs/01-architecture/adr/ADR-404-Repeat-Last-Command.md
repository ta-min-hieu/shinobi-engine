# ADR-404: Lặp lại lệnh vừa gõ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Lặp lại lệnh vừa gõ" trong lượt hỏi lô 59 (AskUserQuestion; mô tả kèm: /. chạy lại lệnh chat (bắt đầu bằng /) gõ gần nhất). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-262](ADR-262-Chat-Line-Recall.md) · [ADR-269](ADR-269-Ping-Command.md) |

---

## Decision

**D1:** gõ `/.` ⇒ chạy lại lệnh gạch chéo gần nhất (sau khi đã mở bí danh ADR-269); chưa có lệnh nào ⇒ báo một dòng. Chỉ nhớ lệnh bắt đầu bằng `/`, không nhớ tin chat thường; không nhớ chính `/.`.

**D2:** bộ đổi dòng của ô nhập (`useLineRewriter`) nay NỐI TIẾP thay vì thay thế để bí danh và lặp lệnh cùng chạy. Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, bộ đổi dòng nối tiếp | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 59): `ClientToolsCoordinator.repeat`, `ChatCoordinator.useLineRewriter` (nối tiếp).
2. ✅ Test: kiểm bằng mắt (lớp cần khung chat thật); đường nối tiếp bộ đổi dòng dùng chung với bí danh.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
