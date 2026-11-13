# ADR-330: Báo khi bị soi trang bị

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Báo khi bị soi trang bị" trong lượt hỏi lô 50 (AskUserQuestion; mô tả kèm: người bị /soi nhận dòng báo 'X vừa xem trang bị của bạn' (có công tắc tắt)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-171](ADR-171-Inspect-Player.md) · [ADR-298](ADR-298-Profile-Likes.md) |

---

## Decision

**D1 — Báo:** soi thành công (ADR-171) một người KHÁC đang online ⇒ người đó nhận dòng hệ thống "X vừa xem trang bị của bạn". Tự soi mình, soi bị ẩn (HIDDEN) hay không tìm thấy ⇒ không báo. Offline ⇒ bỏ qua (không lưu hàng đợi).

**D2 — Công tắc:** mặc định BẬT; bảng `inspect_notice_mute(character_id)` (V176) — có dòng = đã tắt.

**D3 — Dây:** `SetInspectNoticeCommand{query_only, enabled}` / `SetInspectNoticeResponse{enabled}` **281/282**. Client `/baosoi [bat|tat]` (`/inspectnotice on|off`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Báo người bị soi đang online; công tắc bảng `inspect_notice_mute` (V176); 281/282 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 50): `InspectNoticePort`/`Facade`, `PostgresInspectNoticeAdapter`, `Lot46Commands.withLikesAndNotice` → `Lot50Commands.notifyInspected`/`setInspectNotice`, `InspectNoticeCoordinator`.
2. ✅ Test: `Lot50IntegrationTest.inspectNoticeMuteToggles`, `Lot50ServerTest.inspectedNoticeNamesViewer`.
3. ✅ Kiểm sống gameProbe 2026-11-13: `inspectNotice:?` ⇒ true, `off` ⇒ false, `on` ⇒ true; BaoDo01 soi ĐạoHữu01 đang online ⇒ ĐạoHữu01 nhận "BaoDo01 vừa xem trang bị của bạn."
