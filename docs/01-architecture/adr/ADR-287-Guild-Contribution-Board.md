# ADR-287: Bảng cống hiến bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Bảng cống hiến bang" trong lượt hỏi lô 45 (AskUserQuestion; mô tả kèm: /conghien: xếp hạng thành viên bang theo điểm cống hiến). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-068](ADR-068-Guild-Level-And-Contribution.md) (điểm cống hiến) |

---

## Decision

**D1 — Không thêm opcode.** `ListGuildMembersResponse` (714/715) đã mang `contribution` của từng thành viên từ ADR-068.
Client `/conghien` (hoặc `/contrib`) hỏi lại danh sách đó, xếp giảm dần theo cống hiến (hoà ⇒ theo tên), in 10 người
đầu + hạng của chính mình. Cửa sổ bang vẫn nhận cùng gói trả lời như thường (coordinator chỉ in khi chính lệnh này hỏi).

**D2 — Lệch dự tính:** dự tính ban đầu là thêm truy vấn server; khi đọc code thấy dữ liệu đã có sẵn trên dây nên làm
thuần client — ít protocol hơn, cùng kết quả.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Dùng lại 714/715; xếp ở client; `/conghien` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 45): `GuildContributionCoordinator`.
2. ✅ Test: `Lot45ClientTest.ranksContributionDescendingThenByName`.
3. ⏳ Thuần client — probe headless không chạy lệnh gạch chéo; dữ liệu nguồn (714/715 kèm `contribution`) đã kiểm sống từ ADR-068.
