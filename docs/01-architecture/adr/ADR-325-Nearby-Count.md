# ADR-325: Đếm người xung quanh

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Đếm người xung quanh" trong lượt hỏi lô 49 (AskUserQuestion; mô tả kèm: hiện số người chơi đang ở gần mình (trong tầm nhìn) ở góc HUD). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-283](ADR-283-Fps-Counter.md) |

---

## Decision

**D1 — Thuần client.** Nhãn "Xung quanh: N" ở góc dưới-trái HUD (trên nhãn FPS) — N = số người chơi khác client đang
thấy (bản đồ sống của `RemoteCharacterPresenter`, đọc mỗi khung không cấp phát, chỉ đổi chữ khi số đổi). `/xungquanh`
(hoặc `/nearby`) bật/tắt, nhớ trong Preferences (mặc định tắt).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Nhãn HUD từ bản đồ người đang thấy; `/xungquanh` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 49): `NearbyCountCoordinator`, `CombatHud.useNearbyCount`/`setNearbyVisible`, `EmoteDisplay.visibleOthers`.
2. ✅ Test: biên dịch + kiểm tay (UI).
3. ⏳ Thuần client — probe headless không vẽ UI/không chạy lệnh gạch chéo; phủ bằng biên dịch + test đơn vị, cần kiểm tay trên client thật.
