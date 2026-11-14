# ADR-366: % EXP trên thanh kinh nghiệm

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "% EXP trên thanh kinh nghiệm" trong lượt hỏi lô 54 (AskUserQuestion; mô tả kèm: thanh EXP hiện thêm phần trăm bên cạnh số EXP hiện tại/cần). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-034](ADR-034-Manual-Attack-And-Experience.md) · [ADR-334](ADR-334-Level-Goal.md) |

---

## Decision

**D1 — Hiện:** "đã đi / cần (X%)" từ số server gửi trong `ExperienceChangedEvent` (client không tự tính đường cong EXP); phần trăm làm tròn xuống, kẹp 0–100; cấp tối đa giữ chữ cũ. Chữ chỉ dựng lại khi số đổi (UI.md §9). Thuần client.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | "đã đi / cần (X%)" trên thanh EXP; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 54): `HudProgress.expText`.
2. ✅ Test: `HudProgressExpTextTest.appendsPercent`.
3. ⏳ Thuần client — cần chạy client thật để nhìn.
