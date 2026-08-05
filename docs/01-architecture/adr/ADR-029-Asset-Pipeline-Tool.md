# ADR-029: Tạo module `tools:asset-pipeline`, dùng `gdx-tools` TexturePacker làm nền đóng gói atlas

| | |
|---|---|
| Status | ✅ **Accepted** (2026-08-01, chủ dự án xác nhận qua chat) |
| Date | 2026-08-01 |
| Deciders | Chủ dự án quyết (Điều 36: module mới + dependency mới) |
| Related | [ART-PIPELINE.md §8](../../14-art/ART-PIPELINE.md) · [RESOURCE.md §4, §5, §9](../../04-client/RESOURCE.md) · [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md) (đang chờ module này) · [REPOSITORY-STRUCTURE.md §1, §7](../REPOSITORY-STRUCTURE.md) |

---

## Context

Từ 2026-08-01, `assets/raw/render-out/` đã có **15 bộ sprite sheet thật** (5 hệ × 3 bậc trang bị, mỗi bộ
7 action) render từ Blender, master @2x, đúng quy ước ART-PIPELINE.md. Nhưng con đường từ đó tới game
chạy được vẫn **hoàn toàn thủ công** — không có gì hạ mẫu @1x/@0.5x, đóng gói atlas, hay sinh manifest.
ART-PIPELINE.md §8 đã thiết kế chi tiết bước này (`tools/asset-pipeline`) từ trước, nhưng
`settings.gradle.kts` cố tình để một dòng comment chặn: *"tools:asset-pipeline: THÊM ở Phase 1
(RESOURCE.md). Không scaffold trước."* — tức việc tạo module này **đã được nhận diện là cần quyết định
riêng**, không phải "code vào module có sẵn" mà AI có thể tự làm (Điều 36).

Hai quyết định gộp lại trong ADR này vì chúng phụ thuộc lẫn nhau — không tách được "có nên tạo module"
khỏi "module đó dùng gì để đóng gói atlas" mà không làm quyết định rỗng:

1. **Tạo module Gradle mới** `tools/asset-pipeline` (Điều 36: "Thêm/xoá/đổi tên module" → ADR).
2. **Thêm dependency mới** để đóng gói atlas thật — hiện repo **không có** code xử lý ảnh/đóng gói atlas
   nào (đã survey: không `ImageIO`/`BufferedImage`, không `MessageDigest` sha256, không atlas packer).

Ràng buộc từ tài liệu đã chốt, không phải AI tự nghĩ ra:

