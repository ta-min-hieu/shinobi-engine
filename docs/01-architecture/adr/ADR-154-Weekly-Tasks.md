# ADR-154: Nhiệm vụ tuần cá nhân

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-02) — chủ dự án chọn "Nhiệm vụ tuần cá nhân" trong lượt hỏi lô 28 (AskUserQuestion; mô tả kèm: 3 nhiệm vụ tuần (hạ N quái, hái N linh thảo, đào N kho báu…) reset thứ Hai, thưởng lớn hơn nhiệm vụ ngày). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-02 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-106](ADR-106-Activity-Calendar.md) (lịch hoạt động — cùng đường đếm) · [ADR-069](ADR-069-Guild-Weekly-Quest.md) (nhiệm vụ BANG tuần — khác: của bang, không của cá nhân) · [ADR-055](ADR-055-Daily-Quests.md) |

---

## Decision

**D1 — Năm việc cố định (V125)**: `weekly_task(code, name_key, activity_code, target, thưởng…)` — hạ **300** quái,
thu thập **20** lần, **5** lượt Bí Cảnh, **5** truy nã, **10** nhiệm vụ ngày; mỗi việc thưởng 1.500–4.000 linh thạch + vật
phẩm. ⚠️ Lệch mô tả lượt hỏi: **5 việc thay vì 3**, và **không có "đào kho báu"** — các việc chọn theo đường đếm có sẵn
(ADR-106) + "hạ quái" (bước phụ sẵn có của lượt hạ quái); đào kho chưa có hook đếm ở đó. Số là PLACEHOLDER (Điều 27).

**D2 — Đếm**: `ActivityHooks.record` (đã được điểm danh, mốc online, truy nã, Bí Cảnh, nhiệm vụ ngày, thu thập gọi) nay
cũng cộng nhiệm vụ tuần theo cùng mã; lượt hạ quái gọi thêm `recordWeekly("monster_kill")` ở bước phụ (ADR-101/121).
Ghi là UPSERT cộng dồn trên Virtual Thread riêng, lỗi không làm hỏng việc chính.

**D3 — Tuần**: bắt đầu **thứ Hai 0h giờ Việt Nam**; mỗi tuần một bộ dòng `character_weekly_task(character_id,
week_start, task_code, progress, claimed_at)` — sang tuần là bắt đầu lại từ 0, không cần job reset.

**D4 — Nhận thưởng tay**: một giao dịch dưới khoá túi → khoá dòng tiến độ → đủ chỉ tiêu, chưa nhận → trao (túi đầy ⇒
rollback, thưởng vẫn chờ) → đánh dấu + audit `WEEKLY_TASK_CLAIM`.

**D5 — Giao thức, chỉ thêm**: 240/241 `GetWeeklyTasksRequest{}` / `Response{tasks[]}`, 242/243
`ClaimWeeklyTaskCommand{task_code}` / `Response{outcome, task_code, tasks}`.

**D6 — Client**: lệnh chat `/nhiemvutuan` (hoặc `/weekly`) — mỗi việc một dòng (tiến độ/chỉ tiêu, thưởng), đủ thì có nút
"Nhận".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 5 việc cố định đếm qua đường lịch hoạt động + hạ quái, tuần thứ Hai giờ VN, nhận tay một lần, một giao dịch | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-02 (lô 28): V125, `WeeklyTaskPort`/`WeeklyTaskRules`/`WeeklyTaskFacade` (feature-checkin),
   `PostgresWeeklyTaskAdapter`, `ActivityHooks.recordWeekly`, `WeeklyTaskCommands` (240–243), `WeeklyTaskCoordinator`
   (`/nhiemvutuan`), bước probe `weekly`, `claimWeekly:<mã>`.
2. ✅ Kiểm sống gameProbe 2026-11-02 (server thật, DB chạy V125): bảng 5 việc 0/…; hạ 1 mộc nhân ⇒ `monster_hunter`
   = 1 (bước phụ hạ quái ghi thật); đặt tiến độ 300 trên DB dev ⇒ `herbalist` `NOT_DONE`, `monster_hunter` `OK`
   (+3.000 `gold.reason.weekly_task` + vật phẩm), nhận lại ⇒ `ALREADY_CLAIMED`.
3. (Chủ dự án) thêm việc "đào kho báu" (cần một dòng `recordWeekly` ở `TreasureHooks`) nếu muốn.
