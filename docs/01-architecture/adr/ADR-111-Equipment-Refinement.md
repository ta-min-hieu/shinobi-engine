# ADR-111: Tẩy luyện pháp khí

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-25) — chủ dự án chọn "Tẩy luyện pháp khí" trong lượt hỏi lô 22 (AskUserQuestion; mô tả kèm: mỗi pháp khí có 1–3 dòng chỉ số phụ ngẫu nhiên (server sinh); dùng Tẩy Luyện Thạch để quay lại dòng, khoá được 1 dòng bằng giá cao hơn; migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-25 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-064](ADR-064-Spirit-Jade-Sockets.md) (Linh Ngọc — cùng khuôn chỉ số cộng phẳng theo món) |

---

## Decision

**D1 — Dữ liệu (V91)**: `item_refinement(inventory_item_id, line_index, stat, value)` khoá chính `(món, dòng)`, CASCADE
theo món. Vật phẩm mới `refine_stone` (Tẩy Luyện Thạch, xếp chồng 99) — rơi từ Bí Cảnh/boss thế giới về sau; bản đầu
có trong cửa hàng (200 vàng) và cửa hàng danh vọng bậc 1 (40 điểm).

**D2 — Tẩy**: chỉ món đang ở ô trang bị có chỉ số (không linh thú, không phi kiếm). Lần đầu: server gieo 1–3 dòng. Lần
sau: gieo lại MỌI dòng không khoá, số dòng giữ nguyên. Mỗi dòng: `ATK`/`DEF`/`RES` 1..(2 + cấp yêu cầu/10), `HP`
5..(10 + cấp yêu cầu). Giá 1 đá; khoá MỘT dòng (giữ nguyên dòng đó) giá 3 đá. Một giao dịch: khoá túi → đủ đá → trừ đá
→ ghi dòng mới → audit `ITEM_REFINED` (dòng cũ + mới). Server sinh ngẫu nhiên (Điều 15).

**D3 — Chỉ số**: cộng PHẲNG vào ống chỉ số trang bị (như Linh Ngọc), không nhân theo cường hoá; áp lại ngay sau khi tẩy.

**D4 — Giao thức, chỉ thêm**: 524/525 `ListRefinementsRequest`/`Response{items[]}` (các món đang mặc + dòng),
526/527 `RefineItemCommand{item_id, locked_line}`/`Response{outcome, item}` (`locked_line = -1` = không khoá).

**D5 — Client**: cửa sổ "Tẩy luyện" phím **X**: mỗi món đang mặc một dòng tiêu đề + các dòng chỉ số, nút Tẩy và nút
"Giữ dòng i rồi tẩy".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 1–3 dòng gieo ở server, đá tẩy 1 / khoá dòng 3, cộng phẳng, cửa sổ phím X | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-25 (lô 22 phần 3). **Số hiệu migration là V91** đúng như D1. `RefinementPolicy` (gieo, giá,
   biên độ) + `RefinementPort` (domain feature-inventory), `RefinementFacade`, `PostgresRefinementAdapter` (khoá túi
   chung → khoá dòng món phải đang mặc ở ô có chỉ số → trừ đá qua `TemplateQuantityConsumer` → ghi đè dòng → audit
   `ITEM_REFINED` kèm dòng cũ/mới). Chỉ số đi chuỗi `bindGuildBonus` (`RefinementEffects`, cạnh sổ sưu tầm) — chỉ món
   ĐANG MẶC được cộng (SQL nối `equipment_slot`), nên tháo đồ là mất chỉ số ngay. Cửa sổ phím X (`RefinementCoordinator`).
   **Chi tiết AI tự chọn ngoài D1–D5**: món đã tẩy chịu cùng luật món đã khảm — kho đồ, thư, phường thị TỪ CHỐI nó
   (`isSocketed`/SQL `NOT EXISTS item_refinement`), vì các đường đó dời đồ bằng xoá/tạo dòng và dòng tẩy sẽ mất theo.
   Phân giải vẫn cho (huỷ món là huỷ dòng). Giao dịch dời nguyên dòng nên giữ dòng tẩy.
2. ✅ Kiểm sống 2026-10-25 (gameProbe `--lot21=refines,refine:<id>:<dòng>`): Kagemaru 6 đá → tẩy vũ khí (ATK+5, còn 5) → giữ dòng 0 tẩy lại (dòng giữ nguyên, còn 2) → phi kiếm `NOT_REFINABLE`, giữ dòng 5 `INVALID_LOCK` (không trừ đá). Bảng nhân vật: công 99 khi xoá dòng (đối chứng) → 104 khi có dòng ATK+5. Audit `ITEM_REFINED` ghi dòng cũ/mới.
