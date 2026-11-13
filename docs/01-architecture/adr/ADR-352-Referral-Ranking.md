# ADR-352: Xếp hạng người giới thiệu

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Xếp hạng người giới thiệu" trong lượt hỏi lô 53 (AskUserQuestion; mô tả kèm: /topgioithieu: 10 người giới thiệu được nhiều người mới đạt cấp 10 nhất). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-320](ADR-320-Referral-Code.md) |

---

## Decision

**D1 — Nguồn:** bảng `referral` (ADR-320), chỉ đếm lượt đã chốt quà (`rewarded_at` có giá trị = người mới đã tới cấp 10) — lượt mới nhập mã chưa tính, tránh xếp hạng bằng mã nhập ảo.

**D2 — Dây:** `GetReferralRankingRequest{}` / `GetReferralRankingResponse{repeated ReferralRank{name, count}}` **287/288**. Client `/topgioithieu` (`/topreferral`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Top 10 theo số giới thiệu đã nhận quà; 287/288 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 53): `PlayerLookupPort.topReferrers`, `Lot53Commands.referralRanking`, `PlayerLookupCoordinator`.
2. ✅ Test: `Lot53IntegrationTest.ranksStreaksAndReferrers`.
3. LIVE352
