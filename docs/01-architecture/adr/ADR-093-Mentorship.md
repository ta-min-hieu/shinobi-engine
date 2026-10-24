# ADR-093: Sư đồ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Sư đồ" trong lượt hỏi lô 21 (AskUserQuestion; mô tả kèm: người Lv≥40 nhận đồ đệ <Lv30 (tối đa 3); đồ đệ chạm mốc cấp 10/20/30 ⇒ thư thưởng cho cả hai; migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | feature-friend · [ADR-091](ADR-091-Block-List.md) · [ADR-089](ADR-089-Rebirth.md) |

---

## Decision

**D1 — Điều kiện**: đồ đệ **dưới Lv30**, sư phụ **từ Lv40**; mỗi đồ đệ một sư phụ, mỗi sư phụ tối đa **3** đồ đệ. Đồ đệ
CHỦ ĐỘNG bái sư (lời chờ 5 phút trong RAM, sư phụ phải đang online), sư phụ nhận. Cấp kiểm lại lúc nhận.

**D2 — Dữ liệu (V85)**: `mentorship(disciple_id PK, mentor_id)`; trần 3 giữ bằng khoá dòng `character` của sư phụ.
`mentorship_milestone(disciple_id, milestone)` PK — mỗi mốc của một đồ đệ chỉ ghi MỘT lần trong đời nhân vật, kể cả đổi
sư phụ hay Độ Kiếp (chống nuôi đồ đệ để cày thưởng). Lúc nhận đồ đệ, các mốc đồ đệ ĐÃ qua được ghi là đã dùng mà không
thưởng (`rewarded = false`).

**D3 — Thưởng mốc Lv10/20/30**: thư hệ thống cho CẢ HAI — đồ đệ **mốc × 50**, sư phụ **mốc × 100** linh thạch (Lv30:
1.500 / 3.000). Cấp nhảy qua nhiều mốc (thưởng quest) thì nhận đủ các mốc. Lv30 là **xuất sư**: quan hệ khép lại sau khi
thưởng. Số là PLACEHOLDER cân bằng.

**D4 — Móc lên cấp**: `CharacterLevelSync` (điểm DUY NHẤT mọi cú lên cấp đi qua) có thêm `LevelListener`; việc phụ, lỗi
dừng ở đó (log WARN, phần thưởng mất có dấu vết) — không làm hỏng cú lên cấp.

**D5 — Cắt quan hệ**: một trong hai cắt bất cứ lúc nào, không mất gì; chặn nhau (ADR-091) cũng cắt.

**D6 — Giao thức, chỉ thêm**: 1124/1125 `RequestMentorCommand{mentor_name}`, 1126 `MentorRequestReceivedEvent`,
1127/1128 `AcceptDiscipleCommand{disciple_name}`, 1129/1130 `LeaveMentorshipCommand{other_name}`; đọc qua 1122/1123
`GetRelationships` (ADR-091 D5). Outcome chung `MentorshipOutcome`.

**D7 — Client**: tab "Sư đồ" của cửa sổ Quan hệ (Q) — sư phụ/đồ đệ kèm nút Rời, lời bái sư đang chờ kèm nút Nhận, ô
tên + nút Bái sư; sự kiện bái sư báo một dòng trong chat.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Sư phụ mời, đồ đệ nhận | Người cần là đồ đệ — để họ chủ động thì sư phụ không bị làm phiền bởi lời mời hàng loạt |
| Thưởng EXP thay vì linh thạch | Đồ đệ đã đang lên cấp; vàng có ích cho cả hai, và không đẩy nhanh thêm đường cong |
| Thưởng cả khi đổi sư phụ | Một đồ đệ Lv29 đổi qua nhiều sư phụ sẽ đẻ thưởng mốc cho từng người |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | <Lv30 bái ≥Lv40, tối đa 3, mốc 10/20/30 thư cả hai một lần trọn đời, Lv30 xuất sư | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `V85__block_mentorship_dao_lu.sql` · `MentorshipPolicy` · `MentorshipUseCase` · `PostgresMentorshipAdapter` ·
  `MentorshipCommands` · `CharacterLevelSync.LevelListener`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 21 phần 1) — test `MentorshipUseCaseTest`.
2. ✅ Kiểm sống 2026-10-24: Benkei (Lv9) bái sư Kagemaru (Lv41) ⇒ Kagemaru nhận `MentorRequestReceivedEvent` (1126); Kagemaru nhận đồ đệ ⇒ `OK`. Benkei hạ hình nhân lên Lv10 ⇒ `mentorship_milestone` mốc 10 ghi một lần, thư 500 cho Benkei và 1.000 cho Kagemaru.
