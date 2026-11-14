# ADR-387: Rút gọn số sát thương

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Rút gọn số sát thương" trong lượt hỏi lô 57 (AskUserQuestion; mô tả kèm: Số sát thương bay hiện 1.2k / 3.4M; /sorutgon bật/tắt). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-350](ADR-350-Critical-Hit-Numbers.md) · [ADR-285](ADR-285-Hide-Others-Damage.md) |

---

## Decision

**D1:** dưới 1.000 giữ nguyên; từ 1.000 ⇒ `1.2k`; từ 1.000.000 ⇒ `3.4M`; một chữ số thập phân, CẮT không làm tròn, bỏ `.0`; dấu chấm thập phân cố định. Áp cho số trúng và chí mạng (ADR-350) trong `CombatFeedbackPresenter`.

**D2:** `/sorutgon` (`/compactdmg`) bật/tắt, nhớ trên máy, mặc định TẮT (giữ cảm giác cũ). Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, `CompactNumber.format` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 57): `CompactNumber`, `CompactDamageCoordinator`, `CombatFeedbackPresenter.useCompactNumbers`.
2. ✅ Test: `Lot57ClientTest.compactsNumbers`.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
