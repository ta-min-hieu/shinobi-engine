# ADR-064: Linh Ngọc khảm nạm — một ô mỗi trang bị, 50% thành công, bảo hiểm sau 3 lần hỏng

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-18) — chủ dự án chọn "Linh Ngọc khảm nạm" trong lượt hỏi lô 16 (AskUserQuestion; mô tả kèm: Post-MVP trong GDD — ngọc cộng chỉ số phụ, có pity, thêm một gold sink; cần ADR + DB + UI). Câu hỏi mở theo khuyến nghị. AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-18 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [GDD §5/§10](../../13-game-design/GAME-DESIGN-DOCUMENT.md) ("Linh Ngọc khảm nạm: bonus phụ, có pity system"; "Gold Sink: tôi luyện, khảm nạm") · [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) (chỉ số trang bị) · [ADR-061](ADR-061-Sect-Storage.md) · [ADR-063](ADR-063-Market.md) |

---

## Context

Sau Lv30 cường hoá là đường mạnh lên duy nhất của trang bị, và cường hoá chỉ nhân chỉ số gốc của món.
GDD hứa Linh Ngọc: chỉ số phụ, có bảo hiểm (pity) để vận rủi không kéo dài vô tận, và là một gold sink.

## Decision

**D1 — Bốn loại Linh Ngọc** (vật phẩm xếp chồng 99, không mặc được — V66): Hoả/Thổ/Thuỷ/Mộc Linh Ngọc,
lần lượt +3 công, +3 thủ, +3 kháng, +15 máu (bảng `gem_template`, tham số — Điều 27). Nguồn đầu tiên: cửa
hàng tông môn bán 1.500 linh thạch/viên (Q1 theo khuyến nghị — chưa đụng bảng rơi đồ để không lệch cân bằng
trận đã đo; thêm nguồn rơi là việc riêng).

**D2 — Mỗi trang bị MỘT ô khảm** (bảng `item_socket`, khoá theo `inventory_item.id`, `ON DELETE CASCADE`).
Khảm đè ngọc đang có thì ngọc cũ mất (Q2 theo khuyến nghị — không có thao tác "tháo ngọc").

**D3 — Một lần khảm** tốn 1 viên ngọc + **200 linh thạch** (sink) và **50%** thành công; hỏng thì mất ngọc +
vàng, ô giữ nguyên. **Bảo hiểm**: hỏng 3 lần liên tiếp thì lần thứ tư chắc chắn thành công; thành công thì
đếm lại từ 0 (bảng `gem_pity`, theo NHÂN VẬT — không theo món, để người chơi không "nuôi" một món rẻ). Một
transaction dưới khoá túi đồ: khoá túi → khoá chống trùng (ADR-025) → trừ vàng → tiêu ngọc → roll → ghi ô
+ bảo hiểm → audit `GEM_SOCKET`. Random chỉ sinh ở server.

**D4 — Chỉ số ngọc cộng PHẲNG** vào món đang mặc, KHÔNG nhân theo cấp cường hoá (cường hoá khuếch đại
chỉ số GỐC — ADR-036 D3). `ComputeEquipmentBonusUseCase` cộng thêm phần ngọc; khảm món đang mặc thì
gateway tính lại chỉ số + máu tối đa như sau cường hoá.

**D5 — Món đã khảm đi đâu được.** Giao dịch trực tiếp dời NGUYÊN dòng (giữ id) ⇒ ngọc đi theo. Thư, phường
thị, kho đồ dời bằng cách xoá dòng rồi tạo dòng mới ⇒ sẽ làm mất ngọc ⇒ **từ chối** món đã khảm (cùng cách
món đã cường hoá đang bị từ chối ở thư/phường thị). Bán cho cửa hàng: ngọc mất cùng món (người chơi tự
quyết; dòng chi tiết túi đồ nói rõ ngọc đang khảm).

