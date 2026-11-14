# ADR-361: Lịch sử bị soi

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Lịch sử bị soi" trong lượt hỏi lô 54 (AskUserQuestion; mô tả kèm: /aisoi: 10 lần gần nhất có người soi trang bị của bạn (ai, lúc nào) — kể cả khi bạn offline). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-171](ADR-171-Inspect-Player.md) · [ADR-330](ADR-330-Inspect-Notice.md) |

---

## Decision

**D1 — Ghi:** mỗi lần soi thành công một người KHÁC (đường soi ADR-171, sau bộ lọc ADR-362) ⇒ một dòng `inspect_log(target_id, viewer_id, inspected_at)` (V180), giữ 20 dòng mới nhất mỗi người bị soi. Ghi cả khi người bị soi offline — khác báo tức thời ADR-330.

**D2 — Dây:** `GetInspectHistoryRequest{}` / `GetInspectHistoryResponse{repeated InspectVisit{viewer_name, at}}` **293/294**, 10 lần mới nhất. Client `/aisoi` (`/inspectedby`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `inspect_log` (V180) giữ 20, trả 10; 293/294 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 54): `ProfileVisitPort.recordInspect`/`inspectHistory`, `Lot54Commands.guardInspect`/`inspectHistory` (gọi từ `Lot46Commands.withLikesAndNotice`), `ProfileVisitsCoordinator`.
2. ✅ Test: `Lot54IntegrationTest.inspectLogKeepsNewestAndFriendsOnlyToggles`.
3. ✅ Kiểm sống gameProbe 2026-11-14: BaoDo01 soi ĐạoHữu01 lúc ĐạoHữu01 offline; ĐạoHữu01 vào `inspectedBy` ⇒ BaoDo01; lần soi bị HIDDEN (người lạ) KHÔNG ghi. Đã xoá dòng thử.
