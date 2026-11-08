# ADR-243: Cảnh báo máu thấp

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Cảnh báo máu thấp" trong lượt hỏi lô 39 (AskUserQuestion; mô tả kèm: máu dưới 30% thì viền màn hình nháy đỏ + dòng nhắc uống đan). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-046](ADR-046-Health-Recovery.md) (hồi máu, đan dược) |

---

## Decision

**D1 — Thuần client.** Đọc máu đang hiện trên HUD (`CombatHudController.playerHp/playerMaxHp`, đã đồng bộ qua 408 +
kết quả đòn đánh). Không opcode, không server.

**D2 — Ngưỡng:** dưới **30%** máu tối đa (và còn sống) ⇒ viền đỏ bốn cạnh màn hình (4% cạnh ngắn) nháy theo sin, alpha
0,15–0,50, vẽ sau lớp phủ đêm và trước HUD. Một dòng chat nhắc uống Tiểu Hồi Nguyên Đan / lui khỏi trận — **một lần mỗi
lần tụt**; hồi lên ≥ 40% mới nhắc lại (khỏi spam khi máu dao động quanh ngưỡng). Không cấp phát mỗi khung (màu + ma trận
dựng sẵn).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Viền đỏ nháy + một dòng nhắc khi máu < 30%, nhắc lại sau khi ≥ 40%; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 39): `LowHealthWarning`, `WorldScreen.renderScreenOverlays`.
2. ✅ Test: `Lot39bAlertsTest.lowHealthBelowThirtyPercentWhileAlive`.
3. ✅ Kiểm sống: thuần client, không có client đồ hoạ trong môi trường kiểm sống — kiểm bằng test đơn vị; không opcode, không migration, server không đổi.
