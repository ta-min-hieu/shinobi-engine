# ADR-098: Hái linh thảo

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Hái linh thảo" trong lượt hỏi lô 21 (AskUserQuestion; mô tả kèm: điểm hái trên bản đồ (thực thể mới), đứng hái 3s ⇒ nguyên liệu luyện đan, hồi lại sau vài phút; migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-079](ADR-079-Alchemy-Crafting.md) · [ADR-090](ADR-090-Spirit-Vein-Contest.md) (vị trí world chính từ registry) · [ADR-046](ADR-046-Health-Recovery.md) |

---

## Decision

**D1 — Dữ liệu (V86)**: `herb_node(id, code, x, y, item_template_id, quantity, respawn_seconds)` là THAM SỐ (Điều 27).
Seed 4 bụi Linh Thảo dọc trục chính world chính, mỗi bụi 1–2 `spirit_herb`, hồi **180 giây**. Số là PLACEHOLDER.

**D2 — Thực thể = điểm dữ liệu, không phải thực thể ECS**: trạng thái "đang sẵn/đang hồi/đang có người hái" sống trong RAM
của gateway (`HerbNodes`), như bảng tìm nhóm (ADR-082). Mất khi khởi động lại = mọi bụi sẵn sàng — chấp nhận được. Không
đưa vào World/AoI ở bản đầu: không cần đồng bộ hình, client liệt kê bụi theo toạ độ.

**D3 — Hái 3 giây, server quyết**: lệnh hái kiểm (a) nhân vật đang ở world chính, (b) vị trí registry cách bụi
**≤ 2,5 m**, (c) bụi sẵn và không ai đang hái, (d) người này không đang hái bụi khác ⇒ giữ bụi, trả `STARTED`. Sau
**3 giây** server kiểm lại vị trí (đi khỏi ⇒ `MOVED_AWAY`, nhả bụi), cấp vật phẩm qua `InventoryFacade.addItem` (túi đầy
⇒ `BAG_FULL`, nhả bụi), đặt hồi, rồi gửi `HerbGatheredEvent`. Đếm giờ bằng một luồng hẹn giờ nền; cấp đồ trên Virtual
Thread.

**D4 — Giao thức, chỉ thêm**: 312/313 `ListHerbNodesRequest`/`Response{nodes[]}`, 314/315
`GatherHerbCommand{node_id}`/`Response{outcome}`, 316 `HerbGatheredEvent{node_id, outcome, item_template_id, quantity}`.

**D5 — Client**: tab "Linh thảo" của cửa sổ Hằng ngày (E) — mỗi bụi một dòng (toạ độ X, khoảng cách tới mình, sẵn/còn
bao lâu) kèm nút Hái; kết quả hiện chữ nổi + dòng chat. Hình bụi cỏ trên bản đồ để bản sau (cần art).

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Bụi là thực thể ECS trong World, đồng bộ qua AoI | Cần model/sprite, gói spawn/despawn mới; bản đầu không cần hình để chơi được |
| Client tự đếm 3 giây rồi gửi "hái xong" | Client quyết thời gian — server phải tự đếm (Điều 15) |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bụi = dòng DB + trạng thái RAM gateway, hái 3 giây server đếm, ≤2,5 m, hồi 180 giây, 312–316 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `V86__lot21_daily.sql` · `HerbNodes` · `HerbCommands` · `DailyCoordinator` (client)

## Việc cần làm tiếp theo

1. ⏳ Thi hành (lô 21 phần 2).
2. ⏳ Kiểm sống bằng gameProbe.
3. Bản sau: hình bụi trên bản đồ (cần art), thêm loại thảo dược.
