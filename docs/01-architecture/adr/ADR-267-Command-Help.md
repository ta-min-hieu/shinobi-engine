# ADR-267: Danh sách lệnh /trogiup

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Danh sách lệnh /trogiup" trong lượt hỏi lô 42–43 (AskUserQuestion; mô tả kèm: In toàn bộ lệnh chat, lọc theo từ khoá). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | — |

---

## Decision

**D1 — Thuần client.** `/trogiup [từ khoá]` (hoặc `/help`) in các nhóm lệnh (Trò chuyện, Biểu cảm, Bạn bè, Nhóm, Bang hội,
Túi đồ, Thư, Phường thị, Nhiệm vụ, Thông tin, Tiện ích); có từ khoá thì chỉ in nhóm có tên khớp hoặc lệnh chứa từ khoá
(so không dấu). Bảng tĩnh `CommandHelpCoordinator.GROUPS`; test giữ bảng không trùng lệnh. Lệnh mới phải thêm vào bảng.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | bảng tĩnh 11 nhóm, lọc không dấu | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 42): `CommandHelpCoordinator`.
2. ✅ Test: `Lot42ClientTest.helpTableHasNoDuplicateCommand`, `helpFiltersByCommandOrGroupTitle`.
