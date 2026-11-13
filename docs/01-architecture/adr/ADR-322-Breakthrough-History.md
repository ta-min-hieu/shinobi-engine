# ADR-322: Lịch sử đột phá cảnh giới

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Lịch sử đột phá cảnh giới" trong lượt hỏi lô 49 (AskUserQuestion; mô tả kèm: /dotpha: ngày đạt từng cảnh giới của mình (ghi từ bản cập nhật này trở đi)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-136](ADR-136-Realm-Breakthrough-Ceremony.md) · [ADR-182](ADR-182-Friend-Breakthrough-Notice.md) |

---

## Decision

**D1 —** Bộ nghe lên cấp ghi audit `BREAKTHROUGH {realm, level}` mỗi khi cấp mới mở đầu một cảnh giới (cùng hàm
`BreakthroughHooks.realmOpenedAt` của lễ đột phá ADR-136). V175 chỉ mục một phần. **Đột phá trước ADR này không có lịch sử.**

**D2 — Dây:** `GetBreakthroughHistoryRequest{}` / `GetBreakthroughHistoryResponse{repeated BreakthroughEntry{realm, level,
at_epoch_seconds}}` **277/278**. Client `/dotpha` (hoặc `/breakthroughs`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Audit `BREAKTHROUGH` khi mở cảnh giới; 277/278; `/dotpha` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 49): `BreakthroughLogPort`/`Facade`, `PostgresBreakthroughLogAdapter`, `Lot49Commands.breakthroughHistory`/`onLevelReached`, `BreakthroughHistoryCoordinator`.
2. ✅ Test: `Lot49IntegrationTest.recordsBreakthroughsOldestFirst`.
3. ✅ Kiểm sống gameProbe 2026-11-13 (`breakthroughs`): danh sách rỗng — đúng kỳ vọng (chỉ ghi từ ADR này). Đường ghi phủ bằng `Lot49IntegrationTest.recordsBreakthroughsOldestFirst`.
