# CLAUDE.md — feature module (TEMPLATE)

> Copy file này vào mỗi feature module thật (`feature-inventory/CLAUDE.md`...). Đổi "Inventory" thành tên feature.
> Auto-load khi sửa feature đó. Kế thừa [server/CLAUDE.md](../../../server/CLAUDE.md).

## Cấu trúc bắt buộc (Clean Arch)

```
api/            InventoryFacade        ← module khác CHỈ gọi qua đây
application/    <Verb><Noun>UseCase    ← điều phối, không chứa quy tắc nghiệp vụ
domain/         Inventory (aggregate) · ItemStack (VO) · InventoryPort (interface)
infrastructure/ Postgres<Feature>Adapter implements Port
```

Chiều: `api → application → domain ← infrastructure`. **`domain/` không import `infrastructure/`.**

## Cứng

✅ Quy tắc nghiệp vụ ở `domain/` (không anemic — không dồn hết vào UseCase)
✅ Port là interface trong `domain/`; Adapter implement ở `infrastructure/`
✅ Value Object = `record`, validate trong compact constructor
✅ Transaction + audit cho thao tác có giá trị ([DATABASE §5](../../05-server/DATABASE.md))
✅ Module khác gọi qua `Facade`, không đọc bảng DB của mình

❌ SQL ngoài `infrastructure/`
❌ `domain/` biết PostgreSQL/Redis
❌ Đọc bảng của feature khác

## Test
`domain/` test **không mock**. `application/` test với **Fake Port** (không Mockito). Xem [TESTING §4–5](../../10-quality/TESTING.md).
