# ADR-320: Mã giới thiệu bạn

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Mã giới thiệu bạn" trong lượt hỏi lô 49 (AskUserQuestion; mô tả kèm: mỗi nhân vật có mã; người mới nhập mã, khi họ lên cấp 10 cả hai nhận thư quà). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-227](ADR-227-Starter-Gift.md) · [ADR-058](ADR-058-Send-Mail-Idempotency.md) |

---

## Decision

**D1 — Mã = tên nhân vật** người giới thiệu (không sinh mã riêng). Bảng `referral` (V175): mỗi nhân vật được giới thiệu
MỘT lần (khoá chính `referred_id`). Chặn: tự giới thiệu, nhân vật CÙNG TÀI KHOẢN (chống tự nuôi), nhập khi đã từ cấp 10.

**D2 — Quà:** người được giới thiệu lên đúng cấp 10 ⇒ `UPDATE … WHERE rewarded_at IS NULL RETURNING` chốt MỘT lần ⇒ cả
hai nhận thư hệ thống 2.000 linh thạch (khoá chống trùng theo người nhận). Chạy trong bộ nghe lên cấp (Virtual Thread).

**D3 — Dây:** `EnterReferralCommand{referrer_name}` (rỗng = hỏi mã của mình) / `EnterReferralResponse{outcome, code,
reward_level}` **275/276**. Client `/magioithieu [tên]`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Mã = tên; bảng `referral`; quà một lần ở cấp 10; 275/276 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 49): `ReferralPort`/`Facade`, `PostgresReferralAdapter`, `Lot49Commands.enterReferral`/`onLevelReached`, `ReferralCoordinator`.
2. ✅ Test: `Lot49IntegrationTest.referralRegistersOnceAndRewardsOnce`, `Lot49ServerTest.referralKeysDifferPerRecipient`.
3. ⏳ Kiểm sống gameProbe.
