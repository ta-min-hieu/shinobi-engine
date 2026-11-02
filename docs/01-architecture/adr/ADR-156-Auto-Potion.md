# ADR-156: Tự uống đan

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-02) — chủ dự án chọn "Tự uống đan" trong lượt hỏi lô 28 (AskUserQuestion; mô tả kèm: cài đặt trong /caidat: máu dưới X% thì server tự dùng Hồi Khí Đan trong túi (tôn trọng hồi chiêu 10 giây)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-02 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-046](ADR-046-Health-Recovery.md) D5 (đan dược, `UseItemCommand` 512/513, hồi chiêu) · [ADR-143](ADR-143-Display-Settings.md) (`/caidat`) |

---

## Decision

**D1 — Client gửi ý định, server vẫn quyết**: công tắc mới `AUTO_POTION` trong `/caidat`, **mặc định TẮT**. Bật thì
mỗi 0,5 giây client xét: còn sống, máu **dưới 35%** ⇒ gửi ĐÚNG `UseItemCommand` mà người chơi bấm tay, cho món đầu tiên
trong túi có hiệu ứng `HEAL_HP` (danh mục vật phẩm ADR-035). Server kiểm như mọi lần: sở hữu, hồi chiêu 10 giây, cộng
máu, audit. Sau mỗi lần gửi chờ 10 giây (đúng hồi chiêu) để không dội lệnh bị từ chối.

**D2 — ⚠️ Lệch mô tả lượt hỏi ("server tự dùng")**: bản này cho **client** canh máu và gửi lệnh. Lý do: máu sống trong
ECS ở tick thread; server tự dùng đan phải dựng một đường mới tick → gateway → Virtual Thread → túi đồ → quay lại World
(cùng loại đường ADR-046 D5 cố ý tránh). Kết quả cho người chơi giống nhau khi đang chơi; khác biệt duy nhất: rớt mạng
thì không uống — nhưng rớt mạng thì nhân vật cũng không còn trong World để bị đánh. Không thêm opcode, không đổi schema,
không có gì client "quyết" thay server. Muốn bản phía server ⇒ ADR riêng.

**D3 — Ngưỡng cố định 35%** (hằng số `AutoPotion.THRESHOLD_PERCENT`); chỉnh được theo người chơi là việc sau nếu cần
(cài đặt hiện chỉ có bật/tắt).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Công tắc client mặc định tắt, ngưỡng 35%, gửi `UseItemCommand` có sẵn, chờ 10 s giữa hai lần | Accepted |

## ADR cần tạo

- (Tuỳ chủ dự án) Tự uống phía server — xem D2.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-02 (lô 28): `DisplaySettings.Option.AUTO_POTION` (mặc định tắt — `Option.defaultOn`),
   `AutoPotion` (+ `AutoPotionTest`) do `InventoryCoordinator` giữ (nó có túi, danh mục, lệnh dùng), `WorldScreen` chỉ
   đưa `Vitals` (công tắc + máu) qua `buildInventory` và gọi `update` ở `syncCombatHud`.
2. Kiểm tra bằng client thật (cần màn hình) — chưa làm được trong container headless; logic đã phủ bằng unit test.
