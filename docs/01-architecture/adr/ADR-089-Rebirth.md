# ADR-089: Độ Kiếp Chuyển Thế

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Độ Kiếp Chuyển Thế" trong lượt hỏi lô 20 (AskUserQuestion; mô tả kèm: GDD §5 — Lv100 chuyển thế về Lv1, giữ đồ, nhận Đạo Vận cộng chỉ số vĩnh viễn, tối đa 5 lần; lưu ý nội dung hiện chưa đưa người chơi tới Lv100). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [GDD §5](../../13-game-design/GAME-DESIGN-DOCUMENT.md) "Cảnh giới & Độ Kiếp Chuyển Thế" · [ADR-038](ADR-038-Theme-Pivot-Tu-Tien.md) D2 · [ADR-077](ADR-077-Mental-Arts.md) (ống chỉ số chung) |

---

## Decision

**D1 — Điều kiện**: cấp 100 (`ExperienceCurve.MAX_LEVEL`) và đã chuyển thế < **5** lần. Dùng cột
`character.rebirth_count` có sẵn từ V2 — **không migration**.

**D2 — Hiệu lực**: cấp 1, EXP 0, `rebirth_count + 1`, trong MỘT giao dịch dưới khoá dòng nhân vật (audit `REBIRTH`).
GIỮ: pháp khí (kể cả món yêu cầu cấp cao đang mặc), danh hiệu, linh căn, bậc chiêu/tâm pháp (điểm công pháp tính theo
cấp nên đầu kiếp mới không có điểm để nâng thêm).

**D3 — Đạo Vận**: mỗi lần chuyển thế **+30 máu tối đa, +4 công/thủ/kháng**, vĩnh viễn — đi chung ống chỉ số trang bị
(cùng chỗ cộng kỹ năng bang), nên bảng nhân vật/Lực Chiến/trận đánh đều thấy. Số là PLACEHOLDER cân bằng.

**D4 — World**: lệnh mới `ResetCharacterLevelCommand` (lần DUY NHẤT cấp được lùi — lệnh lên cấp vẫn bỏ qua cấp thấp
hơn), rồi gateway áp lại chỉ số (máu tối đa theo cấp mới, máu hiện tại giữ tỉ lệ) và gửi `ExperienceChangedEvent`
cấp 1. Gateway đăng ký lại kết nối để mốc "EXP đã gửi" về rỗng (nếu không, gói EXP sau chuyển thế bị coi là gói muộn).

**D5 — Giao thức, chỉ thêm**: 212/213 `RebirthCommand` / `RebirthResponse{outcome, rebirth_count}`;
`GetCharacterStatsResponse.rebirth_count = 21`. Client: bảng nhân vật (C) hiện số lần + nút "Độ Kiếp" khi đủ cấp.

## Consequences

- Nội dung hiện tại chưa đưa ai tới cấp 100 — tính năng nằm chờ nội dung arc sau; kiểm sống bằng cấp đặt qua SQL dev.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cấp 100, tối đa 5 lần, về cấp 1 giữ đồ, Đạo Vận cộng phẳng, 212/213 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `RebirthUseCase` · `PostgresRebirthAdapter` · `RebirthEffects` · `WorldFacade#resetCharacterLevel`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 20).
2. ✅ Kiểm sống 2026-10-24 (tanthua44, SQL đặt level 100 + exp 495000 — cấp suy từ EXP lúc vào world nên chỉ đặt `level` là bị kéo về): `REBIRTH_OUTCOME_OK` số lần 1, gói EXP về cấp 1; lần hai `LEVEL_TOO_LOW`. Bảng nhân vật: trang bị +78 → +108 máu, công/thủ/kháng +4. Giết quái sau đó nhận EXP bình thường (mốc EXP đã gửi được xoá đúng). Đã trả nhân vật về Lv15 sau phép đo.
