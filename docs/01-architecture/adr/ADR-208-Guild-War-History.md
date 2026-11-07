# ADR-208: Lịch sử bang chiến

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Lịch sử bang chiến" trong lượt hỏi lô 35 (AskUserQuestion; mô tả kèm: xem lại các trận bang chiến đã đánh, thắng/thua, tỷ số). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-102](ADR-102-Guild-War.md) (bang chiến, bảng `guild_war`) |

---

## Decision

**D1 — Không bảng mới.** `guild_war` (V88) đã giữ mọi trận kèm điểm và `winner_guild_id` sau khi chốt; lịch sử chỉ là đọc
các dòng `settled_at IS NOT NULL` của bang người hỏi. Bang đối địch giải tán thì trận bị CASCADE xoá theo (luật sẵn
của ADR-102) — lịch sử chỉ còn trận với bang còn tồn tại; tên bang là tên hiện tại.

**D2 — Nhìn từ phía mình.** Mỗi dòng: đối thủ, mình tuyên hay bị tuyên, điểm mình – điểm đối thủ, THẮNG / THUA / HOÀ
(`winner_guild_id` NULL = hoà), lúc kết thúc. Tối đa 20 trận, mới nhất trước. Thành viên nào cũng xem được (không chỉ
bang chủ).

**D3 — Protocol**: `GetGuildWarHistoryRequest{}` / `GetGuildWarHistoryResponse{repeated GuildWarHistoryEntry}` 759/760,
enum `GuildWarResult`.

**D4 — Client**: `/lichsuchien` (hoặc `/warhistory`) in danh sách vào khung chat.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Đọc `guild_war` đã chốt, 20 trận, nhìn từ phía bang mình; 759/760; `/lichsuchien` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 35): `GuildWarHistoryPort`/`Facade`, `PostgresGuildWarHistoryAdapter`,
   `Lot35Commands.warHistory`, `GuildWarHistoryCoordinator`.
2. ✅ Test: `GuildWarHistoryIntegrationTest` (Postgres thật: chỉ trận đã chốt, mới nhất trước, thắng/thua/hoà từ hai
   phía, giới hạn, người không bang), `GuildWarHistoryCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-07: dựng tạm bang "HuyenThien" + hai trận đã chốt (Linh Kiếm Môn tuyên, thắng 5–2;
   bị tuyên, hoà 4–4) ⇒ `warHistory` của ĐạoHữu01 trả `WIN>HuyenThien 5-2`, `DRAW<HuyenThien 4-4`, mới nhất trước. Bang
   tạm và hai trận đã xoá.
