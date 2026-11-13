# ADR-343: Tra tên cũ người chơi

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Tra tên cũ người chơi" trong lượt hỏi lô 52 (AskUserQuestion; mô tả kèm: /tencu <tên>: xem các tên trước đây của một người (từ lịch sử Dịch Danh Phù, hiện chỉ GM tra được)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-148](ADR-148-Rename-Talisman.md) |

---

## Decision

**D1 — Nguồn:** bảng `character_name_history` có sẵn từ ADR-148 (mỗi lần dùng Dịch Danh Phù một dòng). Tra theo tên HIỆN TẠI, hoặc theo một tên CŨ (không phân biệt hoa thường; người mang gần nhất) — chỉ mục `lower(old_name)` V178.

**D2 — Trả:** tên hiện tại + các tên cũ kèm ngày đổi, mới trước. Thông tin công khai như tên — không lộ id hay tài khoản.

**D3 — Dây:** `GetNameHistoryRequest{name}` / `GetNameHistoryResponse{found, current_name, repeated OldName}` **283/284**. Client `/tencu <tên>` (`/oldnames`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Đọc `character_name_history` theo tên hiện tại hoặc tên cũ; 283/284; chỉ mục V178 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 52): `NameHistoryPort`/`Facade`, `PostgresNameHistoryAdapter`, `Lot52Commands.nameHistory`, `NameHistoryCoordinator`.
2. ✅ Test: `Lot52IntegrationTest.nameHistoryNewestFirstAndByOldName`.
3. LIVE343
