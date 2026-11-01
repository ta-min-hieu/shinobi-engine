# ADR-135: Nhiệm vụ ngày nội môn

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-01) — chủ dự án chọn "Nhiệm vụ ngày nội môn" trong lượt hỏi lô 25 (AskUserQuestion; mô tả kèm: nhiệm vụ ngày cho Lv37+ ở sân nội môn — đấu tập với cỗ khảo hạch, tuần tra cổng). Con số do AI chọn. |
| Date | 2026-11-01 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-055](ADR-055-Daily-Quests.md) (nhiệm vụ ngày) · [ADR-130](ADR-130-Arc-Three-Inner-Sect.md) (arc 3) |

---

## Decision

**D1 — Hai nhiệm vụ ngày mới** (V111, `repeat_policy = 'DAILY'`, làm mới lúc 0h giờ VN như ADR-055):

| Mã | Mục tiêu | Thưởng |
|---|---|---|
| `daily_inner_sparring` — Đấu Tập Nội Môn | hạ 2 `inner_trial_construct` (@232) | 4.000 EXP + 300 linh thạch |
| `daily_gate_patrol` — Tuần Tra Cổng Nội Môn | hạ 3 `blood_sand_infiltrator` (@248/@264) | 5.000 EXP + 400 linh thạch |

**D2 — Tiền đề `inner_sect_examination`** (quest mở arc 3, ADR-044: phải CLAIMED) — chỉ đệ tử đã qua khảo hạch nội
môn mới nhận. Lựa chọn hội thoại vẫn hiện cho mọi người (ADR-044: server từ chối, không giấu lựa chọn).

**D3 — Nhận ở nhánh "việc tông môn" của Chu Hoành**, sau hộ tống; nhánh này không bị ghim vị trí. Không đổi giao thức.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 2 nhiệm vụ ngày Lv37+ (tiền đề khảo hạch nội môn), số ở script | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-01: V111, 2 script, `town_elder.js`, i18n, `QuestFuzzSpec`.
2. ✅ **Kiểm sống bằng gameProbe 2026-11-01** (DB dev lên V111): Kagemaru (đã qua khảo hạch nội môn) chọn việc tông môn → Đấu Tập Nội Môn ⇒ `ACCEPT_QUEST_OUTCOME_OK`; hieutm (Cấp 3) chọn cùng lựa chọn ⇒ `PREREQUISITE_NOT_MET`.
