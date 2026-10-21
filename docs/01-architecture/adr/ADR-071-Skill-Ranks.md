# ADR-071: Điểm công pháp — nâng bậc chiêu thức 1→5 bằng điểm theo cảnh giới

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-21) — chủ dự án chọn "Điểm công pháp" trong lượt hỏi lô 17 (AskUserQuestion; mô tả kèm: mỗi tầng cảnh giới cho điểm; nâng cấp công pháp bằng điểm; phím K — khi làm thì K đã thuộc Luận Kiếm nên dùng R). Câu hỏi mở theo khuyến nghị. AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-21 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [GDD §5](../../13-game-design/GAME-DESIGN-DOCUMENT.md) ("Điểm công pháp từ cảnh giới + nhiệm vụ — không mua bằng tiền thật") · [ADR-066](ADR-066-Faction-Change.md) |

---

## Decision

**D1 — Điểm** = `cấp / 2` (một điểm mỗi tầng cảnh giới ở 5 cảnh giới đầu — ADR-038). Không lưu "điểm còn lại":
điểm còn = tổng − đã tiêu, đã tiêu = Σ(bậc − 1) của các chiêu thuộc LINH CĂN HIỆN TẠI. Lên cấp tự có điểm,
không cần ghi gì.

**D2 — Bậc chiêu 1–5** (`character_skill_rank`, V72). Mỗi bậc trên 1 tăng **10%** con số chiêu thức (sát thương,
hồi máu, khiên) — bậc 5 = +40%. Không đổi hồi chiêu/Linh Lực (Q1 theo khuyến nghị: một trục cho dễ cân bằng).

**D3 — Đổi linh căn**: bậc giữ theo TỪNG chiêu; điểm chỉ tính trên chiêu của linh căn hiện tại ⇒ đổi sang hệ mới
có nguyên quỹ điểm, đổi về hệ cũ lấy lại bậc cũ. Không có "tẩy điểm" (Q2 theo khuyến nghị).

**D4 — Kiểm ở server**: chiêu phải thuộc linh căn hiện tại, đủ cấp mở chiêu, bậc < 5, còn điểm. Danh sách chiêu
từ `SkillRegistry` của server-world (gateway hỏi `WorldFacade.skillsOf`). Nâng xong áp ngay vào world.

**D5 — Giao thức, chỉ thêm**: 409/410 `ListSkillRanksRequest/Response`, 411/412 `UpgradeSkillCommand/Response`.
World nhận bậc qua `CharacterEntry` (mọi đường attach) và `enqueueSkillRanks` sau khi nâng.

**D6 — Client**: cửa sổ "Công pháp" phím **R** (K đã là phím Luận Kiếm) — 4 dòng chiêu kèm bậc và nút "Nâng".

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Lưu điểm còn lại | Phải đồng bộ với lên cấp/đổi hệ; tính lại từ cấp không bao giờ lệch |
| Bậc giảm hồi chiêu | Hai trục chồng nhau; để sau khi có số đo |
| Tẩy điểm bằng vật phẩm | Chưa cần — đổi linh căn đã cho quỹ mới |

## Consequences

- Sát thương chiêu tăng tối đa 40% ở bậc 5 — các số cân bằng trận đã đo (luật biên) chưa tính bậc chiêu.

## Revisit Trigger

- Đo lại luật biên trận arc 1–2 có bậc chiêu; muốn điểm từ nhiệm vụ.

## Technical Decisions

- `SkillRankPort`/`SkillRankPolicy`/`SkillRankUseCase`/`SkillRankFacade` (feature-character); `SkillRankComponent` (server-world).

## ADR cần tạo

Không.

## Tài liệu liên quan

- `V72__skill_ranks.sql` · `SkillSystem` · `SkillRankCoordinator`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-21 (lô 17).
2. ⏳ Kiểm sống: nâng chiêu 1 lên bậc 3, đánh quái, sát thương ≈ +20%.
