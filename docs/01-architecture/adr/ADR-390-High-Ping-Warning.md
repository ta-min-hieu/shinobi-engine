# ADR-390: Cảnh báo ping cao

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Cảnh báo ping cao" trong lượt hỏi lô 57 (AskUserQuestion; mô tả kèm: Ping trên 300 ms ba nhịp liền ⇒ báo một dòng + chữ bay). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-316](ADR-316-Ping-Hud-Toggle.md) |

---

## Decision

**D1:** nghe mỗi nhịp đo độ trễ heartbeat (cùng số liệu nhãn ping HUD, ADR-316) qua `CombatHud.useLatencyListener`; 3 nhịp liên tiếp > 300 ms ⇒ một dòng chat + chữ bay `Đường truyền chậm: ping N ms`, rồi im 20 nhịp để không spam.

**D2:** không đổi protocol; không tắt được riêng (chỉ báo khi thật sự chậm).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, ngưỡng 300 ms × 3 nhịp, nghỉ 20 nhịp | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 57): `ChatAlertsCoordinator.onLatency`, `CombatHud.useLatencyListener`.
2. ✅ Test: kiểm bằng mắt (cần mạng chậm thật).
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
