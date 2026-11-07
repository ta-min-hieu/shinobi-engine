# ADR-197: Kho chung tài khoản

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Kho chung tài khoản" trong lượt hỏi lô 33 (AskUserQuestion; mô tả kèm: một kho nhỏ dùng chung giữa các nhân vật cùng tài khoản, để chuyển đồ cho nhân vật phụ mà không cần người trung gian). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-061](ADR-061-Sect-Storage.md) (kho tông môn — dùng lại toàn bộ luật) |

---

## Decision

**D1 — Dùng lại luật kho tông môn**: cùng `StorageUseCase` (gửi / rút cả chồng, gộp chồng, từ chối món đang mặc /
đang khảm / đã khoá / đang chào giao dịch, kho đầy, túi đầy, giữ cấp cường hoá), chỉ cắm adapter khác:
`PostgresAccountStorageAdapter` đi từ `characterId` sang `account_id`. Sức chứa = `StoragePolicy.CAPACITY` (40) như kho
tông môn. Cùng nơi: phải đứng gần người giữ kho Tô Vân Nhi để gửi / rút (xem thì không cần).

**D2 — Lưu**: V154 bảng `account_storage_item (account_id, item_template_id, quantity, enhance_level, stored_at)`.
Khoá dòng `account` trước khi khoá các dòng kho — hai phiên của hai nhân vật cùng tài khoản không cùng lọt trần. Audit
ghi `ACCOUNT_STORAGE_DEPOSIT` / `ACCOUNT_STORAGE_WITHDRAW` (thêm `StoragePort.auditScope()`, mặc định `STORAGE` nên kho
tông môn không đổi).

**D3 — Giao thức, chỉ thêm**: enum mới `StorageKind {SECT = 0, ACCOUNT = 1}` và field `storage_kind` ở cả 6 message kho
có sẵn (`ListStorageRequest` 1, `ListStorageResponse` 3, `DepositItemCommand` 3, `DepositItemResponse` 4,
`WithdrawItemCommand` 3, `WithdrawItemResponse` 4). Không opcode mới; client cũ không gửi field ⇒ `SECT` = như trước.
Enum (không phải cờ boolean) để kho bang ADR-198 thêm một giá trị thay vì thêm cờ thứ hai.

**D4 — Client**: cửa sổ kho có thêm một dòng đầu "Mở kho chung tài khoản" / "Quay về kho tông môn". Kho đang xem theo
`storage_kind` server trả về.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Kho theo tài khoản dùng lại `StorageUseCase`, V154, `StorageKind` trên message kho có sẵn | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 33): V154, `PostgresAccountStorageAdapter`, `AccountStorageFacade`, `StoragePort.auditScope`,
   `SectStorageCommands` chọn kho theo `storage_kind`, `StorageCoordinator` dòng đổi kho.
2. ✅ Test: `AccountStorageIntegrationTest` (Postgres thật: gửi bằng nhân vật này, rút bằng nhân vật kia, tài khoản khác
   không thấy / không rút được, audit đúng tên).
3. ✅ Kiểm sống gameProbe 2026-11-07 (V154): ĐạoHữu01 đứng cạnh Tô Vân Nhi gửi 20 kunai vào kho chung ⇒ `OK`; tạo
   nhân vật phụ PhuTanThu trên cùng tài khoản ⇒ thấy ngay dòng đó, rút ⇒ `OK` (audit `ACCOUNT_STORAGE_DEPOSIT` /
   `_WITHDRAW` đúng người); BaoDo01 (tài khoản khác) thấy kho trống; rút khi đứng xa ⇒ `TOO_FAR`. Kunai đã chuyển về
   ĐạoHữu01; nhân vật PhuTanThu (cấp 1, tài khoản tanthu01) để lại trong DB dev.
4. ✅ Test fuzz opcode của gateway bắt được lỗi trước khi commit: `storage_kind` lạ ⇒ 500 (protobuf không đặt được giá
   trị UNRECOGNIZED vào response). Nay giá trị lạ được coi là kho tông môn.
5. Chờ chủ dự án: sức chứa 40 dòng như kho tông môn (mô tả lúc hỏi nói "kho nhỏ").
