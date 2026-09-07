# CLAUDE.md — feature-world

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-world/**`.
> Sinh ra bởi [ADR-046](../../../docs/01-architecture/adr/ADR-046-Health-Recovery.md) D3.

## Cấu trúc

```
api/       WorldGeometryFacade — đọc thuần, KHÔNG use case nào
domain/    SafeZone (VO) · SafeZonePort
```

**Không có `application/`** — chủ ý, giống `feature-monster`: module này chỉ tra cứu config tĩnh.

## Module này là chỗ ở của HÌNH HỌC BẢN ĐỒ

Không phải "vùng an toàn". `safe_zone` là thứ đầu tiên vào đây; hai thứ tiếp theo đã biết trước:

- **Bệ nhảy** — đang hardcode ở `WorldFacade.DEMO_PLATFORMS` (ADR-033 ghi nhận là tạm).
- **Dải nền đất** — đang hardcode ở **CLIENT**, và chính nó là nguồn của bug quái đứng lơ lửng
  2026-09-06 (`monster_spawn` ở DB đi tới x=-47 trong khi client chỉ vẽ tới x=-30, không test nào
  đỏ được vì hai con số không nối với nhau bằng gì cả).

Thêm gì vào đây thì nhớ: **nội dung ở DB, cơ chế ở Java** (Điều 27).

## Điều phải nhớ khi sửa

- **Nạp MỘT LẦN lúc khởi động**, không có đường nóng — `safe_zone` là config tĩnh, cùng nhóm
  `monster_template` (DATABASE.md §182). Dời vùng ⇒ migration + khởi động lại.
- **Module này KHÔNG biết `server-world` tồn tại**, và ngược lại. Gateway dịch `SafeZone` →
  `SafeZoneCatalogSnapshot` (`WorldContentLoader`), đúng khuôn ADR-037/ADR-040 đã dựng.
- **`MAIN_WORLD_CODE` phải bằng đúng chuỗi của `feature-monster`/`feature-dialogue`** — ba module
  khai cùng một world. Có test khoá ba hằng số đó bằng nhau; đừng "dọn" bằng cách đổi một cái.
- **Chưa có cột "vùng này làm gì"** — mới có đúng một luật (hồi đầy máu). Thêm cột `heals BOOLEAN`
  khi và chỉ khi có vùng thứ hai với luật khác (ADR-046 Revisit Trigger).
- Biên vùng kể cả **trục Y** dù ADR-033 khoá di chuyển vào trục X — xem javadoc `SafeZone`.

## Test
`domain/` test **không mock**. Adapter thật có test Postgres qua Testcontainers ở `server-database`.
