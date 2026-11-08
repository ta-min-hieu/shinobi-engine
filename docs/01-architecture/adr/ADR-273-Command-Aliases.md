# ADR-273: Biệt danh lệnh /alias

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Biệt danh lệnh /alias" trong lượt hỏi lô 42–43 (AskUserQuestion; mô tả kèm: Tự đặt lệnh tắt, ví dụ /a = /khobang (tối đa 20)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-267](ADR-267-Command-Help.md) |

---

## Decision

**D1 — Thuần client.** `/alias <tắt> <lệnh>` (hoặc `/bietdanh`), ví dụ `/alias a /khobang`; `/alias` liệt kê;
`/alias xoa <tắt>`. Tên tắt `[a-z0-9]{1,12}`, đích bắt đầu bằng `/`, ≤ 60 ký tự, không trỏ vào chính `/alias`. Tối đa
20, lưu Preferences.

**D2 — Viết lại một lần, trước mọi xử lý.** `ChatCoordinator.useLineRewriter`: dòng `/<tắt> phần còn lại` thành
`<lệnh> phần còn lại` rồi mới tới các bộ lệnh client / gửi server. Viết lại MỘT lần nên biệt danh không thể lặp vô hạn.
Biệt danh trùng tên lệnh sẵn có thì che lệnh đó (người chơi tự chọn; xoá biệt danh là lấy lại).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Preferences, 20 biệt danh; viết lại một lần trước xử lý | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 43): `CommandAliasCoordinator`, `ChatCoordinator.useLineRewriter`.
2. ✅ Test: `Lot43ClientTest.aliasExpandsOnce`.
