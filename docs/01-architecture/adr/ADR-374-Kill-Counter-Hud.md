# ADR-374: Đếm quái đã hạ trên HUD

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Đếm quái đã hạ trên HUD" trong lượt hỏi lô 55 (AskUserQuestion; mô tả kèm: nhãn nhỏ đếm số quái bạn đã hạ trong phiên này; /demquai bật-tắt). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-271](ADR-271-Session-Summary.md) |

---

## Decision

**D1 — Nguồn:** bộ đếm phiên của `/phien` (ADR-271, đếm khi đòn của chính mình làm quái chết). Nhãn "Đã hạ: N" cùng cụm góc dưới trái; chỉ đổi chữ khi số đổi, không cấp phát.

**D2 — Công tắc** `/demquai` (`/killcount`), mặc định tắt, nhớ trên máy.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Nhãn "Đã hạ: N" từ bộ đếm phiên; `/demquai` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 55): `SessionSummaryCoordinator.kills`, `HudInfoLines.updateKills`, `CombatHud.useKillCount`, `ChatHudExtrasCoordinator`.
2. ✅ Test: bộ đếm phiên đã có test (`SessionTracker`); nhãn kiểm bằng client thật.
3. ⏳ Thuần client — cần chạy client thật.
