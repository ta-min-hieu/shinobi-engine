# ADR-354: Lưu trữ thư

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Lưu trữ thư" trong lượt hỏi lô 53 (AskUserQuestion; mô tả kèm: cất thư vào kho lưu trữ (ẩn khỏi hộp thư chính, không bị xoá hàng loạt); /thuluutru xem lại). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-216](ADR-216-Delete-Finished-Mail.md) · [ADR-167](ADR-167-Claim-All-Mail.md) |

---

## Decision

**D1 — Lưu:** cột `mail.archived_at` (V179). Cất ⇒ đặt mốc và coi như đã đọc; trả về ⇒ xoá mốc. Chỉ thư của chính mình.

**D2 — Ảnh hưởng:** danh sách thư chính và xoá hàng loạt thư đã xong (ADR-216) bỏ qua thư đã cất, nên các lệnh dựa trên danh sách thư chính (nhận hết thư ADR-167, tìm thư) cũng không đụng tới chúng — muốn nhận quà trong thư đã cất thì trả về trước.

**D3 — Dây:** `ArchiveMailCommand{mail_id, archive}` / `ArchiveMailResponse{outcome, mail_id}` **927/928**; `ListArchivedMailRequest{}` / `ListArchivedMailResponse{repeated ArchivedMail}` **929/930**. Client `/luuthu` (thư đang mở), `/thuluutru`, `/traluu <số thư>`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | `mail.archived_at` (V179); hộp chính và xoá hàng loạt bỏ qua; 927–930 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 53): `MailArchivePort`/`Facade` (feature-mail), `PostgresMailArchiveAdapter`, sửa truy vấn `PostgresMailRepositoryAdapter`/`PostgresMailHousekeepingAdapter`, `Lot53Commands.archiveMail`/`listArchived`, `MailArchiveCoordinator`.
2. ✅ Test: `Lot53IntegrationTest.archivedMailLeavesTheInbox`.
3. ✅ Kiểm sống gameProbe 2026-11-13: BaoDo01 gửi một thư cho ĐạoHữu01; `archiveMail` ⇒ ARCHIVED, `archivedMail` ⇒ 1; trả về ⇒ RESTORED, kho ⇒ 0. Đã xoá thư thử.
