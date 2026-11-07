# ADR-199: So sánh trang bị

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "So sánh trang bị" trong lượt hỏi lô 34 (AskUserQuestion; mô tả kèm: xem món trong túi thì hiện chỉ số chênh lệch +/- so với món đang mặc cùng ô). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) (chỉ số + hệ số cường hoá) · [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) (catalog vật phẩm) |

---

## Bối cảnh

Ô chi tiết túi đồ chỉ hiện chỉ số GỐC, và ghi rõ vì sao: hệ số cường hoá sống ở server, không gói nào gửi sang, nên client
tự nhân là bịa (Điều 24). So sánh đúng nghĩa phải dùng chỉ số HIỆU DỤNG (một thanh kiếm +3 có thể mạnh hơn thanh mới
chưa cường hoá) — nên cần chính hệ số đó.

## Decision

**D1 — Server gửi hệ số**: `ItemCatalogResponse.enhance_step_percent = 4` = `EnhancementTable.ENHANCE_STEP_PERCENT`
(hiện 10). Gửi CẢ khi `unchanged = true`. 0 = server cũ ⇒ client KHÔNG so sánh (không đoán).

**D2 — Client tính để hiển thị**: cùng công thức số nguyên với `EnhancementTable.enhancedBonus` (làm tròn nửa lên),
test chốt `25 × (1 + 10% × 13) = 58`. Server vẫn là bên tính chỉ số thật; đây chỉ là chữ trong ô chi tiết.

**D3 — Hiện gì**: chọn một món mặc được (không phải chính món đang mặc) ⇒ thêm dòng "So với đang mặc: Công +3, Thủ -1"
(chỉ các chỉ số có đổi; "không đổi chỉ số" nếu bằng nhau); ô đang trống ⇒ "Ô này đang trống, mặc vào được: …".

**D4 — Không opcode, không DB.** Chỉ thêm một field; `protocolVersion` không tăng.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Hệ số cường hoá đi kèm catalog (field 4), client so chỉ số hiệu dụng với món đang mặc cùng ô | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 34): `EnhancementTable.ENHANCE_STEP_PERCENT`, field 4 ở handler catalog,
   `ItemCatalog.enhanceStepPercent`, `InventoryItemText.Comparison`.
2. ✅ Test: `InventoryItemTextComparisonTest` (công thức khớp server; chênh lệch có dấu; im lặng khi chưa có hệ số).
3. ✅ Kiểm sống gameProbe 2026-11-07 (bước `catalogStep`): server trả `enhanceStepPercent=10` kèm 87 mẫu vật phẩm. Phần
   so sánh là chữ phía client — kiểm bằng `InventoryItemTextComparisonTest`, chưa xem bằng mắt trong cửa sổ túi đồ.
