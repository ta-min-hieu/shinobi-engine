# ADR-149: Quẻ bói hằng ngày

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-02) — chủ dự án chọn "Quẻ bói hằng ngày" trong lượt hỏi lô 27 (AskUserQuestion; mô tả kèm: mỗi ngày xin một quẻ ở NPC (Đại Cát→Hung): buff/debuff nhỏ EXP/rơi đồ/vàng tới hết ngày). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-02 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-060](ADR-060-Daily-Check-In.md) (điểm danh — cùng module, cùng ngày giờ VN) · [ADR-067](ADR-067-Double-Events.md) (chuỗi hệ số EXP đánh quái) |

---

## Decision

**D1 — Dữ liệu (V121)**: `fortune(code, name_key, text_key, weight, exp_percent, reward_gold, reward_item_template_id,
reward_quantity)` — năm quẻ: **Đại Cát** (8, +20% EXP, 1 Bùa Hộ Linh), **Trung Cát** (20, +10%, 300 linh thạch),
**Tiểu Cát** (32, +5%, 100), **Bình** (25, 0), **Hung** (15, −10%). `character_fortune(character_id, day, fortune_id,
drawn_at)`, `PRIMARY KEY (character_id, day)`. Số là PLACEHOLDER (Điều 27).

**D2 — Gieo quẻ**: lệnh `/xinque` khi đứng trong tầm tương tác của trưởng lão Chu Hoành (`town_elder`, đường
`NpcProximity` như cửa hàng). MỘT giao dịch dưới khoá túi: đã gieo hôm nay (ngày giờ Việt Nam, như điểm danh) ⇒
`ALREADY_DRAWN` kèm lại quẻ cũ, không quà lần hai; chưa ⇒ server tung theo trọng số, cấp quà (túi đầy ⇒ `BAG_FULL`,
rollback, **chưa tính là đã gieo**), cộng linh thạch `gold.reason.fortune`, ghi `character_fortune` + audit
`FORTUNE_DRAW`.

**D3 — Hiệu lực**: `exp_percent` nhân vào EXP **đánh quái** của người đó tới hết ngày, là mắt CUỐI của chuỗi hệ số
gateway (sau sự kiện x2, kỹ năng bang, đạo lữ, kết nghĩa — ADR-067/085/094/110), làm tròn xuống, không âm. Thưởng
nhiệm vụ không nhân (như ADR-067). Phần trăm đọc ở mỗi lượt thưởng nên `FortuneFacade` nhớ theo ngày: lượt đầu trong ngày
hỏi DB một lần, gieo xong ghi đè ngay.

**D4 — Lệch mô tả lượt hỏi**: mô tả có "rơi đồ/vàng". **Bản này chỉ có EXP + quà kèm quẻ.** Tỉ lệ rơi đồ hiện là MỘT
hệ số chung cả server (`GrantLootUseCase.withRollCount(IntSupplier)` — không biết ai hạ quái); cho nó theo từng người là
đổi API của feature-inventory, và quái chưa rơi linh thạch nên "vàng theo quẻ" không có chỗ để nhân. Quà linh thạch/vật
phẩm lúc gieo thay cho phần đó. Muốn rơi đồ theo quẻ ⇒ ADR riêng.

**D5 — Giao thức, chỉ thêm**: 230/231 `DrawFortuneCommand{}` / `DrawFortuneResponse{outcome, fortune_code, name_key,
text_key, exp_percent, gold, item_template_id, quantity}`. `protocolVersion` không tăng.

**D6 — Client**: `/xinque` (hoặc `/fortune`) — một dòng hệ thống: tên quẻ, lời trưởng lão, ±% EXP, quà. Chữ do AI
viết theo bối cảnh tu tiên, **chủ dự án duyệt lại** (STORY §8).

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| NPC thầy bói mới | Cần `npc_template` + model 3D + chỗ đứng; trưởng lão Chu Hoành đã ở Tông Môn và hợp vai |
| Quẻ hiệu lực theo giờ (vd 2 giờ) | "Tới hết ngày" dễ hiểu, khớp mốc reset điểm danh |
| Đọc DB mỗi lượt hạ quái | Hạ quái là đường nóng nhất của phần thưởng; một lượt đọc/ngày/người là đủ |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Năm quẻ theo trọng số, một quẻ/ngày giờ VN ở trưởng lão, ±% EXP đánh quái tới hết ngày, quà cấp trong cùng giao dịch | Accepted |

## ADR cần tạo

- (Tuỳ chủ dự án) Rơi đồ theo từng người — xem D4.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-02 (lô 27): V121, `FortunePort`/`FortuneRules`/`FortuneFacade` (feature-checkin),
   `PostgresFortuneAdapter`, `FortuneCommands` (230/231), mắt cuối chuỗi EXP ở `GatewayBootstrap`,
   `FortuneCoordinator` (`/xinque`), bước probe `fortune`.
2. ✅ Kiểm sống gameProbe 2026-11-02 (server thật, TLS, DB chạy V121): `ĐạoHữu01` đứng ở Tông Môn `fortune` ⇒ `OK`
   Tiểu Cát +5% kèm `GOLD_CHANGED_EVENT` +100 `gold.reason.fortune`; gieo lại ⇒ `ALREADY_DRAWN` cùng quẻ, không quà;
   `BaoDo01` đứng X=96 ⇒ `TOO_FAR`. Hạ mộc nhân (EXP 20) cùng nhóm: người có quẻ +5% nhận **21**, đồng đội không quẻ
   nhận 20 — mắt cuối chuỗi EXP chạy thật.
