# ADR-418: Cảnh báo hộp thư sắp đầy

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Cảnh báo hộp thư sắp đầy" trong lượt hỏi lô 61 (AskUserQuestion; mô tả kèm: Vào world mà hộp thư đã ≥ 80% sức chứa ⇒ một dòng nhắc dọn thư). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-386](ADR-386-Old-Mail-Cleanup.md) · [ADR-216](ADR-216-Delete-Finished-Mail.md) |

---

## Decision

**D1:** vào world, đếm thư đang hiện trong hộp; từ **80%** sức chứa (100 thư) trở lên ⇒ một dòng hệ thống "Hộp thư sắp đầy (n/100)". Hộp đầy thì thư mới bị từ chối (đã có từ trước), nên nhắc trước để người chơi dọn (xoá hàng loạt ADR-216, lưu trữ ADR-354, tự dọn ADR-386).

**D2:** không protocol mới — dòng hệ thống sẵn có.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Dòng hệ thống lúc vào world, ngưỡng 80% | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 61): `Lot61Commands.onEnteredWorld/nearlyFull`.
2. ✅ Test: `Lot61ServerTest.warnsFromEightyPercent`.
3. ✅ Server khởi động và chạy đường vào world mới; dữ liệu thử chưa có hộp thư nào ≥ 80 thư nên dòng nhắc không xuất hiện — ngưỡng kiểm bằng test.
