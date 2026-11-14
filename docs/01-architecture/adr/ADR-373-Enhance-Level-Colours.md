# ADR-373: Màu đồ theo cường hoá

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Màu đồ theo cường hoá" trong lượt hỏi lô 55 (AskUserQuestion; mô tả kèm: tên món trong túi được tô màu theo mức cường hoá (+1–+4, +5–+9, +10 trở lên)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-324](ADR-324-Bag-Filter.md) · [ADR-326](ADR-326-New-Item-Badge.md) |

---

## Decision

**D1 — Màu:** +1–+4 xanh lá, +5–+9 xanh dương, +10 trở lên vàng cam; chưa cường hoá giữ màu cũ. Mức đọc từ nhãn "+N" cuối cùng của dòng (nhãn do `InventoryItemText` dựng từ số server gửi), nên không phải đổi `InventoryCoordinator` (đang ở trần PMD). Màu đặt lúc dựng danh sách, không mỗi khung.

**D2 — Thuần client**, không công tắc.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Màu theo +N đọc từ nhãn dòng túi; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 55): `InventoryPanel.enhanceColor`/`enhanceLevelOf`.
2. ✅ Test: `Lot55HudTest.readsEnhanceLevel`.
3. ⏳ Thuần client — cần chạy client thật.
