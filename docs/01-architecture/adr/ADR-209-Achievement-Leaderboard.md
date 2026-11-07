# ADR-209: Xếp hạng thành tựu

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Xếp hạng thành tựu" trong lượt hỏi lô 35 (AskUserQuestion; mô tả kèm: bảng xếp hạng theo số thành tựu đã mở). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-057](ADR-057-Leaderboards.md) (Bia Anh Danh) · [ADR-062](ADR-062-Achievements-And-Titles.md) (thành tựu + danh hiệu) |

---

## Decision

**D1 — Một bảng mới trong Bia Anh Danh**: `LEADERBOARD_KIND_ACHIEVEMENT = 8`, giá trị = số thành tựu đã mở
(`character_achievement`), cùng khuôn với bảng Động Phủ / Thiên Kiếp (ADR-134): chỉ người đã mở ít nhất một thành tựu
có mặt; bằng điểm thì xếp theo cấp, rồi id; cache 60 giây như mọi bảng. Không opcode mới, không migration — chỉ thêm
một giá trị enum (`protocolVersion` không tăng; client cũ hỏi bảng lạ nhận bảng cảnh giới như luật sẵn có).

**D2 — Đếm số thành tựu, không phải "điểm"**: thành tựu hiện không có điểm riêng; mỗi thành tựu tính 1. Khi thành tựu
có trọng số (ADR khác), đổi giá trị bảng ở đúng một câu SQL.

**D3 — Client**: thêm tab "Thành tựu" vào cửa sổ Bia Anh Danh, dòng hiện "N thành tựu".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | `LEADERBOARD_KIND_ACHIEVEMENT`, giá trị = số thành tựu đã mở, khuôn `rankByScore`; tab mới | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 35): `AchievementPort.unlockedCounts`, `LeaderboardCache.Kind.ACHIEVEMENT`, tab client.
2. ✅ Test: `AchievementCountIntegrationTest` (Postgres thật: đếm theo nhân vật, bỏ người chưa mở gì).
3. ✅ Kiểm sống gameProbe 2026-11-07: `--leaderboards=achievement` ⇒ 2 dòng, ĐạoHữu01 hạng 1 (2 thành tựu), BaoDo01
   hạng 2 (1) — khớp `character_achievement`; người chưa mở gì không có mặt.
