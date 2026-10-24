# ADR-095: Bế quan — EXP khi offline

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Bế quan (EXP offline)" trong lượt hỏi lô 21 (AskUserQuestion; mô tả kèm: offline tích EXP theo cấp, tối đa 8 giờ, nhận khi đăng nhập (trả vàng để nhận x2); migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | GDD §9 (vòng chơi 15–25 phút) · [ADR-055](ADR-055-Daily-Quests.md) · `PostgresQuestExpAdapter` (cùng đường cộng EXP ngoài giết quái) |

---

## Decision

**D1 — Đo thời gian offline**: V86 thêm `character.last_logout_at` (ghi lúc rời world — mọi đường dọn kết nối). Lần
vào world sau, `now − last_logout_at` (phút, làm tròn xuống) cộng vào **quỹ bế quan** `seclusion_bank.banked_minutes`,
quỹ tối đa **480 phút (8 giờ)**. Nhân vật chưa từng thoát (cột rỗng) không tích gì lần đầu.

**D2 — Quy ra EXP lúc nhận**: `phút × max(1, EXP cần cho cấp hiện tại / 480)` — **8 giờ bế quan ≈ 1 cấp** ở cấp hiện tại
(tính theo cấp LÚC NHẬN). Nhận **x2** thì trả **cấp × 50 linh thạch** (đốt, `gold.reason.seclusion`). Số là PLACEHOLDER
cân bằng.

**D3 — Nguyên tử**: một giao dịch khoá dòng quỹ → (x2) trừ vàng qua GoldLedger → cộng `character.exp` → xoá quỹ → audit
`SECLUSION_CLAIMED`; rồi gateway đồng bộ cấp như thưởng quest (`syncExperienceAfterQuestReward` → `sendExperience`),
nên lên cấp tới world/HUD/sư đồ như mọi đường khác. Không cộng qua ống EXP đánh quái (sự kiện x2, kỹ năng bang, đạo lữ
KHÔNG áp vào bế quan).

**D4 — Giao thức, chỉ thêm**: 214/215 `GetSeclusionStatusRequest`/`Response{banked_minutes, exp_x1, double_cost}`,
216/217 `ClaimSeclusionCommand{doubled}`/`Response{outcome, exp_gained}`. Vào world có quỹ > 0 ⇒ một dòng hệ thống.

**D5 — Client**: cửa sổ **Hằng ngày (phím E)** tab "Bế quan" — quỹ, EXP x1, phí x2, hai nút nhận.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Tự cộng EXP lúc đăng nhập | Bỏ mất lựa chọn x2 (gold sink) và người chơi không thấy mình nhận gì |
| Quy EXP theo cấp lúc THOÁT | Phải lưu cấp vào quỹ; khác biệt nhỏ, mà tính lúc nhận thì đơn giản và không lợi dụng được |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | `last_logout_at` + quỹ ≤480 phút, 8 giờ ≈ 1 cấp, x2 = cấp × 50 vàng, cộng exp trong cùng giao dịch | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `V86__lot21_daily.sql` · `SeclusionPolicy` · `PostgresSeclusionAdapter` · `DailyCommands` · `DailyCoordinator` (client)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 21 phần 2) — test `SeclusionPolicyTest`, `DailyIntegrationTest`.
2. ✅ Kiểm sống 2026-10-24: đặt `last_logout_at` lùi 3 giờ ⇒ vào world nhận dòng "Bế quan được 3 giờ 0 phút", quỹ 180 phút, x1 = 1.440 EXP (Lv41); nhận x2 ⇒ +2.880 EXP, trừ 2.050 linh thạch, `ExperienceChangedEvent` tới ngay, quỹ về 0. Bẫy đo: lệnh gửi ngay lúc vào world có thể tới TRƯỚC khi quỹ kịp tích (probe dùng `wait:3`).
