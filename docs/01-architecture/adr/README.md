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
| [ADR-043](ADR-043-Push-Max-Hp-On-Equipment-Change.md) | Máu tối đa mới phải theo gió về client ngay khi đổi trang bị (field additive cho `EquipItemResponse`/`UnequipItemResponse`/`EnhanceItemResponse`) | 🟡 **Proposed** *(2026-09-05)* | Bắt được khi PLAYTEST SỐNG quest `night_watch_bandit`: mặc trọn bộ `novice_*` (**+18 máu**) giữa world mà thanh máu vẫn `100 / 100`, chỉ sau khi CHẾT và hồi sinh mới thành `118 / 118`. **Server KHÔNG sai** — `applyEquipmentBonus` cập nhật `HealthComponent.max` ngay trong tick đó; khoảng trống nằm ở GIAO THỨC: không gói nào chở máu tối đa của chính mình ngoài `SelectCharacterResponse` (vào world) và `CharacterRespawnedEvent` (hồi sinh). Đây **đúng là nửa sau của bug ADR-036 từng sửa** (`139 / 100`) — lần đó chỉ vá thời điểm vào world. ⚠️ **Bẫy thi hành (D5)**: `applyEquipmentBonus` đẩy lệnh vào `inbox` của tick, đọc entity ngay sau khi gọi sẽ ra số CŨ ⇒ phải tính lại qua `WorldFacade.characterMaxHp(bonus)`. Additive, `protocolVersion` KHÔNG tăng | **Chờ chủ dự án**: duyệt/từ chối, và chọn (a) tính lại bằng hàm static hay (b) đợi tick qua `CompletableFuture` ở D5. |
| [ADR-044](ADR-044-Quest-Prerequisite-Enforcement.md) | Thi hành `prerequisite_quest_id` — quest có thứ tự kể chuyện thì phải đi đúng thứ tự | 🟡 **Proposed** *(2026-09-05)* | Cột có từ **V11** (Phase 6) và suốt từ đó **không dòng dữ liệu nào dùng, không dòng code nào đọc** — `grep prerequisite` chỉ ra đúng một javadoc ghi "thêm khi có nhu cầu thật". **V28 (2026-09-05) là dòng đầu tiên dùng nó**, nên nhu cầu đã tới: hôm nay nhận được quest "trực đêm" mà chưa từng đập mộc nhân, ngược thứ tự [STORY.md §6](../../13-game-design/STORY.md). Không ai gặp trước đây vì trước V28 cả dự án chỉ có ĐÚNG MỘT quest. Từ chối vì lý do MỚI ⇒ client phải phân biệt được ⇒ thêm `ACCEPT_QUEST_OUTCOME_PREREQUISITE_NOT_MET = 4` (chỉ THÊM giá trị enum, `protocolVersion` KHÔNG tăng). **Không migration** — cột đã có sẵn. **Không giấu lựa chọn hội thoại** (Điều 15: server từ chối, không phải client tự lọc) | **Chờ chủ dự án**: chủ dự án đã chọn THI HÀNH; ADR này chốt chi tiết. Hai câu cần trả lời: (1) mốc là `CLAIMED` hay `COMPLETED`? (khuyên `CLAIMED`) (2) có ẩn luôn lựa chọn hội thoại không? (khuyên để sau) |

---

## ADR cần tạo (chưa viết)

Tổng hợp từ toàn bộ tài liệu. **Không code chạm các vùng này khi ADR chưa Accepted.**

| ADR | Nội dung | Ưu tiên | Chặn | Nguồn |
|---|---|---|---|---|

> ADR-013, ADR-017, ADR-025 đã Accepted (2026-07-19), ADR-016 đã Accepted (2026-07-20), ADR-026 đã Accepted (2026-07-24), ADR-014/ADR-015 đã Accepted (2026-07-26), ADR-019 đã Accepted (2026-07-28), ADR-027 đã Accepted (2026-07-31) — xem bảng "Danh sách ADR" ở trên, không còn nằm ở đây. ADR-011/020/021/022/024/028/029 tất cả **Accepted 2026-08-01** (chủ dự án xác nhận qua chat, dựa dữ liệu code thật đã chạy qua Phase 1-9); ADR-007 **Superseded** cùng ngày bởi ADR-030 mới (không xây Plugin System, xoá `engine-plugin-api`). **ADR-023 nay ĐÃ VIẾT** (Accepted 2026-09-05, desktop-first) — chỉ mục hết ô "chưa viết". **ADR-043 Accepted 2026-09-05 và đã thi hành + kiểm chứng sống cùng ngày**. Chưa Accepted hiện có hai: **ADR-042** (hoãn có chủ ý) và **ADR-044** (thi hành thứ tự quest, chủ dự án đã chọn làm, ADR đang chờ chốt chi tiết).

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
