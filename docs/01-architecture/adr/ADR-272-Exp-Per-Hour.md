# ADR-272: Đồng hồ EXP/giờ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Đồng hồ EXP/giờ" trong lượt hỏi lô 42–43 (AskUserQuestion; mô tả kèm: Ước tính EXP/giờ và thời gian lên tầng tiếp theo). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-271](ADR-271-Session-Summary.md) |

---

## Decision

**D1 — Thuần client, cùng bộ đếm ADR-271.** `/exph`: EXP/giờ = EXP nhận từ đầu phiên ÷ thời gian phiên (cần ≥ 1 phút mới
ước tính); phút tới tầng kế = (EXP cần − EXP đã có trong tầng) ÷ tốc độ, làm tròn lên — dùng `exp_into_level` /
`exp_for_next_level` server gửi; cấp tối đa (`exp_for_next_level = 0`) hoặc chưa nhận EXP thì không in ước tính.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | `/exph`; tốc độ trung bình cả phiên; ETA từ 208 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 43): `SessionTracker.expPerHour/minutesToNextLevel`.
2. ✅ Test: `Lot43ClientTest.expRateAndEta`.
