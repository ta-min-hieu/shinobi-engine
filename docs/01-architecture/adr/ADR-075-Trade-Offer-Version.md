# ADR-075: `offer_version` — xác nhận đúng lời chào mình đã thấy

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-23) — chủ dự án chọn "25(i)(a): offer_version" trong lượt hỏi lô 18 (AskUserQuestion; mô tả kèm: xác nhận mang version lời chào mình đã thấy, lệch thì từ chối; đổi protocol + migration; khoá 3 giây hiện có GIỮ). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-23 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [docs/README.md](../../README.md) dòng 25(i) · [GAMEPLAY.md](../../05-server/GAMEPLAY.md) §8 · [ADR-025](ADR-025-Idempotency.md) |

---

## Context

Sửa lời chào reset cả hai cờ xác nhận, nhưng nếu A sửa ngay trước khi B bấm thì lệnh của B (gửi trên màn hình
cũ) xác nhận bản MỚI B chưa thấy. Lô 11 đã chặn xác nhận 3 giây sau mỗi lần sửa (`TradeOfferChanges`) — vẫn còn
khe khi mạng trễ hơn 3 giây.

## Decision

**D1 — Cột `trade_session.offer_version BIGINT NOT NULL DEFAULT 1`** (V74). Mỗi lần một bên đặt lại lời chào
(`replaceOffer`) tăng 1 trong cùng transaction.

**D2 — Trên dây (chỉ thêm field)**: `GetTradeStatusResponse.offer_version = 13`;
`ConfirmTradeCommand.offer_version = 3`. Client gửi version của trạng thái nó đang HIỂN THỊ.

**D3 — Server so trong transaction thi hành**: `offer_version` của lệnh ≠ 0 và ≠ version hiện tại (đọc dưới khoá
`lockForExecution`) ⇒ từ chối bằng đường `OFFER_JUST_CHANGED` sẵn có (`ErrorResponse` 409
`error.trade_offer_just_changed`), cờ xác nhận không được đặt, khoá idempotency chưa ghi. `0` = client cũ ⇒ bỏ
qua phép so (chỉ còn khoá 3 giây).

**D4 — Khoá 3 giây GIỮ nguyên** (lô 11): hai lớp độc lập, khoá thời gian vẫn chặn client cũ.

## Consequences

- Khe "xác nhận bản chưa thấy" đóng hẳn với client mới, bất kể độ trễ mạng.
- Một migration nhỏ, không đổi dữ liệu cũ (DEFAULT 1).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Version tăng mỗi lần đặt lời chào, so dưới khoá thi hành | Accepted |
| 2 | `0` = client cũ, bỏ qua phép so | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [NETWORK.md](../../05-server/NETWORK.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-23 (lô 18) — V74, `ConfirmTradeUseCase` ném `StaleOfferException` trong transaction (rollback cả khoá chống trùng); test `ConfirmTradeUseCaseTest`, `TradeIntegrationTest`. ✅ **Kiểm sống 2026-10-23** (hai gameProbe, `--confirmTrade=stale,current` cùng MỘT khoá chống trùng): Kagemaru tự đặt lại lời chào (phiên bản 1 → 2), xác nhận bằng 1 ⇒ `error.trade_offer_just_changed (409)`; xác nhận bằng 2 với chính khoá đó ⇒ `CONFIRM_TRADE_OUTCOME_OK` (khoá không bị đốt). Ngắt kết nối ⇒ `trade_session` về 0 dòng.
