# ADR-414: Linh thạch phiên trên HUD

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Linh thạch phiên trên HUD" trong lượt hỏi lô 60 (AskUserQuestion; mô tả kèm: /vanghud: dòng HUD hiện linh thạch đã kiếm được trong phiên chơi này). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-379](ADR-379-Gold-Gain-Floating-Text.md) · [ADR-271](ADR-271-Session-Summary.md) |

---

## Decision

**D1:** cộng `delta` dương của mọi `GoldChangedEvent` từ lúc vào game (tiêu không trừ — đây là "đã kiếm", không phải số dư).

**D2:** `/vanghud` (`/goldhud`), nhớ trên máy, mặc định tắt. Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, cộng delta dương | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 60): `HudStatsCoordinator` (dòng `SESSION_GOLD`).
2. ✅ Test: kiểm bằng mắt.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
