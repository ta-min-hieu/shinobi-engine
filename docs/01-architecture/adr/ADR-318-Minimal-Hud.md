# ADR-318: HUD tối giản

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "HUD tối giản" trong lượt hỏi lô 48 (AskUserQuestion; mô tả kèm: công tắc ẩn thanh gợi ý phím và các dòng phụ, chỉ giữ máu/mục tiêu/nút chiêu). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-244](ADR-244-Photo-Mode.md) · [ADR-143](ADR-143-Display-Settings.md) |

---

## Decision

**D1 — Thuần client, một dòng cài đặt hiển thị** (`MINIMAL_HUD`, mặc định TẮT). Bật ⇒ ẩn thanh gợi ý phím và thẻ nhiệm
vụ; máu/khí, mục tiêu, nút chiêu, cột thông báo vẫn hiện. Khác chế độ chụp ảnh (ADR-244) ẩn sạch mọi thứ.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Dòng cài đặt `MINIMAL_HUD`: ẩn gợi ý phím + thẻ nhiệm vụ | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 48): `DisplaySettings.Option.MINIMAL_HUD`, `CombatHud.setMinimal`, `HudQuestCard.setSuppressed`.
2. ✅ Test: biên dịch + kiểm tay (UI).
3. ⏳ Thuần client — probe headless không vẽ UI/không chạy lệnh gạch chéo; phủ bằng biên dịch + test đơn vị, cần kiểm tay trên client thật.
