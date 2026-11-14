# ADR-379: Chữ bay khi nhận linh thạch

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Chữ bay khi nhận linh thạch" trong lượt hỏi lô 56 (AskUserQuestion; mô tả kèm: Chữ "+N linh thạch" bay trên đầu nhân vật khi nhận linh thạch; /chuvang bật/tắt). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-331](ADR-331-Floating-Exp-Gain.md) · [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) |

---

## Decision

**D1:** nghe `GoldChangedEvent` (209), lấy thẳng `delta` > 0 (tiêu thì không hiện) — không cần mốc như chữ +EXP (ADR-331). Màu vàng `FFE27A` riêng (`FloatingCombatText.GOLD_GAIN`), vẽ qua cùng `showSelfText`. Chỉ nghe, không nuốt gói.

**D2:** `/chuvang` (`/goldgain`) bật/tắt, nhớ trên máy (mặc định bật). Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, nghe 209, `delta` > 0 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 56): `GoldGainCoordinator`, `ExpansionWindows.wireLot50Display`.
2. ✅ Test: đường dùng chung với ADR-331; kiểm bằng mắt.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
