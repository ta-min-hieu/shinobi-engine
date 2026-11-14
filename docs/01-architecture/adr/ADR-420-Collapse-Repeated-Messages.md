# ADR-420: Gộp tin trùng lặp

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Gộp tin trùng lặp" trong lượt hỏi lô 61 (AskUserQuestion; mô tả kèm: Cùng một người gửi lại đúng câu vừa nói ⇒ gộp thành một dòng kèm "(x3)"). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-348](ADR-348-Caps-Calming.md) · [ADR-249](ADR-249-Chat-Keyword-Filter.md) |

---

## Decision

**D1:** tin mới cùng kênh, cùng người gửi, cùng nội dung với DÒNG CUỐI nhật ký ⇒ thay dòng cuối bằng `nội dung (xN)` thay vì thêm dòng mới; một dòng khác (kể cả dòng hệ thống) chen giữa thì đếm lại. Chống spam lặp chiếm khung chat.

**D2:** `/gopchat` (`/collapsechat`), nhớ trên máy, mặc định **bật**. Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, gộp với dòng cuối | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 61): `ChatController.setCollapseRepeats/isRepeat/repeated`, `ChatTidyCoordinator`.
2. ✅ Test: `Lot61ClientTest.collapsesConsecutiveRepeats/keepsRepeatsWhenOff`.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
