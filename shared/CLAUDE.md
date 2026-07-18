# CLAUDE.md — vùng `shared/`

> Thẻ luật vùng. Auto-load khi sửa `shared/**`. Bản đầy đủ: [/CLAUDE.md](../CLAUDE.md).
> `shared-protocol` = packet/DTO. `shared-common` = constant, common model. **KHÔNG business logic** (Điều 22).

## Được / Cấm

✅ `record` cho mọi Packet/DTO/Value Object · validate trong compact constructor
✅ `sealed interface` cho hierarchy đóng (Packet, Command, Response, Event)
✅ Enum thay int constant

❌ Business logic (chỉ dữ liệu + cấu trúc)
❌ Phụ thuộc bất kỳ module nào khác trong repo (`shared-protocol` không phụ thuộc gì; `shared-common` chỉ `shared-protocol`)
❌ Đổi số field protobuf đang dùng — chỉ được THÊM field mới ([NETWORK.md §6](../docs/05-server/NETWORK.md))
❌ Message hiển thị tiếng Việt cứng — dùng `errorKey` cho i18n

## Thêm packet mới

1. Copy `docs/templates/protocol/` skeleton
2. Cấp opcode đúng dải domain ([NETWORK.md §4](../docs/05-server/NETWORK.md))
3. Cập nhật `opcode_registry.proto` (nguồn chân lý thật)
4. Có round-trip test (encode→decode bằng nhau)

## Đọc sâu khi cần
[NETWORK.md §2–4](../docs/05-server/NETWORK.md) · [CODING-STANDARDS §6–8](../docs/02-coding-standards/CODING-STANDARDS.md)
