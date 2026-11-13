# ADR-357: Bảng phím tắt F1

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Bảng phím tắt F1" trong lượt hỏi lô 53 (AskUserQuestion; mô tả kèm: giữ F1 hiện bảng tóm tắt toàn bộ phím tắt đang gán). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-190](ADR-190-Key-Rebinding.md) |

---

## Decision

**D1 — Hiện:** giữ F1 ⇒ một khung giữa màn hình liệt kê mọi phím đang gán (đúng nội dung `/phimtat`, ADR-190, kể cả phím đã đổi); nhả F1 ⇒ ẩn. Chữ chỉ dựng lúc vừa nhấn, không mỗi khung. F1 không thuộc bảng phím đổi được.

**D2 — Thuần client**, nằm trong `CombatHud` (có sẵn skin và stage).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Giữ F1 hiện danh sách phím đang gán; dựng chữ lúc nhấn; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 53): `CombatHud.useKeyHelp`/`updateKeyHelp`, `Lot53Windows.keyHelpText`.
2. ✅ Test: nội dung là `KeyBindingsCoordinator.list` đã có test; phần nhấn phím kiểm bằng client thật.
3. ⏳ Thuần client — cần chạy client thật để giữ F1.
