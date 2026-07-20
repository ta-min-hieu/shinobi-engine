# CLAUDE.md — feature-inventory

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-inventory/**`.

## Cấu trúc (Clean Arch)

```
api/            InventoryFacade          ← module khác (server-gateway) CHỈ gọi qua đây
application/    AddItemUseCase · GrantLootUseCase
domain/         Inventory (aggregate) · ItemStack (VO) · DropTableEntry (VO)
                InventoryPort · DropTablePort · Transaction · TransactionRunner
                CharacterInventoryNotFoundException
```

Chiều: `api → application → domain`. **`domain/` không import gì bên ngoài** — không có
`infrastructure/` trong module này, cùng lý do đã ghi ở feature-account/CLAUDE.md: adapter thật
(`PostgresInventoryAdapter`, `PostgresDropTableAdapter`) nằm ở **`server-database`** (ArchUnit chặn
JDBC theo package `dev.kage.server.database`, không theo Gradle module).

## Quyết định MVP ghi lại ở đây (không có trong docs gốc)

- **Mở sớm hơn ROADMAP dự kiến** (Phase 5) — quyết định của chủ dự án cho lát cắt "rơi đồ thật vào
  túi đồ" ở Phase 4 (quái chết → rơi đồ thật, không chỉ hiển thị suông).
- **`drop_table.entries_json` → normalize thành bảng `drop_table_entry`**: DATABASE.md ghi
  `drop_table | id, entries_json` (JSON blob), nhưng dự án CHƯA có thư viện JSON nào
  (Jackson/Gson) trong TECHNOLOGY-STACK.md — thêm mới là việc cần dừng hỏi (CLAUDE.md §4). Bảng
  quan hệ (`drop_table_entry`: 1 dòng = 1 entry) đạt cùng mục đích "config, không code" mà không
  cần dependency mới, tận dụng Postgres có sẵn. Không phải quyết định kiến trúc (không đổi ADR
  nào) — chỉ là cách biểu diễn dữ liệu.
- **`Inventory` không phải 1 dòng DB riêng** — không có bảng `inventory`, chỉ có `inventory_item`.
  `PostgresInventoryAdapter.findByCharacter` LUÔN trả `Optional.of(...)` (never empty) — lắp từ
  các dòng `inventory_item` hiện có (có thể rỗng) + `DEFAULT_CAPACITY` hằng số (chưa đọc từ đâu
  khác, placeholder như `MOVE_SPEED_MPS`).
- **`save()` = xoá hết + ghi lại toàn bộ** (không diff) — đơn giản nhất cho quy mô túi đồ nhỏ MVP
  (Điều 9). Tối ưu incremental save nếu đo thấy cần.
- **Không stack item trùng loại** — `AddItemUseCase`/`GrantLootUseCase` luôn thêm dòng mới, không
  gộp `quantity` vào stack đã có cùng `item_template_id`. `ItemStack.plus()` (template) tồn tại
  cho tương lai nhưng CHƯA dùng ở lát cắt này.
- **Túi đầy → item bị "mất"** (không rơi xuống đất) ở `GrantLootUseCase` — chưa có ground-item/mail
  hệ thống để trả lại đồ khi túi đầy. Ghi rõ, không giấu — không phải hành vi cuối cùng.

## Cứng

✅ Quy tắc nghiệp vụ ở `domain/` (túi đầy = `Inventory.add()`, không phải UseCase)
✅ Port là interface trong `domain/`; Adapter implement ở `server-database`
✅ Value Object = `record`, validate trong compact constructor (`ItemStack`, `DropTableEntry`)
✅ Random rơi đồ (`GrantLootUseCase`/roll) — chỉ sinh ở server (ENGINE.md §10)
✅ Module khác gọi qua `InventoryFacade`, không đọc bảng `inventory_item`/`drop_table*` trực tiếp

❌ SQL/JDBC/Redis client trong module này (Điều 23 — enforce bằng ArchUnit)
❌ `domain/` biết PostgreSQL

## Test
`domain/` test **không mock**. `application/` test với **Fake Port** (không Mockito).
