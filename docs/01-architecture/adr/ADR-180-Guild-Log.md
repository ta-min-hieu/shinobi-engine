# ADR-180: Nhật ký bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-06) — chủ dự án chọn "Nhật ký bang" trong lượt hỏi lô 31 (AskUserQuestion; mô tả kèm: ghi ai vào/rời bang, góp bang, đổi tên, bị đuổi... xem 50 dòng gần nhất). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-06 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-068](ADR-068-Guild-Level-And-Contribution.md) (góp bang, cấp bang) · [ADR-172](ADR-172-Guild-Rename.md) · [ADR-179](ADR-179-Guild-Notice.md) |

---

## Decision

**D1 — Không dựng được từ `audit_log`**: kiểm 2026-11-06 — vào bang, rời bang, bị đuổi KHÔNG có dòng audit nào ⇒ thêm bảng
riêng `guild_log` (V143): `guild_id` (xoá theo bang), `kind`, `actor_id`, `subject_id` (`ON DELETE SET NULL`), `amount`,
`detail`, `created_at`. Chỉ mục `(guild_id, created_at DESC, id DESC)`.

**D2 — Ghi cùng transaction**: mỗi dòng ghi trên CHÍNH connection của việc nó kể (`GuildLog.append`) — không có dòng cho
việc đã rollback, không thiếu dòng cho việc đã commit. Rời/bị đuổi là một câu `WITH … DELETE … RETURNING` + `INSERT`
gộp, nên "không xoá gì" ⇒ "không ghi gì". Tám loại: `CREATED`, `JOINED` (bang chủ duyệt → người vào), `LEFT`, `KICKED`
(bang chủ → người bị đuổi), `DONATED` (vàng), `LEVEL_UP` (cấp mới, kèm người góp đẩy lên cấp), `RENAMED` (tên mới),
`NOTICE_CHANGED`. Giải tán bang thì nhật ký đi cùng bang.

**D3 — Đọc**: 50 dòng mới nhất của bang mà người hỏi đang ở, mọi thành viên xem được; tên người là tên HIỆN TẠI (đọc kèm
bảng nhân vật). Giữ **90 ngày** (`RetentionJanitor.purgeGuildLog`).

**D4 — Giao thức, chỉ thêm**: `GetGuildLogRequest{}` / `GetGuildLogResponse{entries}` 745/746, enum `GuildLogKind`.

**D5 — Client**: cửa sổ mở bằng lệnh chat `/nhatkybang` (hoặc `/guildlog`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `guild_log` V143 ghi cùng transaction, 8 loại, 50 dòng / 90 ngày, 745/746, `/nhatkybang` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-06 (lô 31): V143, `GuildLog` (ghi), `PostgresGuildLogAdapter`/`GuildLogPort`/`GuildLogFacade`
   (đọc), ghi ở lập bang / duyệt / rời / đuổi / góp + lên cấp / đổi tên / đổi thông báo, `GuildLogCoordinator`.
2. ✅ Test: `GuildLogIntegrationTest` (Postgres thật: mỗi việc đúng một dòng, đuổi lần hai / bang chủ "rời" không ghi,
   người ngoài không đọc được), `GuildLogCoordinatorTest`; test bang cũ (`GuildIntegrationTest`…) vẫn xanh.
3. ✅ Kiểm sống gameProbe 2026-11-06 (bước `guildLog`): BaoDo01 góp 2.000 vàng, bang chủ ĐạoHữu01 đổi thông báo ⇒ BaoDo01
   xem nhật ký: `NOTICE_CHANGED(ĐạoHữu01)`, `DONATED(BaoDo01, 2000)` — mới nhất trước. Việc xảy ra trước V143 (đổi tên
   bang lô 30, thông báo đầu tiên) không có dòng: nhật ký bắt đầu từ lúc có bảng, không dựng lại quá khứ.
