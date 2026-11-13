# ADR-323: Thu phóng camera

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Thu phóng camera" trong lượt hỏi lô 49 (AskUserQuestion; mô tả kèm: con lăn chuột phóng to/thu nhỏ camera trong giới hạn cho phép, nhớ lựa chọn). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) |

---

## Decision

**D1 — Thuần client.** Lăn chuột đổi `OrthographicCamera.zoom` của camera thế giới 3D trong [0,7; 1,5] (nấc 0,1), nhớ
trong Preferences `camera.zoom`. Nhận lăn chuột qua một "cửa sổ" rỗng đăng ký CUỐI trong `PanelHub` ⇒ lăn trên bảng có
thanh cuộn thì bảng nhận trước. Góc camera khoá (ADR-031) không đổi.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lăn chuột ⇒ `camera.zoom` [0,7; 1,5]; Preferences | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 49): `CameraZoomCoordinator`, `WorldModelRenderer.setZoom`, `ExpansionWindows.useCamera`.
2. ✅ Test: `Lot49ClientTest.cameraZoomStepsAndClamps`.
3. ⏳ Thuần client — probe headless không vẽ UI/không chạy lệnh gạch chéo; phủ bằng biên dịch + test đơn vị, cần kiểm tay trên client thật.
