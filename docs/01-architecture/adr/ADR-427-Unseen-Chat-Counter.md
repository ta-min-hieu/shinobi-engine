# ADR-427: Đếm tin mới khi ẩn chat

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Đếm tin mới khi ẩn chat" trong lượt hỏi lô 62 (AskUserQuestion; mô tả kèm: Đang ẩn khung chat ⇒ dòng HUD "Chat: 5 tin mới", hiện lại chat thì về 0). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-333](ADR-333-Hide-Chat-Key.md) · [ADR-389](ADR-389-Whisper-Alert-When-Chat-Hidden.md) |

---

## Decision

**D1:** khi khung chat đang ẩn (ADR-333), mỗi tin của người chơi tới (không tính dòng hệ thống) tăng bộ đếm; dòng HUD "Chat: tin mới N" (dòng số chung ADR-396). Chat hiện lại ⇒ về 0 và dòng để trống.

**D2:** luôn bật (chỉ có chữ khi đang ẩn chat). Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, đếm khi chat ẩn | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 62): `ChatLensCoordinator.unseenCount/countUnseen`.
2. ✅ Test: kiểm bằng mắt.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
