# ADR-419: Ẩn tin hệ thống

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Ẩn tin hệ thống" trong lượt hỏi lô 61 (AskUserQuestion; mô tả kèm: /antinhethong: khung chat không hiện các dòng thông báo hệ thống từ server). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-317](ADR-317-System-Notice-History.md) · [ADR-229](ADR-229-Chat-Channel-Filter.md) |

---

## Decision

**D1:** khi bật, các `ChatMessageEvent` không có người gửi (thông báo hệ thống từ server: báo bạn bè online, nhắc nhở…) không vào khung chat; tin người chơi và các dòng do chính lệnh của máy này in ra (kết quả `/...`) vẫn hiện.

**D2:** `/antinhethong` (`/hidesystem`), nhớ trên máy, mặc định tắt. Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, chặn tin không người gửi | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 61): `ChatTidyCoordinator`, `ChatCoordinator.useHideSystem`.
2. ✅ Test: kiểm bằng mắt.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
