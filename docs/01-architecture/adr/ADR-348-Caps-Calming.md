# ADR-348: Chống chữ hoa la hét

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Chống chữ hoa la hét" trong lượt hỏi lô 52 (AskUserQuestion; mô tả kèm: tin nhắn người khác viết HOA toàn bộ được hạ về chữ thường khi hiển thị (có công tắc)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-249](ADR-249-Chat-Keyword-Filter.md) · [ADR-091](ADR-091-Block-List.md) |

---

## Decision

**D1 — Luật:** tin của người chơi (không phải dòng hệ thống) mà mọi chữ cái đều in hoa và có ít nhất 5 chữ cái ⇒ hiện bằng chữ thường. Tin ngắn ("OK", viết tắt) để nguyên. Chỉ đổi cách HIỆN trên máy mình.

**D2 — Móc:** `ChatCoordinator.useEventRewriter` (bộ đổi tin trước khi hiện, mặc định giữ nguyên). Công tắc `/chuhoa` (`/caps`), mặc định bật, nhớ trên máy.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Hạ chữ thường khi toàn chữ hoa ≥ 5 chữ cái; móc `useEventRewriter`; `/chuhoa` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 52): `CapsCalmCoordinator`, `ChatCoordinator.useEventRewriter`.
2. ✅ Test: `Lot52ClientTest.detectsShouting`.
3. ⏳ Thuần client — cần chạy client thật.
