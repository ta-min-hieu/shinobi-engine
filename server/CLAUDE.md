# CLAUDE.md — vùng `server/`

> Thẻ luật vùng. Auto-load khi sửa `server/**`. Bản đầy đủ: [/CLAUDE.md](../CLAUDE.md).
> **Server là nguồn chân lý duy nhất** (Điều 15). Không tin client.

## Ranh giới module

❌ Import `io.netty.*` **chỉ** trong `server-gateway`
❌ Import JDBC/Redis client **chỉ** trong `server-database`
❌ `server-world` / `feature-*` biết Netty hay DB driver
❌ `domain/` import `infrastructure/` (Dependency Inversion — [REPOSITORY-STRUCTURE §4](../docs/01-architecture/REPOSITORY-STRUCTURE.md))
❌ Feature đọc bảng DB của feature khác — qua `api/` Facade

## Ba ranh giới thread (vi phạm = server giật/chết — [SERVER.md §2](../docs/05-server/SERVER.md))

| Thread | Cấm |
|---|---|
| Netty EventLoop | query DB, logic nặng, chờ lock |
| Game Tick (1 thread) | blocking I/O, `sleep`, `future.get()` |
| Virtual Thread | sửa ECS state trực tiếp |

## Bắt buộc

✅ `characterId` lấy từ **SESSION**, không từ packet (chống IDOR — [SECURITY.md §4](../docs/08-security/SECURITY.md))
✅ Validate **nghiệp vụ** ở server, không tin validate client
✅ Random ảnh hưởng gameplay: **chỉ** sinh ở server
✅ Combat tính bằng gameplay logic, **KHÔNG** Box2D ([PHYSICS.md §1](../docs/03-engine/PHYSICS.md))
✅ **Transaction + audit log** cho: trade, gold, cường hoá, mail-item, shop ([DATABASE.md §5](../docs/05-server/DATABASE.md))
✅ Item/gold ghi ngay; vị trí/HP ghi định kỳ
✅ `PreparedStatement`, không nối chuỗi SQL
✅ SQL chỉ trong `infrastructure/`; text block cho SQL dài

❌ Task nặng trong tick thread — dùng Virtual Thread riêng
❌ Log mỗi tick

## Thêm feature nghiệp vụ
Copy `docs/templates/feature-module/` (Clean Arch: api→application→domain←infrastructure). Java = cơ chế, Script = nội dung, DB = tham số (Điều 27).

## Đọc sâu khi cần
[SERVER.md](../docs/05-server/SERVER.md) · [GAMEPLAY.md](../docs/05-server/GAMEPLAY.md) · [DATABASE.md](../docs/05-server/DATABASE.md) · [NETWORK.md](../docs/05-server/NETWORK.md) · [SECURITY.md](../docs/08-security/SECURITY.md)
