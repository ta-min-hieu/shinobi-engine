# ADR-061: Kho đồ tông môn — 40 ô cá nhân, gửi/rút ở Tô Vân Nhi

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-18) — chủ dự án chọn "Kho đồ tông môn" trong lượt hỏi lô 16 (AskUserQuestion; mô tả kèm: kho cá nhân 40 ô gửi/rút ở NPC tại tông môn, cần bảng DB + 4 opcode + panel client). Câu hỏi mở theo khuyến nghị. AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-18 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) (liệt kê túi đồ) · dòng 65(a)/80 [docs/README.md](../../README.md) (sức chứa túi, món đang chào giao dịch) |

---

## Context

Túi 20 ô (cộng số món đang mặc). Từ khi có bộ trang bị theo arc, nhiệm vụ ngày, điểm danh, người chơi
phải bán hoặc vứt đồ còn muốn giữ (bộ cũ để đổi hệ, bùa hộ cường hoá để dành, nguyên liệu).

## Decision

**D1 — Bảng RIÊNG `storage_item`** (V63), không thêm cột "vị trí" vào `inventory_item`. Mọi câu đếm/khoá
túi đồ hiện có (cửa hàng, thư, quest, giao dịch, sức chứa) không phải sửa và không thể vô tình đếm đồ
trong kho. Chuyển = xoá/giảm ở bảng này + chèn/cộng ở bảng kia, trong MỘT transaction của feature-inventory.
Id vật phẩm ĐỔI khi chuyển (dòng mới); cấp cường hoá giữ nguyên.

**D2 — Sức chứa 40 dòng mỗi nhân vật** (hằng số domain `StoragePolicy.CAPACITY`, Q1 theo khuyến nghị). Món
xếp chồng gộp vào dòng cùng loại + cùng cấp cường hoá nếu vừa `max_stack` (cùng luật `Inventory.add`).

**D3 — Gửi/rút chỉ khi đứng gần Tô Vân Nhi (`senior_sister`)**, kiểm ở server (`NpcProximity`, Điều 15).
Mở kho qua hội thoại: lựa chọn có `action = "open_storage"` ⇒ gateway gửi danh sách kho (cùng đường
`open_shop`). Không phím tắt: kho là nơi chốn, không phải menu.

**D4 — Luật chuyển.**
- Gửi: món phải nằm trong túi của người gửi; **đang mặc ⇒ từ chối** (`EQUIPPED`); phần đang chào trong
  giao dịch không gửi được (`ITEM_IN_TRADE`, cùng `TradeOfferPort` của dòng 80); kho đầy ⇒ `STORAGE_FULL`.
- Rút: dòng kho phải của người rút; túi đầy ⇒ `BAG_FULL`, không gì thay đổi.
- Số lượng `1..số đang có`; client hiện gửi/rút CẢ chồng.
- Khoá: túi đồ trước (`findByCharacterForUpdate`), rồi dòng kho — một thứ tự cho mọi đường.
- Audit `STORAGE_DEPOSIT` / `STORAGE_WITHDRAW` (DATABASE.md §5: mọi đường làm vật phẩm đổi chỗ đều đối
  chiếu được).
- **Không idempotency key** (cùng lý do `UseItemUseCase`): lệnh chỉ dời đồ của chính mình, không tạo hay
  huỷ tài sản; gửi lặp nhiều nhất dời thêm một chồng — không mất, không nhân đôi.

**D5 — Giao thức: 6 opcode mới, chỉ thêm, `protocolVersion` không tăng.** 514/515 `ListStorageRequest`/
`ListStorageResponse`, 516/517 `DepositItemCommand`/`DepositItemResponse`, 518/519
`WithdrawItemCommand`/`WithdrawItemResponse`. Response gửi/rút mang luôn danh sách kho mới (bớt một vòng
hỏi); túi đồ client tự hỏi lại như sau mọi lệnh túi đồ khác.

**D6 — Client.** Cửa sổ kho dùng lại `ReportPanel` (thêm nút hành động mỗi dòng — "Rút"); trong túi đồ,
khi cửa sổ kho đang mở, món đang chọn có thêm nút "Gửi kho".

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Cột `location` trong `inventory_item` | Phải sửa mọi câu SQL túi đồ hiện có (≥ 6 nơi đếm/khoá); quên một nơi là đếm sai sức chứa hoặc bán được đồ trong kho |
| Kho dùng chung cả tài khoản | Đồ chạy giữa các nhân vật cùng tài khoản = kênh "tự giao dịch" mà quyết định 2026-10-11 (một tài khoản một nhân vật online) đang chặn; để sau nếu chủ dự án muốn |
| Mở rộng túi lên 60 ô | Không có cảm giác "nơi cất"; HUD túi đồ dài gấp ba |
| Mở kho bằng phím tắt ở mọi nơi | Biến kho thành túi thứ hai — mất ý nghĩa của việc về tông môn |

## Consequences

- Một bảng mới, một hằng số sức chứa, 6 opcode. Không câu SQL túi đồ cũ nào đổi.
- Kho không có trong `ListInventoryResponse`; `/gm inspect` chỉ xem túi (có thể thêm kho sau).

## Revisit Trigger

- Cần kho chung tài khoản / mở rộng kho bằng vàng (gold sink).
- Gửi/rút từng phần chồng trên UI.

## Technical Decisions

- `StoragePort` trong domain feature-inventory, adapter `PostgresStorageAdapter`; dùng chung `Transaction` với túi đồ.
- Gộp chồng trong kho theo đúng luật `Inventory.add` (không xé lẻ).

## ADR cần tạo

Không.

## Tài liệu liên quan

- `inventory.proto` (514–519) · `V63__sect_storage.sql` · `StorageUseCase` · `StorageCoordinator`
- Test: `SectStorageIntegrationTest` (Postgres thật)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-10-18 (lô 16).
2. ✅ Kiểm sống 2026-10-21 bằng gameProbe (`--depositItems/--withdrawItems`): đứng x=40 ⇒ `TOO_FAR`; đi tới x=2 (cạnh Tô Vân Nhi) ⇒ gửi OK (kho 1/40), rút OK (kho 0/40).
