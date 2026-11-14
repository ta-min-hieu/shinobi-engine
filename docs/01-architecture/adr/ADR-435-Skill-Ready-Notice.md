# ADR-435: Báo kỹ năng sẵn sàng

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Báo kỹ năng sẵn sàng" trong lượt hỏi lô 63 (AskUserQuestion; mô tả kèm: Chữ bay "Sẵn sàng!" khi kỹ năng vừa hết hồi chiêu; /baokynang bật/tắt). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-331](ADR-331-Floating-Exp-Gain.md) |

---

## Decision

**D1 — Phát hiện:** `CombatHud.setSkillReady` thấy chuyển chưa-sẵn-sàng → sẵn-sàng thì gọi listener (chỉ cạnh lên, không lặp mỗi khung).

**D2 — Hiện:** chữ bay trên đầu mình (`FloatingCombatText.LEVEL_UP`) "Sẵn sàng!"/"Ready!". Mặc định BẬT, `/baokynang` (`/skillready`) bật/tắt, nhớ trong Preferences. Chỉ client.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Chỉ client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 63): `CombatHud.useSkillReadyListener`, `HudPolishCoordinator.onSkillReady`, `ExpansionWindows.wireLot63Display`.
2. ✅ Test: Biên dịch + kiểm bằng mắt (không có test UI tự động cho chữ bay).
3. Chỉ client — biên dịch + check client xanh.
