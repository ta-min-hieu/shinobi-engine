# ADR-422: Câu nói nhanh

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Câu nói nhanh" trong lượt hỏi lô 61 (AskUserQuestion; mô tả kèm: /caunhanh <1-5> <câu> lưu câu; /cn <1-5> gửi ngay câu đã lưu vào kênh đang chọn). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-269](ADR-269-Ping-Command.md) · [ADR-404](ADR-404-Repeat-Last-Command.md) |

---

## Decision

**D1:** 5 ô câu nói nhanh nhớ trên máy. `/caunhanh <1-5> <câu>` (`/phrase`) lưu; `/cn <1-5>` được thay bằng câu đã lưu NGAY trong ô nhập (bộ đổi dòng nối tiếp ADR-404) rồi gửi vào kênh đang chọn như tin gõ tay — server kiểm như mọi tin. Ô trống ⇒ báo một dòng, không gửi.

**D2:** không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, 5 ô, bộ đổi dòng | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 61): `ChatTidyCoordinator.expandPhrase/phraseSlot`.
2. ✅ Test: `Lot61ClientTest.parsesPhraseSlot`.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
