# ADR-225: Nhắc trước boss thế giới

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Nhắc trước boss thế giới" trong lượt hỏi lô 37 (AskUserQuestion; mô tả kèm: báo mọi người online 5 phút trước khi boss xuất hiện). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-081](ADR-081-World-Boss.md) (lịch boss thế giới) |

---

## Decision

**D1 — Trong vòng hỏi lịch sẵn có** (mỗi 15 giây): còn không quá **5 phút** tới giờ xuất hiện và boss chưa sống ⇒ một
dòng hệ thống toàn server "Yêu Vương sẽ xuất hiện sau khoảng N phút ở phía tây tông môn!". Mỗi lần xuất hiện chỉ nhắc
**một lần** (nhớ trong RAM, xoá khi boss xuất hiện). Server vừa khởi động mà giờ xuất hiện đã ở trong cửa sổ 5 phút thì
nhắc ngay với số phút thật còn lại.

**D2 — Không opcode mới**: cùng đường báo "boss xuất hiện / bị hạ" (chat hệ thống song ngữ).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Nhắc một lần khi còn ≤ 5 phút, qua chat hệ thống sẵn có | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 37): `WorldBossScheduler.warnIfClose`.
2. ✅ Kiểm sống gameProbe 2026-11-08: server chạy với boss xuất hiện sau 320 giây, người chơi đứng chờ ⇒ ở giây ~20 nhận
   một dòng hệ thống "Yêu Vương sẽ xuất hiện sau khoảng 4 phút ở phía tây tông môn!" (đúng một lần).
