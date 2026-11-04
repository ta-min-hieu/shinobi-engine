# ADR-168: Tháo Linh Ngọc

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-04) — chủ dự án chọn "Tháo Linh Ngọc" trong lượt hỏi lô 30 (AskUserQuestion; mô tả kèm: gỡ viên ngọc đang khảm trên trang bị trả về túi (tốn vàng), để đổi ngọc/bán đồ mà không mất ngọc). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-04 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-064](ADR-064-Spirit-Jade-Sockets.md) (khảm — D5 "bán đồ thì ngọc mất cùng món", nay có đường tháo trước) · [ADR-161](ADR-161-Gem-Combine.md) |

---

## Decision

**D1 — Tháo**: gỡ dòng `item_socket` của một trang bị của CHÍNH người gửi, trả đúng viên ngọc đó về túi (gộp chồng hoặc mở
dòng), phí **500 vàng** (khảm 200 — đổi ngọc là lựa chọn có giá). PLACEHOLDER (Điều 27). 100% thành công, không random.
Không đổi schema.

**D2 — Một transaction**: khoá túi (`CharacterInventoryLock`) → khoá dòng ô khảm → trừ phí (`GoldLedger`,
`gold.reason.unsocket_gem`) → xoá ô khảm → thêm ngọc vào túi (túi đầy ⇒ rollback, ngọc vẫn trên món) → audit
`GEM_UNSOCKETED`. Món của người khác ⇒ `NOT_FOUND`; món không khảm ⇒ `NOT_SOCKETED`.

**D3 — Chỉ số**: món đang mặc mất ngọc ⇒ gateway áp lại chỉ số trang bị như sau khi khảm
(`applyEquipmentBonusAndComputeMaxHp`) và trả `max_hp` mới.

**D4 — Giao thức, chỉ thêm**: `UnsocketGemCommand{item_id}` / `UnsocketGemResponse{outcome, gem_item_template_id, fee,
max_hp}` — 540/541.

**D5 — Client**: trong cửa sổ khảm (phím V), dòng trang bị ĐANG CHỌN có ngọc hiện nút **Tháo ngọc**; kết quả một dòng hệ
thống (`GemRemovalCoordinator`), cập nhật máu tối đa, hỏi lại túi.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Tháo ngọc về túi, 500 vàng, một transaction, áp lại chỉ số món đang mặc; 540/541 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-04 (lô 30): `GemRemovalPort`/`GemRemovalFacade`, `PostgresGemRemovalAdapter`, `Lot30Facades`,
   `Lot30Commands.unsocketGem` 540/541, nút Tháo ngọc, `GemRemovalCoordinator`.
2. ✅ Test Postgres thật: `GemRemovalIntegrationTest` (tháo đúng ngọc + trừ phí + lần hai NOT_SOCKETED; thiếu vàng /
   món người khác không đổi gì).
3. ✅ Kiểm sống gameProbe 2026-11-04: kiếm đang mặc khảm Mộc Linh Ngọc (máu 183/183) ⇒ `unsocket:5` OK, ngọc=38, phí 500,
   máu tối đa 168; lần hai và món không khảm ⇒ `NOT_SOCKETED`.
4. Chờ chủ dự án: phí 500 vàng (PLACEHOLDER).