**D6 — Giao thức, chỉ thêm, `protocolVersion` không tăng:** 520/521 `SocketGemCommand`/`SocketGemResponse`
(mang `idempotency_key`; response nói thành/hỏng + số lần hỏng liên tiếp hiện tại); `InventoryItemView`
thêm `socket_gem_template_id = 6`; `StorageOutcome`/`MarketResult` dùng kết quả sẵn có
(`STORAGE_OUTCOME_EQUIPPED`… không đủ nghĩa ⇒ thêm `STORAGE_OUTCOME_SOCKETED = 9`; phường thị trả
`ENHANCED_NOT_ALLOWED` với nghĩa mở rộng "đã cường hoá hoặc đã khảm").

**D7 — Client:** cửa sổ "Khảm Linh Ngọc" (phím **V**): chọn một trang bị (trong túi hoặc đang mặc), rồi bấm
"Khảm" ở viên ngọc muốn dùng; dòng trạng thái nói thành/hỏng và còn bao nhiêu lần tới bảo hiểm.

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Nhiều ô / ô theo phẩm chất món | Thêm hệ "đục lỗ" + UI chọn ô; một ô đủ cho bản đầu |
| Ngọc nhân theo cường hoá | Chồng hai hệ số lên nhau, khó cân bằng; GDD gọi là "bonus phụ" |
| Pity theo món | Người chơi nuôi bảo hiểm trên món rẻ rồi khảm món xịn — lách luật |
| Cột `socket_gem` trong `inventory_item` | Mọi đường xoá/tạo dòng phải nhớ chép cột; bảng riêng + từ chối ở các đường đó rõ hơn |
| Rơi từ quái ngay | Đổi bảng rơi đồ = đổi số cân bằng đã kiểm sống; để sau |

## Consequences

- 3 bảng mới (`gem_template`, `item_socket`, `gem_pity`), 4 vật phẩm + 4 dòng cửa hàng.
- Món đã khảm không qua được thư/phường thị/kho cho tới khi các đường đó chở được ngọc.

## Revisit Trigger

- Kho đồ/thư chở được cấp cường hoá + ngọc ⇒ bỏ từ chối ở D5.
- Thêm nguồn ngọc rơi từ quái/hầm ngục; thêm thao tác tháo ngọc.

## Technical Decisions

- `GemPort` (domain feature-inventory) + `PostgresGemAdapter`; `SocketGemUseCase` dùng chung `TransactionRunner` túi đồ.
- Roll thành/hỏng bằng `RandomGenerator` tiêm vào (test được), production `ThreadLocalRandom`.

## ADR cần tạo

Không.

## Tài liệu liên quan

- `V66__spirit_jade.sql` · `SocketGemUseCase` · `GemCoordinator` · `inventory.proto` (520/521)
- Test: `SocketGemUseCaseTest` (bảo hiểm, rollback), `SpiritJadeIntegrationTest` (Postgres thật, chỉ số)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-18 (lô 16).
2. ✅ Kiểm sống 2026-10-21 bằng gameProbe (`--socketGems`) — và **tìm ra lỗi nặng, đã vá**: khảm thành công một món trong khi vẫn mặc món chưa khảm ⇒ `ComputeEquipmentBonusUseCase.gemBonusOf` gọi `get(null)` trên Map bất biến ⇒ NPE ⇒ nhân vật **không vào world được nữa** (`error.select_character_failed`), vàng đã trừ. Test cũ dùng `GemPort.NONE` nên không bắt được; nay có test với Map bất biến như adapter thật. Sau vá: máu tối đa 296 → 311 (ngọc Mộc áo) → 326 (ngọc Mộc kiếm); khảm đè ô đã có ngọc giữ 326 (D2). Bảo hiểm (2026-10-23, 11 lượt khảm Hoả Ngọc vào món trong túi): hai lần liền gặp chuỗi hỏng 1/3 → 2/3 → 3/3 rồi lượt kế THÀNH CÔNG, đếm về 0. Gửi dồn ~30 lệnh/giây thì server trả `error.rate_limited` — đúng luật chặn tần suất.
