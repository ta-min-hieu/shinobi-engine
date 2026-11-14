# ADR-377: Số online cao nhất hôm nay

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Online cao nhất hôm nay" trong lượt hỏi lô 56 (AskUserQuestion; mô tả kèm: /onlinecao: đỉnh số người online cùng lúc trong ngày và số đang online). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-281](ADR-281-Online-Count.md) |

---

## Decision

**D1 — Đếm:** `PeakOnlineTracker` trong RAM của registry: mỗi lần nhân vật vào world, so số đang kết nối với đỉnh; đặt lại lúc 00:00 giờ Việt Nam (`Asia/Ho_Chi_Minh`, cùng mốc reset ngày). KHÔNG ghi DB — khởi động lại server thì đếm lại từ đầu (chấp nhận: con số mang tính tham khảo).

**D2 — Dây:** `GetPeakOnlineRequest{}` / `GetPeakOnlineResponse{peak, peak_at_epoch_seconds, current}` **297/298**. Client `/onlinecao` (`/peakonline`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lệnh 297/298, đỉnh trong RAM, reset 00:00 giờ VN | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 56): `PeakOnlineTracker`, `Lot56Commands.peakOnline`, `PartyMailCoordinator`.
2. ✅ Test: `Lot56ServerTest.peakTracksDayAndResetsAtVietnamMidnight`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14 (gameProbe `peakOnline:`): `đỉnh=1 hiện=1` trên server vừa khởi động.
