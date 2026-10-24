# ADR-097: Phân giải pháp khí

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Phân giải pháp khí" trong lượt hỏi lô 21 (AskUserQuestion; mô tả kèm: đập pháp khí không dùng ⇒ linh thạch + Linh Thảo (nguyên liệu luyện đan), theo cấp cường hoá; opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-079](ADR-079-Alchemy-Crafting.md) (Linh Thảo là nguyên liệu lò đan) · [ADR-064](ADR-064-Spirit-Jade-Sockets.md) · DATABASE.md §5.1 (khoá túi đồ) |

---

## Decision

**D1 — Món phân giải được**: pháp khí mặc được ở 7 ô chiến đấu (vũ khí, giáp, mũ, găng, giày, hai phụ kiện) — KHÔNG
phi kiếm, KHÔNG linh thú. Từ chối: đang mặc, đang khảm Linh Ngọc (gỡ trước — không phá ngọc), đang chào trong giao dịch.

**D2 — Thu về**: **20 × cấp yêu cầu + 100 × cấp cường hoá** linh thạch và **1 + cấp cường hoá / 3** Linh Thảo
(`spirit_herb`). Ví dụ trường kiếm Lv1 +7: 720 linh thạch + 3 Linh Thảo. Số là PLACEHOLDER cân bằng.

**D3 — Nguyên tử**: một giao dịch lấy khoá túi đồ chung (`CharacterInventoryLock`, cùng khoá mọi đường ghi túi), khoá dòng
món, kiểm D1, xoá món, cộng Linh Thảo (gộp chồng, không thì mở ô — luôn vừa vì món vừa xoá trả lại một ô), cộng vàng qua
GoldLedger (`gold.reason.dismantle`), audit `ITEM_DISMANTLED`. Gửi lại lệnh cho món đã phân giải ⇒ `NOT_FOUND` — không
cần khoá chống trùng.

**D4 — Giao thức, chỉ thêm**: 522/523 `DismantleItemCommand{item_id}` / `DismantleItemResponse{outcome, gold,
herb_quantity}`.

**D5 — Client**: nút "Phân giải" trong túi đồ (I), cạnh nút Bán, cho món đang chọn.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Bảng thu về trong DB theo từng mẫu | 26 pháp khí, một công thức đủ; chuyển DB khi có nguyên liệu riêng từng loại |
| Trả lại nguyên liệu cường hoá | Cường hoá chỉ tốn vàng + Bảo Vệ; trả vàng theo cấp là đủ "theo cấp cường hoá" |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 7 ô chiến đấu, 20×cấp + 100×cường hoá vàng, 1 + cường hoá/3 Linh Thảo, một giao dịch dưới khoá túi | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `DismantlePolicy` · `PostgresDismantleAdapter` · `InventoryCoordinator` (client)

## Việc cần làm tiếp theo

1. ⏳ Thi hành (lô 21 phần 2).
2. ⏳ Kiểm sống bằng gameProbe.
