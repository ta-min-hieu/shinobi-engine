# ADR-153: Quà mốc cảnh giới

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-02) — chủ dự án chọn "Quà mốc cảnh giới" trong lượt hỏi lô 28 (AskUserQuestion; mô tả kèm: đạt mỗi cảnh giới (Lv10/19/28…) nhận quà một lần: linh thạch, đan dược, trang bị; cửa sổ xem mốc đã/chưa nhận). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-02 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-136](ADR-136-Realm-Breakthrough-Ceremony.md) (cấp mở đầu mỗi cảnh giới 19/37/55/73/91) · [ADR-038](ADR-038-Theme-Pivot-Tu-Tien.md) |

---

## Decision

**D1 — Mốc (V124)**: `realm_milestone(level, name_key, reward_gold, reward_item_template_id, reward_quantity)` — mốc là
cấp MỞ ĐẦU mỗi cảnh giới theo ADR-136 (**19 Trúc Cơ, 37 Tử Phủ, 55 Kim Đan, 73 Nguyên Anh, 91 Hoá Thần**) cộng một mốc
nhập môn ở **cấp 10**. ⚠️ Lệch mô tả lượt hỏi ("Lv10/19/28…"): 28 không mở cảnh giới nào — dùng đúng thang ADR-136.
Quà: linh thạch (1.000 → 60.000) + đan dược / Hộ Linh Phù / Truyền Tống Phù / Tàng Bảo Đồ. **Chưa có trang bị** trong
quà: trang bị theo cảnh giới đã có nguồn riêng (ADR-132 trang phục, boss, rèn) — thêm vào đây là đổi nhịp kinh tế trang
bị, để chủ dự án quyết. Số là PLACEHOLDER (Điều 27).

**D2 — Nhận tay, không tự trao**: lệnh nhận trong cửa sổ, một giao dịch dưới khoá túi: mốc có thật → đủ cấp (đọc DB, không
tin gói) → chưa nhận (`PRIMARY KEY (character_id, level)` + `ON CONFLICT DO NOTHING`) → trao quà (túi đầy ⇒ `BAG_FULL`,
rollback, **quà vẫn chờ**) → audit `MILESTONE_CLAIM`. Tự trao lúc lên cấp sẽ mất quà khi túi đầy giữa trận.

**D3 — Giao thức, chỉ thêm**: 236/237 `GetRealmMilestonesRequest{}` / `Response{character_level, milestones[]}`,
238/239 `ClaimRealmMilestoneCommand{level}` / `Response{outcome, level, board}`.

**D4 — Client**: lệnh chat `/mocgioi` (hoặc `/milestones`) — mỗi mốc một dòng (cấp, tên, quà, đã nhận/có thể nhận/chưa
đạt), dòng nhận được tô sáng kèm nút "Nhận".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 6 mốc (10 + cấp mở cảnh giới), nhận tay một lần, một giao dịch, túi đầy quà vẫn chờ | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-02 (lô 28): V124, `MilestonePort`/`MilestoneFacade` (feature-character), `PostgresMilestoneAdapter`,
   `MilestoneCommands` (236–239), `MilestoneCoordinator` (`/mocgioi`), bước probe `milestones`, `claimMilestone:<cấp>`.
2. ✅ Kiểm sống gameProbe 2026-11-02 (server thật, DB chạy V124), nhân vật cấp 20: bảng 6 mốc; nhận mốc 37 ⇒
   `LEVEL_TOO_LOW`; mốc 10 ⇒ `OK` +1.000 (`gold.reason.milestone`); nhận lại ⇒ `ALREADY_CLAIMED`; mốc 19 ⇒ `OK` +3.000;
   2 dòng audit `MILESTONE_CLAIM`. (Lưu ý khi dựng dữ liệu thử: cấp tính lại từ EXP — chỉnh `level` thôi sẽ bị ghi đè.)
3. (Chủ dự án) có thêm trang bị vào quà mốc không — xem D1.
