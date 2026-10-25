# ADR-109: Danh vọng tông môn

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-25) — chủ dự án chọn "Danh vọng tông môn" trong lượt hỏi lô 22 (AskUserQuestion; mô tả kèm: điểm danh vọng từ quest/truy nã/bang chiến; các bậc danh vọng mở món trong cửa hàng danh vọng; migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-25 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | ADR-055 (nhiệm vụ ngày) · ADR-101 (truy nã) · ADR-102 (bang chiến) · ADR-080 (cửa hàng bang — cùng khuôn tích luỹ − đã tiêu) |

---

## Decision

**D1 — Dữ liệu (V90)**: `character_reputation(character_id PK, earned, spent)`. Bậc tính theo `earned` (không bao giờ
tụt khi tiêu): 0 Vô Danh, 100 Sơ Hiển, 300 Hữu Danh, 600 Uy Chấn, 1000 Danh Trấn Tông Môn. Điểm tiêu được = `earned −
spent`.

**D2 — Nguồn**: lĩnh nhiệm vụ ngày +10, xong truy nã +5, thành viên bang thắng bang chiến +20 (lúc gửi thư thưởng).
Việc phụ — lỗi ghi không hỏng việc chính.

**D3 — Cửa hàng danh vọng**: `reputation_shop_item(item_template_id, price, required_tier, sort_order)` — trả bằng điểm
danh vọng, món chỉ mua được khi đủ bậc. Một giao dịch: khoá dòng danh vọng → đủ bậc, đủ điểm → `spent += giá` → thêm vật
phẩm (túi đầy ⇒ rollback). Seed 5 món theo bậc.

**D4 — Giao thức, chỉ thêm**: 621/622 `GetReputationRequest`/`Response{earned, available, tier, next_tier_at, items[]}`,
623/624 `BuyReputationItemCommand{item_template_id}`/`Response{outcome, status}`.

**D5 — Client**: tab "Danh vọng" trong cửa sổ Hằng ngày (E).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | earned/spent, 5 bậc theo earned, cửa hàng trả bằng điểm, tab trong cửa sổ E | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ⏳ Thi hành (lô 22 phần 2).
2. ⏳ Kiểm sống bằng gameProbe.
