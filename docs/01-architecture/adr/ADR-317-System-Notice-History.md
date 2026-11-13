# ADR-317: Lịch sử thông báo hệ thống

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Lịch sử thông báo hệ thống" trong lượt hỏi lô 48 (AskUserQuestion; mô tả kèm: xem lại 30 dòng thông báo hệ thống gần nhất (dễ trôi mất trong chat)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | — |

---

## Decision

**D1 — Thuần client.** Giữ 30 dòng hệ thống gần nhất server gửi (tin không có người gửi: boss, sự kiện, thư chưa đọc…)
trong phiên; `/lichsuthongbao` (hoặc `/systemlog`) in lại. **Đổi tên lệnh so với mô tả lúc hỏi:** `/thongbao` đã là
thông báo bang (ADR-179).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Vòng 30 dòng hệ thống; `/lichsuthongbao` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 48): `SystemNoticeHistoryCoordinator`, `ChatCoordinator.onIncoming`.
2. ✅ Test: `Lot48ClientTest.systemHistoryKeepsNewestThirty`.
3. ⏳ Kiểm sống gameProbe.
