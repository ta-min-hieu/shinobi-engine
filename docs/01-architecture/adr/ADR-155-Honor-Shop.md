# ADR-155: Cửa hàng điểm vinh dự đấu trường

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-02) — chủ dự án chọn "Chợ đổi điểm danh vọng" trong lượt hỏi lô 28, rồi ở lượt hỏi làm rõ (vì cửa hàng danh vọng tông môn đã có ở ADR-109) chọn **"Cửa hàng điểm đấu trường"** (mô tả kèm: thắng trận đấu trường tích điểm vinh dự; NPC đổi vật phẩm hiếm bằng điểm đó, mỗi món giới hạn số lần/tuần). AI soạn văn bản và chọn chi tiết. |
| Date | 2026-11-02 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-087](ADR-087-Arena-Seasons.md) (mùa đấu trường, Elo) · [ADR-109](ADR-109-Sect-Reputation.md) (cửa hàng danh vọng — cùng khuôn earned/spent) |

---

## Decision

**D1 — Điểm (V126)**: `character_honor(earned, spent)` (như ADR-109). Mỗi trận đấu trường chốt xong: **thắng +10, thua
+3**; trần **100 điểm/ngày giờ Việt Nam** (`character_honor_day`, khoá dòng ngày khi cộng) — chặn hai người quen đánh
nhau cày điểm. Bước phụ sau `recordResultAndNotify` (đã ở Virtual Thread của việc chốt); lỗi không đụng kết quả/Elo.

**D2 — Cửa hàng**: `honor_shop_item(item_template_id, price, quantity, weekly_limit, sort_order)` — 5 món: Truyền Tống
Phù ×2 (20), Truyền Âm Phù (30), Tàng Bảo Đồ (40), Hồi Hồn Đan (50), Hộ Linh Phù (60); mỗi món trần lượt đổi/tuần
(thứ Hai giờ VN, `character_honor_purchase`). Đổi = một giao dịch: khoá túi → trần tuần → khoá dòng điểm → đủ điểm →
trừ + đếm lượt → thêm vật phẩm (túi đầy ⇒ rollback, không trừ) → audit `HONOR_SHOP_BUY`.

**D3 — Lệch mô tả lượt hỏi**: "ở một NPC riêng" — **chưa có NPC**: thêm NPC cần model 3D + chỗ đứng; bản này mở bằng lệnh
chat `/vinhdu` ở bất cứ đâu. Gắn vào NPC sau khi có art (một kiểm `NpcProximity`).

**D4 — Giao thức, chỉ thêm**: 1615/1616 `GetHonorShopRequest{}` / `Response{available, items[]}`, 1617/1618
`BuyHonorItemCommand{item_template_id}` / `Response{outcome, shop}`.

**D5 — Client**: lệnh chat `/vinhdu` (hoặc `/honor`) — điểm còn lại, mỗi món một dòng (giá, đã đổi/trần tuần) + nút
"Đổi".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | +10/+3 mỗi trận, trần 100/ngày, 5 món trần tuần, một giao dịch, mở bằng lệnh chat (chưa NPC) | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-02 (lô 28): V126, `HonorPort`/`HonorRules`/`HonorFacade` (feature-pvp), `PostgresHonorAdapter`,
   `HonorHooks` (sau chốt trận), `HonorCommands` (1615–1618), `HonorShopCoordinator` (`/vinhdu`), bước probe `honorShop`,
   `buyHonor:<templateId>`.
2. ✅ Kiểm sống gameProbe 2026-11-02 (server thật, DB chạy V126): trận đấu trường thật (thách → nhận → bỏ cuộc) ⇒ người
   thắng +10, người thua +3; 10 điểm ⇒ `NOT_ENOUGH_HONOR`; nạp điểm trên DB dev lên 100 ⇒ đổi Hộ Linh Phù (60) `OK`,
   Truyền Tống Phù ×2 (20) `OK`, còn 20 ⇒ `NOT_ENOUGH_HONOR`; 2 dòng audit `HONOR_SHOP_BUY`, lượt tuần đếm 1/3, 1/5.
3. NPC giữ cửa hàng (cần art) — xem D3.