- Format atlas output phải là **định dạng `.atlas` gốc của libGDX**, vì client (`client-core`,
  `ResourceManager`) sẽ đọc bằng `com.badlogic.gdx.graphics.g2d.TextureAtlas` — và trang quản trị
  (ADMIN.md) cũng cần parse được file này để hiển thị preview (ART-PIPELINE §8.7: *"File `.atlas` của
  libGDX là text thuần, đọc được"*).
- Tên region bắt buộc `<asset_key>_<action>` + `index` tăng dần để `TextureAtlas.findRegions()` gom
  đúng mảng frame cho `Animation` (ART-PIPELINE §8.7) — sai định dạng là hỏng cả game lẫn trang quản trị.
- **Không phạm vi ADR này**: bước cuối "upsert vào DB `art_asset`" (ART-PIPELINE §8.5) — bảng đó cần
  schema `V19__create_art_registry.sql` mà chủ dự án **chưa duyệt** (CLAUDE.md §4: đổi DB schema luôn
  cần dừng hỏi, không phụ thuộc ADR này). Tool ở ADR này dừng lại ở: hạ mẫu → validate tên → đóng gói
  atlas 3 tier → sinh `asset-manifest.json`. Upsert DB là việc thêm nhỏ, làm sau khi V19 được duyệt.

## Decision

Tạo module Gradle `tools:asset-pipeline` (Java, dùng `kage.quality-conventions` + `application` plugin,
theo đúng khuôn `tools:load-test` đã có), và dùng **`com.badlogicgames.gdx:gdx-tools`** (cùng version
`1.14.2` với `gdx-core` đang dùng) làm engine đóng gói atlas — gọi thẳng
`com.badlogic.gdx.tools.texturepacker.TexturePacker`, không tự viết bin-packing.

Phạm vi v1 của tool: đọc `render-meta.json` sinh từ Blender → hạ mẫu @2x xuống @1x/@0.5x (Java
`BufferedImage` + `Graphics2D` bilinear, không cần thư viện resize riêng) → validate tên theo RESOURCE.md
§4 → pack 3 bộ atlas (low/med/high) qua `TexturePacker` → sinh `asset-manifest.json` (đường dẫn + sha256
+ size, khớp format RESOURCE.md §9). **Không** làm bước upsert DB ở v1.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **A. `gdx-tools` TexturePacker** ✅ | Chính là công cụ libGDX dùng để test/ship atlas cho hàng nghìn game khác — format `.atlas` ra khớp **tuyệt đối** với `TextureAtlas` runtime đọc, không có rủi ro lệch định dạng; cùng nhà (`com.badlogicgames.gdx`), cùng version với `gdx-core` nên không có xung đột version; license Apache 2.0 giống engine chính, không thêm rủi ro pháp lý mới; bảo trì cùng nhịp release libGDX (đang active, libGDX 1.14.2 vừa ra) | Chỉ chạy JVM (không dùng lại từ Python) — nhưng đây vốn là ranh giới đã có sẵn (`tools/blender` Python, phần còn lại của pipeline sẽ nối vào server Java sau) | **Chọn** — rủi ro thấp nhất cho đúng 1 việc: bảo đảm output đọc được bởi chính runtime sẽ tiêu thụ nó |
| **B. Tự viết bin-packing (Java, không thêm dependency)** | Không thêm dependency mới, khỏi qua Điều 36 lần hai | Phải tự đảm bảo output tương thích 100% với `TextureAtlas` parser của libGDX — sai một ký tự whitespace/field order trong `.atlas` là vỡ runtime, chỉ phát hiện lúc chạy game; tốn công viết lại thứ đã có sẵn, miễn phí, cùng hãng | **Không chọn** — rủi ro tương thích cao hơn hẳn lợi ích "khỏi thêm dependency", trong khi dependency đó gần như không có nhược điểm thật |
| **C. Viết bằng Python (Pillow + packer lib riêng, nối với `tools/blender/`)** | Cùng ngôn ngữ với phần Blender đã có; không cần JVM cho bước này | Bước cuối cùng của cùng pipeline (upsert DB) **chắc chắn** cần JDBC/Java để khớp pattern `JdbcOperations` đã dùng ở server — tách Python/Java giữa 2 nửa của cùng 1 tool là hai runtime, hai cách quản lý dependency (`pip` lẫn Gradle) cho một luồng logic; vẫn phải tự tái tạo định dạng `.atlas` (rủi ro như phương án B) vì không có TexturePacker Python chính chủ | **Không chọn** — không giải quyết được vấn đề tương thích format, lại thêm runtime thứ hai cho một pipeline sẽ cần Java ở bước cuối dù sao |

## Consequences

**Tích cực:**
- `assets/raw/render-out/` (15 bộ sheet thật đang có) lần đầu có đường đi thành atlas game dùng được,
  không còn dừng ở PNG rời.
- Format atlas bảo đảm đúng ngay từ đầu vì dùng chính công cụ chuẩn của engine — không có lớp "tự chế"
  nào có thể lệch so với những gì `TextureAtlas` runtime mong đợi.
- Mở khoá [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md) (đang Proposed, phụ thuộc rõ vào module
  này) tiến thêm được một bước dù ADR-028 tự nó cũng cần Accept riêng.
- `tools/asset-pipeline` là module Gradle **build-time only** (REPOSITORY-STRUCTURE.md §2: `tools/*`
  không chạy trong game) — không ảnh hưởng kích thước bundle client, không đi vào runtime.

**Đánh đổi phải chấp nhận:**
- Thêm một dependency mới (`gdx-tools`) dù chỉ dùng lúc build asset, không lúc chạy game — vẫn phải
  qua đánh giá Điều 36 lần này (đã trả lời 7 câu ở trên qua Alternatives A).
- v1 **không** có bước upsert DB — pipeline sinh atlas + manifest nhưng chưa tự động đăng ký vào
  `art_asset`, nghĩa là ART-PIPELINE §8.5 ("fail build nếu file không truy ra được dòng DB") **chưa
  được thi hành thật** cho tới khi `V19__create_art_registry.sql` được duyệt và nối thêm.
- `TexturePacker` chạy trong JVM headless — không có vấn đề license/native binary như một số packer C++,
  nhưng vẫn là thêm ~vài MB vào classpath build-time (chấp nhận được, không vào bundle game).

**Module bị ảnh hưởng:**
- `tools/asset-pipeline` (**mới**) — module Gradle, đăng ký vào `settings.gradle.kts`.
- `gradle/libs.versions.toml` — thêm coordinate `com.badlogicgames.gdx:gdx-tools:1.14.2`.
- **Không** ảnh hưởng `client-core`, `server-*`, hay bất kỳ module gameplay nào — đây là tool build-time
  đứng ngoài dependency graph gameplay hoàn toàn.

## Revisit Trigger

1. **libGDX nâng version lớn (breaking change ở `TexturePacker` API)** — khi đó cân nhắc lại có nên ghim
   `gdx-tools` tách khỏi version `gdx-core` hay không.
2. **`V19__create_art_registry.sql` được duyệt** — không phải trigger để *xem lại* ADR này, mà là tín
   hiệu để **mở rộng phạm vi** tool (thêm bước upsert DB) trong một PR riêng, không cần ADR mới vì đã
   nằm trong thiết kế gốc ART-PIPELINE §8.5 mà ADR này chỉ hoãn, không bác bỏ.
3. **Cần đóng gói atlas cho nền tảng ngoài desktop/Android** (ví dụ WebGL) mà `TexturePacker` không hỗ
   trợ định dạng cần thiết — xem lại phương án B/C lúc đó với dữ liệu thật thay vì suy đoán.
