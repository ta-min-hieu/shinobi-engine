# ADR-324: Lọc túi theo loại

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Lọc túi theo loại" trong lượt hỏi lô 49 (AskUserQuestion; mô tả kèm: nút/lệnh lọc túi đồ theo loại: trang bị, đan dược, nguyên liệu, khác). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-254](ADR-254-Bag-Search.md) |

---

## Decision

**D1 — Thuần client, lệnh chat** (cùng khuôn `/timdo` ADR-254): `/loctui trangbi|dan|khac` in các món trong túi thuộc
loại đó. Loại suy từ catalog: có ô trang bị ⇒ trang bị; có hiệu ứng dùng ⇒ đan dược/dùng được; còn lại ⇒ khác.

**D2 — Thu hẹp so với mô tả lúc hỏi:** catalog không có trường "nguyên liệu" riêng nên nguyên liệu gộp vào "khác"; không
thêm nút trên khung túi (lớp túi đồ đã chạm trần độ phức tạp PMD) — dùng lệnh chat.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | `/loctui` theo catalog: trang bị / dùng được / khác | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 49): `BagFilterCoordinator`, `BagSearchCoordinator.Described.itemTemplateId`.
2. ✅ Test: `Lot49ClientTest.bagFilterClassifiesByCatalog`.
3. ⏳ Thuần client — probe headless không vẽ UI/không chạy lệnh gạch chéo; phủ bằng biên dịch + test đơn vị, cần kiểm tay trên client thật.
