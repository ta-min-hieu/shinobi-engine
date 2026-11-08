# ADR-254: Tìm trong túi đồ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Tìm trong túi đồ" trong lượt hỏi lô 40 (AskUserQuestion; mô tả kèm: /timdo <tên> chỉ ra ô đang chứa món đó (thuần client)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-160](ADR-160-Bag-Sort.md) (sắp xếp túi) |

---

## Decision

**D1 — Thuần client.** `/timdo <tên>` (hoặc `/find`) so khớp không dấu, chứa-trong, trên đúng dòng mô tả túi đồ đang
hiện (tên + cường hoá); in tối đa 10 kết quả "ô N: tên ×số lượng". Không thấy ⇒ một dòng báo. Dữ liệu là danh sách túi
client đã có, không hỏi server.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Khớp không dấu trên mô tả món trong túi đang có; `/timdo` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 40): `BagSearchCoordinator`, `InventoryCoordinator.describedItems`.
2. ✅ Test: `Lot40bClientTest`.
3. ✅ Kiểm sống: thuần client, không có client đồ hoạ trong môi trường kiểm sống — kiểm bằng test đơn vị; không opcode, không migration, server không đổi.
