# ADR-429: Tô sáng từ khoá theo dõi

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Tô sáng từ khoá theo dõi" trong lượt hỏi lô 62 (AskUserQuestion; mô tả kèm: /theodoitu <từ>: tin có từ đó được tô màu nổi bật (khác lọc — không ẩn)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-245](ADR-245-Mention-Highlight.md) · [ADR-249](ADR-249-Chat-Keyword-Filter.md) |

---

## Decision

**D1:** tối đa 10 từ, so khớp "chứa" không phân biệt hoa thường; dòng khớp dùng màu nhắc tên (ADR-245). Khác lọc từ khoá (ADR-249) — lọc thì ẩn, theo dõi thì làm nổi.

**D2:** `/theodoitu <từ>` (`/watchword`) thêm/bỏ (gõ lại để bỏ), bỏ trống ⇒ liệt kê; nhớ trên máy. Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, ≤ 10 từ, màu nhắc tên | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 62): `ChatLensCoordinator.watched/contains`, `ChatCoordinator.useHighlight`.
2. ✅ Test: `Lot62ClientTest.matchesWatchWordsIgnoringCase`.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
