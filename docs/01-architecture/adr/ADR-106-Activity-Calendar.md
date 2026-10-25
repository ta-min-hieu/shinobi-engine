# ADR-106: Lịch hoạt động + điểm năng động

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-25) — chủ dự án chọn "Lịch hoạt động + điểm năng động" trong lượt hỏi lô 22 (AskUserQuestion; mô tả kèm: bảng việc hằng ngày (điểm danh, truy nã, Bí Cảnh, online…) cộng điểm năng động; mốc 20/40/60/80/100 mở rương quà qua thư). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-25 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | ADR-060 (điểm danh) · ADR-096 (online) · ADR-099 (Bí Cảnh) · ADR-101 (truy nã) · ADR-055 (nhiệm vụ ngày) |

---

## Decision

**D1 — Tham số (V89, Điều 27)**: `activity_definition(code, name_key, points_each, max_count, sort_order)`:
`check_in` 10×1, `online_reward` 10×3, `bounty` 10×5, `dungeon_run` 10×3, `daily_quest` 10×3, `herb_gather` 5×4.
`activity_chest(milestone, gold, item_template_id, quantity)` cho mốc 20/40/60/80/100.

**D2 — Ghi nhận**: `activity_progress(character_id, day, activity_code, count)` theo ngày giờ Việt Nam (cùng mốc 0h VN
như nhiệm vụ ngày); cộng khi việc tương ứng THÀNH CÔNG ở server (nhận quà điểm danh, nhận mốc online, xong truy nã, vào
Bí Cảnh trừ lượt, lĩnh thưởng nhiệm vụ ngày, hái xong bụi). Vượt `max_count` thì không cộng điểm nữa. Ghi là việc phụ —
lỗi ghi không làm hỏng việc chính.

**D3 — Rương**: đạt mốc ⇒ bấm nhận ⇒ `activity_chest_claim(character_id, day, milestone)` khoá chính chặn nhận đôi; quà
qua thư hệ thống với khoá chống trùng (nhân vật, ngày, mốc).

**D4 — Giao thức, chỉ thêm**: 222/223 `GetActivityRequest`/`Response{activities[], points, chests[]}`, 224/225
`ClaimActivityChestCommand{milestone}`/`Response{outcome}`.

**D5 — Client**: tab "Năng động" trong cửa sổ Hằng ngày (E).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Điểm năng động theo việc hằng ngày có trần, 5 rương mốc qua thư, tab trong cửa sổ E | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành (lô 22 phần 1, 2026-10-25): V89 tham số + tiến độ + nhận rương; `ActivityHooks` (Virtual Thread, việc
   phụ) móc vào điểm danh, mốc online, xong truy nã, trừ lượt Bí Cảnh, lĩnh nhiệm vụ ngày (`QuestFacade#isDailyQuest`),
   hái xong bụi; opcode 222–225; tab "Năng động" (`ActivityTab`) trong cửa sổ E.
2. ✅ Kiểm sống gameProbe 2026-10-25 (tanthua44): điểm danh lần đầu trong ngày ⇒ `check_in 1/1` (10 điểm); hái bụi #1 ⇒
   15; rương 20 khi 15 điểm ⇒ `NOT_AVAILABLE`; hái bụi #2 ⇒ 20 ⇒ rương 20 OK + thư 500, nhận lại ⇒ `NOT_AVAILABLE`.
   Chưa kiểm sống: Bí Cảnh (cần cả nhóm online), mốc online (10 phút), truy nã/nhiệm vụ ngày — đều một dòng gọi cùng hook.
