# ADR Index — Project Kage

**Architecture Decision Record** — nơi lưu mọi quyết định kiến trúc lớn và **lý do** đằng sau.

| | |
|---|---|
| Luật gốc | [Hiến pháp Điều 37](../../00-governance/KAGE-CONSTITUTION.md#điều-37-adr-rules) |
| Mẫu | [ADR-TEMPLATE.md](ADR-TEMPLATE.md) |

---

## Vì sao ADR tồn tại

Sau 2 năm, không ai nhớ vì sao chọn Netty thay vì Virtual Thread. Một AI mới vào sẽ thấy "Virtual Thread đơn giản hơn" và đề xuất đổi — **rồi lặp lại cuộc thảo luận đã có, hoặc tệ hơn, đổi thật rồi phát hiện lý do cũ vẫn đúng.**

ADR chặn điều đó. ADR không ghi *ta làm gì* (code ghi rồi) — ADR ghi **vì sao ta không làm cách kia**.

---

## Quy tắc

1. **Không thay đổi kiến trúc lớn nếu chưa có ADR ở trạng thái Accepted** (Điều 37).
2. Đánh số **tuần tự**, **không tái sử dụng số** kể cả khi ADR bị Deprecated.
3. ADR **không bị xoá, không bị sửa nội dung quyết định**. Sai → tạo ADR mới `Supersedes ADR-XXX`.
4. Bắt buộc có: Context · Decision · **Alternatives Considered (≥2 phương án thật)** · Consequences · **Revisit Trigger**.
5. AI **không được** tự đặt Status = Accepted ([AI-WORKING-RULES.md §7](../../00-governance/AI-WORKING-RULES.md)).

---

## Danh sách ADR

| ADR | Tiêu đề | Status | Chặn gì |
|---|---|---|---|
| [ADR-001](ADR-001-Why-Java25.md) | Chọn Java 25 (LTS) làm ngôn ngữ chủ đạo | ✅ Accepted | — |
| [ADR-002](ADR-002-Why-libGDX.md) | Chọn libGDX làm nền tảng Client Engine | ✅ Accepted | — |
| [ADR-003](ADR-003-Why-Netty.md) | Chọn Netty làm nền tảng Network I/O | ✅ Accepted | — |
| [ADR-004](ADR-004-Why-PostgreSQL.md) | Chọn PostgreSQL, không dùng ORM | ✅ Accepted | — |
| [ADR-005](ADR-005-Why-Redis.md) | Dùng Redis làm in-memory cache/session store | ✅ Accepted | — |
| [ADR-006](ADR-006-Why-ECS.md) | Dùng ECS, khởi đầu bằng Ashley | ✅ Accepted *(có điều kiện)* | Đánh giá lại cuối Phase 9 |
| [ADR-007](ADR-007-Why-Plugin-System.md) | Xây dựng Plugin System | 🔀 **Superseded by ADR-030** | — |
| [ADR-008](ADR-008-Why-EventDriven.md) | Dùng Event-Driven giữa hệ thống gameplay | ✅ Accepted | — |
| [ADR-009](ADR-009-Why-No-Spring-Boot.md) | Không dùng Spring Boot cho Game Server | ✅ Accepted | — |
| [ADR-010](ADR-010-Realm-Model.md) | Single-realm ở MVP, giữ sẵn `realm_id` | ✅ Accepted | — |
| [ADR-011](ADR-011-Enforce-Dependency-Rules.md) | Dùng ArchUnit thực thi luật dependency bằng máy | ✅ Accepted *(2026-08-01)* | — |
| [ADR-012](ADR-012-Package-Root.md) | Package root `dev.kage` | ✅ Accepted | — |
| [ADR-013](ADR-013-Flyway-vs-Liquibase.md) | Dùng Flyway làm công cụ migration DB | ✅ Accepted | — |
| [ADR-014](ADR-014-Kubernetes-vs-Docker-Compose.md) | Docker Compose + VM đơn, không dùng Kubernetes | ✅ Accepted | — |
| [ADR-015](ADR-015-Micrometer-vs-OpenTelemetry.md) | Micrometer cho Metrics, OpenTelemetry cho Tracing | ✅ Accepted | — |
| [ADR-016](ADR-016-Scripting-Engine.md) | Dùng GraalJS làm scripting engine | ✅ Accepted | — |
| [ADR-017](ADR-017-Auth-Token.md) | Auth token: session token tự phát hành + Redis | ✅ Accepted | — |
| [ADR-018](ADR-018-Tick-Rate.md) | Tick rate **60Hz** | ✅ Accepted | — |
| [ADR-019](ADR-019-GC-Choice.md) | Dùng ZGC cho server production | ✅ Accepted | — |
| [ADR-020](ADR-020-Viewport-Strategy.md) | `FitViewport` cho camera gameplay — công bằng PvP | ✅ Accepted *(2026-08-01, khớp code `WorldScreen` từ Phase 1)* | — |
| [ADR-021](ADR-021-Box2D-vs-AABB.md) | Không dùng Box2D cho di chuyển — AABB tự viết khi có map thật | ✅ Accepted *(2026-08-01, dựa dữ liệu Phase 3-9 đã đóng)* | PHYSICS.md §2-§4/Technical Decision #2/#8 cần rà lại (chưa làm) |
| [ADR-022](ADR-022-Custom-FSM-No-Gdx-Ai.md) | Tự viết FSM cho AI quái, không dùng gdx-ai | ✅ Accepted *(2026-08-01, khớp code `AiSystem` từ Phase 4)* | — |
| [ADR-023](ADR-023-Desktop-First-Input.md) | Desktop-first — bàn phím + chuột là sơ đồ điều khiển chính; mobile là Post-MVP và cần ADR riêng | ✅ **Accepted** *(2026-09-05)* | Ô ADR **cuối cùng chưa viết**, treo từ Phase 2 và chặn ba thứ: ngân sách tam giác của ADR-031, CLIENT.md §5/§12, và ADR-024/028. Hợp thức hoá thực tế chứ không đổi hướng — ADR-033 (A/D/W), ADR-034 (chọn mục tiêu bằng CHUỘT) và 9 cửa sổ HUD mở bằng 9 phím chữ đều đã giả định bàn phím+chuột từ lâu. Cố ý KHÔNG thiết kế trước cho cảm ứng (Điều 9) |
| [ADR-024](ADR-024-Asset-Distribution-Strategy.md) | Đóng gói toàn bộ asset trong build, hoãn patch/CDN/Play Asset Delivery tới Revisit Trigger | ✅ Accepted *(2026-08-01)* | **Phần "đóng gói toàn bộ" bị [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md) amend**; phần hoãn CDN/OBB vẫn giữ |
| [ADR-025](ADR-025-Idempotency.md) | Idempotency cho Command có side-effect | ✅ Accepted | — |
| [ADR-026](ADR-026-Dungeon-Instance-Model.md) | Dungeon instance model: cùng process, World riêng trong 1 JVM | ✅ Accepted | — |
| [ADR-027](ADR-027-TLS-Transport-Security.md) | TLS cho kết nối game: in-process Netty (server) + JDK `SSLSocket` pin cert (client), self-signed cho Closed Beta | ✅ Accepted *(2026-07-31, chủ dự án xác nhận)* | — |
| [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md) | Bản cài đóng gói 1 tier đồ họa mặc định (MEDIUM), tier khác tải theo yêu cầu; client luôn qua `ensureAssetsAvailable(tier)` | ✅ Accepted *(2026-08-01)* | **Amend ADR-024**; cần `tools/asset-pipeline` xuất atlas tách tier + manifest theo tier |
| [ADR-029](ADR-029-Asset-Pipeline-Tool.md) | Tạo module `tools:asset-pipeline`, dùng `gdx-tools` TexturePacker đóng gói atlas | ✅ Accepted *(2026-08-01)* | Mở khoá [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md); v1 không gồm upsert DB — chờ `V19__create_art_registry.sql` được duyệt |
| [ADR-030](ADR-030-No-Plugin-System.md) | Không xây Plugin System — xoá `engine-plugin-api` | ✅ Accepted *(2026-08-01)* | Supersedes ADR-007 |
| [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md) | Nhân vật render 3D thời gian thực qua camera orthographic khoá góc (2.5D); VFX/icon/UI vẫn 2D | ✅ Accepted *(2026-08-03)* | Amend [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md), thu hẹp phạm vi [ADR-029](ADR-029-Asset-Pipeline-Tool.md); thêm dependency `gdx-gltf` — **đã giải quyết bằng [ADR-032](ADR-032-JitPack-Dependency-Defence.md)** (vendor vào `third-party/m2/`, không qua JitPack) |
| [ADR-032](ADR-032-JitPack-Dependency-Defence.md) | Đóng gói `gdx-gltf` vào `third-party/m2/` làm kho Maven cục bộ, bỏ hẳn `jitpack.io` khỏi build | ✅ **Accepted** *(2026-08-06)* | Gỡ việc treo #2 của [ADR-031](ADR-031-25D-Realtime-3D-Rendering.md); jar đo thật 318 KB, Apache-2.0, phụ thuộc bắc cầu duy nhất là `gdx` vốn đã có |
| [ADR-033](ADR-033-Ninja-School-Style-Movement.md) | Đổi mô hình di chuyển sang trái/phải + nhảy thật kiểu Ninja School Online — bỏ di chuyển lên/xuống, nhảy né được đòn tầm thấp/lên bệ cao | 🟢 **Accepted** *(2026-08-08)* | Đổi protocol (`MoveCommand`) + đổi mô hình vật lý (trục Z thật lần đầu); kế thừa [ADR-021](ADR-021-Box2D-vs-AABB.md) (không Box2D) |
| [ADR-034](ADR-034-Manual-Attack-And-Experience.md) | Đánh thường phải BẤM mới ra đòn (bỏ auto-target cho người chơi, quái vẫn tự đánh) + hệ thống kinh nghiệm/lên cấp đầu tiên | 🟢 **Accepted** *(2026-08-09)* | Chủ dự án quyết sau playtest; thêm opcode 407/208, mâu thuẫn GDD §6 cho tới khi GDD được sửa |
| [ADR-035](ADR-035-Client-State-Sync-Opcodes.md) | Lấp 4 khoảng trống đồng bộ trạng thái: liệt kê túi đồ (507/508), catalog vật phẩm (509/510), số vàng (209), nhật ký nhiệm vụ (610/611) | ✅ **Accepted** *(2026-08-27)* | Mở khoá giao diện túi đồ/trang bị/cửa hàng/nhật ký nhiệm vụ. Chỉ THÊM 7 opcode, không đổi message đang chạy, không đổi schema DB. Catalog theo **A′** (gửi qua mạng + `version` do server phán quyết) |
| [ADR-036](ADR-036-Equipment-Stats-And-Enhancement-Effect.md) | Trang bị phải có chỉ số thật: thêm `bonus_def`/`bonus_res`/`bonus_hp`/`required_level` vào `item_template`, và cường hoá cộng theo phần trăm chỉ số gốc | ✅ **Accepted** *(2026-08-30)* | Đo được 3 lỗ: 6/7 ô trang bị vô nghĩa (chỉ có cột `bonus_atk`), **cường hoá KHÔNG làm gì** (xác minh bằng chơi thật: +0→+7, mất 8.900 vàng, chỉ số không đổi), và điều kiện cấp độ mà GAMEPLAY.md §7 mô tả thì không tồn tại. Chủ dự án duyệt trọn D1-D5 + `maxHp = 100 + bonus_hp`; **đã thi hành cùng ngày** (migration V22, `EnhancementTable.enhancedBonus`, 4 field `7..10` của `ItemCatalogEntry` — `protocolVersion` không tăng) |
| [ADR-037](ADR-037-Monster-Template-And-Spawn-Tables.md) | Quái đọc từ DB: `monster_template` + `monster_spawn`, thay bốn chỗ hardcode (chỉ số, điểm spawn, EXP, và một phần AI) | ✅ **Accepted** *(2026-08-30)*, đã thi hành | Playtest ADR-036 đo được: quái duy nhất có `atk = 8` nên trọn bộ giáp 10.900 vàng chỉ đổi 7→6 sát thương ⇒ **DEF/RES của V22 không có gì đối chứng**. Cả 4 điểm spawn hardcode CÙNG một mã nên overworld chỉ có một loài. Chủ dự án chọn **module `feature-monster`** + duyệt field `model_key`; migration V23, `MonsterExperience` bị xoá, 3 loài quái |
| [ADR-038](ADR-038-Theme-Pivot-Tu-Tien.md) | Đổi bối cảnh sang **tu tiên** (Phàm Nhân Tu Tiên-style): re-theme tường thuật + tên hiển thị, KHÔNG đổi cơ chế/protocol/DB/tên code; 6 cảnh giới × 9 tầng (Luyện Khí→Trúc Cơ→Tử Phủ→Kim Đan→Nguyên Anh→Hoá Thần) map lên Lv1–100 | ✅ **Accepted** *(2026-09-01 — chủ dự án duyệt trọn D1-D9 + bố cục STORY.md + 2 tên riêng)* | **Đã thi hành TRỌN VẸN**: Đợt 1 (GDD v0.3.0 + GLOSSARY v1.1.0 + [STORY.md](../../13-game-design/STORY.md) §1–§8), Đợt 2 (~90 chuỗi i18n, hội thoại Chu Hoành, `CultivationRealm`), D8 (quét docs), và **Đợt 3 art xong 2026-09-03** — cả 7 dòng bảng §5 của [ART-AUDIT-TU-TIEN](../../14-art/ART-AUDIT-TU-TIEN.md) đã tick và thi hành, **0 lượt Rodin**. Còn treo: chủ dự án đọc duyệt NỘI DUNG STORY.md, và một khoá Hyper3D trả tiền nếu muốn bản Rodin cho `char_town-elder` (không chặn gì) |
| [ADR-039](ADR-039-Per-Species-AI-Parameters.md) | Tham số AI theo LOÀI: 6/8 hằng của `AiConstants` thành cột `monster_template` (aggro/leash, tốc độ đuổi/tuần tra, bán kính tuần tra, thời gian đứng yên); 2 hằng còn lại CỐ Ý ở lại code | ✅ **Accepted** *(2026-09-05)* | Trả nợ D7 của ADR-037. Tìm được **bug thật sống từ Phase 4**: `spawnMonster` gắn AI cho MỌI loài không phân biệt, nên **cọc luyện công rời cột đuổi người chơi 3 m/s và đánh** — và một test đã đóng băng hành vi đó như thể là đặc tả. Cũng kết luận **AI.md §10 nói quá** ("tham số AI là config"): `ARRIVAL_THRESHOLD_METERS` là epsilon hình học theo `FIXED_DT`, `MAX_PATROL_DURATION_SECONDS` là van chống kẹt — van an toàn mà chỉnh được thì không còn là van an toàn. Migration V25 bảo toàn hành vi theo cấu trúc (mọi DEFAULT = hằng đang chạy), ngoại lệ duy nhất là cọc tập thành thụ động. ✅ **V25 đã chạy qua Postgres thật 2026-09-05** (áp lên DB dev đang có dữ liệu, v24→v26) và **playtest sống xác nhận D4**: đứng đè lên cọc 20 giây, 0 sát thương; đi vào tầm `wandering_bandit` thì bị đuổi và đánh **−18** ⇒ thụ động là theo LOÀI, không phải AI hỏng. Cần chủ dự án quyết D1 (chia cơ chế/nội dung) và D4 (cọc tập thụ động) |
| [ADR-040](ADR-040-NPC-Template-And-Placement.md) | NPC đọc từ DB: thêm `npc_spawn` (FK tới `npc_template` đã có từ V10) thay ba hằng `SAMPLE_NPC_*` trong `WorldFacade` | ✅ **Accepted** *(2026-09-05)* | Trả nốt nợ thứ hai ADR-037 đặt tên. Phát hiện: `npc_template` **đã tồn tại từ V10** (Phase 6) và chính comment của nó thừa nhận khoảng trống — NPC có định danh trong DB mà không có vị trí. V26 seed `town_elder` đúng toạ độ `(-5, 0)` cũ ⇒ hành vi sau migration phải y hệt. Cố ý KHÔNG làm: cột `role` (cây hội thoại đã mang thông tin đó), `model_key`/đổi protocol, module mới, và seed `shop_keeper` (chưa có script hội thoại ⇒ `ScriptValidator` sẽ chặn server khởi động). `MonsterCatalogLoader` → `WorldContentLoader` vì hàm dựng `WorldFacade` đã chạm trần 4 tham số của Checkstyle. ✅ **V26 đã chạy qua Postgres thật 2026-09-05** và **kiểm chứng bằng mắt**: Chu Hoành hiện đúng `(-5, 0)`, bấm vào nói chuyện được, cây hội thoại tu tiên chạy đúng |
| [ADR-041](ADR-041-Spawn-Position-In-Enter-World.md) | Server gửi `spawn_x`/`spawn_y` trong `SelectCharacterResponse`; client thôi đoán `(0,0)` lúc vào world | ✅ **Accepted** *(2026-09-05)* | Bắt được khi playtest ADR-039/040: đăng nhập ở `x=8`, `x=-3`, `x=-5` cho ra khung hình **giống nhau từng pixel**, và bấm một phát phím đi thì khung hình nhảy về đúng chỗ. Nguyên nhân là một giả định ĐÚNG-lúc-viết đã mục: javadoc `WorldScreen#spawnLocalPlayer` ghi rõ *"chưa có persistence vị trí"* — persistence sau đó đã có, giả định thì ở lại. Server nạp `loadPosition()` rồi `attachCharacter` đúng chỗ nhưng **không gửi con số đó cho client** ⇒ client bịa một mẩu trạng thái gameplay server sở hữu (Điều 24). Tự lành sau input đi đầu tiên nên sống lâu mà không ai gặp. Additive, `protocolVersion` KHÔNG tăng — đúng tiền lệ ADR-036 trên chính message này |
| [ADR-042](ADR-042-Outbound-Packet-Batching.md) | Giảm SỐ LƯỢNG message gửi ra — **gộp flush đã dựng thử và bị số đo BÁC BỎ** | ⏸️ **Proposed — HOÃN** *(2026-09-05)* | Đo `jdk.ThreadCPULoad` (CPU THẬT theo luồng, không lấy mẫu) ở N=1000: **`nioEventLoopGroup` ~90% CPU, `world-tick-pool` chỉ ~3%** — mô phỏng game gần như miễn phí. Trong Netty: **TLS 39,5%**, cấp phát buffer 27,7%, ghi socket 20,7% — **cả ba là chi phí THEO GÓI**; protobuf chỉ 0,9%. Nguyên nhân: **59.760 gói/giây, trung bình 48 byte**, mỗi gói một `writeAndFlush` ⇒ một TLS record + một syscall. ⇒ ~36µs CPU TLS mỗi gói, tức gần như toàn bộ là overhead mỗi record chứ không phải mã hoá. **Đã dựng thử `FlushConsolidationHandler` và đo: chỉ ~3%, trong nhiễu** — vì mỗi kết nối chỉ nhận ~60 gói/giây, cách nhau ~16ms, KHÔNG có gì để gộp. Prototype đã gỡ, master sạch. Đề xuất xoay sang gộp nội dung AoI (đổi protocol). **Đã tìm ra vì sao TLS đắt**: `Cipher.init` được gọi lại cho MỖI TLS record bởi SSLEngine của JDK (TLS 1.3) — đặc tính của JDK, không phải lỗi dự án ⇒ sinh thêm phương án **F: đổi provider sang `netty-tcnative`** (thư viện MỚI, cần quyết riêng). Cũng cải chính bảng chi phí của chính nó: phân loại theo khung TRÊN CÙNG cho **buffer 22,4% là khoản lớn nhất**, TLS ~26,9% chứ không phải 39,5%. ⚠️ Cũng cải chính §5.1: mọi đích tối ưu ECS/AoI/AI từng đề xuất đều nằm trong 3% kia | **Chủ dự án quyết hoãn**: N=1000 mới dùng 5,3/32 lõi, p95 0,8ms, 0 lỗi — chưa phải vấn đề ở quy mô Closed Beta. Mở lại khi N≥1500 và Netty vượt ~70% CPU. |
| [ADR-043](ADR-043-Push-Max-Hp-On-Equipment-Change.md) | Máu tối đa mới phải theo gió về client ngay khi đổi trang bị (field additive cho `EquipItemResponse`/`UnequipItemResponse`/`EnhanceItemResponse`) | ✅ **Accepted** *(2026-09-05)* — thi hành + kiểm chứng sống cùng ngày | Bắt được khi PLAYTEST SỐNG quest `night_watch_bandit`: mặc trọn bộ `novice_*` (**+18 máu**) giữa world mà thanh máu vẫn `100 / 100`, chỉ sau khi CHẾT và hồi sinh mới thành `118 / 118`. **Server KHÔNG sai** — `applyEquipmentBonus` cập nhật `HealthComponent.max` ngay trong tick đó; khoảng trống nằm ở GIAO THỨC: không gói nào chở máu tối đa của chính mình ngoài `SelectCharacterResponse` (vào world) và `CharacterRespawnedEvent` (hồi sinh). Đây **đúng là nửa sau của bug ADR-036 từng sửa** (`139 / 100`) — lần đó chỉ vá thời điểm vào world. ⚠️ **Bẫy thi hành (D5)**: `applyEquipmentBonus` đẩy lệnh vào `inbox` của tick, đọc entity ngay sau khi gọi sẽ ra số CŨ ⇒ phải tính lại qua `WorldFacade.characterMaxHp(bonus)`. Additive, `protocolVersion` KHÔNG tăng | **Chờ chủ dự án**: duyệt/từ chối, và chọn (a) tính lại bằng hàm static hay (b) đợi tick qua `CompletableFuture` ở D5. |
| [ADR-044](ADR-044-Quest-Prerequisite-Enforcement.md) | Thi hành `prerequisite_quest_id` — quest có thứ tự kể chuyện thì phải đi đúng thứ tự | ✅ **Accepted** *(2026-09-05)* — thi hành cùng ngày | Cột có từ **V11** (Phase 6) và suốt từ đó **không dòng dữ liệu nào dùng, không dòng code nào đọc** — `grep prerequisite` chỉ ra đúng một javadoc ghi "thêm khi có nhu cầu thật". **V28 (2026-09-05) là dòng đầu tiên dùng nó**, nên nhu cầu đã tới: hôm nay nhận được quest "trực đêm" mà chưa từng đập mộc nhân, ngược thứ tự [STORY.md §6](../../13-game-design/STORY.md). Không ai gặp trước đây vì trước V28 cả dự án chỉ có ĐÚNG MỘT quest. Từ chối vì lý do MỚI ⇒ client phải phân biệt được ⇒ thêm `ACCEPT_QUEST_OUTCOME_PREREQUISITE_NOT_MET = 4` (chỉ THÊM giá trị enum, `protocolVersion` KHÔNG tăng). **Không migration** — cột đã có sẵn. **Không giấu lựa chọn hội thoại** (Điều 15: server từ chối, không phải client tự lọc) | **Chủ dự án chốt cả hai**: mốc **`CLAIMED`** (đánh xong mà chưa lĩnh thưởng vẫn bị chặn), và **KHÔNG** giấu lựa chọn hội thoại. Đã thi hành + 4 test. 🐛 Lỗi bắt được lúc làm: bản mapper đầu gọi `rs.wasNull()` SAU hai lần đọc cột khác nên nó trả lời về `script_ref` ⇒ **mọi quest trông như có tiên quyết id=0**, chặn sạch cả quest mở màn — 8 test bắt ngay |
| [ADR-045](ADR-045-Appearance-Changed-Event.md) | Event "ngoại hình vừa đổi" — người ĐANG nhìn thấy phải thấy model mới ngay | ✅ **Accepted** *(2026-09-06)* — thi hành + kiểm chứng đường dây cùng ngày | Đổi hệ phái giữa phiên chỉ vá được **một nửa** (2026-08-29): `updateAppearance` sửa cache nên người tới SAU thấy model mới, còn người **đang đứng cạnh** vẫn thấy model cũ — client cố ý bỏ qua `CharacterSpawnedEvent` lặp để chặn entity trùng, nên server phát lại cũng vô ích. Ba đường đều là quyết định: despawn+spawn (**nháy hình**), client nhận spawn lặp (**phá thế bất biến "spawn = tạo mới"**), hay **event riêng** (đổi protocol ⇒ ADR) | Chủ dự án chọn **event riêng**: opcode **307**, `protocolVersion` KHÔNG tăng (chỉ THÊM, đúng tiền lệ ADR-035). `server-world` KHÔNG biết "ngoại hình" — nó chỉ trả lời "ai đang nhìn thấy ai"; gateway mới tra hệ phái. Client `replaceCharacterModel` thay model tại chỗ, GIỮ hướng mặt + trạng thái chết. Chính người đổi KHÔNG nhận event (đường của họ đã có từ opcode 207) |
| [ADR-046](ADR-046-Health-Recovery.md) | Hồi máu — ba nguồn, một đường ống báo về client | ✅ **Accepted** *(2026-09-07)* — thi hành cùng ngày | Cả arc 1 KHÔNG có một nguồn hồi máu nào (`grep regen|heal` ở `server-world/src/main` = 0 kết quả; kỹ năng `thuy_hoi_phuc` đòi hệ Thuỷ tức từ Lv10 — đúng lúc arc 1 kết thúc). Playtest client thật 2026-09-06: **chết BA lần trong ~15 phút**, cả ba vì không có cách hồi máu. Quest 3 bắt hạ HAI thạch vệ mà hạ xong con đầu còn **38/118**. ⚠️ Và đánh giá cũ "(a) không đổi protocol" là SAI: client KHÔNG có đường nhận HP ngoài đòn đánh (javadoc `CombatHudController`), nên cả ba hướng đều cần protocol ⇒ gộp một ADR | Chủ dự án chọn **cả ba hướng** và Accept nguyên trạng. **1 event cho cả 3 nguồn + reset của quái**: opcode **408** `HealthChangedEvent`, không có field "vì sao" (vạch máu không quan tâm). Người chơi hồi DẦN (8s ngoài chiến đấu → 3%/giây), quái hồi ĐẦY khi về tới điểm spawn — hai cơ chế vì hai loại entity. Vùng an toàn ở **DB** (module MỚI `feature-world`, chủ dự án chọn) và là consumer THẬT đầu tiên của `TriggerZoneSystem`. Đan dược: opcode **512/513**, `UseItemResponse` KHÔNG mang HP (inbox bất đồng bộ — bẫy ADR-043). `protocolVersion` KHÔNG tăng |
| [ADR-047](ADR-047-Dungeon-Entered-Event.md) | Người bị kéo vào hầm ngục theo nhóm phải được báo | ✅ **Accepted** *(2026-09-13)* | Nợ từ bàn giao 2026-08-30, **tái hiện qua socket thật**: A bấm vào hầm ngục, server dời cả B, nhưng chỉ A nhận `EnterDungeonResponse` — B nhận spawn của boss mà **không một gói nào trong dải 1500–1599**. Hậu quả với B: không biết mình đang ở trong hầm ngục (không dải chữ HUD), làng vẫn đứng đông cứng trên màn hình, vị trí sai tới lần đi đầu tiên, có thể trùng id `-1` với boss. Chỉ chiều VÀO bị; exit/mất kết nối/rời nhóm/boss chết đã kiểm, không cần gói báo. Không đường nào sẵn có mà không đổi nghĩa message đang chạy (chưa từng có Response đẩy với `sequenceId = 0`) | ✅ **Chủ dự án chọn phương án B (2026-09-13), thi hành ngay cùng ngày**: opcode **1504** `DungeonEnteredEvent`, cùng khuôn `DuelStartedEvent`, `protocolVersion` KHÔNG tăng. Phương án đã cân nhắc và loại: **E** — event mới `DungeonEnteredEvent` (1504), cùng khuôn `DuelStartedEvent`, `protocolVersion` KHÔNG tăng; hoặc **E** — bỏ "kéo theo cả nhóm" (không đổi protocol nhưng đổi luật chơi). Ràng buộc chung: lời báo phải tới TRƯỚC spawn của World mới. Kèm phát hiện phụ: người GỬI cũng có khe thứ tự response-vs-spawn (chưa sửa) |
| [ADR-048](ADR-048-Boss-Into-Data-Tables.md) | Kéo boss vào bảng dữ liệu — `is_boss` + `ai_script_ref` + 2 cột nhịp AI | ✅ **Accepted** *(2026-09-12)* — thi hành cùng ngày | **Điều kiện mở lại do chính ADR-037 D7 đặt ra đã xảy ra**: boss THỨ HAI (hộ pháp Huyết Sa, cao trào arc 2). Boss hôm nay hardcode ở BỐN chỗ, và chỗ thật sự chặn là `BossAiSystem` nạp ĐÚNG MỘT script lúc khởi tạo ⇒ boss thứ hai không có đường khai hành vi riêng. Boss cũng là thực thể DUY NHẤT còn vẽ bằng hộp vì không có `model_key`. Đề xuất: dùng chung `monster_template` (boss khác quái ở HÀNH VI, không ở dữ liệu), `sample_boss` chuyển nguyên trạng, test Phase 8 làm hàng rào. Không đổi protocol, không đụng client | ✅ **Accepted 2026-09-12**, chủ dự án duyệt trọn D1–D8. Cắt cảnh cố ý KHÔNG thuộc ADR này (D8 — kể bằng hội thoại) |
| [ADR-049](ADR-049-Monster-Retaliation.md) | Quái đánh trả khi bị đánh — cột `retaliates` | ✅ **Accepted** *(2026-09-12)* — thi hành ngay cùng ngày | **Playtest quest 9 đo được cao trào arc 2 KHÔNG đánh trả một nhát nào**: boss khai `aggro_radius = 0` (cố ý — hắn đứng giữa sân tông môn, trên hành lang mà người chơi arc 1 đi qua), mà `AiSystem` chỉ đặt mục tiêu ở MỘT chỗ là vòng quét aggro ⇒ aggro 0 nghĩa là **không bao giờ tự vệ**, và script hai pha của hắn chưa từng chạy một dòng. Không sửa được bằng dữ liệu: trục chơi MỘT chiều nên "đi ngang" là đi xuyên qua, và vùng an toàn KHÔNG chặn aggro (chỉ hồi máu). Đề xuất: cột `retaliates` mặc định `false` (giữ nguyên ADR-039 D4 cho cọc tập) + đặt mục tiêu khi dính damage | Chờ Accept. Không đổi protocol, không đụng client |
| [ADR-050](ADR-050-Safe-Zones-Along-The-Trunk.md) | Trạm tiếp ứng dọc trục — vùng an toàn thôi chỉ hồi máu, bắt đầu CHẶN aggro | ✅ **Accepted** *(2026-09-18)* | **Playtest Lv28–36 đo được lát cắt cuối của v1 KHÔNG đi bộ tới nơi được**: đi `0→150` còn **51/206** máu, và trận Lv29 từ máu ĐẦY thua **2/3 lượt** dù số học đúng thiết kế. Nguyên nhân là hình học: tầm với thật (`patrol 3 + aggro 3..6`) của 11 điểm spawn **phủ kín** trục, khe trống rộng nhất từ `x=126` là **2m** — hẹp hơn chính tầm đánh ⇒ nửa cuối trục KHÔNG có chỗ đứng hồi máu, nên nguồn hồi số một (ADR-046 D2) vô dụng ở đúng chỗ cần. Và đặt vùng an toàn vào đó cũng không cứu được vì **vùng an toàn hiện không chặn aggro** (`AiSystem` không có một dòng nào nhắc tới zone) — trong khi STORY §7 đã hứa "trong tông không thể động thủ" từ ADR-038 | ✅ **Accepted 2026-09-18 — duyệt trọn D1–D8, D6 chọn phương án (a)** (mốc dựng từ `npc_spawn` đã có, 0 opcode). Chủ dự án đã chọn HƯỚNG (c) "vùng an toàn dọc trục" 2026-09-17. Chặn aggro là **luật** của mọi vùng (1 lần đọc component có sẵn, không đổi constructor); hồi máu thành **cột** `heals BOOLEAN` — đúng cột mà **Revisit Trigger của ADR-046 đã đặt tên trước**. Kèm: quái đang đuổi mà mục tiêu vào vùng thì bỏ cuộc; đánh ra từ trong vùng thì mất quyền được che 8 giây (dùng lại `REGEN_DELAY_SECONDS`, chặn lỗ bắn tỉa qua đường nhắm THỨ HAI của ADR-049). 6 trạm ở V49, và bất biến hình học đổi từ "quái không với tới được" sang "vùng không chồng dải tuần tra" |
| [ADR-051](ADR-051-World-Changed-Event.md) | Một event "bạn vừa đổi World" chung cho mọi cú đổi World | ✅ **Accepted** *(2026-09-24)* | **Revisit Trigger của ADR-047 đã xảy ra**: chủ dự án quyết "chết trong hầm ngục ⇒ tự về world chính" — nguồn THỨ HAI đưa người chơi sang World khác mà họ không bấm. Client không có gói nào cho chiều RA do server quyết ⇒ boss/đồng đội đọng, `inDungeon` kẹt, vị trí sai. Khoảng trống này có cả ở đấu trường (đọc code). Đề xuất `WorldChangedEvent` (308) phát TRƯỚC attach cho MỌI cú đổi World, đóng luôn khe thứ tự của người gửi (ADR-047 "Phát hiện phụ") | ✅ **Chủ dự án chọn phương án A (2026-09-24)**: `WorldChangedEvent` (308), `protocolVersion` KHÔNG tăng. **Đã thi hành cùng ngày** (`WorldTransfer` giữ thứ tự cho hầm ngục + đấu trường; gục trong hầm ngục ⇒ về world chính) |
| [ADR-052](ADR-052-In-Game-GM-Commands.md) | Lệnh GM trong game — đi qua đường chat `/gm`, hai cấp quyền GM/ADMIN, nhúng trong server, mọi lệnh đều audit | ✅ **Accepted** *(2026-10-18, lô 15 — Q1–Q6 theo khuyến nghị)* | Thi hành quyết định chủ dự án 2026-10-01 cho dòng #6 "ĐANG CHỜ" (chặn ADMIN.md). Bảng mới `gm_role` + `gm_audit_log` (ghi TRƯỚC khi có hiệu lực) + `chat_mute` (phác thảo V52, chưa tạo — V50/V51 đã thuộc migration dữ liệu khác), module mới `feature-gm`, 0 opcode mới, 2 giá trị enum chỉ-thêm (`OUTCOME_MUTED`, `REASON_GM_MOVED`), `protocolVersion` KHÔNG tăng. `give`/`gold` đi thư hệ thống có sẵn (transaction + `MAIL_CLAIM`). ⚠️ Đăng nhập hôm nay username-only ⇒ đề xuất thêm chốt `KAGE_GM_ENABLED` + CIDR (D3). Kick cần client nhận khoá `error.kicked_by_gm`, nếu không `AutoRelog` vô hiệu hoá nó | **Chờ chủ dự án**: duyệt D1–D10 + trả lời Q1–Q6 |
| [ADR-053](ADR-053-Bundled-Client-Runtime.md) | Bản cài client desktop mang theo runtime Java 25 riêng (jlink + `jpackage --type app-image`, zip, không plugin) | ✅ **Accepted** *(2026-10-18, lô 15 — Q1–Q7 theo khuyến nghị)* | Thi hành quyết định chủ dự án 2026-10-01 "Closed Beta đóng gói KÈM JRE 25": `installDist` hôm nay chạy bằng `java` của máy người chơi ⇒ rủi ro JNI `HasIdentity` của JDK 28 (LWJGL chép thiếu bảng) không kiểm soát được. Đã thử thật: jdeps ra 4 module nhưng **bỏ sót `jdk.localedata`** (số tiếng Việt thành `1,234,567.5`); runtime 5 module `--include-locales=en,vi` = **49 MB**, app-image chưa asset 71 MB / zip 55,7 MB; `KageClient.exe` sống 20 s từ thư mục ngoài repo, nạp đủ native LWJGL/OpenGL. JBR (toolchain hiện tại) KHÔNG có jlink/jpackage; Temurin 25 không có `jmods/` ⇒ phải dựng trên Windows | **Chờ chủ dự án**: duyệt D1–D8 + trả lời Q1–Q7 |

| [ADR-054](ADR-054-Abandon-Quest.md) | Lệnh bỏ nhiệm vụ — `AbandonQuestCommand` (612/613), bỏ rồi nhận lại được | ✅ **Accepted** *(2026-10-18, lô 15)* — thi hành cùng ngày | Dòng 69(a): script quest lỗi ⇒ kẹt `IN_PROGRESS` vĩnh viễn. Chỉ bỏ được quest đang làm; xoá dòng tiến độ bằng compare-and-delete, audit `QUEST_ABANDON`; không hoàn đồ đã giao |
| [ADR-055](ADR-055-Daily-Quests.md) | Nhiệm vụ ngày — cột `repeat_policy`, làm mới lười lúc 0h giờ VN, thưởng vàng qua `ctx.give_gold` | ✅ **Accepted** *(2026-10-18, lô 15)* — thi hành cùng ngày | GDD §8/STORY §7 "công việc tông môn"; dòng 64(a) thiếu nguồn vàng lặp lại. 3 quest ngày ở Chu Hoành |
| [ADR-056](ADR-056-Character-Stat-Sheet-And-Combat-Power.md) | Bảng nhân vật + Lực Chiến — opcode 210/211, server tính | ✅ **Accepted** *(2026-10-18, lô 15)* — thi hành cùng ngày | Người chơi không xem được chỉ số; GDD §5 Lực Chiến chưa có code |
| [ADR-057](ADR-057-Leaderboards.md) | Bảng xếp hạng (Bia Anh Danh) — 3 bảng top 50, opcode 1202/1203, cache 60 s | ✅ **Accepted** *(2026-10-18, lô 15)* — thi hành cùng ngày | GDD §7 "Xếp hạng"; dải opcode Ranking để trống từ Phase 0 |
| [ADR-058](ADR-058-Send-Mail-Idempotency.md) | `SendMailCommand` mang `idempotency_key` — gửi lại không trừ vàng/đồ hai lần | ✅ **Accepted** *(2026-10-18, lô 15)* — thi hành cùng ngày | Dòng 74(c). Khoá rỗng = client cũ, gửi như trước; outcome mới `DUPLICATE_REQUEST` (9) |
| [ADR-059](ADR-059-Party-And-Guild-Changed-Events.md) | Event "nhóm/bang vừa đổi" — `PartyChangedEvent` (1318) + `GuildChangedEvent` (720) | ✅ **Accepted** *(2026-10-18, lô 15)* — thi hành cùng ngày | Dòng 40(iii): bị kick / bang giải tán / đổi trưởng nhóm không ai được báo |
| [ADR-060](ADR-060-Daily-Check-In.md) | Điểm danh hằng ngày — chu kỳ 7 ngày, quà qua thư hệ thống (V62, module `feature-checkin`) | ✅ **Accepted** *(2026-10-18, lô 16)* — thi hành cùng ngày | Không có lý do quay lại mỗi ngày ngoài nhiệm vụ ngày |
| [ADR-061](ADR-061-Sect-Storage.md) | Kho đồ tông môn — 40 dòng/nhân vật, gửi/rút ở Tô Vân Nhi (V63, opcode 514–519) | ✅ **Accepted** *(2026-10-18, lô 16)* — thi hành cùng ngày | Túi 20 ô chật từ khi có bộ đồ theo arc + quà hằng ngày |
| [ADR-062](ADR-062-Achievements-And-Titles.md) | Thành tựu + danh hiệu — 9 mốc (V64), danh hiệu trong chat, dải opcode MỚI 1700–1704, module `feature-achievement` | ✅ **Accepted** *(2026-10-18, lô 16)* — thi hành cùng ngày | Ngoài cấp/Lực Chiến không có mục tiêu dài hạn nhìn thấy được |
| [ADR-063](ADR-063-Market.md) | Phường thị — treo bán món chưa cường hoá, giá cố định, phí 5% (sink), hết hạn 48h trả qua thư (V65, opcode 1006–1014, module `feature-market`) | ✅ **Accepted** *(2026-10-18, lô 16)* — thi hành cùng ngày | Bán đồ cho người lạ phải cùng online + giao dịch trực tiếp; GDD §10 đòi phí đấu giá làm gold sink |
| [ADR-064](ADR-064-Spirit-Jade-Sockets.md) | Linh Ngọc khảm nạm — 4 loại ngọc, 1 ô/trang bị, 50% + bảo hiểm sau 3 lần hỏng, phí 200 (V66, opcode 520/521) | ✅ **Accepted** *(2026-10-18, lô 16)* — thi hành cùng ngày | GDD hứa "bonus phụ có pity" + gold sink khảm nạm |
| [ADR-065](ADR-065-Item-Sets.md) | Bộ pháp khí — thưởng bậc 2/4/6 theo số mẫu khác nhau đang mặc, hai bộ Tạp Dịch/Ngoại Môn (V67, `GetCharacterStatsResponse.item_sets`) | ✅ **Accepted** *(2026-10-21, lô 17)* — thi hành cùng ngày | GDD hứa bộ 4/6/8 món; mặc trọn bộ không khác mặc lẫn |
| [ADR-066](ADR-066-Faction-Change.md) | Đổi linh căn — thức tỉnh + một lần dưới Lv30 miễn phí, sau đó tốn Tẩy Căn Đan (V68 `faction_changes`) | ✅ **Accepted** *(2026-10-21, lô 17)* — thi hành cùng ngày | Đổi hệ không giới hạn, không tốn gì; GDD §4 |
| [ADR-067](ADR-067-Double-Events.md) | Sự kiện nhân đôi EXP/rơi đồ — `/gm event` (ADMIN), sống qua khởi động lại (V69, opcode 309) | ✅ **Accepted** *(2026-10-21, lô 17)* — thi hành cùng ngày | GDD §8 sự kiện bật/tắt không deploy |
| [ADR-068](ADR-068-Guild-Level-And-Contribution.md) | Cấp bang 1–5 + cống hiến — góp linh thạch bội số 1.000 (sink), sĩ số 50+10/cấp, thẻ Bang hội ở Bia Anh Danh (V70, opcode 721/722) | ✅ **Accepted** *(2026-10-21, lô 17)* — thi hành cùng ngày | Bang không có gì để cùng cày; GDD §7 cấp bang |
| [ADR-069](ADR-069-Guild-Weekly-Quest.md) | Nhiệm vụ bang tuần — cả bang hạ 300 yêu thú/tuần, thưởng thư 2.000 mỗi thành viên + 5.000 kinh nghiệm bang (V70) | ✅ **Accepted** *(2026-10-21, lô 17)* — thi hành cùng ngày | GDD §7/§8 nhiệm vụ bang hợp tác |
| [ADR-070](ADR-070-Flying-Sword-Mount.md) | Phi kiếm — ô trang bị MOUNT, chỉ +% tốc độ (V71, opcode 310, `EQUIPMENT_SLOT_MOUNT`) | ✅ **Accepted** *(2026-10-21, lô 17)* — thi hành cùng ngày | GDD §5 phi kiếm chỉ tốc độ |
| [ADR-071](ADR-071-Skill-Ranks.md) | Điểm công pháp — cấp/2 điểm, bậc chiêu 1–5 (+10%/bậc), cửa sổ phím R (V72, opcode 409–412) | ✅ **Accepted** *(2026-10-21, lô 17)* — thi hành cùng ngày | GDD §5 điểm công pháp |
| [ADR-072](ADR-072-Spirit-Pets.md) | Linh thú bản đầu — ô trang bị PET, cấp 1–30 theo EXP chủ nhân, chỉ số +10%/cấp (V71, `pet_state`) | ✅ **Accepted** *(2026-10-21, lô 17)* — thi hành cùng ngày | GDD §5 linh thú |
| [ADR-073](ADR-073-Self-Movement-Modifier-Event.md) | Event hiệu ứng di chuyển trên chính mình — hết giật vị trí khi bị choáng/làm chậm (opcode 311) | ✅ **Accepted** *(2026-10-23, lô 18)* | README dòng 38 |
| [ADR-074](ADR-074-Withdraw-Friend-Request.md) | Rút lời mời kết bạn theo tên (opcode 1114/1115) | ✅ **Accepted** *(2026-10-23, lô 18)* | README dòng 71(c) |
| [ADR-075](ADR-075-Trade-Offer-Version.md) | `offer_version` — xác nhận đúng lời chào đã thấy (V74, thêm field) | ✅ **Accepted** *(2026-10-23, lô 18)* | README dòng 25(i) |
| [ADR-076](ADR-076-Mail-Postage.md) | Phí thư 10 + 1% vàng đính kèm (gold sink, không đổi schema/protocol) | ✅ **Accepted** *(2026-10-24, lô 19)* | GDD §7/§10 |
| [ADR-077](ADR-077-Mental-Arts.md) | Tâm pháp bị động — 2/linh căn, bậc 0–5 bằng điểm công pháp (thêm field 7) | ✅ **Accepted** *(2026-10-24, lô 19)* | GDD §5 |
| [ADR-078](ADR-078-Pet-Evolution.md) | Linh thú tiến hoá bậc 2 (×150%) bằng Linh Thú Đan qua đường dùng vật phẩm (V75) | ✅ **Accepted** *(2026-10-24, lô 19)* | ADR-072 |
| [ADR-079](ADR-079-Alchemy-Crafting.md) | Luyện đan — module `feature-crafting`, công thức DB (V76), lò ở trưởng lão, dải opcode 1800 | ✅ **Accepted** *(2026-10-24, lô 19)* | GDD §8/§10 |
| [ADR-080](ADR-080-Guild-Shop.md) | Cửa hàng bang — đổi cống hiến (tích luỹ − đã tiêu) lấy vật phẩm theo cấp bang (V77, 723–726) | ✅ **Accepted** *(2026-10-24, lô 19)* | ADR-068 |
| [ADR-081](ADR-081-World-Boss.md) | Boss thế giới định kỳ — lịch DB, sổ sát thương, thưởng thư theo hạng, thẻ "Diệt Yêu" (V78) | ✅ **Accepted** *(2026-10-24, lô 19)* | GDD §7 |
| [ADR-082](ADR-082-Party-Finder.md) | Bảng tìm nhóm — tin RAM 10 phút, mục tiêu cố định, vào ngay (1319–1326) | ✅ **Accepted** *(2026-10-24, lô 19)* | feature-party |
| [ADR-083](ADR-083-Mail-Enhanced-Items.md) | Thư chở món cường hoá giữ nguyên cấp (V79, thêm field) | ✅ **Accepted** *(2026-10-24, lô 20)* | feature-mail |
| [ADR-084](ADR-084-Friend-Gifts.md) | Tặng quà bạn bè — thân mật theo cặp, mốc thưởng thư (V80, 1116/1117) | ✅ **Accepted** *(2026-10-24, lô 20)* | feature-friend |
| [ADR-085](ADR-085-Guild-Skills.md) | Kỹ năng bang — Ngộ Tính/Cường Thể/Hộ Thể, điểm = cấp bang × 2 (V81, 727–730) | ✅ **Accepted** *(2026-10-24, lô 20)* | ADR-068 |
| [ADR-086](ADR-086-Pet-Skills.md) | Kỹ năng linh thú — Linh Hồ đánh thêm / Huyền Quy chắn, bậc 2 ×2 xác suất (không đổi protocol/schema) | ✅ **Accepted** *(2026-10-24, lô 20)* | ADR-072 |
| [ADR-087](ADR-087-Arena-Seasons.md) | Mùa Luận Kiếm 28 ngày — chốt nguyên tử, hàng đợi thưởng thư, Elo kéo nửa đường về 1000 (V82) | ✅ **Accepted** *(2026-10-24, lô 20)* | feature-pvp |
| [ADR-088](ADR-088-Bag-Expansion.md) | Mở rộng túi — sức chứa theo nhân vật 20→40, Túi Càn Khôn +5 (V83) | ✅ **Accepted** *(2026-10-24, lô 20)* | ADR-046 D5 |
| [ADR-089](ADR-089-Rebirth.md) | Độ Kiếp Chuyển Thế — cấp 100 về cấp 1, Đạo Vận vĩnh viễn, tối đa 5 lần (212/213) | ✅ **Accepted** *(2026-10-24, lô 20)* | GDD §5 |
| [ADR-090](ADR-090-Spirit-Vein-Contest.md) | Tranh đoạt Linh Mạch bản đầu — bang đông nhất trong vùng tích điểm, thắng giữ 7 ngày + thư thưởng (V84, 731/732) | ✅ **Accepted** *(2026-10-24, lô 20)* | — |
| [ADR-091](ADR-091-Block-List.md) | Chặn người chơi — hai chiều, chặn = cắt mọi quan hệ, trần 50, 403 `error.blocked` (V85, 1118–1123) | ✅ **Accepted** *(2026-10-24, lô 21)* | feature-friend |
| [ADR-092](ADR-092-Emotes.md) | Biểu cảm — 6 lệnh chữ phát theo AoI qua `ObserversCommand`, 1/2 giây (803–805) | ✅ **Accepted** *(2026-10-24, lô 21)* | — |
| [ADR-093](ADR-093-Mentorship.md) | Sư đồ — <Lv30 bái ≥Lv40, tối đa 3, mốc 10/20/30 thư cả hai một lần trọn đời (V85, 1124–1130) | ✅ **Accepted** *(2026-10-24, lô 21)* | ADR-091 |
| [ADR-094](ADR-094-Dao-Lu.md) | Đạo lữ — thân mật ≥600, người cầu trả 5.000, +10% EXP cùng nhóm (V85, 1131–1137) | ✅ **Accepted** *(2026-10-24, lô 21)* | ADR-084 |
| [ADR-095](ADR-095-Seclusion-Offline-Exp.md) | Bế quan — quỹ offline ≤8 giờ, 8 giờ ≈ 1 cấp, x2 trả cấp × 50 vàng (V86, 214–217) | ✅ **Accepted** *(2026-10-24, lô 21)* | ADR-055 |
| [ADR-096](ADR-096-Online-Time-Rewards.md) | Thưởng online — mốc 10/30/60 phút mỗi ngày, quà qua thư (V86, 218–221) | ✅ **Accepted** *(2026-10-24, lô 21)* | ADR-060 |
| [ADR-097](ADR-097-Dismantle-Equipment.md) | Phân giải pháp khí — vàng + Linh Thảo theo cấp yêu cầu và cường hoá (522/523) | ✅ **Accepted** *(2026-10-24, lô 21)* | ADR-079 |
| [ADR-098](ADR-098-Herb-Gathering.md) | Hái linh thảo — bụi là dữ liệu + trạng thái RAM, hái 3 giây server đếm (V86, 312–316) | ✅ **Accepted** *(2026-10-24, lô 21)* | ADR-079 |
| [ADR-099](ADR-099-Dungeon-Daily-Runs-And-First-Clear.md) | Bí Cảnh — 3 lượt/ngày, thư thưởng lần đầu hạ boss (V87) | ✅ **Accepted** *(2026-10-24, lô 21)* | ADR-047 |
| [ADR-100](ADR-100-Teleport-Talisman.md) | Truyền tống phù — điểm mở khi đứng gần, phù tiêu hao, dời qua đường ADR-051 (V87, 317–320) | ✅ **Accepted** *(2026-10-24, lô 21)* | ADR-052 |
| [ADR-101](ADR-101-Bounty-Board.md) | Bảng truy nã — 3 truy nã/ngày tất định, 5 lần xong/ngày (V87, 614–618) | ✅ **Accepted** *(2026-10-24, lô 21)* | ADR-055 |
| [ADR-102](ADR-102-Guild-War.md) | Bang chiến — PvP theo cặp ngoài vùng an toàn, 60 phút, đếm lần hạ (V88, 733–736) | ✅ **Accepted** *(2026-10-24, lô 21)* | ADR-090 |
| [ADR-103](ADR-103-Server-Notice.md) | Thông báo server khi đăng nhập (MOTD) | ✅ **Accepted** *(2026-10-25, lô 22)* | — |
| [ADR-104](ADR-104-Gift-Codes.md) | Mã quà tặng | ✅ **Accepted** *(2026-10-25, lô 22)* | — |
| [ADR-105](ADR-105-Player-Reports.md) | Báo cáo người chơi | ✅ **Accepted** *(2026-10-25, lô 22)* | — |
| [ADR-106](ADR-106-Activity-Calendar.md) | Lịch hoạt động + điểm năng động | ✅ **Accepted** *(2026-10-25, lô 22)* | — |
| [ADR-107](ADR-107-Fishing-And-Mining.md) | Câu cá và khai khoáng | ✅ **Accepted** *(2026-10-25, lô 22)* | — |
| [ADR-108](ADR-108-Bestiary.md) | Sổ sưu tầm yêu thú | ✅ **Accepted** *(2026-10-25, lô 22)* | — |
| [ADR-109](ADR-109-Sect-Reputation.md) | Danh vọng tông môn | ✅ **Accepted** *(2026-10-25, lô 22)* | — |
| [ADR-110](ADR-110-Sworn-Brotherhood.md) | Kết nghĩa huynh đệ | ✅ **Accepted** *(2026-10-25, lô 22)* | — |
| [ADR-111](ADR-111-Equipment-Refinement.md) | Tẩy luyện pháp khí | ✅ **Accepted** *(2026-10-25, lô 22)* | — |
| [ADR-112](ADR-112-Guild-Auction.md) | Đấu giá bang | ✅ **Accepted** *(2026-10-25, lô 22)* | — |
| [ADR-113](ADR-113-Demon-Tower.md) | Trấn Yêu Tháp | ✅ **Accepted** *(2026-10-25, lô 22)* | — |
| [ADR-114](ADR-114-Escort-Quests.md) | Nhiệm vụ hộ tống | ✅ **Accepted** *(2026-10-25, lô 22)* | — |
| [ADR-115](ADR-115-Security-Pin.md) | Khoá an toàn (PIN cấp 2) | ✅ **Accepted** *(2026-10-30, lô 23)* | — |
| [ADR-116](ADR-116-Market-Buy-Orders.md) | Cầu mua ở phường thị | ✅ **Accepted** *(2026-10-30, lô 23)* | — |
| [ADR-117](ADR-117-Revive-In-Place.md) | Hồi sinh tại chỗ (Hồi Hồn Đan) | ✅ **Accepted** *(2026-10-30, lô 23)* | — |
| [ADR-118](ADR-118-Traveling-Merchant.md) | Thương nhân du hành | ✅ **Accepted** *(2026-10-30, lô 23)* | — |
| [ADR-119](ADR-119-Spirit-Field.md) | Linh điền | ✅ **Accepted** *(2026-10-30, lô 23)* | — |
| [ADR-120](ADR-120-Forging.md) | Luyện khí | ✅ **Accepted** *(2026-10-30, lô 23)* | — |
| [ADR-121](ADR-121-Chance-Encounters.md) | Kỳ ngộ | ✅ **Accepted** *(2026-10-30, lô 23)* | — |
| [ADR-122](ADR-122-Sect-Quiz.md) | Hỏi đáp tông môn | ✅ **Accepted** *(2026-10-30, lô 23)* | — |
| [ADR-123](ADR-123-Day-Night.md) | Ngày đêm | ✅ **Accepted** *(2026-10-30, lô 23)* | — |
| [ADR-124](ADR-124-Monster-Taming.md) | Thu phục yêu thú | ✅ **Accepted** *(2026-10-30, lô 23)* | — |
| [ADR-125](ADR-125-Grand-Tournament.md) | Luận võ đại hội | ✅ **Accepted** *(2026-10-30, lô 23)* | — |
| [ADR-126](ADR-126-Heavenly-Tribulation.md) | Thiên Kiếp | ✅ **Accepted** *(2026-10-30, lô 23)* | — |
| [ADR-127](ADR-127-Artifacts.md) | Pháp bảo | ✅ **Accepted** *(2026-10-31, lô 24)* | — |
| [ADR-128](ADR-128-Party-Formations.md) | Trận pháp nhóm | ✅ **Accepted** *(2026-10-31, lô 24)* | — |
| [ADR-129](ADR-129-Cave-Dwelling.md) | Động phủ | ✅ **Accepted** *(2026-10-31, lô 24)* | — |
| [ADR-130](ADR-130-Arc-Three-Inner-Sect.md) | Nội dung arc 3 — Nội Môn | ✅ **Accepted** *(2026-10-31, lô 24)* | — |
| [ADR-131](ADR-131-Blacksmith-And-Merchant-NPCs.md) | NPC thợ rèn + thương nhân du hành | ✅ **Accepted** *(2026-10-31, lô 24)* | — |
| [ADR-132](ADR-132-Costume-Tier-By-Realm.md) | Bậc trang phục theo cảnh giới | ✅ **Accepted** *(2026-11-01, lô 25)* | — |
| [ADR-133](ADR-133-Artifact-Hud-Button.md) | Nút pháp bảo trên HUD | ✅ **Accepted** *(2026-11-01, lô 25)* | — |
| [ADR-134](ADR-134-Cave-And-Tribulation-Leaderboards.md) | Bảng xếp hạng Động phủ + Thiên Kiếp | ✅ **Accepted** *(2026-11-01, lô 25)* | — |
| [ADR-135](ADR-135-Inner-Sect-Daily-Quests.md) | Nhiệm vụ ngày nội môn | ✅ **Accepted** *(2026-11-01, lô 25)* | — |
| [ADR-136](ADR-136-Realm-Breakthrough-Ceremony.md) | Nghi lễ đột phá cảnh giới | ✅ **Accepted** *(2026-11-01, lô 25)* | — |
| [ADR-137](ADR-137-Wasteland-Pvp-Zone.md) | Vùng PvP Hoang Vực | ✅ **Accepted** *(2026-11-01, lô 25)* | — |
| [ADR-138](ADR-138-Cave-Decorations-In-3D.md) | Bày trí động phủ hiện hình 3D | ✅ **Accepted** *(2026-11-01, lô 25)* | — |
| [ADR-139](ADR-139-Arc-Four-Descend-The-Mountain.md) | Nội dung arc 4 — Hạ Sơn (Kim Đan, Lv55–72) | ✅ **Accepted** *(2026-11-01, lô 25)* | — |
| [ADR-140](ADR-140-Overhead-Realm-Title.md) | Danh hiệu cảnh giới trên đầu | ✅ **Accepted** *(2026-11-01, lô 25)* | — |
| [ADR-141](ADR-141-Zone-Map-HUD-And-Wasteland-Waypoints.md) | Biển vùng PvP trên HUD (338/339, click nhắm trong dải) + 3 trạm truyền tống 240/304/368 (V114) | ✅ **Accepted** *(2026-11-01, lô 26)* | — |
| [ADR-142](ADR-142-Killing-Intent-And-Outlaws.md) | Sát khí + Ma Đầu: trạm giữa dải PvP không che, cấm truyền tống, chết rơi 10% vàng (V115, cờ `outlaw` 303/307) | ✅ **Accepted** *(2026-11-01, lô 26)* | — |
| [ADR-143](ADR-143-Display-Settings.md) | Cài đặt hiển thị `/caidat`: ẩn danh hiệu, chữ damage, hậu cảnh parallax (thuần client) | ✅ **Accepted** *(2026-11-01, lô 26)* | — |
| [ADR-144](ADR-144-Regional-Weather.md) | Thời tiết theo vùng (chỉ hiển thị): mưa linh khí tông môn, bão cát Hoang Vực sâu (V116, opcode 340) | ✅ **Accepted** *(2026-11-01, lô 26)* | — |
| [ADR-145](ADR-145-Colossus-Ground-Slam.md) | Cổ Thạch Khổng Lồ thành boss: dậm đất 4 m có báo trước 1,5 giây (V117, opcode 413, hành động script `ground_slam`) | ✅ **Accepted** *(2026-11-01, lô 26)* | — |
| [ADR-146](ADR-146-Heart-Demon.md) | Tâm ma khi độ kiếp: từ kiếp thứ hai, sau đợt sét thứ hai, tâm ma hiện — lôi kiếp ngừng, hạ trong 60 giây hoặc thua (V118, 2 loại sự kiện 332 + `seconds_left`) | ✅ **Accepted** *(2026-11-01, lô 26)* | — |
| [ADR-147](ADR-147-Treasure-Maps.md) | Tàng Bảo Đồ: bản đồ rơi từ quái, đọc ⇒ server chọn 1/6 điểm chôn ngoài dải PvP, đào ≤ 2,5 m trong một giao dịch, phần thưởng theo trọng số có thể gọi quái canh kho (V119, opcode 341–344, `/tangbaodo`) | ✅ **Accepted** *(2026-11-02, lô 27)* | — |
| [ADR-148](ADR-148-Rename-Talisman.md) | Dịch Danh Phù: đổi tên bằng phù bán ở cửa hàng, luật tên như lúc tạo, một giao dịch, hồi chiêu 7 ngày, lịch sử tên + audit (V120, opcode 228/229, `/doiten`) | ✅ **Accepted** *(2026-11-02, lô 27)* | — |
| [ADR-149](ADR-149-Daily-Fortune.md) | Quẻ bói hằng ngày: gieo ở trưởng lão Chu Hoành, năm quẻ Đại Cát→Hung, ±% EXP đánh quái tới hết ngày + quà (V121, opcode 230/231, `/xinque`) | ✅ **Accepted** *(2026-11-02, lô 27)* | — |
| [ADR-150](ADR-150-Group-Loot-Rolls.md) | Xúc xắc chia đồ nhóm: trang bị rơi khi có đồng đội cùng World ⇒ Cần/Tham/Bỏ 15 giây, server tung 1–100, cả nhóm Bỏ về người hạ, túi đầy gửi thư (opcode 1328–1331) | ✅ **Accepted** *(2026-11-02, lô 27)* | — |
| [ADR-151](ADR-151-Lucky-Wheel.md) | Vòng quay may mắn: 1 lượt miễn phí + 10 lượt mua 1.000 linh thạch mỗi ngày giờ VN, 8 ô theo trọng số, một giao dịch, từ chối thì không mất lượt/tiền (V122, opcode 232–235, `/vongquay`) | ✅ **Accepted** *(2026-11-02, lô 27)* | — |
| [ADR-152](ADR-152-Broadcast-Talisman.md) | Truyền Âm Phù: `/loa` tốn một phù (2.000), hồi chiêu 30 s, kiểm như chat (khoá chat, từ cấm, audit), từ chối thì trả phù, phát kênh BROADCAST cho mọi người online (V123, opcode 810/811) | ✅ **Accepted** *(2026-11-02, lô 28)* | — |
| [ADR-153](ADR-153-Realm-Milestone-Rewards.md) | Quà mốc cảnh giới: 6 mốc (cấp 10 + 19/37/55/73/91), nhận tay một lần trong `/mocgioi`, túi đầy quà vẫn chờ (V124, opcode 236–239) | ✅ **Accepted** *(2026-11-02, lô 28)* | — |
| [ADR-154](ADR-154-Weekly-Tasks.md) | Nhiệm vụ tuần cá nhân: 5 việc (hạ quái, thu thập, Bí Cảnh, truy nã, nhiệm vụ ngày) đếm qua đường lịch hoạt động, tuần thứ Hai giờ VN, nhận tay một lần (V125, opcode 240–243, `/nhiemvutuan`) | ✅ **Accepted** *(2026-11-02, lô 28)* | — |
| [ADR-155](ADR-155-Honor-Shop.md) | Cửa hàng điểm vinh dự: trận đấu trường thắng +10 / thua +3 (trần 100/ngày), 5 món đổi bằng điểm có trần lượt tuần, một giao dịch (V126, opcode 1615–1618, `/vinhdu`) | ✅ **Accepted** *(2026-11-02, lô 28)* | — |
| [ADR-156](ADR-156-Auto-Potion.md) | Tự uống đan: công tắc `/caidat` mặc định tắt; máu < 35% thì client gửi `UseItemCommand` có sẵn cho món HEAL_HP, server vẫn kiểm hồi chiêu (không opcode, không schema) | ✅ **Accepted** *(2026-11-02, lô 28)* | — |
| [ADR-157](ADR-157-Live-World-Boss-Board.md) | Bảng sát thương boss thế giới trực tiếp: ảnh chụp sổ qua inbox World mỗi 2 s, top 5 + hạng mình cho người đang đánh, ended khi boss chết; chỉ hiển thị (opcode 414) | ✅ **Accepted** *(2026-11-02, lô 28)* | — |
| [ADR-158](ADR-158-Fortune-Drop-Bonus.md) | Rơi đồ theo quẻ bói: % cơ hội tung bảng rơi thêm một lượt theo quẻ (Đại Cát 30 … Hung 0), riêng từng người, hook mới `withBonusRollPercent`, một FortuneFacade dùng chung (V127, field 9 của 231) | ✅ **Accepted** *(2026-11-02, lô 28)* | — |
| [ADR-159](ADR-159-Item-Lock.md) | Khoá vật phẩm: cột `inventory_item.locked` (V128); món khoá bị chặn như món đang mặc ở mọi đường rời túi (bán/giao dịch/thư/phường thị/kho/phân giải/nộp nhiệm vụ/lò đan), dùng trực tiếp vẫn được; 528/529 qua cổng PIN; nút Khoá trong túi đồ | ✅ Accepted (lô 29) |
| [ADR-160](ADR-160-Bag-Sort.md) | Sắp xếp túi đồ: luật thuần `BagSortPolicy` (trang bị theo ô → dùng được → khác → đang mặc), gom chồng gộp được cùng loại + cùng trạng thái khoá; danh sách túi nay đọc theo `slot_index`, dòng mới nối cuối; 530/531; nút Sắp xếp | ✅ Accepted (lô 29) |
| [ADR-161](ADR-161-Gem-Combine.md) | Ghép Linh Ngọc: thêm bậc 2/3 cho 4 loại ngọc (V129), 3 viên + vàng ⇒ 1 viên bậc trên 100%, ngọc khoá không bị ghép; 532–535; cửa sổ `/ghepngoc` | ✅ Accepted (lô 29) |
| [ADR-162](ADR-162-Dungeon-Sweep.md) | Quét nhanh Bí Cảnh: cần đã hạ boss, tốn một lượt ngày chung (ADR-099) + 1.000 vàng, +400 EXP cộng thẳng, đồ bảng rơi boss đi bằng thư (V130); 1510/1511; `/quetbicanh` | ✅ Accepted (lô 29) |
| [ADR-163](ADR-163-Lucky-Bag.md) | Túi phúc: bảng quà theo trọng số trong DB (V131, 10 quà tổng 100), server rút, lệnh mở riêng một transaction, % công khai; rơi từ boss Bí Cảnh/boss thế giới; 536–539; `/tuiphuc` | ✅ Accepted (lô 29) |
| [ADR-164](ADR-164-Double-Exp-Pill.md) | Đan x2 kinh nghiệm: nút Dùng chung, hạn ghi trong giao dịch dùng (V132), dồn 30 phút/viên tới trần 300, ×2 EXP đánh quái ở cuối chuỗi hệ số; event 244 + dòng HUD | ✅ Accepted (lô 29) |
| [ADR-165](ADR-165-GM-Mass-Compensation.md) | GM thư bồi thường hàng loạt: `/gm compensate <MÃ> <vàng> <đồ|-> <số> <cấp>` (ADMIN), thư hệ thống tới mọi nhân vật cấp ≥ ngưỡng, khoá chống trùng tất định + sổ đợt V133 | ✅ Accepted (lô 29) |
| [ADR-166](ADR-166-Costume-Appearance-Choice.md) | Huyễn hình trang phục (chủ dự án chọn thay cho huyễn hình trang bị — trang bị không vẽ lên nhân vật): chọn hiển thị bậc trang phục đã mở (V134, 2.000 vàng, về tự động miễn phí), áp ở registry mỗi lần ghi ngoại hình, 307 tới người xung quanh; 245/246; `/huyenhinh` | ✅ Accepted (lô 29) |
| [ADR-167](ADR-167-Claim-All-Mail.md) | Nhận hết thư: vòng lặp gateway qua đường lĩnh thư sẵn có, dừng khi túi đầy, dọn thư đã xong; 911/912; nút Nhận hết | ✅ Accepted (lô 30) |
| [ADR-168](ADR-168-Unsocket-Gem.md) | Tháo Linh Ngọc: gỡ ngọc về túi 500 vàng, một transaction, áp lại chỉ số món đang mặc; 540/541; nút Tháo ngọc trong cửa sổ khảm | ✅ Accepted (lô 30) |
| [ADR-169](ADR-169-Gold-Ledger-History.md) | Sổ vàng cá nhân: bảng `gold_ledger_entry` (V135) ghi trong `GoldLedger` cùng transaction với mọi lần đổi vàng, giữ 30 ngày; 247/248; `/sovang` + 37 nhãn lý do | ✅ Accepted (lô 30) |
| [ADR-170](ADR-170-Sell-Junk.md) | Bán nhanh đồ rác: cờ `item_template.junk` (V136: kunai, sắt vụn, thẻ bài, bùa Huyết Sa), xem trước rồi mới bán, bỏ qua món khoá/đang mặc/giao dịch; 542–545; nút Bán rác / `/banrac` | ✅ Accepted (lô 30) |
| [ADR-171](ADR-171-Inspect-Player.md) | Soi trang bị người khác: `/soi <tên>` (cả offline) — cảnh giới, Lực Chiến (công thức ADR-056), món đang mặc; riêng tư lưu server (V137), công tắc trong /caidat; 249–252 | ✅ Accepted (lô 30) |
| [ADR-172](ADR-172-Guild-Rename.md) | Đổi tên bang hội: bang chủ, luật tên như tạo bang, 20.000 vàng của bang chủ (không có quỹ vàng bang), 30 ngày (V138), báo 720 RENAMED; 741/742; `/doitenbang` | ✅ Accepted (lô 30) |
| [ADR-173](ADR-173-Friend-Notes.md) | Ghi chú bạn bè: ghi chú riêng ≤ 24 ký tự cho người đang là bạn (V139 `friend_note`), `FriendView.note`, 1145/1146; `/ghichu` | ✅ Accepted (lô 30) |
| [ADR-174](ADR-174-Rest-Reminder.md) | Nhắc nghỉ ngơi: thuần client, dòng hệ thống sau 2 giờ chơi liên tục rồi mỗi giờ; công tắc `/caidat` mặc định bật | ✅ Accepted (lô 30) |
| [ADR-175](ADR-175-Equipment-Presets.md) | Lưu bộ trang bị: 3 bộ, chụp món cụ thể, mặc lại qua đúng đường mặc đồ thường (V140), 546–551; `/bodo` | ✅ Accepted (lô 31) |
| [ADR-176](ADR-176-Bulk-Item-Use.md) | Dùng nhiều vật phẩm: lặp đúng đường một món, dừng ở lần từ chối đầu, kẹp 1..50; chỉ đan x2/mở rộng túi/túi phúc (không đan hồi máu); 552–555; `/dungnhieu`, "Mở 10" | ✅ Accepted (lô 31) |
| [ADR-177](ADR-177-Market-History.md) | Lịch sử phường thị: đọc `market_listing` SOLD (bán + mua) 30 ngày / 50 dòng, chỉ mục V141, 1029/1030; `/lichsupt` | ✅ Accepted (lô 31) |
| [ADR-178](ADR-178-Away-Auto-Reply.md) | Tự trả lời khi vắng: chỉ bộ nhớ (dùng chung mọi kết nối), ≤ 60 ký tự qua bộ lọc chat, 1 lần/5 phút mỗi người gửi; 812/813 + `ChatMessageEvent.auto_reply`; `/vang` | ✅ Accepted (lô 31) |
| [ADR-179](ADR-179-Guild-Notice.md) | Thông báo bang: bang chủ ghi ≤ 100 ký tự qua bộ lọc chat (V142), `GetMyGuildResponse.notice`, 743/744, 720 NOTICE_CHANGED; `/thongbaobang` | ✅ Accepted (lô 31) |
| [ADR-180](ADR-180-Guild-Log.md) | Nhật ký bang: bảng `guild_log` (V143) ghi cùng transaction 8 loại việc, 50 dòng / giữ 90 ngày, 745/746; `/nhatkybang` | ✅ Accepted (lô 31) |
| [ADR-181](ADR-181-Guild-Leaderboard-Columns.md) | Bảng xếp hạng bang: bảng đã có từ ADR-068 ⇒ (chủ dự án chọn) thêm số thành viên + tổng cống hiến thành viên hiện tại, `LeaderboardEntry` 7/8 | ✅ Accepted (lô 31) |
| [ADR-182](ADR-182-Friend-Breakthrough-Notice.md) | Báo bạn bè đột phá: mốc ADR-136 báo riêng bạn bè đang online, chúc mừng 1 lần / 30 phút, chỉ bộ nhớ; 1147–1150; `/chucmung` | ✅ Accepted (lô 31) |
| [ADR-183](ADR-183-Guild-Leader-Transfer.md) | Nhường chức bang chủ: cho thành viên cùng bang, một transaction đọc-lại-sau-khoá, nhật ký `LEADER_CHANGED` (V144), 747/748, 720 LEADER_CHANGED; `/nhuongbang` | ✅ Accepted (lô 32) |
| [ADR-184](ADR-184-Guild-Vice-Leader.md) | Phó bang chủ: vai `VICE_LEADER` ≤ 2 — duyệt đơn + đăng thông báo, rời / bị đuổi được (sửa SQL rời/đuổi/duyệt/từ chối), V145, 749/750; `/phobang` | ✅ Accepted (lô 32) |
| [ADR-185](ADR-185-Guild-Recruitment-Board.md) | Bảng chiêu mộ bang: một dòng / bang (V146), bang chủ hoặc phó, ≤ 80 ký tự qua bộ lọc chat, cấp tối thiểu kiểm ở đơn xin vào (`LEVEL_TOO_LOW`), 751–754; `/chieumo` | ✅ Accepted (lô 32) |
| [ADR-186](ADR-186-Guild-Check-In.md) | Điểm danh bang: một lần / nhân vật / ngày (V147), +100 cống hiến, +100 kinh nghiệm bang qua đúng hàm cộng của góp bang, 755/756; `/diemdanhbang` | ✅ Accepted (lô 32) |
| [ADR-187](ADR-187-Offline-Whispers.md) | Nhắn riêng khi offline: qua đủ kiểm duyệt + audit rồi giữ (V148), 20 tin / 7 ngày, giao lúc vào game kèm giờ gửi; outcome `DELIVERED_LATER`/`RECIPIENT_INBOX_FULL`, `ChatMessageEvent.sent_at_epoch_seconds` | ✅ Accepted (lô 32) |
| [ADR-188](ADR-188-Chat-Item-Link.md) | Khoe vật phẩm vào chat: server đọc món thật + chỉ số hiệu dụng, gắn `LinkedItemView` vào tin (không ở kênh Khu vực); 2 outcome + 2 field; `/khoe`, `/xemmon` | ✅ Accepted (lô 32) |
| [ADR-189](ADR-189-Trade-History.md) | Lịch sử giao dịch: đọc audit `TRADE_EXECUTED` sẵn có (chỉ mục V149), 30 lần / 30 ngày, 1415/1416; `/lichsugd` | ✅ Accepted (lô 32) |
| [ADR-190](ADR-190-Key-Rebinding.md) | Đổi phím tắt phía client: 27 phím mở bảng/hành động, lưu Preferences, chặn trùng + phím dành riêng; `/phimtat` (không đổi protocol/DB) | ✅ Accepted (lô 32) |
| [ADR-191](ADR-191-Party-Leader-Transfer.md) | Nhường trưởng nhóm khi vẫn ở lại nhóm: dùng lại `PartyPort.transferLeadership`, 1332/1333, báo 1318 LEADER_CHANGED; `/nhuongnhom` | ✅ Accepted (lô 33) |
| [ADR-192](ADR-192-Last-Online.md) | Lần cuối online: cột `character.last_online_at` (V150) ghi khi rời world, field 7 ở `FriendView`/`GuildMemberView`; "3 ngày trước" | ✅ Accepted (lô 33) |
| [ADR-193](ADR-193-Pet-Rename.md) | Đặt tên linh thú đang mặc: `pet_state.custom_name` (V151), 253/254, `PetView.custom_name`; `/tenlinhthu` (tên hiện trên bảng nhân vật — linh thú chưa có hình trong world) | ✅ Accepted (lô 33) |
| [ADR-194](ADR-194-Enemy-List.md) | Danh sách cừu nhân: ai hạ mình trong dải PvP, `pvp_enemy` (V152) trần 20, 1619–1622; `/cuunhan` | ✅ Accepted (lô 33) |
| [ADR-195](ADR-195-Death-Recap.md) | Tóm tắt khi chết: gateway giữ đòn trúng trong RAM (64/nhân vật), lúc bị hạ gửi 415 tổng 10 giây cuối theo kẻ ra đòn; client in vào chat | ✅ Accepted (lô 33) |
| [ADR-196](ADR-196-Market-Watch.md) | Theo dõi phường thị: mẫu + giá mỗi cái tối đa (làm tròn lên), `market_watch` (V153) trần 10, báo người đang online lúc có tin khớp, 1031–1035; `/theodoi` | ✅ Accepted (lô 33) |
| [ADR-197](ADR-197-Account-Storage.md) | Kho chung tài khoản: luật kho tông môn, adapter theo `account_id` (V154), `StorageKind` trên message kho có sẵn; dòng đổi kho trong cửa sổ kho | ✅ Accepted (lô 33) |
| [ADR-198](ADR-198-Guild-Bank.md) | Kho bang: luật kho tông môn theo bang (V155), thành viên gửi / bang chủ + phó rút, nhật ký bang `BANK_*` cùng transaction; `/khobang` | ✅ Accepted (lô 33) |
| [ADR-199](ADR-199-Equipment-Comparison.md) | So sánh trang bị: hệ số cường hoá gửi kèm catalog (field 4), client hiện chênh lệch chỉ số hiệu dụng với món đang mặc | ✅ Accepted (lô 34) |
| [ADR-200](ADR-200-Reset-Timers.md) | Đếm ngược mốc làm mới: 00:00 giờ VN / thứ Hai, 255/256 mang mốc + giờ server; `/lammoi` | ✅ Accepted (lô 34) |
| [ADR-201](ADR-201-Spirit-Beast-Food.md) | Cho linh thú ăn: hiệu ứng dùng `FEED_PET`, EXP cùng transaction với trừ món, V156 Linh Thú Thực (200 EXP / 600 linh thạch) | ✅ Accepted (lô 34) |
| [ADR-202](ADR-202-Shop-Buyback.md) | Mua lại đồ đã bán: ghi cùng transaction bán (giữ cấp cường hoá), 10 món / 1 giờ, đúng giá đã nhận, V157, 1036–1039; `/mualai` | ✅ Accepted (lô 34) |
| [ADR-203](ADR-203-Flying-Sword-Upgrade.md) | Nâng cấp phi kiếm: cấp = `enhance_level` của phi kiếm, +5%/cấp tới 5, Phi Kiếm Tinh Thạch (V158, `UPGRADE_MOUNT`) | ✅ Accepted (lô 34) |
| [ADR-204](ADR-204-Loot-Filter.md) | Lọc nhặt đồ: công tắc `auto_sell_junk` (V159), đồ rác có giá thu rơi ra tự bán qua `GoldLedger`, 556/557; `/tubanrac` | ✅ Accepted (lô 34) |
| [ADR-205](ADR-205-Character-Deletion.md) | Xoá nhân vật: hẹn xoá 3 ngày (`deletion_requested_at`, V160), chọn lại nhân vật = huỷ xoá; 257/258; cấm khi đang chơi/đang là bang chủ; tên vẫn giữ | ✅ Accepted (lô 34) |
| [ADR-206](ADR-206-Auto-Walk-To-Npc.md) | Tự tìm đường: 626/627 liệt kê chỗ đứng NPC world chính (tĩnh + động); client lái hướng −1/0/1 tới chỗ gần nhất, dừng khi tới / bấm phím / vướng; `/den`, `/walkto` | ✅ Accepted (lô 34) |
| [ADR-207](ADR-207-Guild-Item-Donation.md) | Góp vật phẩm cho bang: món biến mất, giá thu × số lượng ⇒ kinh nghiệm bang + cống hiến; nhật ký `ITEM_DONATED` (V161), 757/758; `/gopdo` | ✅ Accepted (lô 35) |
| [ADR-208](ADR-208-Guild-War-History.md) | Lịch sử bang chiến: 20 trận đã chốt của bang mình (thắng/thua/hoà, tỷ số), đọc `guild_war` sẵn có, 759/760; `/lichsuchien` | ✅ Accepted (lô 35) |
| [ADR-209](ADR-209-Achievement-Leaderboard.md) | Xếp hạng thành tựu: `LEADERBOARD_KIND_ACHIEVEMENT = 8` trong Bia Anh Danh, giá trị = số thành tựu đã mở; tab mới | ✅ Accepted (lô 35) |
| [ADR-210](ADR-210-Title-Stat-Bonus.md) | Danh hiệu cộng chỉ số: cột chỉ số trên `achievement` (V162), chỉ danh hiệu đang đeo, cộng phẳng chung đường chỉ số trang bị; `AchievementView` field 7–10 | ✅ Accepted (lô 35) |
| [ADR-211](ADR-211-Friend-Suggestions.md) | Gợi ý kết bạn: tối đa 10 người online, chênh ≤ 10 cấp, chưa bạn / chưa mời / không chặn, 1151/1152; `/goiyban`, `/ketban <tên>` | ✅ Accepted (lô 35) |
| [ADR-212](ADR-212-Stranger-Mail-Block.md) | Chặn thư người lạ: `block_stranger_mail` (V163), thư người chơi không phải bạn ⇒ `SEND_MAIL_OUTCOME_STRANGER_MAIL_BLOCKED`, 913/914; `/chanthu` | ✅ Accepted (lô 35) |
| [ADR-213](ADR-213-Party-Target-Marker.md) | Đánh dấu mục tiêu nhóm: trưởng nhóm gửi id entity, server phát event 1336 cho nhóm (không lưu), 1334/1335; `/danhdau`, `/theodau` | ✅ Accepted (lô 35) |
| [ADR-214](ADR-214-Idle-Disconnect.md) | Tự ngắt khi treo máy: im (trừ PING) quá `KAGE_IDLE_KICK_MINUTES` (30, 0 = tắt) trong world ⇒ báo một dòng chat rồi đóng kết nối | ✅ Accepted (lô 35) |
| [ADR-215](ADR-215-Split-Stack.md) | Tách chồng vật phẩm sang ô mới cuối túi (giữ cường hoá + cờ khoá), 558/559; `/tach` | ✅ Accepted (lô 36) |
| [ADR-216](ADR-216-Delete-Finished-Mail.md) | Xoá hàng loạt thư đã nhận / không đính kèm, trừ thư ghim, 915/916; `/xoathu` | ✅ Accepted (lô 36) |
| [ADR-217](ADR-217-Pin-Mail.md) | Ghim thư: `mail.pinned` (V164), lên đầu hộp thư, không bị xoá hàng loạt, `MailView.pinned`, 917/918; `/ghimthu` | ✅ Accepted (lô 36) |
| [ADR-218](ADR-218-Character-Bio.md) | Hồ sơ cá nhân: `character.bio` 60 ký tự (V164), lọc từ cấm, hiện khi soi trang bị, 259/260; `/gioithieu` | ✅ Accepted (lô 36) |
| [ADR-219](ADR-219-Guild-Direct-Invite.md) | Mời thẳng vào bang: bang chủ / phó mời người online, lời mời RAM 5 phút, đồng ý đi đường xin vào + duyệt sẵn có, 761–765; `/moibang`, `/vaobang` | ✅ Accepted (lô 36) |
| [ADR-220](ADR-220-Party-Mark-Overhead.md) | Ký hiệu "[ MỤC TIÊU NHÓM ]" trên đầu quái được đánh dấu (client, `RealmTitles.drawMarker`), vẽ cả khi tắt danh hiệu | ✅ Accepted (lô 36) |
| [ADR-221](ADR-221-Quest-Go-To-Button.md) | Nút "Đi tới" trong nhật ký nhiệm vụ: NPC cần gặp đọc từ script quest, `QuestLogEntry.target_npc_code = 3`, dùng tự tìm đường | ✅ Accepted (lô 36) |
| [ADR-222](ADR-222-Trade-Chat-Channel.md) | Kênh chat giao dịch `CHAT_CHANNEL_TRADE = 8` toàn server, ẩn / hiện ở client; `/gd`, `/kenhgd` | ✅ Accepted (lô 36) |
| [ADR-223](ADR-223-Personal-Stats.md) | Thống kê cá nhân: số cũ từ bộ đếm thành tựu / `pvp_rating` + `character_lifetime_stats` deaths/playtime (V165), 261/262; `/thongke` | ✅ Accepted (lô 37) |
| [ADR-224](ADR-224-Event-Calendar.md) | Lịch sự kiện: boss thế giới kế tiếp, làm mới ngày / tuần, luận võ đại hội, 263/264; `/lich` | ✅ Accepted (lô 37) |
| [ADR-225](ADR-225-World-Boss-Warning.md) | Nhắc trước boss thế giới 5 phút, một lần mỗi lần xuất hiện, qua chat hệ thống | ✅ Accepted (lô 37) |
| [ADR-226](ADR-226-Combat-Log.md) | Nhật ký chiến đấu client: nghe gói kết quả đòn, 20 dòng của chính mình; `/nhatkychien` | ✅ Accepted (lô 37) |
| [ADR-227](ADR-227-Starter-Gift.md) | Gói quà tân thủ: thư 5 đan + 200 linh thạch khi tạo nhân vật, cờ một lần `starter_gift_sent` (V166) | ✅ Accepted (lô 37) |
| [ADR-228](ADR-228-Next-Quest-Hint.md) | Gợi ý nhiệm vụ tiếp: đang làm → chờ nhận → nhiệm vụ cốt truyện kế tiếp có NPC giao (đọc script hội thoại), 628/629; `/tieptheo [di]` | ✅ Accepted (lô 37) |
| [ADR-229](ADR-229-Chat-Channel-Filter.md) | Lọc kênh chat ở client, lưu Preferences; `/an`, `/hien`, `/kenh` | ✅ Accepted (lô 37) |
| [ADR-230](ADR-230-Guild-Achievement-Share.md) | Khoe thành tựu vào bang: event 1707 tới cả bang khi mở thành tựu, công tắc `share_achievements` (V166) mặc định bật, 1705/1706; `/khoett` | ✅ Accepted (lô 37) |
| [ADR-231](ADR-231-Destroy-Item.md) | Huỷ vật phẩm: 560/561 huỷ cả chồng, chặn khoá/đang mặc/đang giao dịch, audit `ITEM_DESTROYED`; `/huy` + `/huy xacnhan` trong 10 giây | ✅ Accepted (lô 38) |
| [ADR-232](ADR-232-Bulk-Dismantle.md) | Phân giải hàng loạt: 562/563 gọi lại `DismantleFacade` từng món (loại khoá/mặc/khảm ngọc), tối đa 40; `/phangiai <cấp>` | ✅ Accepted (lô 38) |
| [ADR-233](ADR-233-Enhance-History.md) | Lịch sử cường hoá: 564/565 đọc audit `ENHANCE_ITEM`, 20 dòng, không bảng mới; `/lichsucuonghoa` | ✅ Accepted (lô 38) |
| [ADR-234](ADR-234-Market-Price-Hint.md) | Giá gợi ý phường thị: 1040/1041 giá một cái — rẻ nhất đang rao + trung bình đã bán 7 ngày; `/giacho` | ✅ Accepted (lô 38) |
| [ADR-235](ADR-235-Guild-Mail.md) | Thư toàn bang: 766/767 bang chủ/phó gửi thư thường tới mọi thành viên khác, 3 đợt/bang/ngày đếm trên audit `GUILD_MAIL`; `/thubang` | ✅ Accepted (lô 38) |
| [ADR-236](ADR-236-Do-Not-Disturb.md) | Không làm phiền: cột `do_not_disturb` (V167) chặn tin riêng/mời nhóm/giao dịch/kết bạn từ người không phải bạn (lỗi 403), 1153/1154; `/khonglamphien` | ✅ Accepted (lô 38) |
| [ADR-237](ADR-237-Block-Stranger-Invites.md) | Chặn lời mời người lạ: cột `block_stranger_invites` (V167), chỉ bạn bè/cùng bang mời nhóm/giao dịch được; dùng chung 1153/1154; `/chanloimoi` | ✅ Accepted (lô 38) |
| [ADR-238](ADR-238-Login-History.md) | Lịch sử đăng nhập: bảng `login_history` (V167) chỉ lưu IP đã che, giữ 50 dòng/tài khoản; 265/266 trả 10 dòng; `/lichsudangnhap` | ✅ Accepted (lô 38) |
| [ADR-239](ADR-239-Guild-Buff.md) | Buff bang: 768/769 bang chủ tiêu 1.000 kinh nghiệm bang (không được tụt cấp) ⇒ cả bang +20% EXP 1 giờ; cột `guild.exp_buff_until` (V168), đệm 30 giây; `/buffbang` | ✅ Accepted (lô 39) |
| [ADR-240](ADR-240-Personal-Records.md) | Kỷ lục cá nhân: đòn mạnh nhất + chuỗi hạ quái, theo dõi RAM trên tick thread, ghi khi phá (bảng `personal_record` V168), báo chat; 267/268; `/kyluc` | ✅ Accepted (lô 39) |
| [ADR-241](ADR-241-Wealth-Leaderboard.md) | Xếp hạng giàu có: `LEADERBOARD_KIND_WEALTH` top 100 theo linh thạch; tab "Phú hộ" | ✅ Accepted (lô 39) |
| [ADR-242](ADR-242-Quest-Reward-Preview.md) | Xem trước thưởng nhiệm vụ: 630/631 chạy `on_claim` vào danh sách rồi bỏ — không ghi gì; `/xemthuong` | ✅ Accepted (lô 39) |
| [ADR-243](ADR-243-Low-Health-Warning.md) | Cảnh báo máu thấp: < 30% viền đỏ nháy + một dòng nhắc (thuần client) | ✅ Accepted (lô 39) |
| [ADR-244](ADR-244-Photo-Mode.md) | Chế độ chụp ảnh: F10 / `/chupanh` ẩn HUD + chat (thuần client) | ✅ Accepted (lô 39) |
| [ADR-245](ADR-245-Mention-Highlight.md) | Tô sáng khi bị nhắc tên: khớp tên không dấu, dòng màu `MENTION` + một dòng báo (thuần client) | ✅ Accepted (lô 39) |
| [ADR-246](ADR-246-Bag-Nearly-Full-Warning.md) | Cảnh báo sắp đầy túi: ≤ 2 ô trống nhắc một dòng + gợi ý `/tubanrac`, `/phangiai` (thuần client) | ✅ Accepted (lô 39) |
| [ADR-247](ADR-247-Quick-Reply.md) | Trả lời nhanh tin riêng: `/r` gửi tin riêng tới người vừa nhắn mình (thuần client) | ✅ Accepted (lô 40) |
| [ADR-248](ADR-248-Guild-Poll.md) | Bình chọn trong bang: V169 `guild_poll`/`guild_poll_vote`, bang chủ/phó mở 2–4 lựa chọn 24 giờ, đổi phiếu được; 770–775; `/binhchon` | ✅ Accepted (lô 40) |
| [ADR-249](ADR-249-Chat-Keyword-Filter.md) | Lọc chat theo từ khoá: tối đa 20 từ, khớp không dấu, chỉ ẩn tin người chơi; `/locchu` (thuần client) | ✅ Accepted (lô 40) |
| [ADR-250](ADR-250-Location-Bookmarks.md) | Đánh dấu vị trí yêu thích: 10 toạ độ X có tên, đi bằng đường tự đi ADR-206; `/luuvitri`, `/vitri` (thuần client) | ✅ Accepted (lô 40) |
| [ADR-251](ADR-251-Party-Damage-Meter.md) | Bảng sát thương nhóm: cộng sát thương mình + nhóm từ gói đòn đánh, trận cách 30 giây; `/satthuong` (thuần client) | ✅ Accepted (lô 40) |
| [ADR-252](ADR-252-Daily-Reminder.md) | Nhắc việc ngày: ≤ 1 giờ trước reset nhắc một lần người online còn nhiệm vụ ngày chưa lĩnh (không nhắc điểm danh — đã tự nhận) | ✅ Accepted (lô 40) |
| [ADR-253](ADR-253-Loot-History.md) | Lịch sử nhặt đồ: 566/567 đọc audit `LOOT_GRANT`, 20 dòng; `/lichsunhat` | ✅ Accepted (lô 40) |
| [ADR-254](ADR-254-Bag-Search.md) | Tìm trong túi đồ: `/timdo` khớp không dấu trên mô tả món trong túi (thuần client) | ✅ Accepted (lô 40) |
| [ADR-255](ADR-255-Monster-Info.md) | Xem thông tin quái: 416/417 chỉ số + bảng rơi từ `monster_template`/`drop_table_entry`; `/xemquai` (mục tiêu đang nhắm hoặc mã) | ✅ Accepted (lô 41) |
| [ADR-256](ADR-256-Guild-Event-Schedule.md) | Lịch hoạt động bang: V170 `guild_event`, sĩ quan hẹn 1 phút–7 ngày, tối đa 5 buổi, nhắc 10 phút trước một lần; 776–781; `/lichbang` | ✅ Accepted (lô 41) |
| [ADR-257](ADR-257-Enhance-Preview.md) | Dự tính cường hoá: 568/569 đọc `EnhancementTable` cho món đang chọn (giá, tỉ lệ, tụt cấp, Bảo Vệ Phù); `/dutinh` | ✅ Accepted (lô 41) |
| [ADR-258](ADR-258-Friend-Ranking.md) | Xếp hạng bạn bè: 1155/1156 lọc bảng Lực Chiến đã đệm theo mình + bạn bè; `/xephangban` | ✅ Accepted (lô 41) |
| [ADR-259](ADR-259-Reminder-Timer.md) | Hẹn giờ nhắc: `Timer` libGDX, tối đa 5 lời nhắc 1–600 phút; `/hengio` (thuần client) | ✅ Accepted (lô 41) |
| [ADR-260](ADR-260-Server-Clock.md) | Đồng hồ giờ server: dùng lại gói 255/256, in giờ server (UTC+7), giờ máy, độ lệch; `/gio` (thuần client) | ✅ Accepted (lô 41) |
| [ADR-261](ADR-261-Personal-Notebook.md) | Sổ ghi chép cá nhân: Preferences, 20 dòng; `/so` (thuần client) | ✅ Accepted (lô 41) |
| [ADR-262](ADR-262-Chat-Line-Recall.md) | Gọi lại tin đã gõ: ↑/↓ trong ô chat, 30 dòng trong phiên (thuần client) | ✅ Accepted (lô 41) |
| [ADR-263](ADR-263-Party-Ready-Check.md) | Kiểm tra sẵn sàng nhóm: 1337–1340; lời mời/kết quả qua dòng chat hệ thống; RAM gateway, 30 giây | ✅ Accepted (lô 42) |
| [ADR-264](ADR-264-Party-Countdown.md) | Đếm ngược nhóm: 1341/1342; 3–10 giây; nhịp 1 giây qua dòng chat hệ thống | ✅ Accepted (lô 42) |
| [ADR-265](ADR-265-Dice-Roll.md) | Gieo xúc xắc vào chat: 814/815; 1..max (2–1000, mặc định 100); 3 giây/lần; tới nhóm (khu vực gần: chưa) | ✅ Accepted (lô 42) |
| [ADR-266](ADR-266-Guild-Member-Online-Notice.md) | Báo thành viên bang online: 782 tới đồng môn online lúc vào world; tắt ở client (Preferences) | ✅ Accepted (lô 42) |
| [ADR-267](ADR-267-Command-Help.md) | Danh sách lệnh /trogiup: bảng tĩnh 11 nhóm, lọc không dấu | ✅ Accepted (lô 42) |
| [ADR-268](ADR-268-Chat-Timestamps.md) | Giờ trước tin chat: `Line.receivedAtMillis`; `/giochat`; Preferences, mặc định tắt | ✅ Accepted (lô 42) |
| [ADR-269](ADR-269-Ping-Command.md) | Đo ping /ping: dùng lại PING/PONG; ngưỡng 100/250 ms | ✅ Accepted (lô 42) |
| [ADR-270](ADR-270-Coordinates-Command.md) | Toạ độ hiện tại /toado: hoành độ + trạng thái vùng; `/toado gui` gửi vào kênh đang chọn | ✅ Accepted (lô 42) |
| [ADR-271](ADR-271-Session-Summary.md) | Tóm tắt phiên chơi: nghe 208/209/500/402/406; `/phien`; RAM | ✅ Accepted (lô 43) |
| [ADR-272](ADR-272-Exp-Per-Hour.md) | Đồng hồ EXP/giờ: `/exph`; tốc độ trung bình cả phiên; ETA từ 208 | ✅ Accepted (lô 43) |
| [ADR-273](ADR-273-Command-Aliases.md) | Biệt danh lệnh /alias: Preferences, 20 biệt danh; viết lại một lần trước xử lý | ✅ Accepted (lô 43) |
| [ADR-274](ADR-274-Bag-Value.md) | Tổng giá trị túi đồ: 570/571; Σ sell_price × số lượng, bão hoà | ✅ Accepted (lô 43) |
| [ADR-275](ADR-275-Mail-Search.md) | Tìm thư theo từ khoá: dùng lại 902/903; lọc tiêu đề + nội dung + thư hệ thống | ✅ Accepted (lô 43) |
| [ADR-276](ADR-276-Favorite-Friends.md) | Bạn bè yêu thích: Preferences; ghim lên đầu, ★; cùng thứ tự cho vẽ và nút Xoá | ✅ Accepted (lô 43) |
| [ADR-277](ADR-277-Mail-Draft.md) | Thư nháp: Preferences; lưu lúc đóng, điền lúc mở, xoá khi gửi OK | ✅ Accepted (lô 43) |
| [ADR-278](ADR-278-Party-Gold-Split.md) | Chia vàng cho nhóm: 1343/1344; cùng World; một transaction + audit; khoá an toàn | ✅ Accepted (lô 43) |
| [ADR-279](ADR-279-Item-Lookup.md) | Tra cứu vật phẩm: client tìm theo tên trong catalog; 572/573 giá cửa hàng + quái rơi; `/vatpham` | ✅ Accepted (lô 44) |
| [ADR-280](ADR-280-Duel-History.md) | Lịch sử đấu tay đôi: audit `DUEL_RESULT` hai bên trong giao dịch Elo; 1204/1205; `/lichsudau` | ✅ Accepted (lô 44) |
| [ADR-281](ADR-281-Online-Count.md) | Đếm người online: 269/270 tổng + linh căn + cảnh giới, không tên; `/online` | ✅ Accepted (lô 44) |
| [ADR-282](ADR-282-Weekly-Reminder.md) | Nhắc nhiệm vụ tuần: ≤ 3 giờ trước reset tuần nhắc một lần người còn việc tuần chưa lĩnh | ✅ Accepted (lô 44) |
| [ADR-283](ADR-283-Fps-Counter.md) | Bộ đếm FPS: nhãn góc HUD, Preferences; `/fps` (thuần client) | ✅ Accepted (lô 44) |
| [ADR-284](ADR-284-Whisper-History.md) | Lịch sử tin riêng: RAM, 30 dòng/người; `/tinrieng` (thuần client) | ✅ Accepted (lô 44) |
| [ADR-285](ADR-285-Hide-Others-Damage.md) | Ẩn số sát thương người ngoài nhóm: cài đặt hiển thị mặc định tắt (thuần client) | ✅ Accepted (lô 44) |
| [ADR-286](ADR-286-Auto-Accept-Friend-Party.md) | Tự nhận lời mời nhóm từ bạn bè: V171 cột công tắc, server tự chấp nhận khi người mời là bạn; 1345/1346; `/tunhannhom` | ✅ Accepted (lô 44) |
| [ADR-287](ADR-287-Guild-Contribution-Board.md) | Bảng cống hiến bang: Dùng lại 714/715; xếp ở client; `/conghien` | ✅ Accepted (lô 45) |
| [ADR-288](ADR-288-Shop-Purchase-History.md) | Lịch sử mua cửa hàng NPC: Audit `SHOP_BUY` + V172 chỉ mục; 1042/1043; `/lichsumua` | ✅ Accepted (lô 45) |
| [ADR-289](ADR-289-Loyalty-Milestone-Gift.md) | Quà mốc gắn bó: Mốc 30 ngày từ `created_at`; thư idempotent; 1000×min(n,12) linh thạch | ✅ Accepted (lô 45) |
| [ADR-290](ADR-290-Sent-Mail.md) | Thư đã gửi: `mail.sender_id` + V172 chỉ mục; 919/920; `/thudagui` | ✅ Accepted (lô 45) |
| [ADR-291](ADR-291-Pin-Chat-Line.md) | Ghim tin chat: Nhãn ghim trong `ChatPanel`; `/ghimchat [bo]` | ✅ Accepted (lô 45) |
| [ADR-292](ADR-292-Chat-Idle-Fade.md) | Thu gọn khung chat khi rảnh: Mờ dần 2 giây cuối; `/chatan` 3–300 hoặc tắt; Preferences | ✅ Accepted (lô 45) |
| [ADR-293](ADR-293-Combo-Counter.md) | Đếm combo đòn đánh: `ComboCounter` 3 giây, hiện từ đòn 3; `/combo` | ✅ Accepted (lô 45) |
| [ADR-294](ADR-294-Chat-Font-Size.md) | Cỡ chữ khung chat: 3 cỡ chữ qua `setFontScale`; Preferences | ✅ Accepted (lô 45) |
| [ADR-295](ADR-295-Mail-Forward.md) | Chuyển tiếp thư: Client dựng `SendMailCommand` từ thư đang chọn; `/chuyentiep` | ✅ Accepted (lô 46) |
| [ADR-296](ADR-296-Boss-Kill-History.md) | Lịch sử boss đã hạ: Audit `BOSS_KILL` mỗi người; 418/419; `/bossdaha` | ✅ Accepted (lô 46) |
| [ADR-297](ADR-297-Scheduled-Mail.md) | Thư hẹn giờ gửi: Bảng `scheduled_mail`; job 60 giây qua đường thư thường; 921/922; `/thuhengio` | ✅ Accepted (lô 46) |
| [ADR-298](ADR-298-Profile-Likes.md) | Thích hồ sơ người chơi: Bảng `profile_like`; 271/272; `InspectCharacterResponse.likes` | ✅ Accepted (lô 46) |
| [ADR-299](ADR-299-Target-Distance.md) | Khoảng cách tới mục tiêu: Dòng khoảng cách trong `HudTargetFrame` | ✅ Accepted (lô 46) |
| [ADR-300](ADR-300-Screenshot-Key.md) | Chụp màn hình F12: F12 → PNG trong `~/KageScreenshots`, ghi trên Virtual Thread | ✅ Accepted (lô 46) |
| [ADR-301](ADR-301-Stopwatch.md) | Đồng hồ bấm giờ: `/bamgio [xem]` | ✅ Accepted (lô 46) |
| [ADR-302](ADR-302-Quick-Calculator.md) | Máy tính nhanh: Phân tích đệ quy xuống, `BigDecimal`; `/tinh` | ✅ Accepted (lô 46) |
| [ADR-303](ADR-303-Boss-Damage-Ranking.md) | Xếp hạng sát thương boss: Tổng từ `world_boss_damage` + số trận từ `BOSS_KILL`; 420/421; `/topboss` | ✅ Accepted (lô 47) |
| [ADR-304](ADR-304-Guild-Chat-Backlog.md) | Tin bang lúc vắng: Ring buffer RAM 50 tin/bang; phát lại tin sau mốc rời world | ✅ Accepted (lô 47) |
| [ADR-305](ADR-305-Like-Ranking.md) | Xếp hạng lượt thích: Đếm `profile_like`; 273/274; `/topthich` | ✅ Accepted (lô 47) |
| [ADR-306](ADR-306-Event-Reminder.md) | Báo trước sự kiện: Job 60 giây, 3 loại mốc, mỗi mốc một lần | ✅ Accepted (lô 47) |
| [ADR-307](ADR-307-Colorblind-Mode.md) | Chế độ mù màu: Dòng cài đặt `COLORBLIND`: số sát thương phải chịu đổi xanh dương | ✅ Accepted (lô 47) |
| [ADR-308](ADR-308-UI-Scale.md) | Thu phóng giao diện: `PanelHub.setUiScale` qua `ScreenViewport`; `/giaodien`; Preferences | ✅ Accepted (lô 47) |
| [ADR-309](ADR-309-Hide-Other-Titles.md) | Ẩn tên người chơi khác: Dòng cài đặt `HIDE_OTHER_TITLES`, giữ đồng đội | ✅ Accepted (lô 47) |
| [ADR-310](ADR-310-Share-Position-Party.md) | Chia sẻ vị trí vào nhóm: `/chiasevitri` gửi tọa độ vào kênh nhóm | ✅ Accepted (lô 47) |
| [ADR-311](ADR-311-Friend-Presence-Chat-Line.md) | Báo bạn bè vào game (dòng chat + công tắc): Dòng chat kèm chữ nổi; `/baobanbe` | ✅ Accepted (lô 48) |
| [ADR-312](ADR-312-Friends-Mail.md) | Thư gửi mọi bạn bè: Mỗi bạn một thư thường; 1 đợt/ngày qua audit; 925/926 | ✅ Accepted (lô 48) |
| [ADR-313](ADR-313-Guild-Welcome.md) | Lời chào thành viên mới: Cột `welcome_message`; gửi chat + thư khi vào bang; 783/784; `/loichao` | ✅ Accepted (lô 48) |
| [ADR-314](ADR-314-Unread-Mail.md) | Thư chưa đọc: Cột `read_at`; `MailView.unread`; 923/924; báo lúc vào world | ✅ Accepted (lô 48) |
| [ADR-315](ADR-315-Chat-Bubbles.md) | Bong bóng chat trên đầu: Tin khu vực ⇒ chữ trên đầu qua `EmoteBubbles`; `/bongbong` | ✅ Accepted (lô 48) |
| [ADR-316](ADR-316-Ping-Hud-Toggle.md) | Ping trên HUD (bật/tắt + màu): Màu theo ngưỡng `/ping`; `/pinghud` | ✅ Accepted (lô 48) |
| [ADR-317](ADR-317-System-Notice-History.md) | Lịch sử thông báo hệ thống: Vòng 30 dòng hệ thống; `/lichsuthongbao` | ✅ Accepted (lô 48) |
| [ADR-318](ADR-318-Minimal-Hud.md) | HUD tối giản: Dòng cài đặt `MINIMAL_HUD`: ẩn gợi ý phím + thẻ nhiệm vụ | ✅ Accepted (lô 48) |
| [ADR-319](ADR-319-Guild-Member-Notes.md) | Ghi chú thành viên bang: Cột `officer_note`; chỉ cấp quản lý; 785/786; `/ghichubang` | ✅ Accepted (lô 49) |
| [ADR-320](ADR-320-Referral-Code.md) | Mã giới thiệu bạn: Mã = tên; bảng `referral`; quà một lần ở cấp 10; 275/276 | ✅ Accepted (lô 49) |
| [ADR-321](ADR-321-High-Enhance-Announcement.md) | Báo toàn server khi cường hoá cao: Cường hoá ≥ +10 ⇒ chat toàn server kèm `linked_item` | ✅ Accepted (lô 49) |
| [ADR-322](ADR-322-Breakthrough-History.md) | Lịch sử đột phá cảnh giới: Audit `BREAKTHROUGH` khi mở cảnh giới; 277/278; `/dotpha` | ✅ Accepted (lô 49) |
| [ADR-323](ADR-323-Camera-Zoom.md) | Thu phóng camera: Lăn chuột ⇒ `camera.zoom` [0,7; 1,5]; Preferences | ✅ Accepted (lô 49) |
| [ADR-324](ADR-324-Bag-Filter.md) | Lọc túi theo loại: `/loctui` theo catalog: trang bị / dùng được / khác | ✅ Accepted (lô 49) |
| [ADR-325](ADR-325-Nearby-Count.md) | Đếm người xung quanh: Nhãn HUD từ bản đồ người đang thấy; `/xungquanh` | ✅ Accepted (lô 49) |
| [ADR-326](ADR-326-New-Item-Badge.md) | Nhãn "mới" cho đồ vừa nhặt: Tiền tố "(mới) " theo mẫu tới khi mở túi | ✅ Accepted (lô 49) |
| [ADR-327](ADR-327-Login-Streak.md) | Chuỗi đăng nhập liên tiếp: Bảng `login_streak` V176, mốc 3 / bội 7 ngày tặng linh thạch qua thư; `/chuoi` | ✅ Accepted (lô 50) |
| [ADR-328](ADR-328-New-Network-Login-Warning.md) | Cảnh báo đăng nhập từ vùng mạng lạ: So IP che với 50 lần trước, khác hết ⇒ cảnh báo lúc vào world; không opcode | ✅ Accepted (lô 50) |
| [ADR-329](ADR-329-Recent-Players.md) | Người vừa chơi cùng: Bảng `recent_player` V176 ghi lúc vào nhóm, giữ 10; `/vuachoi` | ✅ Accepted (lô 50) |
| [ADR-330](ADR-330-Inspect-Notice.md) | Báo khi bị soi trang bị: Dòng báo cho người bị soi đang online; công tắc V176; `/baosoi` | ✅ Accepted (lô 50) |
| [ADR-331](ADR-331-Floating-Exp-Gain.md) | Chữ +EXP khi nhận kinh nghiệm: Hiệu `total_exp` giữa hai event ⇒ chữ bay; `/chuexp` | ✅ Accepted (lô 50) |
| [ADR-332](ADR-332-Session-Clock-Hud.md) | Đồng hồ phiên chơi trên HUD: Nhãn H:MM:SS góc dưới trái, không cấp phát; `/dongho` | ✅ Accepted (lô 50) |
| [ADR-333](ADR-333-Hide-Chat-Key.md) | Phím ẩn/hiện khung chat: Binding `anchat` mặc định S, khung chat không vẽ khi ẩn | ✅ Accepted (lô 50) |
| [ADR-334](ADR-334-Level-Goal.md) | Mục tiêu cấp độ: HUD "còn K cấp, X% cấp này" từ số server; `/muctieu` | ✅ Accepted (lô 50) |
| [ADR-335](ADR-335-Status-Message.md) | Lời nhắn trạng thái: Bảng `status_message` V177, hiện cạnh tên trong danh sách bạn; `/trangthai` | ✅ Accepted (lô 51) |
| [ADR-336](ADR-336-Bulletin-Board.md) | Bảng rao vặt: Bảng `bulletin_post` V177, một tin/người sống 24 giờ; `/raovat` | ✅ Accepted (lô 51) |
| [ADR-337](ADR-337-Death-History.md) | Lịch sử tử vong: Audit `DEATH` (người hạ / mã quái / x), 10 lần gần nhất; `/tuvong` | ✅ Accepted (lô 51) |
| [ADR-338](ADR-338-Party-Vote-Kick.md) | Biểu quyết đuổi khỏi nhóm: Quá nửa đồng ý trong 60 giây, trạng thái RAM; `/duoi`, `/bophieu` | ✅ Accepted (lô 51) |
| [ADR-339](ADR-339-Chat-Channel-Colors.md) | Màu riêng cho kênh chat: Ghi đè màu theo 6 loại dòng, lưu máy; `/maukenh` | ✅ Accepted (lô 51) |
| [ADR-340](ADR-340-Full-Health-Notice.md) | Báo hồi đầy máu: Dòng "Đã hồi đầy máu" sau khi bị thương; `/baodaymau` | ✅ Accepted (lô 51) |
| [ADR-341](ADR-341-Double-Click-Use-Equip.md) | Nhấp đúp dùng/mặc đồ: Bấm tên = chọn, nhấp đúp = Dùng/Mặc | ✅ Accepted (lô 51) |
| [ADR-342](ADR-342-Screen-Shake.md) | Rung màn hình khi trúng đòn: Đòn ≥ 10% máu tối đa ⇒ camera rung tắt dần; `/rung` | ✅ Accepted (lô 51) |
---

## ADR cần tạo (chưa viết)

Tổng hợp từ toàn bộ tài liệu. **Không code chạm các vùng này khi ADR chưa Accepted.**

| ADR | Nội dung | Ưu tiên | Chặn | Nguồn |
|---|---|---|---|---|

> ADR-013, ADR-017, ADR-025 đã Accepted (2026-07-19), ADR-016 đã Accepted (2026-07-20), ADR-026 đã Accepted (2026-07-24), ADR-014/ADR-015 đã Accepted (2026-07-26), ADR-019 đã Accepted (2026-07-28), ADR-027 đã Accepted (2026-07-31) — xem bảng "Danh sách ADR" ở trên, không còn nằm ở đây. ADR-011/020/021/022/024/028/029 tất cả **Accepted 2026-08-01** (chủ dự án xác nhận qua chat, dựa dữ liệu code thật đã chạy qua Phase 1-9); ADR-007 **Superseded** cùng ngày bởi ADR-030 mới (không xây Plugin System, xoá `engine-plugin-api`). **ADR-023 nay ĐÃ VIẾT** (Accepted 2026-09-05, desktop-first) — chỉ mục hết ô "chưa viết". **ADR-043 Accepted 2026-09-05 và đã thi hành + kiểm chứng sống cùng ngày**. **ADR-044 cũng Accepted + thi hành 2026-09-05.** **ADR-045 Accepted 2026-09-06** (event ngoại hình đổi, thi hành cùng ngày). **ADR-046 Accepted 2026-09-07** (hồi máu — ba nguồn, một event; thi hành cùng ngày). Chưa Accepted nay là **hai**: **ADR-042** (giảm số gói gửi ra — hoãn có chủ ý) và **ADR-047** (báo cho người bị kéo vào hầm ngục theo nhóm — viết 2026-09-11). **ADR-049 Accepted 2026-09-12** (quái đánh trả khi bị đánh) và thi hành cùng ngày. **ADR-048 Accepted 2026-09-12** và thi hành cùng ngày. **Cập nhật 2026-10-01 (đếm bằng máy trên dòng `Status` của từng file, ADR-034 khai bằng gạch đầu dòng):** 53 file — **49 Accepted · 3 Proposed · 1 Superseded**. Chưa Accepted là **ADR-042** (hoãn có chủ ý), **ADR-052** (lệnh GM — chờ duyệt) và **ADR-053** (client mang theo runtime Java 25 — mới viết, chờ duyệt); câu "ADR-047 còn chờ" ở trên đã cũ — ADR-047 Accepted 2026-09-13.

---

## Ba ADR chặn Phase 0 — trạng thái

1. ✅ **ADR-012** — package root `dev.kage`. Đã chốt.
2. ✅ **ADR-011** — enforce dependency rule bằng ArchUnit. **Code đã chạy thật từ Phase 0** (`quality/architecture-tests`, 8 rule); file ADR viết ngày 2026-07-22, Accepted 2026-08-01.
3. ✅ **ADR-010** — single-realm + `realm_id` dự phòng. Đã chốt.

→ Dự án đã đủ điều kiện mở Phase 0 từ lâu; Phase 0-4 đã xong ([ROADMAP.md](../../11-process/ROADMAP.md)). Cả 3 ADR chặn Phase 0 đều đã Accepted.

---

## Tài liệu liên quan

- [KAGE-CONSTITUTION.md](../../00-governance/KAGE-CONSTITUTION.md) — Điều 37 là luật gốc
- [AI-WORKING-RULES.md §7](../../00-governance/AI-WORKING-RULES.md) — quy tắc AI tạo ADR
- [ARCHITECTURE.md](../ARCHITECTURE.md) — phần lớn ADR bắt nguồn từ đây
- [docs/README.md](../../README.md) — tổng hợp toàn bộ quyết định đang treo
