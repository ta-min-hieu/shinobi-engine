# ADR-198: Kho bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Kho bang" trong lượt hỏi lô 33 (AskUserQuestion; mô tả kèm: thành viên gửi đồ vào kho chung của bang; bang chủ / phó lấy ra hoặc chia; mọi lần gửi / lấy đều ghi nhật ký bang). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-061](ADR-061-Sect-Storage.md) (luật kho) · [ADR-197](ADR-197-Account-Storage.md) (`StorageKind`) · [ADR-180](ADR-180-Guild-Log.md) (nhật ký bang) · [ADR-184](ADR-184-Guild-Vice-Leader.md) |

---

## Decision

**D1 — Ai làm gì**: **mọi thành viên** gửi được; **chỉ bang chủ và phó bang chủ** rút được (gateway kiểm trước khi gọi
feature: chưa vào bang ⇒ `NOT_IN_GUILD`, thành viên thường rút ⇒ `NO_PERMISSION`). "Chia cho thành viên" = bang chủ /
phó rút rồi đưa qua giao dịch hoặc thư — không có lệnh chia riêng. Dùng **ở đâu cũng được** (kho của bang, không phải của
một nơi — cùng lý do cửa hàng bang ADR-080).

**D2 — Dùng lại luật kho**: cùng `StorageUseCase` (gửi / rút cả chồng, gộp chồng, từ chối món đang mặc / khảm / khoá /
đang giao dịch, kho đầy = `StoragePolicy.CAPACITY` 40, túi đầy, giữ cấp cường hoá), adapter
`PostgresGuildStorageAdapter` đi từ `characterId` sang bang qua `guild_member`. Khoá dòng `guild` trước các dòng kho.
Người gửi vừa rời / bị đuổi sau lúc gateway kiểm ⇒ adapter ném lỗi khi chèn, cả transaction lùi lại — món không mất.

**D3 — Nhật ký bang cùng transaction**: thêm `StoragePort.recorded(tx, Movement)` (mặc định không làm gì; kho tông môn và
kho tài khoản không đổi); adapter kho bang ghi `guild_log` loại mới `BANK_DEPOSIT` / `BANK_WITHDRAW` (actor = người gửi /
rút, amount = số lượng, detail = mã vật phẩm). Audit ghi `GUILD_BANK_*`.

**D4 — Lưu**: V155 bảng `guild_storage_item (guild_id … ON DELETE CASCADE)` — bang giải tán thì kho mất theo (bang chủ
nên rút trước); mở rộng CHECK loại dòng `guild_log`.

**D5 — Giao thức, chỉ thêm**: `StorageKind.STORAGE_KIND_GUILD = 2`; `StorageOutcome.NOT_IN_GUILD = 11`,
`NO_PERMISSION = 12`; `GuildLogKind.BANK_DEPOSIT = 12`, `BANK_WITHDRAW = 13`. Không opcode mới.

**D6 — Client**: lệnh chat `/khobang` (hoặc `/guildbank`) mở cửa sổ kho ở chế độ kho bang; nhật ký bang hiện "X gửi Tên
món xN vào kho bang" (mã vật phẩm đổi sang tên theo catalog).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Kho bang dùng lại `StorageUseCase`, V155, thành viên gửi / bang chủ + phó rút, nhật ký bang cùng transaction | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 33): V155, `PostgresGuildStorageAdapter`, `GuildBankFacade`, `StoragePort.recorded`,
   `SectStorageCommands` (ba kho, kiểm quyền), `GuildBankCoordinator` `/khobang`, `GuildLogCoordinator` tên món.
2. ✅ Test: `GuildBankIntegrationTest` (Postgres thật: chia sẻ trong bang, nhật ký 2 dòng, người ngoài gửi ⇒ lùi lại).
3. ✅ Kiểm sống gameProbe 2026-11-07 (V155, bang LinhKiemMon): BaoDo01 (thành viên) gửi 3 sắt vụn ⇒ `OK`, thấy dòng trong
   kho; BaoDo01 rút ⇒ `NO_PERMISSION`; PhuTanThu (không ở bang) xem kho ⇒ trống, gửi ⇒ `NOT_IN_GUILD`; ĐạoHữu01 (bang chủ)
   rút ⇒ `OK`; `guild_log` có `BANK_DEPOSIT` (BaoDo01, 3, `iron_scrap`) và `BANK_WITHDRAW` (ĐạoHữu01). Sắt vụn đã trả về
   BaoDo01, hai dòng nhật ký thử đã xoá.
4. Chờ chủ dự án: sức chứa 40 dòng; kho mất theo khi bang giải tán.
