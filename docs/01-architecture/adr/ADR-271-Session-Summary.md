# ADR-271: Tóm tắt phiên chơi

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Tóm tắt phiên chơi" trong lượt hỏi lô 42–43 (AskUserQuestion; mô tả kèm: /phien: EXP, vàng, quái hạ, đồ nhặt từ lúc đăng nhập). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-223](ADR-223-Personal-Stats.md) (thống kê trọn đời — server) |

---

## Decision

**D1 — Thuần client, chỉ nghe gói server đã gửi.** `SessionSummaryCoordinator` nghe (không giành) `ExperienceChangedEvent`
(208 — gói đầu tiên lúc vào world chỉ ghi mốc), `GoldChangedEvent` (209 — `delta` dương là thu, âm là chi),
`ItemGrantedEvent` (500 — số lượng), và đòn `AttackResultEvent`/`SkillResultEvent` của CHÍNH mình có `target_died`. Không
tự suy gameplay; mọi con số là tổng các gói server.

**D2 —** `/phien` (hoặc `/session`) in số phút đã chơi, EXP nhận, số lần lên cấp, linh thạch thu/chi, mục tiêu đã hạ, vật
phẩm nhận. Đếm trong RAM: rời màn world (kể cả tự đăng nhập lại) là phiên mới.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | nghe 208/209/500/402/406; `/phien`; RAM | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 43): `SessionTracker`, `SessionSummaryCoordinator`.
2. ✅ Test: `Lot43ClientTest.sessionTrackerCounts`.
