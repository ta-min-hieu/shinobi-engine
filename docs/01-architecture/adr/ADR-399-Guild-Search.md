# ADR-399: Tìm bang theo tên

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Tìm bang theo tên" trong lượt hỏi lô 59 (AskUserQuestion; mô tả kèm: /timbang <chữ>: liệt kê bang có tên chứa chữ đó kèm cấp bang, số thành viên, bang chủ). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-185](ADR-185-Guild-Recruitment-Board.md) · [ADR-352](ADR-352-Referral-Ranking.md) |

---

## Decision

**D1:** tìm `guild.name` chứa chuỗi (không phân biệt hoa thường, `%`/`_` được thoát), 1–32 ký tự, tối đa 10 bang, cấp bang cao trước rồi theo tên; mỗi dòng có cấp bang (ADR-068), số thành viên, tên bang chủ, ký hiệu bang (ADR-344).

**D2 — Dây:** `SearchGuildsRequest{query}` / `SearchGuildsResponse{entries{name, level, members, leader_name, tag}}` **795/796**; chuỗi rỗng / quá dài ⇒ danh sách rỗng. Client `/timbang <chữ>` (`/findguild`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lệnh 795/796, `ILIKE` có thoát ký tự, top 10 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 59): `GuildDirectoryPort.search`, `PostgresGuildDirectoryAdapter`, `Lot59Commands.searchGuilds`, `GuildToolsCoordinator`.
2. ✅ Test: `Lot59IntegrationTest.searchesGuildsByFragment`, `Lot59ServerTest`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14 (gameProbe `searchGuild:`): `kiem` ⇒ `LinhKiemMon cấp=1 tv=2 chủ=ĐạoHữu01`; `%` ⇒ rỗng (ký tự đại diện đã thoát).
