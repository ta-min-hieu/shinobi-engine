# ADR-251: Bảng sát thương nhóm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Bảng sát thương nhóm" trong lượt hỏi lô 40 (AskUserQuestion; mô tả kèm: tổng sát thương từng người trong trận gần nhất, tính từ gói đòn đánh (thuần client)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-226](ADR-226-Combat-Log.md) (nhật ký chiến đấu — cùng nguồn gói 401/40x) |

---

## Decision

**D1 — Thuần client.** Cộng sát thương từ gói kết quả đòn đánh / kỹ năng mà client đã nhận (chỉ những đòn trong tầm
nhìn), cho **chính mình và thành viên nhóm** (tên lấy từ gói danh sách thành viên nhóm sẵn có). Một "trận" kết thúc khi
**30 giây** không có đòn nào — đòn kế tiếp bắt đầu bảng mới. `/satthuong` (hoặc `/dps`) in bảng xếp giảm dần kèm phần
trăm; `/satthuong xoa` xoá tay. Chỉ là con số tham khảo phía máy mình (đòn ngoài tầm nhìn không tính).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cộng sát thương mình + nhóm từ gói đòn đánh, trận = khoảng không quá 30 giây; `/satthuong` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 40): `PartyDamageMeterCoordinator`.
2. ⚠️ Test: phần cộng dồn/đổi trận chưa có test đơn vị riêng (lớp cần `ChatCoordinator` dựng trên libGDX); logic ngắn, mảng cố định — rà bằng mắt.
3. ✅ Kiểm sống: thuần client, không có client đồ hoạ trong môi trường kiểm sống — kiểm bằng test đơn vị; không opcode, không migration, server không đổi.
