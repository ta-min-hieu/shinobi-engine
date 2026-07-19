# CLAUDE.md — feature-account

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-account/**`.

## Cấu trúc (Clean Arch)

```
api/            AccountFacade          ← module khác (server-gateway) CHỈ gọi qua đây
application/    LoginOrCreateAccountUseCase
domain/         Account (aggregate) · AccountPort · SessionPort · Transaction · TransactionRunner
```

Chiều: `api → application → domain`. **`domain/` không import gì bên ngoài** — không có `infrastructure/` trong module này (xem ghi chú dưới).

## ⚠️ Khác template chuẩn: KHÔNG có `infrastructure/` ở đây

`docs/templates/feature-module/` cho thấy `infrastructure/Postgres<Feature>Adapter` nằm ngay
trong feature module. Với `feature-account`, adapter thật (`PostgresAccountAdapter`,
`RedisSessionAdapter`) nằm ở **`server-database`** thay vì ở đây — lý do: rule ArchUnit
`chi_server_database_dung_jdbc`/`chi_server_database_dung_redis` chặn theo **package**
(`..server.database..`), không theo module, nên bất kỳ class nào import `java.sql.*`/Jedis đều
phải nằm trong package đó. `domain/Transaction.java` của chính template cũng ghi rõ: "Implementation
thật nằm ở server-database". `server-database` phụ thuộc ngược vào `feature-account` (chỉ để thấy
interface `Port`) — đúng Dependency Inversion, không phá `domain/` là tâm.

## ⚠️ Khác template chuẩn: `TransactionRunner` không phải `TransactionManager`

Template dùng tên `TransactionManager` — nhưng chính Checkstyle của dự án cấm hậu tố
`Manager/Helper/Util/Info/Data` (`config/checkstyle/checkstyle.xml`). Đặt tên `TransactionRunner`
ở đây, cùng interface/trách nhiệm y hệt template.

## Cứng

✅ Quy tắc nghiệp vụ ở `domain/` (validate trong compact constructor — xem `Account`)
✅ Port là interface trong `domain/`; Adapter implement ở `server-database`
✅ Module khác gọi qua `AccountFacade`, không đọc bảng `account` trực tiếp

❌ SQL/JDBC/Redis client trong module này (Điều 23 — enforce bằng ArchUnit)
❌ `domain/` biết PostgreSQL/Redis

## Test
`domain/` test **không mock**. `application/` test với **Fake Port** (không Mockito).
