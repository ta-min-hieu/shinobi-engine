# ADR-405: Đồng hồ giao chiến

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Đồng hồ giao chiến" trong lượt hỏi lô 59 (AskUserQuestion; mô tả kèm: HUD hiện thời gian đang giao chiến (từ đòn đầu tới khi hết đánh 5 giây)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-226](ADR-226-Combat-Log.md) · [ADR-396](ADR-396-Coordinates-Hud.md) |

---

## Decision

**D1:** mỗi `AttackResultEvent`/`SkillResultEvent` mà mình là người đánh hoặc bị đánh ⇒ đang giao chiến; mốc bắt đầu là đòn đầu, kết thúc khi 5 giây không có đòn nào. HUD dòng "Giao chiến H:MM:SS" (dòng số chung ADR-396), ngoài giao chiến để trống.

**D2:** `/giaochien` (`/combattimer`) bật/tắt, nhớ trên máy, mặc định bật. Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, nghỉ 5 giây = hết giao chiến | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 59): `CombatTimerCoordinator`, `CombatHud.addCounterLine`.
2. ✅ Test: kiểm bằng mắt; dòng HUD dùng chung ADR-396.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
