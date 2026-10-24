# ADR-087: Mùa Luận Kiếm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-24) — chủ dự án chọn "Mùa Luận Kiếm" trong lượt hỏi lô 20 (AskUserQuestion; mô tả kèm: mùa PvP 4 tuần, hết mùa thưởng theo hạng Elo qua thư, Elo kéo về gần 1000; lịch DB, thông báo toàn server). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-24 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | feature-pvp/CLAUDE.md ("KHÔNG season/decay" — nay có mùa) · [ADR-057](ADR-057-Leaderboards.md) |

---

## Decision

**D1 — Mùa là DỮ LIỆU** (V82 `pvp_season`: số mùa, bắt đầu, hết hạn, lúc đóng; đúng một mùa đang mở — chỉ mục duy
nhất có điều kiện). Migration mở mùa 1 dài 28 ngày.

**D2 — Chốt mùa nguyên tử** (feature-pvp `PvpSeasonPort#closeSeason`): khoá dòng mùa → khoá mọi dòng Elo theo
`character_id` tăng dần (cùng thứ tự chốt trận — trận settle đúng lúc chốt mùa xếp hàng, không deadlock) → ghi HÀNG
ĐỢI THƯỞNG (`pvp_season_payout`: hạng theo Elo giảm dần, hoà thì id nhỏ trước; chỉ người đã đánh ≥ 1 trận) → kéo Elo
mọi người nửa đường về 1000 (`softReset`: 1200 ⇒ 1100, 900 ⇒ 950) → đóng mùa → mở mùa kế 28 ngày. Thắng/thua tích luỹ
giữ nguyên.

**D3 — Gửi thưởng từ hàng đợi** (`PvpSeasonScheduler`, gateway, hỏi mỗi 60 s): mỗi dòng ⇒ THƯ HỆ THỐNG theo bậc thưởng
(`pvp_season_reward`, bậc đầu tiên phủ hạng), khoá chống trùng suy từ (mùa, nhân vật) ⇒ xoá dòng. Server chết giữa
vòng gửi thì lượt sau gửi tiếp, không gửi đôi. Thông báo toàn server lúc chốt.

**D4 — Không đổi protocol** (thông báo đi chat hệ thống, thưởng đi thư; Bia Anh Danh thẻ Luận Kiếm vẫn đọc Elo hiện tại).

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Reset Elo về đúng 1000 | Xoá sạch thứ bậc — đầu mùa ghép người giỏi với người mới; kéo nửa đường giữ dấu vết |
| Gửi thư ngay trong giao dịch chốt | Thư + chốt mùa chung một giao dịch dài; lỗi một thư làm hỏng cả lượt chốt |

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Mùa 28 ngày trong DB, chốt nguyên tử + hàng đợi thưởng, kéo Elo nửa đường | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- `V82__pvp_seasons.sql` · `PostgresPvpSeasonAdapter` · `PvpSeasonScheduler`

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-24 (lô 20).
2. ⏳ Kiểm sống (đặt `ends_at` mùa 1 về quá khứ trên DB dev).
