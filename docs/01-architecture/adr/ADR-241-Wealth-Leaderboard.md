# ADR-241: Xếp hạng giàu có

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Xếp hạng giàu có" trong lượt hỏi lô 39 (AskUserQuestion; mô tả kèm: bảng xếp hạng mới theo số linh thạch). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-057](ADR-057-Leaderboards.md) (Bia Anh Danh) · [ADR-209](ADR-209-Achievement-Leaderboard.md) (cùng khuôn) |

---

## Decision

**D1 — Một bảng mới trong Bia Anh Danh**: `LEADERBOARD_KIND_WEALTH = 9`, giá trị = linh thạch **đang có**
(`character_stat.gold`), cùng khuôn `rankByScore` của ADR-209 (cache 60 giây như mọi bảng). Chỉ **100 người** nhiều nhất
có ít nhất 1 linh thạch (chỉ mục một phần V168). Không opcode mới — chỉ thêm giá trị enum (`protocolVersion` không
tăng). `rankByScore` nhận điểm kiểu số bất kỳ (linh thạch là `long`).

**D2 — Công khai số vàng là chủ ý** của bảng này (chủ dự án chọn). Không tính vàng trong kho/phường thị/thư chưa nhận —
chỉ số dư túi, đúng con số `GoldChangedEvent` báo.

**D3 — Client**: tab "Phú hộ" trong cửa sổ Bia Anh Danh, dòng hiện "N linh thạch".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | `LEADERBOARD_KIND_WEALTH`, top 100 theo `character_stat.gold`, khuôn `rankByScore`; tab mới | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 39): `WealthRankingPort`/`WealthRankingFacade`, `PostgresWealthRankingAdapter`, `LeaderboardCache.Kind.WEALTH`, tab `LeaderboardCoordinator`.
2. ✅ Test: `Lot39IntegrationTest.ranksRichestFirst`.
3. ✅ Kiểm sống gameProbe 2026-11-08: `--leaderboards=wealth` ⇒ `#1 ĐạoHữu01 6648`, `#2 BaoDo01 3600` (khớp `character_stat.gold`).
