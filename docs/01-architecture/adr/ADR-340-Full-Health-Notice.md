# ADR-340: Báo hồi đầy máu

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Báo hồi đầy máu" trong lượt hỏi lô 51 (AskUserQuestion; mô tả kèm: dòng chữ nhỏ 'Đã hồi đầy máu' khi máu hồi lại 100% sau chiến đấu (có công tắc)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-046](ADR-046-Health-Recovery.md) · [ADR-243](ADR-243-Low-Health-Warning.md) |

---

## Decision

**D1 — Khi nào:** đã mất máu (trúng đòn đánh/kỹ năng, hoặc một `HealthChangedEvent` của chính mình dưới tối đa) rồi một `HealthChangedEvent` về đúng tối đa ⇒ một dòng hệ thống "Đã hồi đầy máu". Mỗi lần hồi chỉ báo một lần. Không đổi protocol.

**D2 — Công tắc:** `/baodaymau` (`/fullhp`), mặc định bật, nhớ trên máy.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cờ "đang bị thương" + `HealthChangedEvent` về tối đa ⇒ một dòng; `/baodaymau`; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 51): `FullHealthCoordinator` (+ `Tracker`).
2. ✅ Test: `Lot51ClientTest.fullHealthNotifiesOncePerRecovery`.
3. ⏳ Thuần client — cần chạy client thật (probe không in dòng client).
