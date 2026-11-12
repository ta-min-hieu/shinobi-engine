# ADR-300: Chụp màn hình F12

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Chụp màn hình F12" trong lượt hỏi lô 46 (AskUserQuestion; mô tả kèm: phím F12 lưu ảnh PNG, báo đường dẫn vào chat). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-244](ADR-244-Photo-Mode.md) |

---

## Decision

**D1 — Thuần client.** Nhả F12 ⇒ chép khung hình vừa vẽ (cả HUD; đang ở chế độ chụp ảnh ADR-244 thì không có HUD) ra
`~/KageScreenshots/kage-yyyyMMdd-HHmmss.png`. Đọc framebuffer trên luồng render (bắt buộc với GL), mã hoá PNG + ghi đĩa
trên Virtual Thread (I/O chặn — đúng luật dùng VT) để khung hình không khựng; xong báo đường dẫn vào chat.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | F12 → PNG trong `~/KageScreenshots`, ghi trên Virtual Thread | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 46): `ScreenshotCoordinator`, `PhotoModeCoordinator.afterRender`.
2. ✅ Test: `Lot46ClientTest.screenshotNameCarriesTimestamp`.
3. ⏳ Kiểm sống gameProbe.
