# ADR-307: Chế độ mù màu

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Chế độ mù màu" trong lượt hỏi lô 47 (AskUserQuestion 2026-11-12; mô tả kèm: đổi bảng màu số sát thương/thanh máu/kênh chat sang tông dễ phân biệt cho người mù màu đỏ-lục). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-143](ADR-143-Display-Settings.md) |

---

## Decision

**D1 — Thuần client, một dòng cài đặt hiển thị** (`COLORBLIND`, mặc định TẮT). Bật ⇒ số sát thương PHẢI CHỊU đổi đỏ →
**xanh dương** (`#4AA3FF`), tách hẳn vàng của sát thương gây ra — cặp vàng/xanh phân biệt được với mù màu đỏ-lục.

**D2 — Thu hẹp so với mô tả lúc hỏi:** mô tả nêu cả thanh máu và kênh chat; lượt này CHỈ đổi số sát thương (chỗ đỏ/lục
gây nhầm nặng nhất khi đánh nhau). Thanh máu và màu kênh chat để lượt sau nếu chủ dự án muốn.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Dòng cài đặt `COLORBLIND`: số sát thương phải chịu đổi xanh dương | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 47): `DisplaySettings.Option.COLORBLIND`, `CombatFeedbackPresenter.useColorblind`.
2. ✅ Test: biên dịch + kiểm tay (UI).
3. ⏳ Kiểm sống gameProbe.
