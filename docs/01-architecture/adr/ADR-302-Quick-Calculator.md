# ADR-302: Máy tính nhanh

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Máy tính nhanh" trong lượt hỏi lô 46 (AskUserQuestion; mô tả kèm: /tinh 1200*3+50: tính biểu thức số học ngay trong chat). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | — |

---

## Decision

**D1 — Thuần client.** `/tinh <biểu thức>` (hoặc `/calc`): `+ - * / ( )`, số thập phân, dấu âm; `x`→`*`, `:`→`/`,
`,`→`.`. Bộ phân tích đệ quy xuống viết tay (`ArithmeticExpression`, `BigDecimal`), KHÔNG eval/script — biểu thức sai,
chia cho 0, dài quá 120 ký tự hoặc lồng ngoặc quá 32 tầng ⇒ báo cách dùng. Chỉ máy mình thấy.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Phân tích đệ quy xuống, `BigDecimal`; `/tinh` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 46): `ArithmeticExpression`, `CalculatorCoordinator`.
2. ✅ Test: `Lot46ClientTest.calculatorEvaluatesArithmetic`.
3. ⏳ Thuần client — probe headless không chạy lệnh gạch chéo/không vẽ UI; phủ bằng test đơn vị + biên dịch, cần kiểm tay trên client thật.
