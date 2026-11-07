# ADR-189: Lịch sử giao dịch

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Lịch sử giao dịch" trong lượt hỏi lô 32 (AskUserQuestion; mô tả kèm: xem các lần giao dịch trực tiếp với người khác (30 ngày): đưa gì, nhận gì, với ai). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-075](ADR-075-Trade-Offer-Version.md) / sửa audit giao dịch 2026-10-03 (`ConfirmTradeUseCase.logAudit`) · [ADR-177](ADR-177-Market-History.md) (cùng khuôn với lịch sử phường thị) |

---

## Decision

**D1 — Nguồn có sẵn**: mỗi lần giao dịch thành công đã ghi CHO MỖI BÊN một dòng `audit_log` `TRADE_EXECUTED` (người kia,
vàng đưa / nhận, món đưa / nhận kèm mẫu + số lượng + cường hoá — sửa 2026-10-03). Phiên giao dịch bị xoá ngay sau đó nên
đây là dấu vết duy nhất ⇒ lịch sử ĐỌC từ audit, KHÔNG thêm bảng. V149 chỉ thêm chỉ mục một phần
`(character_id, created_at DESC) WHERE action = 'TRADE_EXECUTED'`.

**D2 — Đọc**: 30 lần gần nhất trong 30 ngày, mới nhất trước; JSON đọc bằng toán tử `jsonb` của Postgres (không thêm thư
viện JSON phía Java); tên người kia là tên HIỆN TẠI.

**D3 — Giao thức, chỉ thêm**: `GetTradeHistoryRequest{}` / `GetTradeHistoryResponse{entries}` 1415/1416 (người hỏi từ
session).

**D4 — Client**: cửa sổ `/lichsugd` (hoặc `/tradehistory`) — "dd/MM với X: đưa … — nhận …".

**Ràng buộc mới**: định dạng JSON của dòng audit `TRADE_EXECUTED` nay là hợp đồng đọc — đổi khoá nào ở
`ConfirmTradeUseCase.auditDetail/itemsJson` thì phải đổi `PostgresTradeHistoryAdapter` (test Postgres đóng đinh định dạng).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Đọc audit `TRADE_EXECUTED` (chỉ mục V149), 30 lần / 30 ngày, 1415/1416, `/lichsugd` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 32): V149, `TradeHistoryPort`/`TradeHistoryFacade`, `PostgresTradeHistoryAdapter`,
   `Lot32Commands.tradeHistory` 1415/1416, `TradeHistoryCoordinator` `/lichsugd`.
2. ✅ Test: `TradeHistoryIntegrationTest` (Postgres thật, đúng định dạng JSON audit), `TradeHistoryCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-07 (bước `tradeHistory`): probe không tự chạy trọn một giao dịch hai bên xác nhận, nên
   đặt tạm một dòng audit `TRADE_EXECUTED` đúng định dạng `ConfirmTradeUseCase` ghi (ĐạoHữu01 đưa 300 vàng, nhận 2 đan từ
   BaoDo01) rồi đọc qua server thật ⇒ "với BaoDo01 đưa 0 món + 300, nhận 1 món"; BaoDo01 ⇒ rỗng (dòng audit là của từng
   bên). Dòng thử đã xoá khỏi DB dev sau khi đo.
