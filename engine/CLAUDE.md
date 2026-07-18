# CLAUDE.md — vùng `engine/`

> Thẻ luật vùng. Auto-load khi sửa `engine/**`. Bản đầy đủ: [/CLAUDE.md](../CLAUDE.md).
> Engine phục vụ gameplay (Điều 10). Tái sử dụng, không biết game cụ thể.

## Ranh giới bắt buộc

❌ `engine-ecs` **KHÔNG** phụ thuộc `engine-core` (server không được kéo theo rendering) — [ENGINE.md §9](../docs/03-engine/ENGINE.md)
❌ Import `com.badlogic.ashley.*` **chỉ** trong `engine-ecs` — nơi khác cấm ([ADR-006](../docs/01-architecture/adr/ADR-006-Why-ECS.md)). Vi phạm = mất khả năng thay ECS
❌ `ServiceRegistry` trong constructor của System — dùng constructor injection tường minh ([ENGINE.md §6](../docs/03-engine/ENGINE.md))

## ECS — quy tắc cứng

✅ Entity = ID (không logic, không kế thừa, giữ **ID** không giữ tham chiếu entity)
✅ Component = dữ liệu; **mutable + field public là ĐÚNG** ở đây (ngoại lệ Điều 43 — [ECS.md §3.1](../docs/03-engine/ECS.md)). Được có method thuần đọc chính nó (`isDead()`)
✅ Mọi logic ở System; dùng `ComponentMapper`, không `getComponent()` trong loop
✅ System giao tiếp qua Event, không gọi trực tiếp nhau ([ADR-008](../docs/01-architecture/adr/ADR-008-Why-EventDriven.md))
✅ Xoá entity **cuối tick**, không giữa lúc lặp

❌ Component gọi service / chứa gameplay
❌ `new` object trong `processEntity()` / game loop (Điều 31) — dùng field tạm
❌ Đổi thứ tự System tuỳ tiện = đổi behavior ([ECS.md §6](../docs/03-engine/ECS.md))

## Thêm Component/System
Copy `docs/templates/ecs/`. AI quái: FSM đơn giản, **không** GOAP, **không** A* ở MVP ([AI.md](../docs/03-engine/AI.md)).

## Đọc sâu khi cần
[ECS.md](../docs/03-engine/ECS.md) · [ENGINE.md](../docs/03-engine/ENGINE.md) · [RENDERING.md](../docs/03-engine/RENDERING.md) · [PHYSICS.md](../docs/03-engine/PHYSICS.md) (⚠️ Box2D KHÔNG dùng cho combat)
