# templates/

**Skeleton code để copy — pattern-match thay vì viết từ đầu.**

> Mục đích: AI (và người) copy template, đổi tên, điền logic — rẻ hơn nhiều so với suy luận cấu trúc từ prose.
> Xem [AI-CODEGEN-GUIDE.md](../AI-CODEGEN-GUIDE.md) để biết khi nào dùng cái nào.

⚠️ **Đây là template, KHÔNG phải code chạy được.** Nằm dưới `docs/` nên không bị build. Khi copy vào module thật:
- `dev.kage` là package root chính thức ([ADR-012](../01-architecture/adr/ADR-012-Package-Root.md)) — **giữ nguyên**, chỉ đổi phần `<feature>` sau nó.
- Đổi `Example`/`Inventory` → tên domain thật.
- Xoá comment `// TEMPLATE:` sau khi điền.

---

## Danh mục

| Template | Dùng khi | Kèm |
|---|---|---|
| [`feature-module/`](feature-module/) | Thêm feature nghiệp vụ (Clean Arch) | Facade, UseCase, domain (Aggregate, VO, Port), Adapter, CLAUDE.md, test |
| [`ecs/`](ecs/) | Thêm ECS Component/System | Component, System, test |
| [`protocol/`](protocol/) | Thêm packet | Command, Response, Event |

Template ADR: [`../01-architecture/adr/ADR-TEMPLATE.md`](../01-architecture/adr/ADR-TEMPLATE.md)

---

## Nguyên tắc khi dùng

1. **Copy nguyên cấu trúc**, đừng bỏ bớt layer (api/application/domain/infrastructure).
2. Chiều dependency: `api → application → domain ← infrastructure`. `domain/` không import ra ngoài.
3. Mỗi template đã nhúng sẵn các ràng buộc cứng của vùng — giữ nguyên chúng.
4. Test đi kèm template: copy luôn, đừng để "viết test sau".

## Tài liệu liên quan
[AI-CODEGEN-GUIDE.md](../AI-CODEGEN-GUIDE.md) · [REPOSITORY-STRUCTURE §4](../01-architecture/REPOSITORY-STRUCTURE.md) · [server/CLAUDE.md](../../server/CLAUDE.md)
