# ADR-380: Tab tự hoàn thành lệnh chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Tab tự hoàn thành lệnh" trong lượt hỏi lô 56 (AskUserQuestion; mô tả kèm: Đang gõ /tên-lệnh dở, bấm Tab để điền nốt). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-267](ADR-267-Command-Help.md) · [ADR-262](ADR-262-Chat-Line-Recall.md) |

---

## Decision

**D1:** chỉ hoàn thành TÊN lệnh (chữ bắt đầu bằng "/" và chưa có dấu cách). Một lệnh khớp ⇒ điền đủ kèm dấu cách; nhiều lệnh ⇒ điền phần chung dài nhất; không thêm được chữ nào ⇒ in tối đa 8 lệnh khớp kèm tổng số. Nguồn lệnh là bảng `CommandHelpCoordinator.GROUPS` (cùng `/trogiup`, ADR-267).

**D2:** ô nhập tắt focus traversal (`setFocusTraversal(false)`) để Tab không nhảy ô; phím xử lý ở `keyDown` giống gọi lại tin đã gõ (ADR-262). Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, Tab trong ô chat, nguồn `GROUPS` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 56): `ChatCompletionCoordinator`, `ChatPanel.setCompleter`.
2. ✅ Test: `Lot56ClientTest` (khớp duy nhất, phần chung, chỉ tên lệnh).
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
