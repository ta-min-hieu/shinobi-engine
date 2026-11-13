# ADR-355: % máu mục tiêu

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "% máu mục tiêu" trong lượt hỏi lô 53 (AskUserQuestion; mô tả kèm: khung mục tiêu hiện thêm phần trăm máu bên cạnh số máu hiện tại/tối đa). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-299](ADR-299-Target-Distance.md) |

---

## Decision

**D1 — Hiện:** "hiện tại / tối đa (X%)" cho cả quái và nhân vật đang nhắm khi biết máu tối đa; phần trăm làm tròn xuống, kẹp 0–100. Chữ chỉ dựng lại khi số máu đổi (UI.md §9). Thuần client, không công tắc.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | "hiện tại / tối đa (X%)" trong khung mục tiêu; thuần client | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 53): `HudTargetFrame.hpText`.
2. ✅ Test: `HudTargetHpTextTest.appendsPercent`.
3. ⏳ Thuần client — cần chạy client thật để nhìn.
