# ADR-264: Đếm ngược nhóm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Đếm ngược nhóm" trong lượt hỏi lô 42–43 (AskUserQuestion; mô tả kèm: /demnguoc 3-10: server phát 3…2…1…Bắt đầu! cho cả nhóm). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-263](ADR-263-Party-Ready-Check.md) |

---

## Decision

**D1 —** `StartPartyCountdownCommand{seconds}` → `StartPartyCountdownResponse{outcome}` (1341/1342). Chỉ trưởng nhóm;
`seconds` 3–10 (`INVALID_SECONDS`); mỗi nhóm một lượt đếm (`ALREADY_ACTIVE`). Server phát dòng mở đầu, rồi mỗi giây một
dòng `N…` và cuối cùng `Bắt đầu! / Go!` tới các thành viên online lúc bắt đầu — dòng chat hệ thống, không event mới.

**D2 —** Hẹn giờ RAM (`PartySignals`), cùng lý do ADR-263. Client `/demnguoc [giây]` (hoặc `/countdown`), mặc định 5.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 1341/1342; 3–10 giây; nhịp 1 giây qua dòng chat hệ thống | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 42): `PartySignals.startCountdown`, `Lot42Commands.startCountdown`, `PartyCountdownCoordinator`.
2. ✅ Test: `PartySignalsTest.countdownBoundsAndText`, `Lot42ClientTest`.
