# ADR-169: Sổ vàng cá nhân

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-04) — chủ dự án chọn "Sổ vàng cá nhân" trong lượt hỏi lô 30 (AskUserQuestion; mô tả kèm: xem 50 lần vàng thay đổi gần nhất của mình (lý do, số tiền, số dư) — đọc từ nhật ký sẵn có). AI soạn văn bản và chọn chi tiết sau khi được chọn. ⚠️ Lệch mô tả lúc hỏi: **không có nhật ký vàng sẵn có** — mỗi lần đổi vàng chỉ được đẩy cho client sau commit rồi bỏ đi; `audit_log` chỉ ghi vài đường với định dạng khác nhau. Nên phải thêm bảng mới (D1). |
| Date | 2026-11-04 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) D3 (`GoldLedger` — điểm ghi vàng DUY NHẤT, `GoldLedgerSourceGuardTest` canh) |

---

## Decision

**D1 — Dữ liệu (V135)**: `gold_ledger_entry(character_id, delta, balance, reason_key, created_at)`, chỉ mục
`(character_id, id DESC)`. Không khoá ngoại (ghi cả cho nhân vật đã xoá — chỉ là lịch sử).

**D2 — Ghi ở MỘT chỗ, cùng transaction**: `GoldLedger.stage` (gọi sau MỌI lần `deduct`/`credit` thành công) chèn một dòng
trên CHÍNH connection của lần đổi ⇒ rollback thì dòng sổ cũng mất, commit thì chắc chắn có. Vì `GoldLedgerSourceGuardTest`
cấm mọi câu `SET gold` ngoài `GoldLedger`, sổ đủ 100% các đường đổi vàng (37 lý do hiện có) mà không sửa đường nào.
Chi phí: thêm một INSERT mỗi lần vàng đổi — vàng không đổi theo tick.

**D3 — Giữ 30 ngày**: `RetentionJanitor` thêm luật dọn thứ ba (cùng nhịp 6 giờ, lỗi luật này không kéo luật khác).

**D4 — Giao thức, chỉ thêm**: `GetGoldHistoryRequest` / `GetGoldHistoryResponse{entries[]{delta, balance, reason_key,
at_epoch_ms}}` — 247/248; 50 dòng mới nhất của CHÍNH người hỏi.

**D5 — Client**: cửa sổ `/sovang` (hoặc `/goldlog`): "dd/MM HH:mm   +40   (còn 580) — Bán cho cửa hàng", dòng cộng tô
nổi. 37 nhãn lý do `gold.reason.*` vi/en mới (trước đây client chưa từng hiện lý do); lý do lạ hiện khoá thô.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng sổ vàng ghi trong GoldLedger cùng transaction, giữ 30 ngày, 50 dòng gần nhất; 247/248; `/sovang` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-04 (lô 30): V135, `GoldLedger` ghi sổ, `RetentionJanitor`, `GoldHistoryPort`/`GoldHistoryFacade`,
   `PostgresGoldHistoryAdapter`, 247/248, `GoldHistoryCoordinator` `/sovang`, 37 nhãn lý do.
2. ✅ Test Postgres thật: `GoldHistoryIntegrationTest` (cộng + trừ trong một transaction ⇒ hai dòng đúng số dư, mới nhất
   trước; transaction rollback ⇒ không dòng nào, vàng nguyên). `GoldLedgerSourceGuardTest` vẫn xanh.
3. ✅ Kiểm sống gameProbe 2026-11-04: sổ rỗng (trước V135 chưa ghi) ⇒ bán 2 thanh kiếm ⇒ `[40 còn 580 sell_item] [40 còn
   540 sell_item]`.
4. Chờ chủ dự án: duyệt 37 nhãn lý do `gold.reason.*`; thời hạn giữ 30 ngày.
