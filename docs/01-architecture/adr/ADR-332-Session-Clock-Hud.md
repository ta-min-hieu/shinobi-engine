# ADR-332: Đồng hồ phiên chơi trên HUD

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Đồng hồ phiên chơi trên HUD" trong lượt hỏi lô 50 (AskUserQuestion; mô tả kèm: nhãn nhỏ góc màn hình đếm thời gian đã chơi phiên này; /dongho bật-tắt). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-271](ADR-271-Session-Summary.md) · [ADR-283](ADR-283-Fps-Counter.md) |

---

## Decision

**D1 — Nhãn** "Đã chơi: H:MM:SS" góc dưới trái (cụm FPS / đếm người), tính từ lúc dựng màn world bằng giờ máy. Đọc mỗi khung nhưng chỉ đổi chữ khi sang giây mới, dựng vào `StringBuilder` có sẵn — không cấp phát mỗi khung (CLAUDE.md §6).

**D2 — Công tắc:** `/dongho` (`/clock`), mặc định TẮT, nhớ trên máy. Khác `/phien` (ADR-271): đó là bảng tóm tắt in một lần, đây là đồng hồ luôn hiện.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Nhãn HUD H:MM:SS không cấp phát; `/dongho`; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 50): `HudInfoLines` (ui/hud), `CombatHud.useSessionClock`/`setSessionClockVisible`, `SessionClockCoordinator`.
2. ✅ Test: `HudInfoLinesTest.formatsClock`.
3. ⏳ Thuần client — cần chạy client thật để nhìn nhãn.
