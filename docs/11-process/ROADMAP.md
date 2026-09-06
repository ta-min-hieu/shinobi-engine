# ROADMAP.md

**Lộ trình phát triển**

| | |
|---|---|
| Version | 2.0.0 |
| Status | Draft |
| Luật gốc | [Hiến pháp Điều 33–34](../00-governance/KAGE-CONSTITUTION.md#chương-vii--definition-of-done--definition-of-ready) |

> ⚠️ **Mốc thời gian (ngày/tuần) cố tình KHÔNG điền** — phụ thuộc quy mô đội ngũ thực tế, thứ chưa biết.
> Tài liệu này định nghĩa **thứ tự** và **điều kiện hoàn thành**, không định nghĩa deadline.

---

## 1. Nguyên tắc

| Nguyên tắc | Vì sao |
|---|---|
| **Không mở Phase N+1 khi Phase N chưa đạt Exit Criteria** | Tránh xây trên nền chưa vững (Điều 7) |
| **Không làm trước tính năng Phase sau** dù "tiện tay" | Điều 9 |
| Mỗi Phase phải thoả **Definition of Done** (Điều 34) | Không có "xong 90%" |
| ADR chặn Phase phải **Accepted trước** khi mở Phase | Điều 33 |

---

## 2. Tổng quan

```mermaid
graph LR
    P0["0· Foundation"] --> P1["1· Engine Core"]
    P1 --> P2["2· Network"]
    P2 --> P3["3· Character<br/>Map · Movement"]
    P3 --> P4["4· Combat<br/>Skill · Monster"]
    P4 --> P5["5· Inventory<br/>Equipment · Shop"]
    P5 --> P6["6· Quest<br/>NPC · Dialogue"]
    P6 --> P7["7· Guild · Party<br/>Trade"]
    P7 --> P8["8· Dungeon<br/>Boss · PvP"]
    P8 --> P9["9· Optimization"]
    P9 --> P10["10· Release"]

    style P0 fill:#2d6a4f,color:#fff
    style P10 fill:#1d3557,color:#fff
```

⚠️ **Không có "MVP" là một Phase.** MVP = kết thúc Phase 6 (chơi được từ tạo nhân vật → làm quest → đánh quái → nhặt đồ). Phase 7+ là mở rộng.

---

## 3. ✅ Điều kiện mở Phase 0 — đã đủ

| ADR | Trạng thái |
|---|---|
| ✅ **ADR-012** — package root `dev.kage` | Đã chốt (2026-07-18) |
| ✅ **ADR-010** — single-realm + `realm_id` dự phòng | Đã chốt (2026-07-18) |
| ✅ **ADR-018** — tick rate 60Hz (cần cho Phase 1) | Đã chốt (2026-07-18) |
| ⏳ **ADR-011** — enforce dependency rule (ArchUnit) | Là **việc kỹ thuật trong Phase 0** (viết ArchUnit test), không phải quyết định chặn khởi động |

→ **Dự án đủ điều kiện mở Phase 0.** ADR-011 được hoàn thành như một hạng mục của chính Phase 0.

---

## Phase 0 — Foundation

**Mục tiêu**: repo, build, quy ước sẵn sàng. **Chưa có gameplay.**

| Việc | Nguồn |
|---|---|
| `settings.gradle.kts`, `build-logic/`, `libs.versions.toml` | [REPOSITORY-STRUCTURE.md §1](../01-architecture/REPOSITORY-STRUCTURE.md) |
| ⚠️ **Verify mọi version thật** trước khi ghi vào catalog | [TECHNOLOGY-STACK.md](../01-architecture/TECHNOLOGY-STACK.md) |
| Spotless + Checkstyle + Error Prone + SpotBugs + PMD | [STATIC-ANALYSIS.md §12](../10-quality/STATIC-ANALYSIS.md) |
| 🔴 **ArchUnit** | [CI-CD.md §4](../07-infrastructure/CI-CD.md) |
| 🔴 **Secret scanning + pre-commit hook** | [OWASP.md §2](../08-security/OWASP.md) |
| CI pipeline: build + lint | [CI-CD.md §5](../07-infrastructure/CI-CD.md) |
| `docker-compose.yml` (⚠️ **UTF-8 collation**) | [DOCKER.md §5](../07-infrastructure/DOCKER.md) |
| Module scaffold (rỗng) | [REPOSITORY-STRUCTURE.md](../01-architecture/REPOSITORY-STRUCTURE.md) |
| JFR + GC log bật sẵn | [JVM.md](../09-performance/JVM.md) |

**Exit Criteria:**
- [x] `./gradlew build` xanh trên CI — `.github/workflows/ci.yml` chạy ba bước tách rời
      (`build -x test` → `checkstyleMain spotlessCheck spotbugsMain pmdMain pmdTest` → `test`, tức
      ArchUnit nằm trong bước cuối) cộng job `gitleaks` quét toàn bộ lịch sử. Lần chạy CI xanh gần
      nhất ghi nhận được là mẻ Dependabot 2026-07-31; **kiểm chứng ngày 2026-08-30 là chạy đầy đủ
      tại máy** (345 task, có Docker), không phải trên runner.
- [x] Mọi module ở §1 REPOSITORY-STRUCTURE tồn tại — **ĐÓNG 2026-09-06** bằng cách chủ dự án chọn:
      **sửa tài liệu theo thực tế**, KHÔNG dựng module rỗng cho đủ danh sách (5 thư mục code chết
      làm mọi lần build đắt thêm mà không phục vụ ai; `client-android`/`client-ios` còn đi ngược
      [ADR-023](../01-architecture/adr/ADR-023-Desktop-First-Input.md) desktop-first vừa Accepted).
      §1 nay liệt kê đúng cây thật (bổ sung `feature-party`/`feature-trade`/`feature-pvp`/
      `feature-monster`, `quality/architecture-tests`, `third-party/m2/`, `tools/blender/`), và 4
      module dự kiến chuyển xuống **§1.1 "chưa tồn tại"** kèm điều kiện để được sinh ra. Ô này từng
      là ô DUY NHẤT còn trống của Phase 0–3.
- [x] ArchUnit chạy (dù chưa có gì để kiểm) — `quality/architecture-tests`
      (`DependencyRuleTest`, **9 rule**) chạy trong `./gradlew test` và có bước CI riêng đặt tên
      thẳng "Unit test + ArchUnit (ADR-011)".
- [x] `docker compose up` dựng được PostgreSQL + cache — `deploy/docker/docker-compose.yml`
      (postgres:17 có healthcheck `pg_isready`, redis:8 bắt buộc `CACHE_PASSWORD`). Không phải suy
      từ file: hai container `docker-postgres-1` + `docker-cache-1` **đang chạy** lúc rà, và chính
      chúng là DB mà Flyway áp V23 lên trong buổi playtest ADR-037 cùng ngày.
- [x] ~~Secret scanning hoạt động~~ — **KHÔNG khả dụng** (repo private, cần GitHub Advanced Security
      trả phí — phát hiện thật 2026-07-31, không phải thiếu sót). Đã bật thay thế: Dependency graph +
      Dependabot alerts (miễn phí). Xem [OWASP.md §2](../08-security/OWASP.md), [BUILD.md](../../BUILD.md).

---

## Phase 1 — Engine Core

**Mục tiêu**: mở được cửa sổ, nhân vật di chuyển. **Không network.**

| Việc | Nguồn |
|---|---|
| `engine-core`: render loop + Screen/State | [ENGINE.md §3](../03-engine/ENGINE.md) |
| ⚠️ **Fixed timestep + MAX_FRAME_TIME** | [ENGINE.md §4](../03-engine/ENGINE.md) |
| `engine-ecs`: bọc Ashley ở mức **khái niệm** | [ECS.md §10](../03-engine/ECS.md) |
| ⚠️ **World unit = mét** (không pixel) | [RENDERING.md §5](../03-engine/RENDERING.md) |
| Input abstraction (`GameAction`, không phím) | [CLIENT.md §5](../04-client/CLIENT.md) |
| `PlatformAdapter` | [CLIENT.md §11](../04-client/CLIENT.md) |
| ⚠️ **Test font tiếng Việt có dấu** | [RESOURCE.md §7](../04-client/RESOURCE.md) |
| ⚠️ **Test atlas 2048 trên Android thật** | [RESOURCE.md §5](../04-client/RESOURCE.md) |
| Hạ tầng i18n | [UI.md §7](../04-client/UI.md) |
| `tools/asset-pipeline` + validate | [RESOURCE.md §11](../04-client/RESOURCE.md) |
| JMH harness | [BENCHMARK.md](../09-performance/BENCHMARK.md) |

**ADR cần trước Phase 1**: ✅ ADR-018 (tick rate 60Hz — đã chốt) · ⏳ ADR-020 (viewport)

**Exit Criteria:**
- [x] Chạy `client-desktop` → nhân vật hiển thị, di chuyển bằng bàn phím — **nhìn thấy chạy thật
      2026-08-07** (`27a2f12`/`0108857`/`1b4bc61`, xem mục 31) và **chơi thật nhiều vòng** từ
      2026-08-09 tới 2026-08-29 (mục 35, 39). Ô này bị bỏ trống suốt Phase 1-9 vì nhánh render chưa
      bao giờ vẽ ra được nhân vật thật, không phải vì input chưa xong.
- [x] Asset load qua pipeline thật (không file tạm) — `tools:asset-pipeline` xuất
      `assets/exported/` (atlas + `.glb` 3 tier + `asset-manifest.json`) và client đọc thẳng từ đó
      (`27a2f12` chỉ cwd của task `run` sang đúng gốc asset; `fec30ef` tra thư mục model theo
      `GraphicsTier`). ⚠️ **Chỉ đúng cho đường đọc**: `ensureAssetsAvailable(tier)` của ADR-028
      (giao asset theo mạng) **vẫn chưa tồn tại ở đâu trong repo** — xem mục 34.
- [x] Tiếng Việt có dấu hiển thị đúng — font **Be Vietnam Pro** (OFL 1.1) bundle +
      `dev.kage.client.core.render.FontFactory` (FreeType, `client-core`) + test tự động
      `FontFactoryVietnameseGlyphTest` chứng minh `hasGlyph()` PASS cho toàn bộ 6 thanh điệu tiếng
      Việt qua cmap FreeType thật (2026-08-02, xem [RESOURCE.md §7](../04-client/RESOURCE.md)).
      ⚠️ Chỉ chứng minh glyph TỒN TẠI trong font — chưa có màn hình UI thật render lên nền cho
      người xem, việc đó nằm ngoài phạm vi checkbox này.
- [x] Fixed timestep hoạt động (FPS đổi không ảnh hưởng tốc độ nhân vật) — **2026-08-30**:
      `engine/engine-core` từ trước tới nay **không có test nào**, nên ô này chỉ có bằng chứng
      "đọc code". Nay có `KageEngineFixedTimestepTest` (**9 test**, không cần GL: `Gdx.graphics`
      thay bằng `Proxy`). Ba tính chất đo được:
      **(1)** một giây mô phỏng ở 20 FPS ra **59** tick, ở 120 FPS ra **60** — lệch đúng 1 tick vì
      `FIXED_DT` là float 1/60 làm tròn LÊN (0,016666668), tức 0,0001%, không phải lỗi logic;
      **(2)** một frame 2 giây (GC pause) bị `MAX_FRAME_TIME` kẹp còn **≤15 tick** thay vì 120 —
      đúng thứ chống "spiral of death"; **(3)** `alpha` luôn nằm trong [0,1) qua các frame lẻ.
      Kèm hai test cho `setScreen` (show + **resize** màn hình mới, dispose màn cũ) — chốt lại lỗi
      thật 2026-08-08 (màn hình vào bằng chuyển màn có ma trận chiếu suy biến về 0).

⚠️ **Bốn thứ ở Phase 1 cực đắt để sửa sau**: fixed timestep · world unit · input abstraction · tách rendering khỏi `engine-ecs`.

---

## Phase 2 — Network

**Mục tiêu**: client ↔ server nói chuyện được. Chưa có gameplay thật.

| Việc | Nguồn |
|---|---|
| `shared-protocol`: `PacketCodec`, header | [NETWORK.md §2](../05-server/NETWORK.md) |
| `opcode_registry.proto` | [NETWORK.md §4](../05-server/NETWORK.md) |
| `server-gateway`: Netty, framing | [SERVER.md §5](../05-server/SERVER.md) |
| ⚠️ **Ba ranh giới thread** | [SERVER.md §2](../05-server/SERVER.md) |
| Game tick single-thread | [SERVER.md §3](../05-server/SERVER.md) |
| Heartbeat + reconnect (⚠️ **backoff**) | [NETWORK.md §10](../05-server/NETWORK.md) |
| Round-trip protocol test | [TESTING.md §6](../10-quality/TESTING.md) |
| ⚠️ Netty leak detector `paranoid` | [JVM.md §8](../09-performance/JVM.md) |

**ADR cần trước Phase 2**: ADR-022 (gdx-ai) · ADR-023 (mobile input, nếu mobile-first). ADR-016 (scripting — GraalJS) đã Accepted 2026-07-20.

**Exit Criteria:**
- [x] Client kết nối server, ping/pong hoạt động —
      `GatewayServerIntegrationTest.clientShouldReceivePong_overRealSocket` (socket THẬT, không
      `EmbeddedChannel`), cộng `serverShouldAcceptMultipleConnectionsConcurrently`.
- [x] Mất kết nối → reconnect có backoff — `GameConnection.RECONNECT_BACKOFF_SECONDS` +
      `GameConnectionReconnectTest.shouldReconnect_afterServerAppearsOnSamePort` (dựng server giả
      trên đúng cổng cũ rồi chờ client tự nối lại).
- [x] Protocol round-trip test pass — **17 file** dưới `shared-protocol/src/test/.../wire/`, gồm
      `PacketCodecTest` (khung + header) và một file cho mỗi nhóm gói tin
      (`CombatPacketsTest`, `InventoryPacketsTest`, …).

---

## Phase 3 — Character · Map · Movement

**Mục tiêu**: 2 người chơi thấy nhau di chuyển real-time, dữ liệu persist.

| Việc | Nguồn |
|---|---|
| `feature-account` + `feature-character` | [GAMEPLAY.md §2](../05-server/GAMEPLAY.md) |
| `server-database` + migration | [DATABASE.md §10](../05-server/DATABASE.md) |
| 🔴 **`characterId` từ SESSION, không từ packet** | [SECURITY.md §4](../08-security/SECURITY.md) |
| 🔴 **AoI ngay từ đầu** | [SERVER.md §6](../05-server/SERVER.md) |
| `MovementSystem` + physics server-side | [PHYSICS.md §3](../03-engine/PHYSICS.md) |
| Client prediction + interpolation | [CLIENT.md §6–7](../04-client/CLIENT.md) |
| Persistence strategy | [SERVER.md §8](../05-server/SERVER.md) |
| Graceful shutdown | [SERVER.md §10](../05-server/SERVER.md) |
| Rate limiting | [NETWORK.md §13](../05-server/NETWORK.md) |
| **Metric `kage.tick.duration`** | [OBSERVABILITY.md §3.1](../07-infrastructure/OBSERVABILITY.md) |
| Test bảo mật: authz, replay | [TESTING.md §7](../10-quality/TESTING.md) |

**ADR cần trước Phase 3**: ✅ ADR-005 (Redis) · ✅ ADR-013 (Flyway) · ✅ ADR-017 (auth token) · ✅ **ADR-025 (idempotency)** — cả 4 đã Accepted 2026-07-19, Phase 3 đủ điều kiện mở.

**Exit Criteria:**
- [x] 2 client kết nối cùng server, thấy nhau di chuyển —
      `GatewayServerIntegrationTest.twoClientsShouldSeeEachOtherMove_overRealSockets`, và **đã chơi
      thật bằng hai client song song** ngày 2026-08-29 (buổi kiểm giao dịch, mục 41).
- [x] Dữ liệu persist qua restart server —
      `GatewayServerIntegrationTest.shouldSavePosition_whenServerShutsDownWhileClientConnected`
      (đường graceful shutdown, SERVER.md §10) cộng bộ integration test Postgres thật
      (`PostgresCharacterAdapterIntegrationTest` và các adapter khác, Testcontainers). Đăng nhập
      lại sau khi tắt server đã làm nhiều lần trong các buổi playtest từ 2026-08-09.
- [x] Speed hack / teleport bị server từ chối — **chặn bằng THIẾT KẾ GIAO THỨC chứ không bằng bộ
      lọc**: `MoveCommand` chỉ có `dir_x`/`dir_y` (hướng), `JumpCommand` **rỗng hoàn toàn**, không
      gói client→server nào mang toạ độ — nên "teleport" không biểu diễn nổi trên dây, và tốc độ
      là hằng số phía server. Phần còn lại (spam lệnh để đi nhanh hơn) do rate limit chặn:
      `SecurityIntegrationTest.shouldRejectMoveCommands_pastRateLimitThreshold_andIncrementMetric`
      và `shouldDisconnectConnection_whenMoveRateLimitViolatedForSustainedPeriod`.
- [x] Test authz pass (client A không điều khiển được nhân vật B) —
      `SecurityIntegrationTest.shouldRejectSelectCharacter_whenCharacterBelongsToAnotherAccount`
      (cộng cặp `shouldRejectAuthenticatedCommand_beforeAnyLogin` /
      `_afterLoginButBeforeCharacterSelected`); `characterId` lấy từ session, không từ gói tin.
      ⚠️ **Chưa đóng**: chống replay — `moveCommand_sequenceIdIsNotEnforcedAsReplayGuard_documentedGap`
      ghi thẳng rằng `sequenceId` KHÔNG phải bộ chặn phát lại (phát hiện 2026-08-02, vẫn mở).

⚠️ **AoI phải làm ngay Phase 3** — thêm sau khi đã có state sync là refactor lớn.

---

## Phase 4 — Combat · Skill · Monster

**Mục tiêu**: đánh nhau được. 1 hệ phái (Hỏa).

| Việc | Nguồn |
|---|---|
| `CombatSystem` — ⚠️ **không dùng Box2D** | [PHYSICS.md §1](../03-engine/PHYSICS.md) |
| `SkillSystem`: Java = cơ chế, Script = hiệu ứng | [GAMEPLAY.md §4](../05-server/GAMEPLAY.md) |
| Script sandbox | [SCRIPTING.md §4](../06-plugin/SCRIPTING.md) |
| `AISystem`: **FSM đơn giản**, không A*, không BT | [AI.md §3](../03-engine/AI.md) |
| Monster spawn + drop table | [GAMEPLAY.md §5](../05-server/GAMEPLAY.md) |
| Random **chỉ ở server** | [ENGINE.md §10](../03-engine/ENGINE.md) |

**Chặn**: ✅ Đã hết — công thức damage đã chốt ([GAME-DESIGN-DOCUMENT.md §5–6](../13-game-design/GAME-DESIGN-DOCUMENT.md#5-nhân-vật--tiến-trình), 2026-07-19; placeholder, cần playtest cân bằng — không phải lý do trì hoãn mở Phase 4).

**Exit Criteria:**
- [x] Đánh quái, quái chết, rơi đồ — combat+AI (`47c63dd`/`e014c6c`), rơi đồ thật vào túi đồ (mở sớm feature-inventory, 2026-07-20)
- [x] Damage hack / cooldown hack bị từ chối — chặn bằng thiết kế (client chỉ gửi ý định), test `shouldNotBypassAttackCooldown_bySpammingSetTargetCommand`
- [x] Unit test cho CombatSystem, SkillSystem — cả 2 tested qua `WorldFacadeTest` (`SkillSystem` thêm 2026-07-22, skill "fireball" thật)
- [x] Script sandbox: test escape pass — `ScriptSandboxTest.shouldRejectJavaTypeAccess_whenScriptTriesToEscapeSandbox` (GraalJS, [ADR-016](../01-architecture/adr/ADR-016-Scripting-Engine.md)); sandbox đang ở mức CONSTRAINED-equivalent, chưa phải ISOLATED/UNTRUSTED thật (xem ADR-016 "Kiểm chứng thêm khi code")

---

## Phase 5 — Inventory · Equipment · Shop

| Việc | Nguồn |
|---|---|
| `feature-inventory` | [GAMEPLAY.md §7](../05-server/GAMEPLAY.md) |
| ✅ **Transaction + audit log** cho cường hoá (`EnhanceItemUseCase`, 2026-07-23 — logic + DB + protocol/gateway xong end-to-end) | [DATABASE.md §5](../05-server/DATABASE.md) |
| ⚠️ **Chừa chỗ khoá item** (cho trade Phase 7) | [GAMEPLAY.md §7](../05-server/GAMEPLAY.md) |
| ✅ Chỉ số dẫn xuất tính lại khi đổi trang bị (`c731cd5`, 2026-07-22 — chỉ ATK, mở rộng khi có item cần chỉ số khác) | [GAMEPLAY.md §2](../05-server/GAMEPLAY.md) |
| ✅ `feature-shop` (mua/bán, 2026-07-23 — module riêng, logic + DB (V8 `shop_item`) + protocol/gateway xong end-to-end) | [GAMEPLAY.md §7.2](../05-server/GAMEPLAY.md) |
| **Metric `kage.gold.total`** | [OBSERVABILITY.md §3.5](../07-infrastructure/OBSERVABILITY.md) |

**Exit Criteria:**
- [x] Nhặt đồ, mặc đồ, cường hoá, mua bán — nhặt đồ ✅ (`ae69f4f`, Phase 4), mặc đồ ✅ (`b84afe5`/`3b2e84e`, 2026-07-22, kể cả sửa nền tảng ID item ổn định cho `Inventory`); cường hoá ✅ chơi được thật qua socket (2026-07-23, gold ở `character_stat` migration V7, opcode 505-506); mua bán ✅ chơi được thật qua socket (2026-07-23, `shop_item` migration V8, opcode 1000-1005)
- [x] Audit log ghi đủ — ✅ `audit_log` (migration V7) + ghi cho cường hoá VÀ mua/bán (`SHOP_BUY`/`SHOP_SELL`); trade sẽ ghi khi tới lượt (Phase 7)
- [x] Test: không duplicate item qua transaction fail — ✅ cường hoá có test Postgres thật (trừ gold + đổi cấp + audit cùng transaction, không đủ gold thì không đổi gì); mua/bán có test Postgres thật (rollback gold khi túi đầy lúc mua, khoá dòng `FOR UPDATE` khi bán)

---

## Phase 6 — Quest · NPC · Dialogue → **MVP**

| Việc | Nguồn |
|---|---|
| ✅ NPC tĩnh lát cắt 1 (2026-07-23 — hiện qua AoI, chưa dialogue) | [GAMEPLAY.md §9.1](../05-server/GAMEPLAY.md) |
| ✅ Dialogue NPC lát cắt 2 (2026-07-23 — `feature-dialogue` mới, node/nhánh + mở shop qua dialogue) | [GAMEPLAY.md §9.2](../05-server/GAMEPLAY.md) |
| ✅ `feature-quest` + script lát cắt 3 (2026-07-23 — nhận qua dialogue → theo dõi → hoàn thành → nhận thưởng, đầy đủ) | [GAMEPLAY.md §9.3](../05-server/GAMEPLAY.md) |
| ✅ Validate mọi script lúc khởi động (2026-07-23 — `ScriptSandbox.validateSyntax` (parse-only) + `ScriptValidator` mới ở `engine-scripting`; `GatewayBootstrap` gom script_ref quest+dialogue từ DB (`QuestTemplatePort`/`NpcTemplatePort` thêm `listAll*ScriptRefs`) + hằng số skill (`WorldFacade.listSkillScriptResources`), validate TRƯỚC khi world tick/server nhận kết nối) | [SCRIPTING.md §8](../06-plugin/SCRIPTING.md) |
| ✅ `feature-friend` lát cắt 4 (2026-07-23 — gửi/chấp nhận/từ chối/huỷ kết bạn theo tên + trạng thái online qua `ConnectedCharacterRegistry`, chơi được thật qua socket) | [GAMEPLAY.md §11](../05-server/GAMEPLAY.md) |
| ✅ `feature-mail` lát cắt 5 (2026-07-23 — gửi/xem/nhận/xoá thư, gold+item đính kèm nhận NGUYÊN TỬ trong 1 transaction chống duplicate, chơi được thật qua socket + Postgres thật) | [GAMEPLAY.md §11](../05-server/GAMEPLAY.md) |
| ✅ `feature-chat` lát cắt 6 (2026-07-23 — kênh Thế giới/Riêng tư/Khu vực chơi được thật qua socket; Khu vực tích hợp world tick thật qua `AreaOfInterestTracker`; Bang hội/Party dời Phase 7 cùng Guild/Party thật) | [GAMEPLAY.md §10](../05-server/GAMEPLAY.md) |

**Exit Criteria — đây là MVP:**
- [x] Chơi được: tạo nhân vật → nhận quest → đánh quái → nhặt đồ → cường hoá → hoàn thành quest —
      verify bằng `MvpLoopIntegrationTest` (server-database, 2026-07-23): xâu chuỗi CÙNG 1 nhân vật
      qua toàn bộ UseCase thật (Postgres thật, không mock) theo ĐÚNG thứ tự người chơi thật trải
      qua. Combat/AI/loot qua Netty thật đã verify riêng ở `WorldFacadeTest`/
      `GatewayServerIntegrationTest` — test này ghép nối các mảnh đã chứng minh riêng lẻ thành 1
      hành trình liền mạch, không lặp lại đã test.
- [x] Toàn bộ Definition of Done (Điều 34) — compile/test/checkstyle xanh xuyên suốt (bao gồm build
      đầy đủ `./gradlew build` + ArchUnit); JavaDoc cho API công khai mới; tài liệu (ROADMAP/
      GAMEPLAY/SCRIPTING/GLOSSARY) đã cập nhật khớp code; không vi phạm ranh giới kiến trúc
      (ArchUnit xanh); không thêm dependency mới ngoài TECHNOLOGY-STACK.md.

---

## Phase 7 — Guild · Party · Trade

> Mail đã làm ở Phase 6 lát cắt 5 (feature-mail) — tiêu đề Phase này KHÔNG còn Mail (sửa
> 2026-07-23, khớp lại với bảng công việc bên dưới chưa từng liệt Mail).

| Việc | Ghi chú |
|---|---|
| ✅ `feature-guild` lát cắt 1 (2026-07-24 — thành viên: tạo/xin gia nhập/leader duyệt-từ chối/rời/kick/giải tán + kênh chat Bang hội, chơi được thật qua socket + Postgres thật) | Kho bang, nhiệm vụ bang dời lát cắt sau |
| ✅ `feature-party` lát cắt 2 (2026-07-24 — thành viên: tạo/mời/chấp nhận-từ chối/rời (tự động chuyển leader)/kick + kênh chat Party, sức chứa tối đa 5, chơi được thật qua socket + Postgres thật) | Chia EXP dời tới khi Combat/Character có cơ chế cấp EXP từ giết quái thật (chưa tồn tại, phát hiện lúc lát cắt này) |
| ✅ `feature-trade` lát cắt 3 (2026-07-24 — yêu cầu trade/chấp nhận/huỷ/đặt lời chào (gold+item, cho phép tách stack)/xác nhận 2 chiều, chơi được thật qua socket + Postgres thật — verify cả rollback gold+item khi item bị bán mất giữa chừng) | [GAMEPLAY.md §8](../05-server/GAMEPLAY.md) — **hệ thống nguy hiểm nhất**: transaction + audit + 2-phase confirm + xác thực lại lúc thi hành (thay khoá lúc chào) + idempotency |

**Quyết định lát cắt 1 (2026-07-24, cùng chủ dự án qua `AskUserQuestion` trước khi code)**: thứ tự
Guild → Party → Trade (Trade nguy hiểm nhất, hoãn tới khi có kinh nghiệm 2-phase confirm từ hệ
thống khác); luồng gia nhập = **xin gia nhập + leader duyệt** (khác feature-friend là mời trực
tiếp); phạm vi lát cắt 1 = chỉ thành viên + kênh chat Bang hội, kho bang/nhiệm vụ bang dời lát cắt
sau (cùng cách Quest tách khỏi Dialogue ở Phase 6). Chi tiết đầy đủ: feature-guild/CLAUDE.md.

**Quyết định lát cắt 2 (2026-07-24, cùng chủ dự án qua `AskUserQuestion` trước khi code)**: luồng
gia nhập party = **mời + chấp nhận** (NGƯỢC hướng guild, giống feature-friend — party rủ người
quen); phạm vi = chỉ thành viên + kênh chat Party, **KHÔNG chia EXP** — phát hiện thật lúc code:
giết quái solo hiện KHÔNG cấp EXP trực tiếp (chỉ có qua `ClaimQuestRewardUseCase`), không có công
thức EXP/quái hay level-up nào tồn tại, nên "chia EXP party" cần thiết kế Combat/Character
progression trước, ngoài phạm vi lát cắt này; leader rời **tự động thăng** thành viên vào sớm nhất
(khác guild phải giải tán). Chi tiết đầy đủ: feature-party/CLAUDE.md.

⚠️ **Không mở Trade nếu ADR-025 (idempotency) chưa Accepted.** (✅ Đã Accepted 2026-07-19 — điều kiện thoả, nhưng vẫn phải triển khai `idempotencyKey` thật trước khi Trade thật sự mở, không phải chỉ cần ADR tồn tại.)

**Quyết định lát cắt 3 (2026-07-24, cùng chủ dự án qua `AskUserQuestion` trước khi code)**: (1)
"khoá item khi đang trade" đạt được bằng **xác thực lại toàn bộ ngay lúc thi hành** (khoá dòng FOR
UPDATE + kiểm sở hữu/số lượng/gold trong `ConfirmTradeUseCase`), KHÔNG thêm cột `locked` hay sửa
feature-inventory/feature-shop — cùng đảm bảo không bao giờ duplicate với blast radius nhỏ hơn
nhiều; (2) **cho phép tách stack** (chào 1 phần số lượng của 1 dòng inventory_item); (3) tranh thủ
vá luôn lỗ hổng `idempotencyKey` còn thiếu ở `BuyItemCommand`/`SellItemCommand`/`ClaimMailCommand`
(phát hiện khi rà ADR-025 trước lúc code Trade) trong cùng đợt. **Phase 7 đóng — cả 3 hệ thống
(Guild/Party/Trade) đã hoàn thành.** Chi tiết đầy đủ: feature-trade/CLAUDE.md.

---

## Phase 8 — Dungeon · Boss · PvP

| Việc | Nguồn |
|---|---|
| ✅ Dungeon instance lát cắt 1 (2026-07-25 — hạ tầng: party cùng vào 1 World riêng qua `EnterDungeonCommand`/`ExitDungeonCommand`, tick độc lập world chính, cách ly lỗi tick, chơi được thật qua socket) | [ADR-026](../01-architecture/adr/ADR-026-Dungeon-Instance-Model.md) |
| ✅ Boss AI lát cắt 2 (2026-07-25 — script GraalJS quyết định pha/chiêu (`BossAiSystem`), tái dùng `ScriptSandbox` từ Phase 4, boss tự spawn trong dungeon, loot thật qua `drop_table` có sẵn, chơi được thật qua socket) | [AI.md §4](../03-engine/AI.md) |
| ✅ 4 hệ phái còn lại lát cắt 3 (2026-07-25 — Thủy/Phong/Lôi/Thổ chơi được thật qua socket: khắc chế nguyên tố, status-effect framework mới (slow/stun/shield/buff/debuff), 16 skill script mới + passive, chọn hệ Lv10) | [GDD §4](../13-game-design/GAME-DESIGN-DOCUMENT.md) |
| ✅ Arena 1v1 + MMR cơ bản lát cắt 4 (2026-07-25 — thách đấu theo tên (`ChallengeDuelCommand`, giống Trade) → World riêng 2 người (ADR-026) → hạ gục/bỏ cuộc chốt Elo (K=32, module mới `feature-pvp`), chơi được thật qua socket) | [GDD §6](../13-game-design/GAME-DESIGN-DOCUMENT.md) |
| Battlefield 5v5 + Chiếm Lãnh Địa (guild war) | [GDD §6](../13-game-design/GAME-DESIGN-DOCUMENT.md) |

**ADR cần trước Phase 8**: ✅ ADR-026 Accepted 2026-07-24 — Phase 8 đủ điều kiện mở.

**Quyết định lát cắt 1 (2026-07-24, cùng chủ dự án qua `AskUserQuestion` trước khi code)**: bắt đầu
Phase 8 bằng hạ tầng dungeon instance (không phải Boss AI hay 4 hệ phái) vì dọn đường cho mọi lát
cắt sau. Phạm vi: map trống (không quái/NPC), chỉ chứng minh cơ chế tách World hoạt động thật —
CHƯA có Boss AI hay loot đặc biệt. **Không tạo module `feature-dungeon` mới** ở lát cắt này — chưa
có dữ liệu nghiệp vụ nào cần persist (không cooldown, không reward, không dungeon template DB);
logic "cả party cùng vào" đặt thẳng ở `server-gateway` (`DungeonInstanceRegistry`, compose
`PartyFacade`/`CharacterFacade` sẵn có, giống pattern `SocialFacades`) thay vì Clean Architecture
đầy đủ cho một thứ chưa có business rule nào. Khi lát cắt sau cần dữ liệu thật (cooldown, reward,
dungeon template) mới tách `feature-dungeon` theo đúng template. Chi tiết kỹ thuật: `WorldFacade`
thêm overload constructor `spawnOverworldContent=false` (dungeon world khởi động rỗng, tái dùng
combat/skill/AI/AoI có sẵn) + bọc tick loop trong try/catch (cách ly lỗi ADR-026 yêu cầu, đồng thời
vá 1 bug tiềm ẩn có sẵn ở world chính: exception không bắt trước đây sẽ âm thầm giết tick thread).
`DungeonOutboundSink` đảm bảo toạ độ dungeon không ghi đè vị trí thật lúc `CharacterPositionPersister`
lưu định kỳ (không cần cờ "đang ở dungeon" — vị trí tự "đóng băng" vì world chính hết gọi
`onMoveAccepted` cho nhân vật đã rời).

**Quyết định lát cắt 2 (2026-07-25, cùng chủ dự án qua `AskUserQuestion` trước khi code)**: chọn
Boss AI làm lát cắt kế tiếp (thay vì 4 hệ phái còn lại hay PvP arena) để dungeon thành tính năng
chơi được đầy đủ, không chỉ hạ tầng. Trước khi code, phát hiện + sửa mâu thuẫn tài liệu: `AI.md §4`
còn ghi "chưa chốt Java hay Script cho Boss AI" dù bảng Technical Decisions #9 đã Accepted "Script
(GraalJS)" — xác nhận lại qua `AskUserQuestion`, chốt **Script**, sửa `AI.md §4` khớp bảng (commit
`bf10543`). Phạm vi thu hẹp có chủ đích: chỉ 2 pha đặc biệt (`heavy_strike` <70% HP, `enrage` <30%
HP, mỗi pha chỉ dùng 1 script quyết định action+damage) — **KHÔNG làm "summon minions"** (AI.md §4
liệt ở Phase 2, nhưng spawn entity động + AI riêng + dọn dẹp khi boss chết là hạng mục lớn riêng,
để dành lát cắt sau, cùng tinh thần Party hoãn chia EXP/Dungeon lát cắt 1 hoãn Boss).

Chi tiết kỹ thuật: `BossAiSystem` (mới) mô hình lặp lại chính xác `SkillSystem` — script GraalJS
(`sample_boss.js`) quyết định hành động dựa trên %HP/cooldown, Java lo cơ chế (cooldown, miss/crit/
RES qua `CombatConstants.resolveSkillAttack` có sẵn). Boss vẫn có đủ `AiStateComponent`/
`TargetComponent` như quái thường — `AiSystem`/`CombatSystem` KHÔNG bị sửa, `BossAiSystem` chỉ cộng
thêm quyết định "dùng chiêu đặc biệt ngay bây giờ không" khi boss đang `AiState.ATTACK`. Kết quả
chiêu đặc biệt TÁI DÙNG nguyên `SkillResultEvent`/`onSkillResult` đã có (KHÔNG đổi protocol) —
`skillCode` = `"heavy_strike"`/`"enrage"`. Loot boss tái dùng nguyên hạ tầng `drop_table`/
`GrantLootUseCase` có từ Phase 4 (chỉ thêm 1 migration seed dữ liệu, không code Java mới).

**2 bug thật phát hiện khi viết test E2E qua socket thật** (không phải đoán trước):
1. `handleMonsterDeath` gọi `spawnTrainingDummy()` vô điều kiện sau MỌI cái chết quái — boss chết
   trong dungeon sẽ vô tình spawn nhầm training dummy. Sửa: bọc dòng respawn trong
   `if (spawnOverworldContent)`.
2. **Lỗ hổng AoI có sẵn từ Phase 4, không phải riêng Boss AI**: `observersOf(targetEntityId)` chỉ
   tìm được nhân vật KHÁC đang thấy mục tiêu qua `visibleTo` (khớp cặp NHÂN VẬT-NHÂN VẬT) — nhân
   vật chơi SOLO không có ai "vouch" cho mình, nên khi QUÁI/boss đánh trả, `AttackResultEvent`/
   `SkillResultEvent` bị rơi mất hoàn toàn, không bao giờ tới client. Không bị bắt ở
   `WorldFacadeTest` (dùng sink ghi thẳng không lọc theo "có phải client thật") — chỉ lộ ra khi
   viết test socket thật cho boss đấu solo. Sửa: `handleCombatResults`/`handleSkillResults`/
   `handleBossResults` đều thêm `targetEntityId` (nếu dương, tức nhân vật) vào observers — mục
   tiêu luôn thấy đòn nhắm vào mình, cùng nguyên tắc đã áp dụng cho attacker.

**Quyết định lát cắt 3 (2026-07-25, cùng chủ dự án qua `AskUserQuestion` trước khi code, 4 vòng)**:
(1) đúng theo GDD §4 là **Thủy, Phong, Lôi, Thổ** — không phải Kim/Mộc (đã xác nhận `Faction.java`
ghi chú sẵn đúng 4 tên này); (2) mỗi hệ mới làm **đầy đủ theo GDD §5** (3 active + 2 passive + 1
Tuyệt Kỹ/hệ) — Hỏa GIỮ NGUYÊN 1 active hiện có, hoàn thiện nốt là việc lát cắt sau (cùng tinh thần
Party hoãn chia EXP); (3) **hệ số khắc chế nguyên tố** (GDD §6, ×1.15/×0.85, hardcode 1.0 từ Phase 4)
làm luôn; (4) 3 skill active/hệ cần **cơ chế thật** (hồi máu/khống chế/khiên) — không chỉ đổi công
thức damage-nuke, đây là quyết định lớn nhất: cần xây MỚI 1 hệ thống status-effect (chưa từng có
trong engine); (5) chọn hệ **đúng GDD** — `SelectFactionUseCase` gate `level>=10`, dùng field
`level` có sẵn ở `Character` dù chưa có cơ chế cộng EXP-giết-quái (người chơi cày Lv10 qua quest).

Chi tiết kỹ thuật (6 phần, làm tuần tự vì phần sau phụ thuộc phần trước):
1. **Faction enum + protocol**: `Faction.java` thêm `THUY/PHONG/LOI/THO`; `character.proto` dùng
   đúng số enum đã giữ chỗ sẵn (2-5) từ trước.
2. **Element + khắc chế**: `server-world` tự có bản sao `Element` (không import feature-character,
   đúng ranh giới module — cùng pattern `CharacterBaseStats`); `ElementCounterTable` mã hoá chu
   trình khắc Hỏa→Phong→Thổ→Thủy→Hỏa, Lôi/NONE trung lập tuyệt đối; nhân vào bước cuối pipeline
   damage (`CombatConstants.resolveAttack`/`resolveSkillAttack`).
3. **Status-effect framework (mới)**: `StatusEffectComponent` (mảng 4 slot CỐ ĐỊNH, tránh boxing
   `Map<String,Float>` trong game loop — /CLAUDE.md §6) + `StatusEffectType`
   (MOVE_SPEED_MOD/STUN/SHIELD/DEF_MOD/RES_MOD/CRIT_MOD) + `StatusEffectSystem` (đếm ngược, pattern
   y hệt `SkillSystem.processEntity`) + helper `StatusEffects` (đọc/ghi tập trung, dùng chung
   `CombatSystem`/`SkillSystem`/`AiSystem`/`WorldFacade.handleMoveIntent`). SLOW/buff tốc độ áp
   dụng CẢ cho quái đuổi theo (`AiSystem.moveToward`), không chỉ nhân vật gửi `MoveCommand`.
4. **Skill registry**: thay `SkillConstants` hardcode 1 skill bằng `SkillRegistry`
   (`Map<String,SkillDefinition>`) + `SkillTargetKind` (ENEMY/SELF/ALLY) + `SkillCooldownComponent`
   đổi từ 1 `float` sang `float[4]` (index = slot trong hệ, KHÔNG dùng Map tránh boxing) +
   `CharacterLevelComponent` mới (gate Tuyệt Kỹ Lv50). Script đổi hợp đồng trả về: KHÔNG còn scalar
   đơn thuần, phải trả object `{effect, value, stat?, duration?, armor_pierce?}` — tái dùng đúng
   pattern `BossAiSystem` đã làm cho `sample_boss.js` (Value.getMember qua extractor
   `ScriptSandbox.eval`). `fireball.js` (Hỏa, Phase 4) cũng đổi theo hợp đồng mới cho nhất quán.
5. **16 skill script mới** (4 hệ × [3 active + 1 Tuyệt Kỹ], passive KHÔNG dùng script — chỉ số cộng
   thẳng, Điều 27 chỉ bắt buộc script cho nội dung skill) + `combat.proto` thêm `EffectKind` enum
   (DAMAGE/HEAL/SHIELD/BUFF/DEBUFF) vào `SkillResultEvent` (additive, không đổi field cũ).
6. **Chọn hệ Lv10**: `SelectFactionUseCase` (feature-character) + `CharacterPort.updateFaction` (1
   UPDATE đơn, không cần Transaction — không có ghi phụ nào cùng nguyên tử ở lát cắt này) +
   `SelectFactionRequest/Response` (opcode 206/207, characterId từ session như `UseSkillCommand`,
   KHÔNG có field character_id trong packet).

**Scope cut có chủ đích** (ghi lại để không lẫn với "quên làm"): (a) ~~Hỏa vẫn chỉ có 1 skill
active~~ **ĐÃ XONG 2026-08-02**: thêm `hoa_tia_lua`/`hoa_bung_no` (active 2-3) + `hoa_diem_nguc`
(Tuyệt Kỹ) + 2 passive (`skillDmg`+6, `critPercent`+0.05) — Hỏa giờ đủ 3 active + 1 Tuyệt Kỹ + 2
passive như 4 hệ kia (`SkillRegistry`/`CombatConstants`); (b) "Đá Chuyển Hệ" sau lần đổi hệ
miễn phí đầu tiên (GDD §4) CHƯA enforce — `SelectFactionUseCase` chỉ gate `level>=10`, chưa đếm số
lần đổi/tốn item (cần thêm cột DB + item ở feature-inventory, để dành lát cắt sau).

**1 bug thật phát hiện khi viết test E2E** (không phải đoán trước): `WorldFacade.handleUseSkill` có
guard cũ từ Phase 4 (`targetEntityId == characterId` → bỏ qua) chặn NHẦM mọi skill SELF/ALLY mới
(heal/buff/khiên tự thân) — guard này đúng cho `fireball` (chỉ có ENEMY-target) nhưng sai hoàn toàn
với thiết kế đa-loại-mục-tiêu mới. Phát hiện qua `shouldHealAlly_whenHealSkillCast`/
`shouldAbsorbDamageWithShield_beforeReducingHp` treo timeout dù logic `SkillSystem.tryCast` đúng.
Sửa: bỏ guard tự nhắm ở `WorldFacade`, chuyển validate vào `SkillSystem.tryCast` (chặn tự nhắm CHỈ
với `SkillTargetKind.ENEMY`, cho phép SELF/ALLY tự nhắm có chủ đích).

**Quyết định lát cắt 4 (2026-07-25, cùng chủ dự án qua `AskUserQuestion` trước khi code)**: phạm vi
PvP là **Arena 1v1 + MMR/rating cơ bản** (không phải Battlefield 5v5 hay Chiếm Lãnh Địa — 2 hạng
mục còn lại của GDD §6 để dành lát cắt sau, cùng tinh thần các lát cắt trước hoãn phần lớn/phức
tạp). Đóng luôn Phase 8 — cả 4 hạng mục lát cắt đã xong, chỉ Battlefield/Chiếm Lãnh Địa (post-MVP,
GDD §11) còn lại, không mở phase mới cho 2 hạng mục đó ngay.

Chi tiết kỹ thuật:
1. **Module mới `feature-pvp`** (Clean Architecture đầy đủ) CHỈ lo phần cần persist (rating/wins/
   losses, bảng `pvp_rating` mới, V18) — vòng đời trận đấu (thách đấu, tạo World riêng) KHÔNG có dữ
   liệu nghiệp vụ cần lưu nên đặt ở `server-gateway` (`ArenaInstanceRegistry`), Y HỆT lý do
   `DungeonInstanceRegistry` không phải `feature-dungeon` (ADR-026, lát cắt 1). `EloRating` (K=32
   cố định, KHÔNG season/decay) là hàm thuần Java trong `domain/`, test được không cần Port/DB.
2. **Thách đấu theo TÊN, request-based giống Trade** (không phải giống Party) — `ChallengeDuelCommand`
   đẩy `DuelChallengeReceivedEvent` real-time cho target (không polling như Party invite), vì đấu
   là hành động tương tác thời gian thực giống trade, không phải lời mời có thể trả lời sau.
3. **Cách ly PvP hoàn toàn bằng World instance riêng** (ADR-026, giống dungeon) — CHỈ 2 người trong
   instance đó nên "chỉ đánh được đối thủ" là hệ quả TỰ NHIÊN, KHÔNG sửa `CombatSystem`/`SkillSystem`
   thêm cờ team/faction nào. Do đó cũng KHÔNG sửa được 1 lỗ hổng CŨ (có từ Phase 4, không phải do
   lát cắt này): world CHÍNH về lý thuyết vẫn cho phép 1 nhân vật `SetTargetCommand`/`UseSkillCommand`
   nhắm ID nhân vật khác (không chặn theo dấu, chỉ chặn tự nhắm chính mình) —
   `WorldFacadeTest.shouldDealMoreDamage_whenAttackerElementCountersDefenderElement` dựa ĐÚNG vào
   hành vi này để test khắc chế nguyên tố bằng 3 nhân vật thay vì quái. Không có UI nào ở client
   hiện tại phơi bày đường này ở world chính — ghi nhận là giới hạn đã biết (feature-pvp/CLAUDE.md),
   không phải phạm vi lát cắt này.
4. **`WorldOutboundSink` thêm `onCharacterDefeated`** (world CHÍNH vẫn chưa có cơ chế tử vong nhân
   vật, cùng hiện trạng Boss AI lát cắt 2 — no-op ở `ConnectedCharacterRegistry`) — chỉ
   `ArenaOutboundSink` (mới) override để chốt kết quả trận đấu thật.
5. **Forfeit (`ExitDuelCommand`) và mất kết nối giữa trận đều tính THUA thật** (mất điểm Elo) —
   khác `ExitDungeonCommand` (không mất gì). `ArenaInstanceRegistry.settleDuel` gọi
   `WorldFacade.stop()` (join tick thread) + ghi DB — PHẢI dispatch sang Virtual Thread riêng khi
   gọi từ tick thread của CHÍNH duel instance (tránh self-join deadlock), khác khi gọi từ
   `authExecutor` (forfeit chủ động, an toàn gọi thẳng).
6. **Opcode**: dải mới **1600-1699 "Arena"** (Challenge/Accept/Decline/Exit + 4 Event) + dùng nốt
   dải **1200-1299 "Ranking"** đã dự trữ từ trước cho `GetPvpRatingRequest/Response` (rating cá
   nhân — bảng xếp hạng toàn server vẫn chưa làm, để dành lát cắt PvP ranking/season sau nếu có).

---

## Phase 9 — Optimization

⚠️ **Đây là Phase đầu tiên được phép tối ưu** (Điều 30 — đo trước).

| Việc | Nguồn |
|---|---|
| 🔴 **Load test + Soak test ≥ 2h** | [BENCHMARK.md §5](../09-performance/BENCHMARK.md) |
| Đo & cập nhật **toàn bộ ngân sách** | [PERFORMANCE.md §2](../09-performance/PERFORMANCE.md) |
| 🔴 **Đánh giá lại ADR-006 (Ashley)** | [ECS.md §9](../03-engine/ECS.md) |
| ✅ **ADR-019 (GC) — Accepted** (2026-07-28, soak test thật 2h14, 80 client) | [JVM.md §2](../09-performance/JVM.md), [ADR-019](../01-architecture/adr/ADR-019-GC-Choice.md) |
| Prometheus + Grafana + Alerts + Tracing | [OBSERVABILITY.md §10](../07-infrastructure/OBSERVABILITY.md) |
| ✅ ADR-015, ADR-014 đã chốt (2026-07-26, trước khi mở lát cắt code) | [ADR-015](../01-architecture/adr/ADR-015-Micrometer-vs-OpenTelemetry.md), [ADR-014](../01-architecture/adr/ADR-014-Kubernetes-vs-Docker-Compose.md) |

**Exit Criteria:**
- [x] Tick p99 ≤ ngân sách dưới tải mục tiêu — N=1000 0.82-1.57ms, N=2000 0.98-3.15ms (1 đỉnh đơn lẻ
      28.6ms), đều dưới ngân sách 16.6ms, 0 lỗi client ([BENCHMARK.md §5.3](../09-performance/BENCHMARK.md),
      lát cắt 7, 2026-07-28)
- [x] **Không leak sau soak 2h** (heap sau major GC không tăng đều) — soak 2h14/N=80, heap Live phẳng
      39MB suốt 34 chu kỳ GC ([ADR-019](../01-architecture/adr/ADR-019-GC-Choice.md), 2026-07-28)
- [x] Observability đầy đủ (Điều 44) — Prometheus+Alertmanager+Grafana (5 dashboard)+Tracing chạy qua
      `docker compose up` ([OBSERVABILITY.md](../07-infrastructure/OBSERVABILITY.md) §10, lát cắt 9+10, 2026-07-28)

⚠️ **Chốt 2026-07-30 kèm 2 caveat CHƯA chặn đóng phase** (đã hỏi chủ dự án, quyết định đóng dù còn 2
việc mở, vì Exit Criteria chỉ đòi "dưới tải mục tiêu đã đo" chứ không đòi công thức ngoại suy an toàn
mọi N):
1. Benchmark đo lại sau khi đổi sang shared tick pool (§5.1 v2, 2026-07-29) phát hiện **CPU không
   tuyến tính theo N** (độ dốc CPU/người tăng dần 100→500→1000) — tick p99 vẫn ổn trong vùng ĐÃ đo
   (N≤2000), nhưng KHÔNG có công thức ngoại suy đáng tin cho N lớn hơn (Điều 30). Việc cần làm nếu
   quy mô người chơi thật vượt 2000: đo lại thật, không ngoại suy.
2. Off-heap (~500-650KB/kết nối, NMT) chỉ đo 1 lần chụp ngắn N=0→1000, CHƯA từng chạy trong 1 buổi
   soak dài để xác nhận tự giải phóng đúng khi client ngắt kết nối (leak thật hay không) — soak 2h14
   gốc (ADR-019) chỉ đo Java heap, chưa biết off-heap lúc đó vì phát hiện này đến sau. Việc cần làm:
   1 buổi soak dài có bật NMT nếu cần xác nhận chắc chắn hơn trước khi vận hành production thật lâu.

⚠️ **Phase 9 không đóng nếu chưa có observability** — Điều 44: không có Observability thì Production không hoàn chỉnh.

**Quyết định lát cắt 1 (2026-07-26)**: sau khi chốt ADR-014/ADR-015 (thảo luận trực tiếp, không cần
số liệu), lát cắt code đầu tiên của Phase 9 là **wiring Micrometer + `kage.tick.duration`** — đây là
metric ROADMAP/OBSERVABILITY.md §3.1 tự gọi là "quan trọng nhất, không có nó thì không biết server
có khoẻ không", và đã bị bỏ sót từ Phase 3 (liệt kê trong bảng "Việc" nhưng chưa từng code). Chọn
mục này trước load/soak test vì load test mà không đo được gì thì vô nghĩa — cần công cụ quan sát
trước khi tạo tải.

Chi tiết kỹ thuật: `TickMetricsPort` (mới, `server-world`) — Port giống `WorldOutboundSink`, giữ
đúng ranh giới "gameplay không import Micrometer trực tiếp" (server/CLAUDE.md); implementation thật
`MicrometerTickMetricsPort` + `MetricsHttpServer` (`GET /metrics`, cổng 9404, dùng
`com.sun.net.httpserver` có sẵn trong JDK thay vì dựng pipeline Netty HTTP riêng chỉ cho 1 endpoint
scrape — Điều 9) chỉ tồn tại ở `server-gateway` (ArchUnit rule mới
`chi_server_gateway_dung_micrometer` chặn nơi khác import `io.micrometer.*`, cùng pattern
Netty/JDBC/Redis). Tag `world` là nhãn CỐ ĐỊNH (`"main"`), KHÔNG phải ID instance
(OBSERVABILITY.md §2 "không dùng ID làm tag — cardinality nổ").

**Scope cut có chủ đích**: World chính có metric thật; **dungeon/arena instance CHƯA đo** (vẫn dùng
`TickMetricsPort.NO_OP` qua overload constructor cũ của `WorldFacade`) — capacity planning ưu tiên
World chính trước (nơi luôn có người chơi), dungeon/arena là instance ngắn hạn theo party/trận đấu,
để dành lát cắt sau nếu cần. Cũng CHƯA làm: `kage.gold.total`, `kage.tick.overrun.count`,
`kage.players.online`, Grafana dashboard, Alerts, Tracing (OpenTelemetry) — đây là lát cắt đầu tiên,
chỉ 1 metric xuyên suốt từ code tới scrape endpoint thật (verify bằng `MetricsHttpServerTest`, HTTP
thật, không mock).

**Quyết định lát cắt 2 (2026-07-26)**: chọn load/soak test tooling trước Grafana/Alerts/Tracing —
đo trước, làm đẹp sau (Điều 30). Module mới **`tools:load-test`** (BENCHMARK.md §5) — quyết định KHÔNG
dùng tool ngoài (k6/Gatling/JMeter): protocol game là TCP + protobuf custom (KHÔNG HTTP), nên mọi
tool chuẩn cần viết plugin/protocol extension riêng — chi phí đó cao hơn tự viết 1 client Java tái
dùng thẳng `shared-protocol` (đã có sẵn từ `GatewayServerIntegrationTest`, chỉ khác chạy N client
song song bằng Virtual Thread thay vì 1 client trong 1 test case), đúng Điều 9 (không phát minh lại
những gì không cần, nhưng cũng không kéo dependency ngoài chỉ để né viết ~500 dòng Java). Client giả
lập nói ĐÚNG giao thức dây (không gọi tắt API nội bộ) — move+combat(auto-target)+chat+ping thật, đo
packet latency phía client + `kage.tick.duration` phía server (scrape lát cắt 1) trong CÙNG 1 dòng
report định kỳ. Verify thật: chạy server thật (Postgres/Redis Docker tạm, cổng khác cổng dev thường
dùng để không đụng môi trường khác đang chạy trên máy) + 10 client 20 giây — 0 lỗi, latency/tick đều
lên số thật (không phải chạy `./gradlew test` giả lập).

**Scope cut có chủ đích**: KHÔNG đo GC pause/heap/CPU/RAM trong tool này — JFR (bật sẵn từ Phase 0,
JVM.md) đã lo phần đó, dùng song song lúc chạy soak thật, không cần trùng lặp trong `tools:load-test`.
KHÔNG có DB pool pending metric (HikariCP chưa expose qua Micrometer — lát cắt sau nếu cần). Tool chỉ
nhắm vào World chính qua map/dummy có sẵn — CHƯA giả lập dungeon/PvP/trade trong kịch bản tải.

**Lát cắt 5 (2026-07-26) — Capacity planning theo yêu cầu chủ dự án**: thêm bộ đếm băng thông thật
(`bytesSent`/`bytesReceived`) vào `SimulatedPlayer`/`LoadTestRunner` (đo tại tầng ứng dụng, tách
biệt hoàn toàn traffic Postgres/Redis vì không chung socket). Chạy 2 lần: (1) soak 2h14 lát cắt 3
cho số RAM/CPU đáng tin (2 điểm ổn định lâu: 100 và 500 client); (2) 1 lần đo nhanh 100→500 (1
phút/mức, sau pre-warm 4 phút) cho tick time + băng thông ở cả 5 mức — phát hiện: 1 phút/mức KHÔNG
đủ để ZGC hết pha Warmup (heap phải leo qua nhiều ngưỡng %capacity trước khi vào Proactive, mất
~15 phút bất kể tải), nên RAM/CPU ở lần đo nhanh này KHÔNG dùng — chỉ tick time + băng thông (ít bị
ảnh hưởng bởi GC warmup) mới đáng tin từ lần chạy đó. Kết quả tổng hợp thành công thức tại
[BENCHMARK.md §5.1](../09-performance/BENCHMARK.md). Thử đo Disk I/O qua
`Win32_PerfFormattedData_PerfProc_Process` (CIM) — THẤT BẠI, PHÁT HIỆN THẬT: counter "PerSecond" của
WMI/CIM cần polling LIÊN TỤC cùng 1 session để tính delta đúng, gọi `Get-CimInstance` rời rạc mỗi
lần cho số liệu chủ yếu 0 kèm đỉnh giả — không dùng được, để lại làm việc cần sửa nếu cần Disk I/O
thật sau này.

**Quyết định lát cắt 3 (2026-07-26)**: chạy soak test thật 2h14 (80 client đồng thời, `server-gateway`
thật + Postgres/Redis thật qua Docker, `-XX:+UseZGC` + JFR + GC log bật đủ) để lấy số liệu chốt
ADR-019. Kết quả: **ZGC STW pause tối đa đo được 0.052ms**, heap Live giữ nguyên 39M suốt 34 chu kỳ
GC liên tiếp (~1h33 phút, không leak), tick p99 luôn dưới ngân sách 16.6ms (dao động 2-10ms tuỳ
thời điểm). Viết [ADR-019](../01-architecture/adr/ADR-019-GC-Choice.md) — Status **Accepted** (2026-07-28,
chủ dự án xác nhận; AI không tự chốt, adr/README.md quy tắc #5).

**2 bug thật phát hiện VÀ SỬA trong `tools:load-test`** khi soak test lần đầu chạy hỏng ngay (không
phải đoán trước — cả hai phát hiện qua log thật):
1. `SimulatedPlayer.playOneCycle` sleep throttle nằm cuối khối `try` — 1 lỗi socket (bình thường
   khi nhiều client tụ 1 điểm) khiến vòng lặp gọi lại NGAY LẬP TỨC không throttle → busy-loop thật,
   60 client sinh >20 triệu lỗi trong <5 phút. Đối chiếu server.log xác nhận server hoàn toàn khoẻ
   mạnh suốt lúc đó — lỗi 100% phía client tool. Sửa: sleep chuyển vào `finally`.
2. Tần suất chat giả lập (20%/cycle) vượt `RateLimiter` World chat (5 lần/10s, `GatewayChannelHandler`)
   → server ngắt kết nối ~1 client/phút ĐÚNG THEO THIẾT KẾ chống spam, nhưng client bị ngắt không rút
   lui, cứ lỗi lặp lại vô thời hạn suốt phần đời còn lại của soak test → lỗi cộng dồn tăng dần đều
   dù server khoẻ mạnh (dễ nhầm là leak nếu chỉ nhìn số lỗi, không đối chiếu log server). Sửa: giảm
   tần suất chat 20→3%, thêm `MAX_CONSECUTIVE_FAILURES=5` (client bỏ cuộc thay vì spin mãi).

Server không có bug nào bị phát hiện trong suốt soak test — cả 2 sự cố đều ở công cụ đo. Server vẫn
chạy LIÊN TỤC suốt cả 3 lần thử (không restart giữa chừng khi sửa lỗi client) để giữ nguyên lịch sử
GC/heap — chỉ lần thứ 3 (sau khi sửa cả 2 bug) mới cho số liệu sạch dùng trong ADR-019.

**Phát hiện phụ**: `-XX:+ZGenerational` (JVM.md §3) đã bị JDK gỡ từ bản 24 — generational giờ là
chế độ ZGC duy nhất, không cần flag riêng. JVM.md đã sửa.

**Quyết định lát cắt 4 (2026-07-26)**: đánh giá lại ADR-006 (Ashley ECS) bằng JMH thật thay vì suy
đoán — dựng harness `me.champeau.jmh` cho `engine-ecs` (trễ hơn ROADMAP dự kiến ban đầu ở Phase 1,
làm luôn ở đây vì đây mới là lúc thật sự cần). Benchmark `EcsWorldUpdateBenchmark` dùng
`MovementSystem` làm proxy (chỉ có system này sẵn trong `engine-ecs` — AI/Combat/Skill/Boss/
StatusEffect System đều ở `server-world`, phụ thuộc ngược, không benchmark được từ trong module).
Kết quả: 100→5000 entity, `EcsWorld.update()` từ 1.271µs→146.560µs — kể cả ở 5000 entity (~60 lần
quy mô 1 map thật) chỉ ăn 3.66% ngân sách ECS 4ms. **Revisit Trigger ADR-006 CHƯA kích hoạt** — giữ
nguyên Ashley, không tạo ADR `Supersedes`. Chi phí/entity tăng không tuyến tính (~3x khi entity tăng
10 lần) — ghi nhận làm tín hiệu theo dõi tương lai, không phải lý do đổi ECS bây giờ (Điều 9). Chi
tiết: [ADR-006 "Ghi chú — đánh giá lại Phase 9"](../01-architecture/adr/ADR-006-Why-ECS.md).

**Lát cắt 6 (2026-07-28)** — quyết định lát cắt 1 (tách AoI sync 20Hz) KHÔNG đủ: load test thật
N=1000/2000 lại (sau lát cắt 3) cho thấy N=1000 vẫn 67-83ms tick p99 (vượt ngân sách), N=2000 SẬP
hẳn (tick tối đa 10.76s, lỗi client leo thang không dừng). Thay `AreaOfInterestTracker` từ quét
O(n²) toàn world sang **spatial-grid sharding** (lưới ô cạnh = `AOI_RADIUS_METERS`, quét 3×3 ô lân
cận — đúng kỹ thuật spatial hashing tiêu chuẩn, không đổi tập kết quả AoI, chỉ đổi cách tìm ứng
viên). Verify: toàn bộ test hiện có (`WorldFacadeTest`, `GatewayServerIntegrationTest`,
`GatewayPipelineTest`) PASS — không đổi behavior. Đo lại cùng kịch bản: N=1000 cải thiện khiêm tốn
(~20-30%, tick p99 67-83ms→56-58ms); N=2000 cải thiện thật nhưng CHƯA đạt ngân sách (lỗi client
3553→177 và hết leo thang, tick tối đa 10.76s→~3.7s) — số liệu đầy đủ + diễn giải trung thực (kịch
bản test hiện có là TỤ 1 ĐIỂM, đúng trường hợp sharding giúp ÍT NHẤT) tại
[BENCHMARK.md §5.2](../09-performance/BENCHMARK.md). 2 việc follow-up phát hiện, CHƯA làm: chính
sách giới hạn người/khu vực ở tầng gameplay (mới là giả định dùng để diễn giải số liệu, chưa
enforce), và HikariCP `maximumPoolSize=10` backlog hàng nghìn request lúc burst connect (chưa gây
lỗi nhưng đáng tăng trước khi test N lớn hơn).

**Lát cắt 7 (2026-07-28, cùng ngày, theo yêu cầu chủ dự án)** — code chính sách giới hạn người/khu
vực còn để trống ở lát cắt 6: `server-gateway.MainWorldChannelRegistry` — mỗi khu vực (channel,
1 `WorldFacade` riêng, cùng kỹ thuật `DungeonInstanceRegistry`/`ArenaInstanceRegistry` nhưng áp
dụng cho MAP CHÍNH) tối đa 20 người mặc định, 50 người khu vực đặc biệt (hook sẵn, chưa map nào
dùng), tự mở khu vực mới khi tổng người đạt 80% tổng sức chứa. Khu vực mở LAZY từ #1 (không pre-warm
25 khu vực rỗng — diễn giải của AI cho "25 khu mặc định" trong yêu cầu gốc, CHƯA xác nhận lại với
chủ dự án). 2 bug thật phát hiện+sửa: (1) race điều kiện — registry bị gọi từ nhiều thread
(`authExecutor` VÀ `ArenaInstanceRegistry.settlementExecutor`) mà dùng `HashMap`/`ArrayList` thường,
gây `GatewayServerIntegrationTest` treo ngẫu nhiên — sửa bằng `synchronized`; (2) sau khi sửa (1),
2 test đó VẪN treo — nguyên nhân THẬT: lỗ hổng đồng bộ hoá CÓ SẴN từ lát cắt 1 (test rời world
chính trước khi AoI `sync()` 20Hz kịp chạy lần đầu, nên `visibleTo` rỗng, không ai để báo despawn)
— chỉ hiếm khi lộ ra, bị lộ rõ dưới tải nặng lúc chạy lại nhiều lần liên tục. Sửa bằng cách cho 2
test đợi xác nhận AoI spawn thật trước khi tiếp tục (tất định, không dựa may rủi thời gian). Đo lại
N=1000/2000 (DB xoá sạch, cùng máy/JVM/kịch bản lát cắt 6): **N=1000 tick p99 67-83ms→0.82-1.57ms,
N=2000 tick p99 8.59s (đỉnh)→0.98-3.15ms (ổn định suốt), lỗi client 3553→0** — cả 2 mức trong ngân
sách 16.6ms (trừ 1 đỉnh đơn lẻ 28.6ms ở N=2000, không lặp lại). Xác nhận spatial-grid sharding (lát
cắt 6) + giới hạn người/khu vực (lát cắt này) BỔ SUNG nhau, không thay thế nhau. Chi tiết đầy đủ:
[BENCHMARK.md §5.3](../09-performance/BENCHMARK.md). Còn lại CHƯA làm: HikariCP pool size, xác nhận
lại cách diễn giải "25 khu mặc định" (lazy vs pre-warm) với chủ dự án, multi-zone THẬT trong game
content (channel giải quyết mật độ, không thay thế nhu cầu nội dung bản đồ đa khu vực nếu cần sau).

**Lát cắt 7.5 (2026-07-28, cùng ngày)** — ✅ tăng HikariCP `maximumPoolSize` 10→20, dựa trên backlog
thật đo được ở lát cắt 6-7 (~1790 request chờ lúc burst connect N=1000). Vẫn là khởi điểm hợp lý,
chưa phải số đã tune kỹ (chưa có `kage.db.pool.pending` metric để verify hết backlog ở N lớn hơn).

**Lát cắt 8 (2026-07-28, cùng ngày, chủ dự án chọn "chỉ code thuần, không thêm dependency")** — thêm
phần observability KHÔNG cần Grafana/Alertmanager/OpenTelemetry (những thứ đó cần dependency/hạ
tầng mới, hỏi trước khi thêm — CLAUDE.md §4): `kage.tick.overrun.count` (mở rộng `TickMetricsPort`,
cùng chỗ log WARN "World tick quá hạn"), `kage.players.online` (Gauge đọc `ConnectedCharacterRegistry
.onlineCount()`), `kage.gold.total` (Gauge đọc `ServerDatabaseBootstrap.totalGold()`, query
`SUM(gold) FROM character_stat` — OBSERVABILITY.md §3.5 gọi đây là "metric chống gian lận", đáng lẽ
phải có từ Phase 7 trade nhưng bị bỏ sót), `/health/live` + `/health/ready` (dùng chung
`MetricsHttpServer`/cổng 9404 sẵn có, `/health/ready` kiểm tra DB qua `SELECT 1`). Verify: 165 test
(`server-world`+`server-gateway`+`server-database`) PASS, ArchUnit `chi_server_gateway_dung_micrometer`
vẫn PASS (Micrometer vẫn chỉ ở `server-gateway`). Chi tiết: [OBSERVABILITY.md §3, §7](../07-infrastructure/OBSERVABILITY.md).
Còn lại CHƯA làm (liệt kê đầy đủ ở OBSERVABILITY.md "Việc cần làm tiếp theo"): `kage.entities.count`,
`kage.ecs.system.duration`, metric network/business còn lại, `kage.db.pool.*` (cần port riêng qua
ranh giới Điều 23), JSON structured log, và toàn bộ Grafana/Alerts/Tracing (chặn đóng Phase 9 theo
Điều 44, nhưng cần quyết định hạ tầng/dependency trước).

---

## Phase 10 — Release

Theo [GIT-WORKFLOW.md](GIT-WORKFLOW.md):

```
Closed Beta → Open Beta → Release Candidate → Production → Hotfix
```

| Quy tắc | Nguồn |
|---|---|
| **Không deploy Production từ nhánh phát triển** | Điều 40 |
| **Không auto-deploy Production** | [CI-CD.md §8](../07-infrastructure/CI-CD.md) |
| Không tính năng nào vào Production chưa qua ≥1 vòng Closed Beta | — |

---

## 4. Bảng tóm tắt

| Phase | Network? | Server? | Hệ phái | Điều kiện qua |
|---|---|---|---|---|
| 0 Foundation | ❌ | ❌ | 0 | Build xanh, ArchUnit chạy |
| 1 Engine Core | ❌ | ❌ | 0 | Render + input, tiếng Việt OK |
| 2 Network | ✅ | ✅ | 0 | Ping/pong, reconnect |
| 3 Character/Map/Move | ✅ | ✅ | 0 | 2 client thấy nhau, persist |
| 4 Combat/Skill/Monster | ✅ | ✅ | 1 (Hỏa) | Đánh quái, rơi đồ |
| 5 Inventory/Equip/Shop | ✅ | ✅ | 1 | Cường hoá, không duplicate |
| **6 Quest/NPC** | ✅ | ✅ | 1 | **← MVP** |
| 7 Guild/Party/Trade | ✅ | ✅ | 1 | Trade an toàn |
| 8 Dungeon/Boss/PvP | ✅ | ✅ | 5 | — |
| 9 Optimization | ✅ | ✅ | 5 | Soak 2h không leak |
| 10 Release | ✅ | ✅ | 5 | Qua Beta |

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | **Không điền deadline** — chỉ thứ tự + điều kiện | Accepted |
| 2 | **MVP = hết Phase 6**, không phải một Phase riêng | Accepted |
| 3 | ADR-010/012/018 đã chốt; chỉ còn ADR-011 (việc trong Phase 0) (§3) | Accepted |
| 4 | **AoI ở Phase 3**, không để sau | Accepted |
| 5 | **Phase 9 là Phase đầu tiên được tối ưu** (Điều 30) | Accepted |
| 6 | Trade **không mở** nếu ADR-025 chưa Accepted | Accepted |
| 7 | Phase 9 không đóng nếu thiếu observability | Accepted |

## ADR cần tạo

Xem [ADR Index](../01-architecture/adr/README.md) — **40 Accepted · 1 Proposed (hoãn) · 1 Superseded** trên tổng **42 file ADR** (đếm lại 2026-09-05 bằng dòng `Status` của **từng file một**, không suy từ bảng
index: gồm 010/012/018 chốt 2026-07-18, ADR-026 chốt 2026-07-24, ADR-014/015/019 chốt 2026-07-26/28,
ADR-027 TLS chốt 2026-07-31, ADR-011/020/021/022/024/028/029/030 chốt 2026-08-01, **ADR-031 chốt
2026-08-03**, **ADR-032 chốt 2026-08-06**, **ADR-033 chốt 2026-08-08**, **ADR-034 chốt 2026-08-09**,
**ADR-035 chốt 2026-08-27**, **ADR-036 + ADR-037 chốt 2026-08-30**, **ADR-038 (pivot tu tiên) chốt
2026-09-01**). ADR-007 **Superseded by ADR-030** (không xây Plugin System). Con số cũ ở đây
("**33 Accepted · 1 Superseded · 1 Proposed** trên 35 file", đếm 2026-08-29) **đã sai** vì ADR-036 đã
được Accept 2026-08-30 và ADR-037/038 ra đời sau lần đếm đó. **ADR-023 nay ĐÃ VIẾT** (Accepted 2026-09-05,
desktop-first) ⇒ chỉ mục HẾT ô "chưa viết".

> ~~✅ **Đã gộp và đếm lại 2026-09-04**: ADR-039 và ADR-040 đã lên master ở trạng thái `Proposed`,
> chờ chủ dự án Accept; cả hai migration **chưa từng chạy qua Postgres thật** (Docker tắt lúc làm).~~
> ✅ **CẬP NHẬT 2026-09-05**: cả hai **đã Accepted**, và **V25/V26 đã chạy qua Postgres thật** —
> V25 kèm playtest sống (đứng đè lên cọc 20 giây, 0 sát thương; `wandering_bandit` vẫn đuổi và đánh
> −18), V26 kèm kiểm chứng bằng mắt (Chu Hoành đúng `(-5, 0)`, nói chuyện được). Chỉ còn **ADR-042**
> ở trạng thái `Proposed` và nó **hoãn có chủ đích**, không phải chờ duyệt.

Sáu ADR mới so với lần đếm trước (2026-08-07):

| ADR | Nội dung | Trạng thái | Ghi ở mục |
|---|---|---|---|
| [ADR-033](../01-architecture/adr/ADR-033-Ninja-School-Style-Movement.md) | Di chuyển trái/phải + nhảy thật kiểu Ninja School Online, bỏ đi tự do 4 hướng | Accepted 2026-08-08 | 35 |
| [ADR-034](../01-architecture/adr/ADR-034-Manual-Attack-And-Experience.md) | Đánh thường phải BẤM mới ra đòn + hệ thống kinh nghiệm/lên cấp đầu tiên | Accepted 2026-08-09 | 35 |
| [ADR-035](../01-architecture/adr/ADR-035-Client-State-Sync-Opcodes.md) | 7 opcode lấp 4 khoảng trống đồng bộ trạng thái client (catalog theo **A′**) | Accepted 2026-08-27 | 37, 38 |
| [ADR-036](../01-architecture/adr/ADR-036-Equipment-Stats-And-Enhancement-Effect.md) | 4 cột chỉ số cho `item_template` + làm cho cường hoá thật sự có tác dụng | ✅ **Accepted** 2026-08-30, đã thi hành | 29, 40, 41 |
| [ADR-037](../01-architecture/adr/ADR-037-Monster-Template-And-Spawn-Tables.md) | `monster_template` + `monster_spawn` đọc từ DB, module `feature-monster` | ✅ **Accepted** 2026-08-30, đã thi hành + đã playtest | 43, 44 |
| [ADR-038](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) | Đổi bối cảnh ninja → **tu tiên**: chỉ tường thuật + chuỗi hiển thị, không đụng cơ chế/protocol/DB | ✅ **Accepted** 2026-09-01, Đợt 1+2+3 đã thi hành | 50, 51 |

## Tài liệu liên quan

- [KAGE-CONSTITUTION.md](../00-governance/KAGE-CONSTITUTION.md) — Điều 33–34
- [ADR Index](../01-architecture/adr/README.md) — ADR chặn từng Phase
- [GIT-WORKFLOW.md](GIT-WORKFLOW.md) — quy trình release
- [GAME-DESIGN-DOCUMENT.md](../13-game-design/GAME-DESIGN-DOCUMENT.md) — nội dung từng Phase

## Việc cần làm tiếp theo

1. ✅ 3 ADR chặn Phase 0 đã chốt (010/012/018). **Có thể mở Phase 0.**
2. Đầu Phase 0: chốt/triển khai **ADR-011** (ArchUnit) như hạng mục kỹ thuật.
3. ✅ **GDD đã bổ sung công thức damage** (2026-07-19) — Phase 4 hết bị chặn về mặt game design.
4. ✅ **ADR-026 Accepted** (2026-07-24) — dungeon instance model chốt (cùng process, World riêng). **Phase 8 đủ điều kiện mở.**
5. ✅ **4 hệ phái còn lại xong** (2026-07-25) — 5/5 hệ phái đã chơi được.
6. ✅ **Arena 1v1 + MMR cơ bản xong** (2026-07-25) — Phase 8 ĐÓNG (4/4 lát cắt: dungeon, boss AI,
   4 hệ phái, Arena). Battlefield 5v5 + Chiếm Lãnh Địa (GDD §6) để dành mở phase/lát cắt riêng sau,
   không nằm trong Phase 8.
7. ✅ **ADR-014 + ADR-015 Accepted** (2026-07-26, quyết định qua thảo luận trực tiếp với chủ dự án,
   không cần chờ số liệu Phase 9) — Docker Compose + VM đơn (không K8s), Micrometer cho Metrics +
   OpenTelemetry cho Tracing. **ADR-019 (GC) vẫn phải chờ số liệu load/soak test thật** — không thể
   chốt bằng thảo luận, đây là lát cắt kế tiếp của Phase 9.
8. ✅ **Phase 9 lát cắt 1 xong** (2026-07-26) — `kage.tick.duration` đo thật qua Micrometer, scrape
   được ở `GET /metrics` (World chính).
9. ✅ **Phase 9 lát cắt 2 xong** (2026-07-26) — `tools:load-test` chạy thật, verify bằng server thật
   + 10 client 20 giây (0 lỗi).
10. ✅ **Phase 9 lát cắt 3 xong** (2026-07-26) — soak test thật 2h14 (80 client), ADR-019 viết
    Proposed với số liệu thật (ZGC pause max 0.052ms, không leak). 2 bug thật phát hiện+sửa trong
    `tools:load-test` (không phải trong server). Cụm tick-stall bất thường (~800ms dồn trong 2.5s,
    1 lần duy nhất lúc 12:02, không tái diễn, không rõ nguyên nhân từ log) — ghi nhận, không chặn.
11. ✅ **Phase 9 lát cắt 4 xong** (2026-07-26) — JMH harness `engine-ecs` + benchmark thật, ADR-006
    Revisit Trigger đánh giá lại: CHƯA kích hoạt, giữ nguyên Ashley.
12. ✅ **Phase 9 lát cắt 5 xong** (2026-07-26) — công thức ước tính hạ tầng (RAM/CPU/băng thông theo
    N người chơi), xem [BENCHMARK.md §5.1](../09-performance/BENCHMARK.md).
13. ✅ **ADR-019 Accepted** (2026-07-28, chủ dự án xác nhận). Còn lại trước khi đóng Phase 9: mở rộng
    observability (Grafana dashboard, Alerts, Tracing) — xem tiến độ ở
    [OBSERVABILITY.md](../07-infrastructure/OBSERVABILITY.md) — **Điều 44 chặn đóng Phase 9 nếu
    thiếu phần này**.
14. Trả lời các câu hỏi mở còn lại ở [docs/README.md](../README.md).
15. ✅ **Phase 9 lát cắt 6 xong** (2026-07-28) — AoI spatial-grid sharding, đo lại N=1000/2000 thật
    (số liệu [BENCHMARK.md §5.2](../09-performance/BENCHMARK.md)). N=2000 tụ 1 điểm vẫn CHƯA đạt
    ngân sách dù cải thiện rõ — còn 2 việc chưa làm: chính sách giới hạn người/khu vực ở tầng
    gameplay (chỉ là giả định dùng để diễn giải số liệu, chưa code), tăng HikariCP
    `maximumPoolSize` (backlog hàng nghìn request lúc burst connect, chưa gây lỗi client nhưng
    đáng sửa trước khi test N lớn hơn 2000).
16. ✅ **Phase 9 lát cắt 7 xong** (2026-07-28) — `MainWorldChannelRegistry` (giới hạn 20/50 người
    mỗi khu vực, tự mở khu vực mới ở 80% sức chứa) giải quyết dứt điểm khoảng trống lát cắt 6:
    N=1000/2000 đo lại đều TRONG ngân sách 16.6ms, lỗi client N=2000 3553→0 (số liệu
    [BENCHMARK.md §5.3](../09-performance/BENCHMARK.md)). 2 bug thật phát hiện+sửa (race điều kiện
    + lỗ hổng đồng bộ hoá test có sẵn từ lát cắt 1, xem BENCHMARK.md §5.3 chi tiết).
17. ✅ **Phase 9 lát cắt 7.5 xong** (2026-07-28) — HikariCP `maximumPoolSize` 10→20 dựa trên backlog
    thật đo được.
18. ✅ **Phase 9 lát cắt 8 xong** (2026-07-28) — `kage.tick.overrun.count`/`kage.players.online`/
    `kage.gold.total` + `/health/live`/`/health/ready`, thuần code không thêm dependency (chủ dự án
    chọn hướng này khi được hỏi). 165 test PASS. Còn lại CHƯA làm cho observability: metric network/
    business còn lại, `kage.db.pool.*`, JSON log, và Grafana/Alerts/Tracing (cần quyết định
    dependency/hạ tầng mới trước — xem [OBSERVABILITY.md](../07-infrastructure/OBSERVABILITY.md)
    "Việc cần làm tiếp theo"). Vẫn CHƯA xác nhận lại cách diễn giải "25 khu mặc định" (lazy vs
    pre-warm) với chủ dự án, và multi-zone thật trong game content vẫn chưa có.
19. ✅ **Phase 9 lát cắt 9 xong** (2026-07-28) — toàn bộ metric "pure code" còn lại:
    `kage.entities.count`, `kage.ecs.system.duration` (per-System Timer, `EcsWorld.update` overload
    mới nhận `SystemTimingListener`), `kage.packet.received`/`sent`, `kage.connection.active`,
    `kage.ratelimit.rejected`, `kage.login.success`/`failed`, `kage.item.enhance`,
    `kage.db.pool.active`/`pending` (đọc `HikariPoolMXBean` qua `ServerDatabaseBootstrap`), JSON
    structured log production (`JsonLogEncoder` tự viết, không thêm dependency ngoài — chỉ đổi
    `logback-classic` từ `runtimeOnly` sang `implementation` để compile được). Toàn bộ test (120
    file, 0 lỗi) + checkstyle PASS. Chi tiết: [OBSERVABILITY.md](../07-infrastructure/OBSERVABILITY.md)
    §3/§5.1.
20. ✅ **Phase 9 lát cắt 10 xong (2026-07-28, chủ dự án bật đèn xanh cùng phiên)** — Observability
    lớn: `opentelemetry-bom` 1.63.0 mới thêm vào `libs.versions.toml` (verify thật, ADR-015 đã
    Accepted trước đó chỉ mới chốt Micrometer/OTel PHÂN ĐỊNH, chưa code); `GatewayTracing` (OTel SDK
    thật, OTLP/HTTP export tới Jaeger, sampling 1%) + span `gateway.command` (mọi opcode) + span sâu
    đại diện `feature.inventory.enhance`. JVM metric binder (GC/memory/thread) bind vào registry —
    đóng khoảng trống `jvm.gc.pause` (không cần dependency mới). `docker-compose.yml` thêm
    `prometheus`/`alertmanager`/`grafana`/`jaeger`, verify thật bằng `docker compose config` +
    `docker compose up`. 5 dashboard Grafana thật (Server Health/Network/Database/Business/
    Anti-cheat) + alert rules Prometheus (2/6 ngưỡng có baseline thật — tick p99, GC pause; 2 còn
    lại đánh dấu [NGƯỠNG CHƯA VERIFY]). Phát hiện thật: Micrometer đổi `kage.gold.total` thành
    `kage_gold` khi scrape (KHÔNG PHẢI `kage_gold_total`) — xác nhận bằng dump `registry.scrape()`
    thật trước khi viết PromQL. Chi tiết: [OBSERVABILITY.md](../07-infrastructure/OBSERVABILITY.md).
    **Phase 9 Exit Criteria observability giờ ĐỦ (Điều 44)** — CHƯA làm (ngoài phạm vi, không phải
    thiếu sót): tracing sâu cho ~50 opcode còn lại, nhánh sampling "100% Command chậm", kênh thông
    báo Alertmanager thật (Slack/email), `server.Dockerfile` (server-gateway vẫn chạy native).
21. ✅ **Phase 9 ĐÓNG (2026-07-30, chủ dự án xác nhận)** — sau loạt benchmark bổ sung §5.1/5.2/5.4/
    5.7/5.8/5.9 (2026-07-28 đến 2026-07-29, phát hiện công thức capacity-planning gốc lỗi thời +
    CPU không tuyến tính + off-heap ~500-650KB/kết nối bị bỏ sót), rà lại cả 3 Exit Criteria — cả 3
    tick, kèm 2 caveat KHÔNG chặn đóng phase (xem ghi chú ngay dưới Exit Criteria ở đầu mục Phase 9):
    CPU chưa có công thức ngoại suy an toàn cho N>2000, off-heap chưa soak-test dài hạn để loại trừ
    leak. Next: Phase 10 (Release).
22. Phase 10 — tiến độ tính đến 2026-08-01 (chưa có Exit Criteria hình thức như Phase 0-9, chỉ có
    bảng quy tắc Beta→RC→Production ở §Phase 10 phía trên):
    - ✅ `server.Dockerfile` + `.dockerignore` (`35bfe03`), `server` service wired vào
      `docker-compose.yml` (`c789281`), CI job `docker-build-push` đẩy image lên GHCR (`db4ffa8`,
      verify phiên bản action thật thay vì copy số cũ).
    - ✅ Bug thật phát hiện+sửa ngay sau khi push: `ci.yml` trigger sai nhánh `main` trong khi repo
      chỉ có `master` (`c2dd43a`) — nghĩa là `docker-build-push` (và có thể cả `build-and-verify`
      trên push trực tiếp) chưa từng chạy thật trước đó.
    - ✅ **ADR-027 (TLS) Accepted + code xong** (2026-07-31, chủ dự án chốt trực tiếp) — Netty
      `SslHandler` phía server + JDK `SSLSocket` pin phía client. Build+test xanh. Cert bundle
      trong repo CHỈ để test (khóa public trên GitHub) — **cần cert thật trước khi chạy Production**.
    - ✅ `docker-compose.prod.yml` (chưa nối TLS, có cảnh báo chặn dùng cổng public 7777 tới khi có
      cert thật) — commit `ff5e165`.
    - ✅ CI trên `master` **CONFIRMED XANH lần đầu tiên trong lịch sử repo** (run #31, commit
      `ff5e165`, 2026-07-31) sau khi sửa thêm 1 bug thật: `gradlew` không có exec bit khi checkout
      từ Windows → `chmod +x gradlew` thiếu trong `ci.yml` (đã có trong Dockerfile nhưng chưa có
      trong CI) — sửa ở `018cb7a`.
    - ✅ Bug thật thứ hai trong chính Dockerfile: layer "dependency warmup" là no-op kép — chạy
      `./gradlew dependencies` trên **root project** (vốn không khai dependency nào) thay vì target
      đúng `:server:server-gateway:dependencies`. Sửa + verify bằng `docker build --no-cache` thật
      (thấy đúng Netty/Micrometer/protobuf resolve vào layer) — commit `b81a0f7`. **CI run #58
      confirmed Success 2026-08-01** (`build-and-verify` 12m24s + `docker-build-push` 4m34s).
    - ✅ Dependabot backlog (15 PR) gần như đã dọn sạch qua các lần merge CI #40-57 — chỉ còn 1 PR mở
      (`netty-all` 4.1.136→4.2.16, #8), **cố tình giữ lại** vì build thật cho thấy Netty 4.2 gây lỗi
      TLS (`bad_record_mac`) dưới tải trong integration test.
    - ✅ **Tối ưu kích thước image: jlink custom runtime** (`47293f7`, 2026-08-01) — thêm stage
      jlink vào `server.Dockerfile` (DOCKER.md §8), bớt ~40MB image `server-gateway` bằng cách tự
      quét THẬT bộ jar qua `jdeps` (không đoán module list tay), tách `modpath/`/`classpath/` để né
      lỗi Netty split-package khi đưa cả ~117 jar vào `--module-path` cùng lúc. Alpine **vẫn KHÔNG
      dùng** — DOCKER.md §8 đã reject (musl libc phá native lib Netty), jlink chỉ đổi runtime JRE,
      giữ nguyên base OS Ubuntu. Kèm 1 bug thật phát hiện+sửa ngay sau đó: `ubuntu:26.04` trần mặc
      định locale `POSIX` (không UTF-8) làm mojibake log/exception tiếng Việt — vá bằng
      `LANG=C.utf8`/`LC_ALL=C.utf8` (`8ab0e94`).
    - ⛔ **Chưa làm** (không phải thiếu sót, ngoài phạm vi các đợt trên): TLS thật trong
      `docker-compose.prod.yml`, cert Production thật, và toàn bộ quy trình
      Closed Beta → Open Beta → RC → Production ở bảng quy tắc phía trên vẫn chưa bắt đầu (đòi hỏi
      quyết định của chủ dự án, không phải việc AI tự chạy).
    - ✅ **ADR-024 (Asset Distribution) viết 2026-08-01, Status Proposed** — gỡ nhãn "chặn Phase 10"
      bằng cách chính thức hoá lại quyết định MVP có sẵn (đóng gói toàn bộ asset, hoãn patch/CDN/Play
      Asset Delivery) thay vì đoán số liệu chưa tồn tại (repo hiện chưa có `assets/` thật nào, Android
      cũng chưa phải target đã chốt). Chờ chủ dự án Accept. Xem
      [ADR-024](../01-architecture/adr/ADR-024-Asset-Distribution-Strategy.md).
23. Phase 10 — 3 luồng song song đóng nốt các khoảng trống observability/security còn lại
    (2026-08-02, chạy đồng thời qua worktree-isolated agent, main session review + merge):
    - ✅ **GoldTotalSpike đo lại thật, gỡ marker `[NGƯỠNG CHƯA VERIFY]`** — `SimulatedPlayer` (tools:
      load-test) giờ ghi nhận `ItemGrantedEvent` rồi bán lại qua `SellItemCommand` thật (50%/cycle
      nếu đang giữ item); đo lại đúng quy trình cũ (100 client, ramp 10→100, 1500s, Postgres/Redis
      thật cổng 55432/55679): `kage_gold` tăng thật 0 → 14, 0 lỗi client. Số tuyệt đối nhỏ vì 2 giới
      hạn RIÊNG của phép đo (không phải trần thật): chỉ 1 quái nội dung tồn tại + tool chỉ chốt
      `monsterId` 1 lần lúc connect (không re-target sau respawn, ID mới mỗi lần hồi sinh) — xem
      phân tích đầy đủ [OBSERVABILITY.md §8.1](../07-infrastructure/OBSERVABILITY.md). Giữ ngưỡng
      20% (bằng chứng thật củng cố biên độ an toàn), thêm guard `kage_gold > 1000` vào
      `alert_rules.yml` sau khi phát hiện thật biểu thức tỉ lệ gốc chia gần 0 lúc server mới khởi
      động (nổ số/không xác định).
    - ✅ **Grafana dashboard vá khoảng trống với metric đã implement nhưng chưa có panel** —
      `kage.packet.size` (network.json) và `kage.db.query.duration`/`kage.db.transaction.failed`
      (database.json) đã có code từ trước (2026-08-02, round quan sát trước) nhưng dashboard/doc
      §9 vẫn ghi "chưa implement". Verify tên Prometheus series thật (không đoán) bằng scrape thử,
      xác nhận cả 2 dashboard provision đúng qua Grafana API thật (`docker compose up` thật).
    - ✅ **Đóng gap kiểm chứng runtime cho Redis `requirepass`** (SECURITY.md §14) — thêm
      `RedisSessionAdapterIntegrationTest.shouldRejectConnection_whenNoPasswordProvided`
      (Testcontainers, tái dùng container `--requirepass` có sẵn): xác nhận kết nối không password
      bị từ chối thật với `JedisAccessControlException: NOAUTH Authentication required.`,
      sanity-check bằng cách tạm bỏ `--requirepass` để xác nhận test fail đúng khi mất bảo vệ.
24. Phase 10 — các đợt song song **khác** của ngày 2026-08-02 (mục 23 chỉ ghi đợt cuối; bổ sung ở đây
    cho đủ, cùng cách chạy: worktree-isolated agent, main session review + merge):
    - ✅ **PMD 7.10.0 → 7.26.0** (`8087b61`) — 7.10.0 ra trước khi PMD hỗ trợ JDK 25 nên nổ
      `StackOverflowError` ở bước type-inference; gỡ hẳn workaround `-x pmdMain -x pmdTest` khỏi CI
      và tài liệu. Trước đó (2026-08-01) `tools:load-test` đã theo dõi liên tục đỉnh
      `kage_players_online` (`12a1a59`) — vá đúng lỗ công cụ làm hỏng lần đo N=2000, **bản thân phép
      đo N=2000 vẫn CHƯA làm lại**.
    - ✅ **Đợt quan sát/hạ tầng**: metric `kage.packet.size` (`95ad1d7`); verify index DB bằng
      `EXPLAIN ANALYZE` thật (`d5f2a22` — không cần migration mới, nhưng doc sai **cả hai chiều**:
      kê nhầm cột của bảng `mail`, và `audit_log` không có truy vấn SELECT nào); font tiếng Việt
      **Be Vietnam Pro** (OFL 1.1) bật `gdx-freetype` thật (`c10b6fd`) — **Exit Criteria "tiếng Việt
      có dấu hiển thị đúng" của Phase 1 tới đây mới thật sự xong**, trước đó chỉ được tick trên giấy.
    - ✅ **Đợt security/ECS**: `SecurityIntegrationTest` tự viết thay DAST (authz/replay/rate-limit/
      fuzzing, `d80012d`+`eb3c4bc`) — phát hiện thật: **`sequenceId` KHÔNG phải chốt chống replay**
      dù tài liệu ghi vậy (không tồn tại state nào lưu nó), phòng vệ thật là `idempotencyKey`
      (ADR-025); bộ test thoát sandbox GraalJS (`902ad8f`+`15a5432`) — **không tìm ra lỗ**, mức
      CONSTRAINED-equivalent giữ nguyên; đo lại **Revisit Trigger ADR-006** bằng metric production
      thật (`0320995`+`5f06d01`): ECS ăn **0,83% ngân sách 4ms @N=500** → trigger **CHƯA kích hoạt**,
      giữ Ashley.
    - ✅ **Đợt gitleaks/doc/hệ phái**: gitleaks pre-commit hook + CI job `secret-scan` (`1312877`,
      OWASP.md §2 — thế chỗ GitHub Secret Scanning vốn không dùng được trên private + free tier);
      viết lại SECURITY.md §6 theo phòng vệ replay **thật** thay vì `sequenceId` (`5f5f3be`, cùng
      commit sửa luôn dòng ROADMAP còn ghi jlink là "Chưa làm" trong khi đã code từ `47293f7`);
      hệ **Hỏa** đủ bộ 3 active + 1 Tuyệt Kỹ + 2 passive (`a4791cb`) — đóng scope cut ghi ở Phase 8,
      **5/5 hệ phái giờ đủ kit**, không còn hệ nào chỉ có 1 skill.
    - ✅ **Đợt observability tiếp theo**: `kage.db.query.duration` + `kage.db.transaction.failed`
      (`6e1aca9`); tracing sâu mở sang `Buy`/`Sell`/`ConfirmTrade` (`e49fae4`); baseline đo thật cho
      `LoginFailedSpike`/`GoldTotalSpike` (`e0681ca`) — chọn cách để ngỏ ngưỡng GoldTotalSpike thay
      vì đoán số, và mục 23 là đợt đóng nốt nó.
    - ✅ **JVM.md đối chiếu thực tế** (`061dbce`) — trạng thái ADR-019 + cờ JVM trong `server.Dockerfile`
      thật, không chép lại số cũ. **Rà toàn bộ Command có side-effect theo ADR-025** và tìm thấy
      **2 lỗ thật**: `EnhanceItemCommand` + `ClaimQuestRewardCommand` chưa có `idempotencyKey` —
      đã vá (`4a7d7c0`).
25. **Hướng art bị đổi hẳn (2026-08-03)** — bộ "gold standard" dựng bằng mesh kit thủ tục **bị chủ dự
    án BÁC** (đạt ~1/5 so với benchmark Ninja School Online), đợt re-render 15 sheet **huỷ**. Thay
    bằng **Text→3D (Hyper3D Rodin, tier Detail)** qua `kage_text3d_kit.py` mới (`aff3f55`); pilot Hỏa
    được **duyệt cùng ngày** sau 3 vòng (`pilot_text3d_hoa_v2_*.png` là chuẩn so mới). Giữ lại từ thế
    hệ cũ: rig 11 xương, vũ khí/phụ kiện, 7 action, bảng nghiệm thu Q1–Q12. 15 sheet khối hộp đang có
    trở thành **PLACEHOLDER**, thay dần khi 14 nhân vật còn lại được sản xuất. Chi tiết + 6 lỗi render
    thật: [ART-QUALITY-STANDARD §8](../14-art/ART-QUALITY-STANDARD.md).
26. ✅ **[ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md) Accepted 2026-08-03**
    (`3d67a5f`, chủ dự án chốt qua chat) — nhân vật/quái/NPC **render 3D thời gian thực** qua camera
    orthographic khoá góc (2.5D); VFX/icon/UI **vẫn sprite 2D**. Hệ quả lớn nhất cho pipeline:
    **sản phẩm giao của nhánh nhân vật là model glTF, không còn là sprite sheet** — ảnh render chỉ
    còn giá trị làm bằng chứng nghiệm thu, và bước hạ mẫu/pack atlas **không áp dụng** cho nhân vật
    nữa (thu hẹp phạm vi [ADR-029](../01-architecture/adr/ADR-029-Asset-Pipeline-Tool.md), amend
    [ADR-028](../01-architecture/adr/ADR-028-Graphics-Tier-Asset-Delivery.md)). Lan toả sang 4 tài
    liệu còn giả định sprite (`1c8a022`) + đợt sửa sau review (`d42a423`). ADR **tự đặt điều kiện
    chặn cho chính nó**: phải chốt cách phòng vệ JitPack trước khi thật sự phụ thuộc `gdx-gltf`.
27. ✅ **Sổ việc do ADR-031 sinh ra — đóng 2026-08-05** (7 commit, `72dc01c`…`ab27027`):
    - **Ngân sách tam giác đo thật**, không còn ước lượng: mesh Rodin pilot Hỏa **93.165 tam giác**,
      đo bằng `tools/blender/measure_triangle_budget.py` theo 2 lượt quét tách biệt (hình học ×
      texture) rồi so từng pixel → chốt **5.000 / 10.000 / 20.000** tam giác cho Low/Medium/High
      (0,59 / 1,38 / 3,60 MB `.glb`). Kết luận có số liệu: mesh 93k là **dư thừa**, và **hạ texture
      rẻ hơn hạ mesh** (cùng mức lệch trung bình, texture 2048→512 lệch tối đa 0,30 còn mesh 93k→40k
      lệch 0,76 — vỡ silhouette cục bộ). Xem [ART-PIPELINE §4.4](../14-art/ART-PIPELINE.md).
    - **Bước E đã code** — `tools/blender/export_character_lods.py` sinh cả 3 tier `.glb` + sidecar
      `model-meta.json` (`b98a940`).
    - **Chốt cách gắn vũ khí**: vũ khí **luôn gắn rời vào xương**, không bao giờ nằm trong prompt
      Rodin (mesh sinh ra là một khối hàn liền, skin theo khoảng cách — lưỡi dao vươn khỏi tay bị
      chia qua nhiều xương và cong như cao su khi gập khuỷu). Trang phục thì ngược lại, vẫn nằm
      trong prompt vì `item_template` chưa có cột hình ảnh nào để đổi lúc chạy.
    - ✅ **Đóng 2026-08-08**: dòng frame time của điều kiện Revisit Trigger #1 (20 nhân vật trên
      màn) đã đo (`CharacterModelFrameBenchmarkTool`) — xa dưới ngưỡng 8ms, kèm giới hạn thật (chưa
      cel shader/viền, chưa có máy tham chiếu chính thức của dự án). Xem
      [ART-PIPELINE §4.4](../14-art/ART-PIPELINE.md).
28. **Dây chuyền nhánh 3D — đã thông, còn thiếu phép đo** (cập nhật 2026-08-07):
    - ✅ **[ADR-032](../01-architecture/adr/ADR-032-JitPack-Dependency-Defence.md) Accepted
      2026-08-06 và đã thi hành cùng ngày** (`38daea2`): `gdx-gltf` 2.3.0 đóng gói vào
      `third-party/m2/` dưới dạng kho Maven cục bộ, `jitpack.io` bỏ hẳn khỏi build, thư viện đã vào
      `libs.versions.toml`. **Không còn ADR nào ở trạng thái `Proposed`.**
    - ✅ **`WorldScreen` đã nối sang nhánh 3D** (`df5ace2`) — `CharacterModelRenderer` dùng
      `SceneManager` của gdx-gltf qua camera orthographic khoá góc, và **`RENDERING.md` đã viết lại**
      cho hai nhánh render.
    - ⛔ **Còn thiếu**: chưa ai nhìn thấy nhánh 3D vẽ ra model thật — `.glb` là sản phẩm sinh ra,
      không commit, nên bản checkout sạch rơi về hình hộp thay thế. Chưa chạy được thì chưa đo được
      frame time (mục 27), chưa viết được cel shader, và chưa biết `SceneManager` cấp phát bao nhiêu
      mỗi khung. Danh sách việc đầy đủ: [RENDERING.md "Việc cần làm tiếp theo"](../03-engine/RENDERING.md).
    - 🛑 **Cần chủ dự án**: `CharacterSpawnedEvent` không có trường mô tả ngoại hình nên **mọi nhân
      vật đang nạp cùng một model**. Thêm trường đó là **đổi protocol** ([/CLAUDE.md §4](../../CLAUDE.md)).
29. ⛔ **Đang chặn thật, chờ chủ dự án** (đồng bộ với ["🔴 ĐANG CHỜ CHỦ DỰ ÁN"](../README.md) —
    đọc ở đó, không chép số liệu sang đây; **rà lại 2026-08-29**):
    - ~~V20 — 3 cột 3D cho `art_asset`~~ ✅ **duyệt + thi hành 2026-08-07**
      ([ART-PIPELINE §8.6](../14-art/ART-PIPELINE.md)): `asset_kind`/`model_name`/`tri_counts` +
      `CHECK art_asset_kind_branch`, và `ArtRegistryUpserter` đã upsert được nhánh `MODEL`.
      ~~**Còn hở**: bước kiểm asset mồ côi chưa soi thư mục `assets/exported/models/`~~ ✅ **cũng đã
      đóng 2026-08-08** (`5a389e9`, xem mục 34) — `OrphanAssetChecker` soi **cả hai** thư mục.
      **Khoản này đóng hoàn toàn, không còn phần nào chờ chủ dự án.**
    - Câu hỏi **desktop-first hay mobile-first** vẫn chưa trả lời → ADR-023 vẫn chưa viết được, và
      ngân sách tam giác ở mục 27 mới chỉ đo cho desktop. **Đây là khoản chặn cũ duy nhất còn mở.**
    - ~~**[ADR-036](../01-architecture/adr/ADR-036-Equipment-Stats-And-Enhancement-Effect.md) —
      trang bị và cường hoá không có ý nghĩa cơ học**~~ ✅ **Accepted + thi hành 2026-08-30, KHÔNG
      còn là nợ.** Ba lỗ (chỉ có cột `bonus_atk` ⇒ áo giáp cộng ATK; `enhance_level` không được đọc
      ở bất kỳ công thức chỉ số nào ⇒ +0→+7 trừ 8.900 vàng mà chỉ số không đổi; `required_level`
      được tài liệu mô tả mà không tồn tại) đều đã vá trong **một lượt**: migration **V22** +
      `EnhancementTable.enhancedBonus` + `EquipmentBonusSnapshot` + 4 field additive của
      `ItemCatalogEntry`. Chi tiết ở **mục 41**. Nợ CÒN LẠI sau đợt này, cố ý và đã ghi trong ADR:
      chưa gói nào mang HP/HP-tối-đa của chính mình **lúc đăng nhập** (chỉ có ở sự kiện hồi sinh),
      và `ENHANCE_STEP = 0,10` cùng toàn bộ số liệu V22 **chưa qua playtest**.
    - ~~**`drop_table` im lặng khi mã quái không khớp**~~ ✅ **đã sửa 2026-08-29, KHÔNG còn là nợ.**
      `ConnectedCharacterRegistry#onMonsterKilled` truyền thẳng `monsterCode` làm `dropTableCode`, và
      `findEntries` trả rỗng cho **cả hai** trường hợp "quái cố ý không rơi gì" và "mã không khớp
      dòng nào" — nên một con quái mới gõ sai mã sẽ **không rơi đồ vĩnh viễn mà không ai biết**.
      `PostgresDropTableAdapter` nay phân biệt hai trường hợp đó và ghi **WARN một lần cho mỗi mã
      lạ** (INFO nếu bảng có thật nhưng chưa có entry — đó có thể là cố ý). Không đổi kiểu trả về,
      không ném lỗi: một mã sai không đáng làm sập lượt giết quái. Truy vấn kiểm tra thứ hai chỉ
      chạy khi danh sách rỗng và chỉ một lần mỗi mã, nên đường chạy bình thường **không tốn thêm
      lượt đi DB nào**, và tập mã đã cảnh báo chặn Điều 31 (không log theo nhịp gameplay).
    - ⚠️ **Đổi hệ phái xong người khác vẫn thấy model cũ — đã sửa MỘT NỬA (2026-08-29).**
      `ConnectedCharacterRegistry.updateAppearance` khiến mọi `CharacterSpawnedEvent` phát về **sau**
      lúc đổi mang hệ phái MỚI; trước đó ảnh chụp ngoại hình chốt lúc chọn nhân vật nên sai cho tới
      khi người chơi **đăng nhập lại**. **Phần còn mở**: người đang NHÌN THẤY nhân vật ngay tại thời
      điểm đổi vẫn thấy model cũ cho tới khi nhân vật rời rồi vào lại tầm nhìn của họ.
      ⚠️ **Nửa còn lại KHÔNG chỉ là "server chưa gửi lại" — client cũng chặn**:
      `RemoteCharacterPresenter.onSpawned` **bỏ qua** một `CharacterSpawnedEvent` cho nhân vật đã có
      mặt (`if (entities.containsKey(...)) return;` — cố ý, để không tạo entity trùng). Nghĩa là dù
      server có phát lại spawn cho những người đang nhìn thấy, **model vẫn KHÔNG đổi**. Đóng hẳn thì
      phải chọn **một trong ba, và cả ba đều là quyết định chứ không phải vá**:
      (1) server gửi **despawn + spawn** cho mọi observer (dùng `AreaOfInterestTracker.observersOf`)
      — không đổi protocol, nhưng nhân vật sẽ **"nháy" mất-hiện một khung hình** ở máy người khác;
      (2) client **cập nhật model khi nhận spawn lặp** thay vì bỏ qua — không đổi protocol, nhưng
      **phá thế bất biến "spawn = tạo mới"** mà chính comment ở đó đang dựa vào;
      (3) thêm **event "ngoại hình vừa đổi"** — sạch nhất, nhưng **đổi protocol** (§4).
    - Bốn câu hỏi sản phẩm/vận hành khác vẫn mở ở [docs/README.md](../README.md) (xác nhận hình thức
      font Be Vietnam Pro · có tiếng Anh ở MVP không · mô hình kinh doanh · GM/admin tool là
      in-game command hay web app) — **không cái nào chặn phase**.
30. **Art — nhân vật Text→3D đầu tiên qua trọn vòng nghiệm thu (2026-08-06)**: `char_thuy-b1` là con
    **đầu tiên sau pilot Hỏa** đi hết vòng §7.1 của
    [ART-QUALITY-STANDARD](../14-art/ART-QUALITY-STANDARD.md) và chạy tiếp bước E — xuất được **3 LOD
    glTF: 20.684 / 10.684 / 5.684 tam giác**, khớp ngân sách chốt ở mục 27.
    - Tìm + sửa **6 lỗi thật trong pipeline art**, trong đó **3 lỗi nghiêm trọng**:
      (a) `setup_scene()` bật lại Freestyle **vô điều kiện** ⇒ **mọi** preview nhân vật Text→3D ra
      mảng đen — nghĩa là **vòng nghiệm thu §7.1 chưa từng chạy đúng lần nào cho nhánh ADR-031**;
      (b) camera nghiệm thu vẫn là camera sprite **nhìn ngang**, trong khi pilot được duyệt ở **góc
      3/4** ⇒ Q1–Q10 chấm sai đối tượng;
      (c) bước E xuất glTF **chết khi chạy headless** — gọi `mode_set` trước khi gán active object.
    - ~~⛔ **Chưa commit** tại thời điểm ghi mục này, nên không tra được trong `git log`.~~ ✅ Đã
      commit cùng ngày: `3bb80ca` (Thuỷ + Phong B1 kèm 6 lỗi pipeline), rồi `62a07ba` (đủ B1 cả 5
      hệ, vũ khí đặt theo phép đo), `3584eb0`, `a83deb6` (B2), `20d4015` + `35feabe` (B3 — **đủ 15
      nhân vật**), `360062b`.

31. **Nhánh 3D đi từ "đã nối" tới "đã nhìn thấy chạy" (2026-08-07)** — đóng **cả ⛔ lẫn 🛑** của mục 28:
    - ✅ **Ba lỗi thật chặn nhánh 3D, không cái nào là lỗi asset**: task `run` của Gradle lấy thư mục
      subproject làm cwd nên `Gdx.files.internal` không thấy `assets/exported/` ⇒ renderer **âm thầm
      tắt nhánh 3D** và vẽ hộp thay thế (`27a2f12`); vật liệu có texture bị gán cho mesh vũ khí
      **không có UV** ⇒ Blender ghi `"texCoord": -1` (glTF 2.0 cấm, `texCoord` đánh chỉ số
      `TEXCOORD_n` nên không âm), gdx-gltf đọc lại sinh shader hỏng và **cả màn hình nhân vật sập**,
      không riêng vũ khí — **đây chính là thứ khiến nhánh 3D chưa bao giờ được nhìn thấy chạy**
      (`1b4bc61`); glTF quy ước Y-up còn thế giới Kage đi trên mặt phẳng XY với Z-up nên model nằm
      nghiêng (`0108857`).
    - ✅ **Ghi nhận đã nhìn thấy chạy** (`c3e8918`), và **mục "khung hình lệch tâm" đóng lại vì đó
      KHÔNG phải lỗi engine** (`c521af3`): log thật (vị trí/hướng camera, hình chữ nhật GL viewport)
      khớp chính xác phép tính tay; thủ phạm là script chụp PowerShell `PrintWindow` **không nhận
      biết DPI** nên `GetWindowRect` trả về khung đã bị thu nhỏ và chỉ chép được góc trên-trái của
      khung 1280x720 thật, làm một nhân vật vốn đã ở giữa trông như lệch xuống góc dưới-phải.
    - ✅ **🛑 của mục 28 đã được chủ dự án duyệt và thi hành cùng ngày** — `CharacterSpawnedEvent`
      mang thêm `faction` + `costume_tier` (`92e9f15`), chấm dứt tình trạng **mọi nhân vật nạp cùng
      một model**. Gateway chụp ngoại hình một lần lúc chọn nhân vật rồi tra lại khi AoI báo spawn;
      `server-world` **không bị đụng tới** — nó biết vị trí và chỉ số, luồn dữ liệu ngoại hình qua
      `WorldOutboundSink` sẽ bắt tầng gameplay chở dữ liệu render mà nó không bao giờ đọc. Thêm
      field vào message proto3 đang chạy là tương thích dây nên `ProtocolVersion.CURRENT` giữ nguyên
      1. **Chưa làm**: `costume_tier` vẫn là hằng B1 — art có đủ cả 3 tier nhưng gameplay chưa có
      khái niệm bậc trang bị để suy ra, và bịa ra một cái là quyết định game design.
    - ✅ **`tools:asset-pipeline` mọc nhánh 3D** (`c9d7e63`, `3075dc1`): quét
      `assets/exported/models/{low,med,high}`, đếm tam giác **thật sự có trong file** bằng cách đọc
      **chỉ chunk JSON** của `.glb` (model 3,7MB tốn vài KB để soi), gãy build khi vượt ngân sách
      tier. Ngân sách đặt trên chính `GraphicsTier` chứ không tạo enum tier thứ hai — ba mức đồ hoạ
      là **một khái niệm**, chỉ khác đơn vị giữa hai nhánh (tỉ lệ ảnh cho sprite, tam giác cho
      model). Số khớp **chính xác cả 45 giá trị `tri_counts`** trong sidecar. Vi phạm được gom báo
      **một lượt** thay vì gãy ở file đầu tiên — nhờ vậy nhìn ra ngay là bản xuất hiện tại lệch một
      **lượng cố định mỗi nhân vật**, không phải lệch ngẫu nhiên từng file (dẫn thẳng tới mục 32).
    - ✅ **V20 + `art_asset` nhận nhánh MODEL** (`bbff6b1`, `4ce1e99`) — `asset_kind`/`model_name`/
      `tri_counts` + `CHECK art_asset_kind_branch`, verify thật trên Postgres 17 (V20 áp qua Flyway
      thật lên trên dữ liệu V19 sẵn có **và** trên container rỗng, 84 test `server-database` xanh; 15
      dòng model phủ 45 phép đo tier; chạy lần hai md5 payload không đổi; `CHECK` từ chối đủ 4 hình
      dạng lai giữa hai nhánh). Một dòng cho mỗi `asset_key` dù có 3 file `.glb`, vì ba tier chỉ
      khác nhau một con số và con số đó nằm vừa trong `tri_counts`. **Phát hiện thật**: quy ước đặt
      tên suy ra `blend_path` **nói dối ở đúng 1/15 model** (`char_hoa-b1` xuất từ
      `hoa-text3d-pilot.blend` trong khi `hoa-b1.blend` cũng tồn tại) — một cột sinh ra để render
      lại được asset mà trỏ sai file thì vô giá trị, nên nó đọc từ sidecar. `render_dir` chỉ ghi khi
      contact sheet nghiệm thu **thật sự có trên đĩa** (3 model chưa có): một đường dẫn 404 đọc
      giống bằng chứng trong khi không phải.
    - ✅ Tài liệu theo sau: `RENDERING.md` viết lại quanh **hai nhánh render** (`ce6417e`, `4b74d74`,
      `0af35e5`) và gỡ các dòng còn ghi ADR-032 là `Proposed` ở TECHNOLOGY-STACK/ROADMAP/hai sổ ADR
      (`537b9d9`).

32. ✅ **Ngân sách tam giác đạt 45/45 — sổ việc của ADR-031 đóng hoàn toàn (2026-08-08)**: xuất lại cả
    15 nhân vật × 3 tier qua phiên Blender MCP, xác nhận 45 file `.glb` hết `texCoord` sai và nạp
    được — pipeline chuyển từ "sập ngay" sang "gãy đúng ở lỗi ngân sách đã biết" (`9e7e0c5`); chủ dự
    án chốt **ngân sách là TỔNG xuất xưởng, tính cả vũ khí**, nên script hạ mesh thân theo *ngân
    sách trừ tam giác vũ khí* (`b774932`); xuất lại lần cuối, `tools:asset-pipeline` báo **"ngân
    sách tam giác: OK (45/45)"** và chạy sạch qua bước pack atlas (`a4e58bd`). Mục #8 và #9 — hai
    mục cuối trong sổ của ADR-031 — đóng. Cùng commit đó dọn luôn đoạn `/CLAUDE.md` §0 vẫn ghi
    `gdx-gltf` chưa nối và ADR-032 còn `Proposed`, **cả hai đã xong từ nhiều ngày trước**.

33. **Client có giao diện thật lần đầu (2026-08-08)** — trước ngày này client không có gì ngoài di
    chuyển thô:
    - ✅ **HUD combat đầu tiên** (`334ff1e`): mặt Scene2D thật đầu tiên (Stage/Skin/widget, chủ đề
      màu phẳng dựng runtime nên chưa cần asset art), thanh Máu/Khí, mục tiêu, nút chiêu. Dải opcode
      400-406 sẵn sàng trên dây từ Phase 4 mà **không một dòng client nào tiêu thụ**. Kiểm chứng
      trọn vòng với server + Postgres + Redis thật — và **một giả định sai bị sửa bằng log packet
      tạm chứ không phải phỏng đoán**: hình nộm luyện tập **có đánh trả**.
    - ✅ **Nút chiêu tra theo hệ phái người chơi** (`9f7809a`) — trước đó hardcode `fireball` cho mọi
      hệ, nghĩa là 4/5 hệ bấm nút thì server **âm thầm bỏ `UseSkillCommand`**.
    - ✅ **Màn Đăng nhập + Chọn nhân vật** (`76a9967`): bỏ bắt tay chặn luồng trước khi cửa sổ mở,
      thay bằng luồng Scene2D thật theo đúng nếp poll-trong-`fixedUpdate` mà `WorldScreen` đã dùng,
      không bao giờ chặn luồng render. Kèm rò rỉ thật đã vá: `KageEngine.setScreen` trước đây chỉ gọi
      `hide()` chứ **không `dispose()`** màn hình cũ, nên Stage/font của Login/CharacterSelect rò mỗi
      lần chuyển màn. Cũng dọn các vi phạm checkstyle/PMD/SpotBugs còn sót từ `334ff1e` — commit HUD
      đó **chưa bao giờ thật sự chạy qua gate `check` đầy đủ**.
    - ✅ **Lỗi hồi quy thật do chính luồng mới sinh ra** (`198c44a`): libGDX chỉ gọi `resize()` lúc app
      khởi động thật và khi cửa sổ đổi kích thước — **không bao giờ khi đổi Screen giữa phiên**. Đi
      qua Đăng nhập → Chọn nhân vật → Thế giới thì `FitViewport` và camera của renderer còn nguyên
      giá trị mặc định 0, ma trận chiếu sụp về không: **thế giới đen trơn trong khi HUD vẫn đúng**
      (Stage tự áp viewport của nó mỗi khung bất kể). Tìm ra bằng cách **tái hiện sống rồi log
      `viewportWidth/Height` lúc `show()`** — xác nhận 0/0 trước khi sửa — chứ không suy từ triệu
      chứng.
    - ✅ **Hồi sinh khi HP về 0 ở thế giới chính** (`27a376a`): trước đó `onCharacterDefeated` là
      no-op ngoài Arena nên nhân vật **nằm chết vĩnh viễn**. Thêm `CharacterRespawnedEvent` (305),
      chỉ áp cho world chính (Arena/Dungeon giữ luồng kết thúc trận riêng). Kèm **1 bug thật lộ ra
      khi test chính nó**: kẻ hạ gục không xoá mục tiêu tự đánh của mình, nên đối tượng vừa hồi sinh
      **bị giết lại mỗi chu kỳ đánh** thay vì trận kết thúc gọn.
    - ✅ `UI.md` đồng bộ lại với thứ **thật sự** được xây (`d36b668`) — cây file §3 vẫn còn là bản kế
      hoạch trước khi code, và 2 mục "Việc cần làm tiếp theo" đã xong từ phiên trước mà chưa ai đánh
      dấu.

34. **Vòng song song #7 và #8 — render, hoạt ảnh, và một chẩn đoán sai bị lật lại (2026-08-08)**:
    - ✅ **Đo frame time thật, đóng Revisit Trigger #1 của ADR-031** (`5d9446d`): 20 nhân vật đi qua
      đúng `CharacterModelRenderer` production, quay vòng đủ 15 tổ hợp hệ phái × bậc trang phục tier
      med, đo 6000 khung sau warmup trên phần cứng thật (iGPU Intel RaptorLake-S Mobile, GL 4.6) —
      **p95 0,30-0,31ms, max 1,28-2,92ms, xa dưới ngưỡng 8ms**. Là công cụ chẩn đoán thủ công chứ
      không phải test JUnit (cần GL context thật) nên chạy qua task `runFrameBenchmark`, **không bao
      giờ chạy trong CI headless**. Kiểm cấp phát mỗi khung bằng JFR (có sẵn trong JDK, không thêm
      dependency): 0 mẫu rơi vào vòng lặp render so với 253 mẫu lúc nạp `.glb` — **tín hiệu thật
      nhưng KHÔNG phải bằng chứng cấp phát bằng 0**, lấy mẫu theo TLAB bỏ sót cấp phát nhỏ/thưa. Ghi
      kèm giới hạn trung thực: chưa có máy tham chiếu chính thức, chưa cel shader/viền, chưa culling,
      desktop chứ không phải Android.
    - ✅ **Hoạt ảnh nối vào trạng thái gameplay** (`89f1e19`) — model đứng nguyên tư thế bind vì
      renderer nạp `Scene` cho mỗi nhân vật mà **không bao giờ chạy `AnimationController`**. Nhân vật
      KHÁC không có trường vận tốc nào trên dây và server **không bao giờ gửi gói "đã dừng"**, nên
      `RemoteMotionTracker` suy ra chuyển động từ hiệu vị trí giữa các `CharacterMovedEvent` cộng
      timeout. Xác minh dây nối gdx-gltf bằng cách **dịch ngược `gltf-2.3.0.jar` đã vendored** thay
      vì tin tài liệu. Hồi sinh của nhân vật ở xa không có tín hiệu giao thức nào nên suy từ
      `CharacterMovedEvent` đầu tiên sau `die`.
    - ✅ **Cel shader + viền vỏ lộn** (`a08f5ef`) — **xung đột thật khi gộp**: nhánh cel shader **bỏ
      hẳn `SceneManager`** để đổi sang hai lượt `ModelBatch` thô, gộp máy móc thì hoạt ảnh vừa vào
      sẽ **chết im lặng**. Hoá ra `Scene#update()` — **không phải `SceneManager`** — mới là thứ đẩy
      `AnimationController`, nên bỏ `SceneManager` là an toàn và **giữ được cả hai**; đo lại trên GPU
      thật p95 0,30ms → 0,52ms, vẫn dưới ngân sách 8ms hơn 15 lần.
    - ✅ **Nhân vật nằm ngang — HAI lỗi thật trong Java/GLSL, KHÔNG phải asset** (`7cce995`): nghi ngờ
      ghi ở ADR-031 note #11 (một phép xoay Y 35° bake sẵn trong node gốc của rig) được điều tra và
      **loại bỏ hoàn toàn**. Thủ phạm thứ nhất: `CelBodyShader`/`CelOutlineShader` **cố ý bỏ nhân
      `u_worldTrans`** dựa trên tiền đề sai là `Renderable.bones[i]` đã gồm biến đổi gốc — bác bỏ
      bằng cách **đọc thẳng `gdx-1.14.2-sources.jar`**: với `NodePart` có skin,
      `Renderable.worldTransform` bằng đúng `ModelInstance.transform` và
      `Node.calculateWorldTransform()` **không hề đọc** `ModelInstance.transform`. Bỏ `u_worldTrans`
      tức là bỏ **cả** phép đổi Y-up→Z-up **lẫn** vị trí thế giới của nhân vật ⇒ mesh vẽ trong toạ độ
      cục bộ Y-up thô, đúng nghĩa "nằm bẹp". Thủ phạm thứ hai: `Y_UP_TO_Z_UP` sai dấu (+90f thay vì
      -90f) — **vô hại chừng nào lỗi thứ nhất còn đó** (dấu của một ma trận bị bỏ qua thì không có
      nghĩa gì), nên chỉ lộ ra sau khi vá lỗi #1. Không phải xuất lại `.glb` nào.
      ⚠️ **Kết luận -90f này về sau bị lật lại lần nữa — xem mục 35, và đó mới là câu chuyện thật.**
    - ✅ **Thư mục model tra theo `GraphicsTier`** thay vì hardcode `models/med/` (`fec30ef`), với một
      enum tier **cục bộ của client** tách khỏi enum của `tools:asset-pipeline` (client-core không
      phụ thuộc module đó). Phạm vi cố ý hẹp — không đổi tier lúc chạy, không màn hình cài đặt,
      không lưu Preferences, không tải asset qua mạng — và `RENDERING.md` được sửa để **nói thẳng
      rằng `ensureAssetsAvailable(tier)` của ADR-028 vẫn chưa tồn tại ở đâu trong repo**, để không ai
      nhầm đây là ADR-028 đã xong phía client.
    - ✅ **Kiểm asset mồ côi** (`5a389e9`) — đóng nốt chỗ hở ghi ở mục 29: `OrphanAssetChecker` đối
      chiếu đĩa với `art_asset` theo **cả hai chiều** cho **cả hai** thư mục `atlases/` và `models/`,
      gãy build khi đĩa có file không có dòng DB.
    - ✅ Bug tư thế nằm ngang được ghi lại kèm **đề xuất ADR-033** (di chuyển kiểu Ninja School
      Online) ngay trong ngày (`0c3a77a`).

35. **ADR-033 + ba vòng playtest của chủ dự án (2026-08-09) — bài học đắt nhất trong cả đợt**:
    - ✅ **[ADR-033](../01-architecture/adr/ADR-033-Ninja-School-Style-Movement.md) Accepted
      2026-08-08**, thi hành ở `328b019`: bỏ di chuyển tự do 4 hướng, đổi sang trái/phải + **nhảy
      thật có tác dụng gameplay** (né đòn tầm thấp, lên bệ cao). `JumpCommand` (306) +
      `MoveResponse` thêm `z`/`grounded` (chỉ thêm, giữ nguyên field đang dùng);
      `VerticalMovementSystem` tự viết theo ADR-021 (**không Box2D**); `CombatSystem` cho đòn thường
      né TUYỆT ĐỐI khi mục tiêu đang trên không. Kiểm chứng đủ tầng — unit, integration, **E2E qua
      socket thật**, và JMH cho thấy **<0,2% ngân sách tick 16,6ms ở N=5000 kịch bản xấu nhất**. AI
      quái (ADR-022) rà lại và **không cần sửa**: nó chỉ đọc `(x,y)` hiện tại, không phụ thuộc trục
      di chuyển.
    - ⛔ **Nhưng bản build đó — xanh toàn bộ test — KHÔNG chơi được.** Chủ dự án test tay và báo 3
      lỗi; truy ra **3 nguyên nhân khác nhau, trong đó 2 cái là do lượt trước tự tin sai**
      (`c24668a`):
      (a) **Nhân vật lộn ngược.** `Y_UP_TO_Z_UP` quay lại +90f — **bản sửa ở mục 34 đổi sang -90f là
      SAI**. Lý do kết luận sai lần đó là **CÔNG CỤ CHỤP, không phải engine**:
      `ScreenUtils.getFrameBufferPixmap` trả dữ liệu GL thô (dòng 0 = đáy màn hình) còn PNG quy ước
      dòng 0 = đỉnh, và `writePNG` bản 2 tham số mặc định `flipY=false` ⇒ ảnh lưu ra **bị lật dọc**,
      nên một nhân vật lộn ngược hiện ra thành "đứng thẳng". Phép toán bàn tay phải **đúng ngay từ
      đầu**. Đây là **lần thứ hai** dự án dính đúng cái bẫy đường chụp (lần đầu ở `c521af3`, mục
      31) — nay thành luật trong `RENDERING.md`: **ảnh chụp chỉ là bằng chứng khi ĐƯỜNG CHỤP đã được
      kiểm chứng**.
      (b) **Điều khiển ngược.** Camera yaw 35° làm `right` = `(-0,71, +0,50, 0)`, tức world +X chiếu
      ra thành hướng **sang TRÁI màn hình**, lại còn lệch chéo 19°. **Không phải lỗi của ADR-031** —
      hồi đó đi tự do 2 chiều nên không trục nào đặc biệt; ADR-033 khoá di chuyển vào **một trục**
      nên camera **buộc** phải thoả `cos(yaw) < 0`. Đổi camera về 180° và chuyển góc 3/4 của nhân
      vật sang **xoay MODEL**, tách "nhân vật nghiêng bao nhiêu" khỏi "trục gameplay nằm đâu trên
      màn hình". **Khoá một trục gameplay cũng là ràng buộc góc phương vị của camera** — hệ quả
      không hiển nhiên cho tới khi có người thật cầm bàn phím.
      (c) **Bậc vô nghĩa — 2 lỗi chồng nhau.** `VerticalMovementSystem` thoát sớm **vô điều kiện**
      khi `grounded && vz == 0` nên đi ra khỏi mép bậc là **lơ lửng vĩnh viễn**; và client có bản
      `VerticalMovementSystem` riêng để dự đoán nhưng **chưa nạp danh sách bệ**, lệch 0,8m < ngưỡng
      snap 1,0m nên **không bao giờ được sửa** — mắt luôn thấy rơi xuyên bệ. Bài học chung: **hệ
      thống dự đoán ở client phải được cho ăn cùng dữ liệu thế giới với server.**
      Cùng gốc rễ "thu trục": quái đứng ở y=5 mà người chơi khoá ở y=0, tầm đánh 2m ⇒ **không bao giờ
      chủ động đánh được** (chỉ "chạy" nhờ quái tự đuổi tới); NPC tĩnh ở y=-5 thì **vĩnh viễn không
      tương tác được**. Và **17 sprite VFX đã nằm trong atlas từ lâu mà không một dòng code client
      nào tham chiếu tới** — dùng chiêu thì màn hình không hiện gì.
    - ✅ **[ADR-034](../01-architecture/adr/ADR-034-Manual-Attack-And-Experience.md) Accepted
      2026-08-09** — chủ dự án quyết ngay trong phiên playtest, sau khi thấy máu quái tụt mà mình
      không bấm gì (`2111b1d`): nhảy đôi (server đếm và chặn số lần nhảy, đó mới là thứ ngăn client
      sửa đổi bay lên trời), click chọn mục tiêu, `AttackCommand` (407) — đánh thường **phải bấm mới
      ra đòn** cho người chơi, quái vẫn tự đánh — và **hệ thống kinh nghiệm đầu tiên**
      (`ExperienceChangedEvent` 208): `character.exp`/`level` có từ **V2 mà chưa ai đọc**, nên EXP
      nhiệm vụ tích từ Phase 6 tới giờ mới được tính. **Không đổi schema.** Test giới hạn nhảy đôi
      được **kiểm bằng đột biến hai chiều** (đặt `MAX_AIR_JUMPS` = 0 và = 99 đều làm test đỏ).
    - ✅ **Nói cho người chơi biết chuyện gì vừa xảy ra** (`c0f66c4`) — phòng đúng lớp phàn nàn đã đẻ
      ra ADR-034: chữ "LÊN CẤP!" (cờ `leveled_up` đã có sẵn trên dây, **client đang vứt đi**), và lý
      do đánh hụt. Ngoài tầm vẫn **GỬI** lệnh đi: client chỉ **đoán** tầm đánh, server vẫn là bên
      phán quyết. Hai lỗi bố cục do **ảnh chụp HUD bắt được chứ không phải build**: số EXP tràn hẳn
      khỏi khung ở thanh 12px, và hai nút tròn khác cỡ trong cùng lưới 2 hàng kéo giãn hàng biển tên.
    - ⚠️ **Từ đây `WorldScreen` ngồi đúng trần PMD CyclomaticComplexity của lớp** — `2111b1d` đã phải
      tách `JumpInputController`/`AttackInputController`/`CombatFeedbackPresenter`/
      `WorldCommandSender`. Trần này còn bị chạm thêm 4 lần nữa trước khi được gỡ thật ở mục 39.

36. **Quái/NPC hết vô hình, và vòng nhiệm vụ của Phase 6 lần đầu chạy được (2026-08-09)**:
    - ✅ **Quái có model 3D** (`0ee7a60`) — trước đó quái **luôn** là một khối hộp đỏ
      `ShapeRenderer`, vì pipeline art 3D chỉ từng nhắm tới nhân vật. Mở **đường xuất `.glb` thứ
      hai** (`--flat-materials`) cho asset dựng tay: `export_character_lods.py` giả định 1 mesh + 1
      UV + 1 albedo đã bake nên **gộp mọi material slot**, làm mất mảng bia đỏ. **Bẫy không gian màu
      thật**: `baseColorFactor` phải ghi theo màu **HIỂN THỊ**, không phải linear như spec glTF — bản
      đúng spec cho ra con quái **gần như đen kịt** trên màn hình (kết luận từ ảnh chụp, không suy
      đoán), vì không có bước gamma nào ở cuối đường. Và `animate()` với clip không tồn tại **ném
      `GdxRuntimeException` giữa game loop** — một model thiếu clip đánh sập cả khung hình ⇒
      `clipOrIdle`. Cũng sửa một tiền đề sai của script dựng bia tập từ 2026-08-01 ("bia tập không di
      chuyển, không phản công"): `WorldFacade.spawnTrainingDummy` gắn cả `AiStateComponent` lẫn
      `AttackCooldownComponent`.
    - ✅ **NPC hiện ra** (`1c8b906`) — server phát `NpcSpawnedEvent` từ Phase 6 nhưng **không một dòng
      client nào đọc nó**, nên ông trưởng làng — **cửa vào toàn bộ hệ hội thoại và nhiệm vụ đã làm
      xong từ Phase 6** — chưa bao giờ hiện lên màn hình. Hộp thay thế của NPC màu xanh ngọc chứ
      không đỏ như quái: "đánh được hay nói chuyện được" là phân biệt đáng đọc ngay từ xa, và **một
      mã NPC chưa có art phải HIỆN RA chứ không được biến mất** — vô hình đúng là thứ vừa sửa xong.
    - ✅ **Nói chuyện được với NPC** (`57aa10d`, dải 602-605) rồi **đóng trọn vòng nhiệm vụ**
      (`239bdfa`, dải 606-609): nói chuyện → nhận → giết 3 hình nộm → HUD báo hoàn thành → lĩnh 500
      EXP + 5 kunai. **Cả hai dải trước đó chưa từng được client dùng một gói nào** dù server xong từ
      Phase 6. Một chỗ suýt mất việc âm thầm: `AcceptQuestResponse` có nghĩa với **CẢ HAI**
      coordinator (cập nhật nhiệm vụ vs đóng khung thoại) nên `WorldScreen` gọi cả hai chứ không
      dừng ở cái đầu tiên trả `true` — **một `else if` quen tay sẽ nuốt mất một trong hai việc**.
      Khoá idempotency (ADR-025) sinh **một lần rồi giữ nguyên** qua các lần thử lại của cùng một lần
      lĩnh; sinh mới mỗi lần bấm sẽ biến chính cơ chế chống trùng thành vô dụng.
    - 🛑 **Lỗ giao thức thật phát hiện ngay tại đây**: `quest_code` chỉ đến từ chuỗi `action` của lựa
      chọn hội thoại, và **không opcode nào hỏi được "tôi đang làm nhiệm vụ gì"** ⇒ **nhận nhiệm vụ
      rồi đăng nhập lại là mất thưởng VĨNH VIỄN**, dù server nhớ đủ. Ghi lại chứ **không tự vá** —
      vá là thêm opcode = đổi protocol (§4). Đây chính là **Khoảng trống 4** mà ADR-035 sau này lấp,
      và bug thật sự được sửa ở mục 39.

37. **Tầng xã hội client (2026-08-25)** — bốn hệ thống server đã xong từ Phase 6-7 mà client phủ
    **0 opcode**:
    - ✅ Chat (`2b70d51`, 800-802), Nhóm (`7aa0eb6`, 1300-1317), Bạn bè (`f174942`, 1100-1113), Bang
      hội (`9d6b14a`, 700-719). Cùng một bộ ba lớp cho cả bốn: controller **thuần Java test được** /
      panel **chỉ vẽ**, Stage riêng để đặt thứ tự nuốt input / coordinator nối trạng thái + mạng.
    - ✅ **Hai bẫy thật của khung chat**: phím gameplay được **POLL** qua `Gdx.input.isKeyPressed`,
      tức đi vòng qua lưng `InputProcessor` — Stage của khung chat nuốt phím **cũng không ngăn được**
      chữ "a" vừa gõ đồng thời làm nhân vật chạy sang trái. Và **font UI là một tập glyph ĐÓNG**: vô
      hại khi mọi chữ đến từ bundle i18n của mình, thành bẫy thật **ngay khi màn hình bắt đầu hiện
      chữ do NGƯỜI KHÁC gõ** ⇒ `FontFactory.canRender(char)` dùng cho cả bộ lọc ô nhập lẫn sanitize
      chữ đến. Hệ quả kéo theo ở Bạn bè: tên phải giữ **HAI bản** — bản THẬT để gửi lên server và bản
      đã sanitize để hiện ra, vì `RemoveFriendCommand` nhận `target_name`, gửi bản hiển thị lên là
      gửi **một cái tên KHÁC** và người bạn có tên chứa ký tự lạ sẽ **không bao giờ xoá được**.
    - ✅ **Ba bug thật chỉ lộ ra khi gộp 3 luồng song song** (`187024f`) — không luồng nào tự thấy
      được vì mỗi luồng chỉ nhìn thấy panel của chính nó: (1) **phím mở cửa sổ rò sang nhau** — gõ
      "p" trong ô chat bật cửa sổ Nhóm, gõ "t" trong ô tên bạn bè bật ô chat; ba luồng nghĩ ra ba
      cách chữa khác nhau (một cái chưa chữa gì), nay gộp về **MỘT** nguồn chân lý
      `WorldScreen.isTextInputActive()`. (2) **Hai cực ngược nhau trên cùng một API** —
      `setOpenBlocked` (true = chặn) vs `setCanOpen` (true = cho phép), và một coordinator còn **tự
      đảo dấu bên trong**: truyền cùng một supplier cho cả hai cho ra hai hành vi trái ngược, cửa sổ
      **chỉ mở được đúng lúc đáng lẽ phải bị chặn**. (3) Chính script gộp xung đột **cắt mất 2 thẻ
      `</Match>`** trong `config/spotbugs/exclude.xml` — **SpotBugs im lặng bỏ qua CẢ FILE hỏng**,
      nên mọi exclusion mất tác dụng cùng lúc và chỉ lộ ra vì gate bắt đầu báo những lỗi vốn đã được
      duyệt từ lâu.
    - ✅ **[ADR-035](../01-architecture/adr/ADR-035-Client-State-Sync-Opcodes.md) viết Proposed**
      (`01a7db0`): client mới dùng **40/150 opcode** và **0/7 opcode dải Inventory**; đề xuất 7 opcode
      lấp 4 khoảng trống, trong đó Khoảng trống 4 chính là bug mất thưởng sau relog ở mục 36. Khuyến
      nghị catalog theo **A′** (gửi qua mạng kèm `version` do server phán quyết) chứ **không** đóng
      gói tĩnh như ADR-028: catalog chỉ nặng kilobyte nhưng đổi mỗi lần cân bằng game, và lệch phiên
      bản thì **hiện chỉ số sai trong im lặng**.
    - ✅ Sửa dòng chỉ mục ADR-032 ghi `Proposed` trong khi chính file ADR ghi `Accepted` (`5baebe8`) —
      **lỗi sổ sách, không phải mâu thuẫn cần quyết**; nhân đó đối chiếu **cả 34 ADR** giữa ô
      `Status` trong từng file và cột trạng thái trong bảng, sau thay đổi không còn dòng nào lệch.
    - ⛔ **Khoảng trống giao thức ghi lại chứ không tự vá**: `party.proto` và `guild.proto` **không có
      một event đẩy nào** — bị mời vào nhóm, bị đuổi, đơn xin được duyệt, bang bị giải tán: client
      **không được báo gì hết**. Vì vậy toàn bộ trạng thái ở client là **ẢNH CHỤP**, coordinator hỏi
      lại server sau **MỌI** lệnh (kể cả lệnh hỏng — một lệnh hỏng vì "đã ở trong nhóm rồi" **chính
      là bằng chứng ảnh chụp đang sai"**), và không chỗ nào suy trạng thái mới từ một chữ OK
      (Điều 15).

38. **Vòng song song #9 — ADR-035 vào code, và một lỗ ĐÚC VÀNG thật (2026-08-28)** (`f685824`,
    `fb86621`, `393fc41`; độ phủ opcode client **92/151 → 121/158**):
    - ✅ **ADR-035 Accepted 2026-08-27** (`f9fa566`) — chủ dự án duyệt **nguyên đề xuất**: cả 7 opcode,
      catalog theo **A′**; 7/8 Technical Decision chốt (#8 — thống nhất id-vs-code toàn protocol —
      vốn cố ý ngoài phạm vi, vẫn Mở). Cùng commit **đính chính một chỗ SAI trong chính ADR**: bản
      viết 2026-08-25 ghi "3 icon vật phẩm mới chỉ có nguồn `.blend`", kiểm lại thì icon **đã có**
      trong atlas cả 3 tier. Thi hành ở `783131f`: LIST_INVENTORY 507/508, ITEM_CATALOG 509/510,
      GOLD_CHANGED_EVENT 209, LIST_QUESTS 610/611 — **chỉ THÊM**, không đổi message đang chạy, không
      đổi schema, không tăng `protocolVersion`; **511 cố ý để trống** cho `ITEM_REMOVED_EVENT` sau
      này. `GoldLedger` thành **điểm ghi `character_stat.gold` DUY NHẤT**, có test quét mã nguồn gác.
    - 🔴 **Lỗ đúc vàng và nhân bản vật phẩm qua hòm thư** (`63b277c`) — lỗi nghiêm trọng nhất tìm được
      trong cả đợt: `SendMailUseCase` chỉ có `MailRepositoryPort`, nên `gold_amount` và danh sách
      đính kèm đi **thẳng từ packet client vào bảng `mail`/`mail_item` mà không ai bị trừ gì**, trong
      khi `ClaimMailUseCase` **cộng vàng thật và cấp item thật**. Tự gửi thư cho chính mình rồi nhận
      = **đúc vàng từ không khí**; gửi cùng một món cho 10 người = **nhân bản 10 lần**. Vi phạm
      thẳng Server Authority. Vá bằng **ký quỹ tài sản người gửi trong CÙNG transaction** với
      `INSERT INTO mail` (thiếu ở bước nào thì rollback cả phần đã trừ), đi qua `GoldLedger` nên
      người gửi nhận `GoldChangedEvent`. Test: 20 test Postgres thật gồm **test hồi quy chống đúc
      vàng** và test bảo toàn tổng vàng giữa hai người. **Chủ dự án quyết thứ tự: bịt lỗ server
      trước, gộp giao diện hòm thư sau** — vì chính giao diện đó là thứ khiến lỗ này với tới được từ
      client thật.
    - ✅ Hòm thư (`d22c9f2`, 900-908), đấu tay đôi + xếp hạng PvP (`8103d2f`, 1600-1611 + 1200-1201),
      hầm ngục (`4eed8ac`, 1500-1503) và chọn hệ phái Lv10 (206/207).
    - ✅ Ba thứ chặn gộp được dọn (`0d1e00a`), trong đó **`kage_character_base.blend` là file DUY NHẤT
      trong 44 file `.blend` bị lưu dạng raw** trong khi `.gitattributes` bắt cả `assets/raw/**` qua
      LFS — hệ quả: **mọi worktree và mọi clone mới thấy nó "modified" vĩnh viễn**. Nội dung KHÔNG
      đổi (md5 và số byte khớp trước/sau), chỉ chuyển chỗ lưu; phải dùng `git add --renormalize` vì
      `git add` thường bỏ qua do stat cache nói file không đổi nên clean filter không chạy
      (`593a26c`).
    - ⛔ **Khoảng trống nói thẳng ra chứ không tự vá**: đính kèm thư chỉ mang `item_template_id` (đúng
      là Khoảng trống 2 của ADR-035, khi đó còn Proposed) nên hiện "Vật phẩm #12 x3" — **không bịa
      tên, không tự thêm opcode catalog**; `MailView.sender_id` là id trần nên hiện "Người chơi #42";
      `ClaimMailResponse`/`DeleteMailResponse` **không mang `mail_id`** nên hai lệnh chồng nhau không
      phân biệt được. Đấu tay đôi: `ChallengeDuelCommand` thách theo **TÊN** mà
      `CharacterSpawnedEvent` không mang tên nhân vật ⇒ client bù bằng sổ tên LRU 64 dòng nhặt từ
      chat/bạn/nhóm/bang. Hầm ngục: **chỉ người GỬI lệnh nhận `EnterDungeonResponse`**, các thành
      viên còn lại **bị server kéo sang World khác trong im lặng tuyệt đối** — nên nút "Rời hầm ngục"
      **luôn bấm được**, đó là lối thoát duy nhất của họ.
    - ⚠️ **Hai chỗ SERVER KHÔNG VALIDATE mà client đang là hàng rào duy nhất** (mô tả, không tự sửa):
      `subject`/`body` vượt `VARCHAR(64)`/`VARCHAR(1024)` thành `SQLException` trong `authExecutor`
      **chứ không phải outcome**, và `gold_amount` có `CHECK (>= 0)` nhưng **không ai kiểm trước**.
      Client chặn cả hai để người chơi không vấp vào hố, nhưng đó **KHÔNG thay được validate phía
      server**. (Bản vá `63b277c` sau đó đã bịt phần này bằng `MailDraft` + 6 outcome mới.)
    - ⚠️ **Ký quỹ chỉ nhận món chưa cường hoá** (`enhance_level = 0`): `mail_item` và
      `MailAttachmentView` không chở `enhance_level` nên gửi một món +7 sẽ **xoá mức cường hoá vĩnh
      viễn**. Mở ra cần đổi schema + protocol ⇒ chờ chủ dự án.
    - ⚠️ **`WorldScreen` chạm trần Checkstyle MethodLength LẦN THỨ TƯ** và PMD CyclomaticComplexity
      của lớp lên **đúng 80** (ngưỡng đỏ) — **tách hàm nào cũng làm đỏ**, đã thử và đo, nên nhánh gộp
      phải gộp lại một dòng chú thích. Ghi rõ lúc đó: lớp này cần tách thật, và đó là thay đổi kiến
      trúc tầng client. Cũng ở đúng điểm giao hai luồng, **test của nhánh hòm thư bắt được một lỗi
      thật**: bản vá server thêm 6 giá trị `SendMailOutcome` mà client **chưa có khoá i18n nào**,
      người chơi sẽ đọc "không rõ lỗi" đúng vào lúc cần biết vì sao gửi hụt.

39. **Vòng #10 — bốn màn hình ADR-035 và playtest SỐNG 9 màn hình (2026-08-29)**:
    - ✅ **Gỡ trần `WorldScreen` TRƯỚC, làm giao diện sau** (`8015bac`): mỗi tính năng có cửa sổ riêng
      phải được nhắc tên ở **chín chỗ rời rạc**, nên bốn màn hình còn lại của ADR-035 **không có chỗ
      để cắm vào**. `PanelHub` — sổ đăng ký MỘT chỗ cho mọi khung HUD: thứ tự đăng ký là thứ tự nuốt
      input, thứ tự vẽ **suy ngược** (khung nhận input trước là khung nằm trên cùng), thứ tự dispose
      **suy xuôi** (panel mượn skin của `CombatHud` nên phải trả tài nguyên trước chủ skin). Thêm một
      cửa sổ mới giờ tốn **đúng một dòng và 0 điểm cyclomatic complexity**. `WorldScreen` 928 → 734
      dòng, tổng complexity **81 → 63**.
    - 🔴 **Dấu xung đột git lọt thẳng lên master** ở vòng gộp hôm trước (`393fc41`) — ba dòng
      dấu xung đột chuẩn của git (ba dòng `HEAD` / phân cách / tên nhánh) nằm trong **CẢ HAI** bundle
      i18n — chính tài liệu này cố ý **không viết ra nguyên văn** ba chuỗi ký tự đó, để phép grep
      quét dấu xung đột trên toàn cây không báo nhầm ở đây. **Không gì bắt được**: file
      `.properties` không được biên dịch, `Properties.load` **nuốt dòng rác thành khoá vô nghĩa thay
      vì báo lỗi**, và không test nào đọc toàn bộ bundle. Nội dung hai nhánh đều đúng và bổ sung cho
      nhau — chỉ ba dòng dấu là rác. Gỡ xong: 274 khoá mỗi bundle, khớp nhau chính xác; và
      `MessageBundleIntegrityTest` gác **ba thứ** từ nay (không dấu xung đột, hai bundle cùng tập
      khoá, không khoá nào rỗng) — `80cf536`. **Bài học: build xanh vẫn có thể giấu rác; grep dấu
      xung đột trên TOÀN cây khi gộp song song.**
    - ✅ Hai mảnh **dữ liệu nền dùng chung** tách ra trước để hai giao diện không viết một thứ hai lần
      (`771838f`): `ItemCatalog` (509/510 — version do **SERVER** phán quyết theo A′, client chỉ lưu
      chuỗi server gửi rồi gửi trả; `unchanged=true` nghĩa là **giữ nguyên bản cũ, KHÔNG phải catalog
      rỗng**) và `GoldCoordinator` (209 — số dư **TUYỆT ĐỐI** từ server, client **không bao giờ** tự
      cộng trừ theo giá món hàng nó vừa bấm mua; mất một gói thì gói kế tự sửa lại).
    - ✅ Túi đồ + trang bị (`74c9d56`, 501-504/507-508), cửa hàng (`81fa74f`, 1000-1005), nhật ký
      nhiệm vụ (`b327d0a`, 610/611) — **và chính bug mất thưởng sau relog ở mục 36 được SỬA THẬT**,
      có test tái hiện đúng kịch bản (nhận → vứt tracker mô phỏng relog → `ListQuestsResponse` →
      lĩnh được). Icon tra atlas MEDIUM theo `item_template.code`, **để trống khi thiếu vùng chứ
      không vẽ hộp màu bịa**. Cửa hàng cũng gỡ được một chỗ nuốt gói: `DialogueCoordinator` từng nuốt
      `ListShopItemsResponse` thành câu "chưa mở được cửa hàng" — `PanelHub#dispatch` phát gói cho
      **MỌI** luồng nên không ai giấu gói của ai.
    - ✅ **Hai agent song song cùng tự phát hiện một lỗi và cùng báo lên đúng chỗ** (`f487598`):
      `ItemCatalogCoordinator`/`GoldCoordinator` chỉ có **đúng MỘT ô listener**, nên lời gọi sau giẫm
      lên lời gọi trước và giao diện **thua IM LẶNG** — không lỗi, không log, chỉ sai (catalog về
      muộn thì không bao giờ vẽ lại; số vàng cũ hiện mãi mãi).
    - 🔴 **Playtest sống 9 màn hình bắt được lỗi KẸT CỨNG giao diện mà 1000+ test không thấy**
      (`bbbfaf4`): `ShopCoordinator.onListings()` gọi `panel.open()`, `open()` chạy
      `onOpened = listShopItems()`, server trả lời, `onListings()` **lại gọi `open()`**… Mở cửa hàng
      bằng phím B = **không đóng được (dù panel ghi "Nhấn ESC để đóng"), không mở được cửa sổ khác,
      không đi lại, không đánh** — kèm spam `ListShopItemsRequest` liên tục lên server. Cùng lượt:
      dòng gợi ý chỉ nói phím T và P nên **sáu cửa sổ mới không có cách nào để người chơi biết là
      chúng tồn tại**; và `MessageBundleIntegrityTest` thêm phép gác "mọi ký tự trong bundle phải nằm
      trong tập glyph của `FontFactory`" — **bắt được ngay lỗi đầu tiên**: dấu chấm giữa (U+00B7) vừa
      định dùng làm dấu phân cách trong chính dòng gợi ý đó, **nó sẽ biến mất không dấu vết**.
    - ✅ **Ô nhập trống không nhãn — hoá ra ở BỐN panel, không phải một** (`1b09fc3`): hòm thư, bang
      hội, bạn bè và đấu tay đôi đều gọi `setKeyboardFocus(field)` ngay khi mở, mà `TextField` của
      libGDX **chỉ vẽ `messageText` khi KHÔNG có focus** ⇒ **tự lấy focus là tự xoá mất chữ gợi ý của
      chính mình**: người chơi nhìn thấy một ô trống trơn cạnh nút "Lập bang"/"Kết bạn"/"Thách đấu".
      Kèm dòng gợi ý của nhật ký nhiệm vụ hiện hai lần và nói sai (J **không** đóng được, chỉ ESC).
    - ✅ Nút **Bán** nối vào túi đồ (`8cd4ab1`) — `SellItemCommand` đòi `inventory_item.id` THẬT mà chỉ
      giao diện túi đồ mới biết số đó; xác minh **trọn vòng bằng cách chơi thật + đối chiếu DB** (mua
      Kunai 500→495, bán 1 → 497 đúng giá bán 2, `inventory_item` còn 0 dòng). Bán **đúng một món mỗi
      lần bấm** vì bán nhầm cả chồng đồ là thứ không hoàn tác được.
    - ✅ **Giao dịch giữa hai người chơi, đủ 15/15 opcode `trade.proto`** (`50e10fc`) — server có đủ 36
      lớp `feature-trade` từ Phase 7, client trước đó phủ **0/15**. Xác minh bằng **HAI client chạy
      song song**. Đây là "hệ thống nguy hiểm nhất" (GAMEPLAY.md §8): trò lừa kinh điển là đổi lời
      chào ngay trước khi đối phương bấm xác nhận. Server đã chặn bằng cách reset cả hai cờ mỗi lần
      lời chào đổi — **nhưng người chơi chỉ tin được điều đó nếu họ THẤY cờ rơi về chưa-xác-nhận**,
      nên cờ hiện thành CHỮ, không rút thành một dấu nhỏ. Cố ý **không** giữ "lời chào nháp" song
      song với `my_items` của server: `SetTradeOfferCommand` đặt lại TOÀN BỘ lời chào chứ không cộng
      dồn, nên hai bản sao của cùng một thứ sẽ lệch nhau **đúng vào lúc quan trọng nhất**.

40. **Đóng độ phủ opcode client và nội dung vật phẩm (2026-08-29)**:
    - ✅ **V21 — 15 `item_template` phủ đủ 7 ô trang bị** (`684cc31`, `eecd199`): cả game trước đó chỉ
      có **3 dòng `item_template`** — 6/7 ô trang bị **không có gì để mặc**, cửa hàng bán 3 mặt hàng,
      quái chỉ rơi kunai. V21 **chỉ seed dữ liệu, KHÔNG đổi schema**: hai bậc đồ ("Tập Sự", "Hạ
      Nhẫn") phủ mọi `EquipmentSlot`, hai loại nguyên liệu quái biến lượt giết thành vàng, `shop_item`
      cho tất cả, và `drop_table_entry` cho **hai con quái thật sự tồn tại**. Bất biến giữ nguyên:
      `buy_price > sell_price` ở **mọi** dòng (bằng không là vàng vô hạn), bảng rơi giữ ngắn
      (`Inventory.add` **không bao giờ gộp chồng**, túi 20 ô). **Cố ý không có bình máu** — chưa có
      use case `UseItem` nào, thêm vào là nội dung chết. Hạn chế đã biết và **cố ý không lách**: chỉ
      cộng ATK (xem mục 29 / ADR-036).
    - ✅ **Test tích hợp chứng minh trọn vòng rơi đồ** (`1e0f431`, `eb3105e`): quái chết → roll bảng
      rơi → dòng `inventory_item` **thật** → `ItemGrantedEvent`. Trước đó chuỗi này chỉ được phủ bởi
      **ba mảnh rời, không mảnh nào chạm mảnh kế tiếp** — `WorldFacadeTest` dừng ở "sink đã được báo
      ai hạ gục" (sink giả); `ConnectedCharacterRegistryTest` dùng `InventoryPort` giả mà `save()`
      **trả về chính object nhận vào**, nên `ItemGrantedEvent.item_id` **luôn bằng 0 ở đó — và chưa
      bao giờ được assert**; `MvpLoopIntegrationTest` gọi thẳng `GrantLootUseCase` trên Postgres
      thật, **bỏ qua cả world lẫn gateway**. Test mới nối chúng lại: hình nộm bị giết thật bên trong
      `WorldFacade` 60Hz đang chạy, đồ rơi vào `inventory_item` của **đúng người hạ gục**
      (Testcontainers). Ngẫu nhiên được ghim bằng **DỮ LIỆU chứ không phải mock** (`LootRoller` gọi
      thẳng `ThreadLocalRandom` một cách cố ý, nên test viết lại `drop_table_entry` **trong chính
      container dùng một lần** — không đụng migration nào). Kênh client dùng `LocalChannel` trên
      `EventLoopGroup` **thật** thay vì `EmbeddedChannel`: một cú giết toả ra **ba virtual thread
      cộng luồng tick**, mà `EmbeddedEventLoop` chạy inline trên luồng gọi nên **âm thầm đánh rơi
      packet**. Cũng phủ nhánh túi đầy (đồ **mất im lặng**, không event, không dòng DB — hành vi tạm
      thời đã ghi trong tài liệu) và nhánh mất kết nối trước khi báo. Kiểm bằng **đột biến**: thay
      `setItemId(stack.id())` bằng `setItemId(0L)` làm test mới đỏ.
    - ✅ **Cường hoá vật phẩm + 3 opcode hạ tầng cuối ⇒ độ phủ opcode client 152/157 → 157/157**
      (`4e38401`): ENHANCE_ITEM 505/506, `ITEM_GRANTED_EVENT` (trước đó **bị bỏ qua im lặng**, nên
      giết quái xong client **không báo gì cả**), `PONG` + `SERVER_SHUTTING_DOWN_EVENT` (thời gian
      khứ hồi đo **chỉ bằng đồng hồ của chính client**, `server_timestamp_ms` cố ý không dùng). Khoá
      idempotency (ADR-025) **cố ý KHÔNG đổi** khi bật/tắt bùa Bảo Vệ giữa chừng — vẫn là **cùng một
      lần cường hoá**, khoá mới ở đó nghĩa là **bị trừ vàng lần thứ hai**. **Không hiện chi phí hay
      tỉ lệ thành công**: `EnhancementTable` nằm ở server và không packet nào chở nó sang client, mọi
      con số ở đây sẽ là **bịa** (Điều 24). Xác minh **sống** với server thật (TLS, Postgres thật):
      +0 → +7 trên katana thép, vàng trừ **đúng theo `EnhancementTable`**, **9 dòng `audit_log` cho 9
      lần bấm** (không tính tiền hai lần), và cả nhánh thất bại lẫn nhánh Bảo Vệ đều hiện dòng riêng.
    - ✅ **Hai lỗi bố cục túi đồ mà 1000+ test và MỌI static check bỏ sót**, chỉ thấy khi chụp
      `HudPreviewTool` ở **trạng thái xấu nhất**: nhãn "Giao dịch"/"Cường hoá" **rộng hơn nút 84px
      nên bị cắt chữ** (hàng nút hành động chuyển sang hàng riêng theo bề rộng thật); và **túi đầy
      thì cửa sổ đòi 1238px trên màn hình 720px**, đẩy khu trang bị và hàng nút **ra ngoài tầm với**
      ⇒ `KageSkin` thêm `ScrollPaneStyle`, cả hai danh sách cuộn được, còn **668px**. Ghi lại một bẫy
      cụ thể: `solid()` dựng drawable từ ảnh **1x1** và `ScrollPane` lấy bề rộng thanh cuộn **thẳng
      từ `minWidth` của drawable** — không `setMinWidth` thì thanh cuộn **rộng 1px**.
    - ✅ Gộp hai luồng song song làm test rơi đồ **đỏ** dù **cả hai nhánh đều xanh khi đứng riêng**
      (`214bc00`): V21 thêm 2 dòng `drop_table_entry` cho `training_dummy` trong khi phần dựng của
      test đẩy **cả bảng** lên 100% ⇒ ba món rơi ở chỗ nó chờ một. Sửa bằng cách **xoá mọi dòng trừ
      kunai rồi mới ghim**, để test **miễn nhiễm với nội dung tương lai** thay vì dính vào việc
      migration tình cờ seed bao nhiêu dòng.
    - ⛔ **Ba khoản nợ sinh ra hoặc chạm tới trong ngày, ghi đầy đủ ở mục 29**: chỉ số trang bị +
      cường hoá không có tác dụng ⇒ **ADR-036 Proposed** (đang chờ chủ dự án); `drop_table` im lặng
      khi mã quái không khớp ⇒ **đã sửa**; đổi hệ phái xong người khác thấy model cũ ⇒ **đã sửa một
      nửa**.
    - ✅ ~~**`item_template.max_stack` là cột TRANG TRÍ — vật phẩm KHÔNG hề xếp chồng**~~ **ĐÃ SỬA 2026-09-05** (phát hiện
      2026-09-05 khi playtest cửa hàng). `kunai` có `max_stack = 99`, nhưng một nhân vật test đang
      giữ **5 dòng `inventory_item` riêng** cho 17 phi đao (2+3+1+5+5+1) — mua thêm 1 cái tốn thêm
      MỘT Ô. `Inventory.add()` luôn `items.add(stack)`, không tìm chồng cùng loại, không đọc
      `max_stack`; cột đó chỉ được đọc để **gửi cho client hiển thị**. Với túi 20 ô, người chơi
      nhặt vài lần là đầy.
      **Chi phí sửa (vì sao KHÔNG tự làm, /CLAUDE.md §4):** (a) `ItemStack`/`Inventory` chưa biết
      `maxStack`, phải nối dây từ catalog vào domain; (b) **`GrantLootUseCase` PHỤ THUỘC vào việc
      `add()` luôn append** — nó lấy `items().size() - 1` làm chỉ số món vừa thêm để phát
      `ITEM_GRANTED`, gộp chồng sẽ phá logic đó; (c) đổi sức chứa túi hiệu dụng ⇒ là quyết định
      gameplay. Chủ dự án đã chốt luật: **trang bị KHÔNG xếp chồng; tiêu hao / vật phẩm nhiệm vụ /
      vật phẩm sự kiện thì CÓ** — đúng thứ `max_stack` đã ghi sẵn (14 món có `equip_slot` = 1, 4 món
      còn lại = 99), nên không phải đổi một dòng dữ liệu nào.
      **Đã thi hành:** `Inventory.add()` gộp chồng và trả về CHỖ nó rơi vào (`Added(index)`), nên
      `GrantLootUseCase` thôi đoán `size()-1`; `ItemGrantedEvent.quantity` nay là SỐ VỪA NHẬN chứ
      không phải tổng chồng. ⚠️ **Hoá ra có BA đường cấp vật phẩm, không phải một** — mua hàng và
      thưởng nhiệm vụ đi `INSERT` thẳng, bỏ qua domain; playtest bắt được vì sửa xong domain mà mua
      vẫn đẻ ô mới. Cả ba nay dùng chung `InventoryStackMerge`.
      ⚠️ **Còn lại:** túi CŨ vẫn phân mảnh (dữ liệu cũ không tự gộp) — muốn dồn lại thì cần một lượt
      migration, là thao tác ghi hàng loạt nên chờ chủ dự án.
    - ⛔ **Vẫn CHƯA làm, không phải thiếu sót**: toàn bộ quy trình Closed Beta → Open Beta → RC →
      Production ở bảng §Phase 10 **vẫn chưa bắt đầu**, TLS thật trong `docker-compose.prod.yml` và
      cert Production thật vẫn thiếu (mục 22 vẫn nguyên hiệu lực); ~~phép đo lại **N=2000** vẫn chưa
      chạy~~ **✅ ĐÃ ĐO 2026-09-05 (cấu hình production, ZGC + `leakDetection=simple`): CPU N=2000
      = 20,9 lõi, không phải ~7,3 — sai ~3 lần (số N=1000 cũ thì tái lập tốt). Heap chạm trần,
      nhưng là ứ đọng chứ không rò rỉ. **Đã profile JFR**: `world-tick-pool` 62,9% CPU vs Netty
      36,7% — mô phỏng tốn gấp đôi mạng; khoản lớn nhất là tra cứu Component qua `HashMap`, và
      **đo đạc (`recordSystemDuration`) tự nó ăn ~7%**. Xem
      [BENCHMARK §5.1](../09-performance/BENCHMARK.md) mục "ĐO LẠI N=2000 THẬT"**; và **`WorldScreen` chỉ mới hết chật, chưa hết
      lớn** — `PanelHub` mua thêm chỗ chứ không đổi kiến trúc tầng client.

41. **ADR-036 thi hành — trang bị có chỉ số thật, cường hoá có tác dụng thật (2026-08-30)**:
    - ✅ **Chủ dự án duyệt trọn D1-D5** + câu hỏi phụ "chấp nhận `maxHp = 100 + bonus_hp` như
      placeholder mới". ADR-036 chuyển **Proposed → Accepted** và thi hành **cùng ngày** ⇒ không
      còn ADR `Proposed` nào.
    - ✅ **Migration V22** — `bonus_def`/`bonus_res`/`bonus_hp`/`required_level` (chỉ THÊM cột có
      `DEFAULT`, không backfill), rồi **cân bằng lại 16 món** (15 của V21 + `wooden_sword`) cho chỉ
      số về đúng chỗ: vũ khí cộng ATK, giáp/mũ/giày cộng DEF/RES/HP, găng lai, trang sức nghiêng
      RES/HP. Trọn bộ "Hạ Nhẫn" kèm katana: ATK 30→59, DEF 20→43, RES 15→39, HP 100→158.
      `required_level` 1 cho "Tập Sự", **5** cho "Hạ Nhẫn" — trước đó Cấp 1 đủ vàng là mặc cả bộ.
    - ✅ **Cường hoá thôi là cỗ máy đốt vàng không đầu ra**: `EnhancementTable.enhancedBonus` áp
      `base × (1 + 0,10 × cấp)` cho **cả 4 chỉ số**, tính cho **TỪNG món theo cấp của chính món
      đó** rồi mới cộng lại (cộng tổng trước rồi nhân là sai — mỗi món một cấp riêng). Cường hoá
      **khuếch đại chứ không tạo chỉ số từ hư vô**: món `bonus_def = 0` thì +15 vẫn 0 DEF, có test
      riêng cho đúng điều đó.
    - ✅ **DEF/RES/HP thật sự tới được ECS**: `EquipmentBonusSnapshot` (record của `server-world`,
      vì module đó **không được import `feature-inventory`**, và vì 4 số rời làm
      `applyEquipmentBonus` thành 5 tham số — vượt Checkstyle `ParameterNumber`). Máu hiện tại dịch
      theo đúng **độ chênh** của máu tối đa, **chặn dưới ở 1**: gỡ áo +HP lúc máu thấp không được
      giết người chơi, mặc áo +HP giữa trận không được là nút hồi máu miễn phí. Nhân vật đang chết
      thì không đụng tới — `respawnCharacter` tự đọc `health.max` mới.
    - ✅ **Protocol additive đúng luật**: 4 field `7..10` của `ItemCatalogEntry`, field `1..6`
      nguyên vẹn, **`protocolVersion` KHÔNG tăng**, không thêm opcode nào.
    - ⚠️ **Khoá i18n trong ADR bản Proposed viết SAI và bị bắt lúc thi hành**: `error.level_too_low`
      **đã có chủ** — luồng chọn hệ phái dùng nó với câu "Chưa đủ cấp 10 để chọn hệ phái". Thêm
      trùng khoá vào `.properties` là **im lặng ghi đè** câu của luồng kia (khoá sau thắng), không
      test nào bắt. Khoá thật: `error.equip_level_too_low`.
    - ✅ **Vạch máu client hết hardcode**: `CharacterRespawnedEvent.max_hp` **đã nằm trên dây từ
      lâu** nhưng client nhận rồi vứt, và `playerMaxHp()` trả thẳng hằng số `CHARACTER_MAX_HP` —
      với `bonus_hp` thì mẫu số đó sai. Nay lấy số của server, và `maxHp <= 0` (server cũ chưa gửi
      field) thì **giữ giá trị đang biết** thay vì chia cho 0.
    - ⚠️ **Nợ cố ý để lại**: chưa gói nào mang HP/HP-tối-đa của **chính mình lúc ĐĂNG NHẬP** (grep
      `current_hp` trong `.proto` — chỉ có ở quái và ở sự kiện hồi sinh), nên giữa lúc vào game và
      lần hồi sinh đầu tiên mẫu số vạch máu vẫn có thể lệch nếu đang mặc đồ `+HP`. Sửa đúng cần
      thêm field vào gói trạng thái ban đầu, **ngoài phạm vi ADR-036** ("không thêm opcode, không
      đổi message đang chạy"). Và client **cố ý chỉ hiện chỉ số GỐC** kèm nhãn "Chỉ số gốc":
      `ENHANCE_STEP` sống ở server, client tự nhân là bịa (Điều 24).
    - ⚠️ **`ENHANCE_STEP = 0,10` và toàn bộ số liệu V22 CHƯA qua playtest** — đúng thân phận mà
      bảng tỉ lệ §7.1 từng có.

42. **PLAYTEST cân bằng ADR-036 — chơi thật, đo bằng số trên HUD (2026-08-30)**:
    - ✅ **Cả năm cơ chế của ADR-036 đã được nhìn thấy chạy thật** trên server thật (TLS, Postgres
      thật, migration V22 do Flyway áp lúc khởi động) chứ không chỉ trong test:
      | Đo | Kết quả | Đối chiếu công thức |
      |---|---|---|
      | Sát thương tay không lên hình nộm | **29** (50→21) | `ATK 30 × 100/(100+5) = 28,6` ✓ |
      | Sát thương hình nộm gây ra, không giáp | **−7** | `8 × 100/(100+20) = 6,7` ✓ |
      | Máu tối đa, bộ Hạ Nhẫn **+0** | **158** | `100 + 58` ✓ |
      | Sát thương nhận, bộ Hạ Nhẫn **+0** | **−6** | DEF 43 ⇒ `8 × 100/143 = 5,6` ✓ |
      | Máu tối đa, bộ Hạ Nhẫn **+15** | **245** | `100 + 145` (58 × 2,5, làm tròn TỪNG món) ✓ |
      | Sát thương nhận, bộ Hạ Nhẫn **+15** | **−4** | DEF 78 ⇒ `8 × 100/178 = 4,5` ✓ |
      Cặp **158 → 245** là bằng chứng dứt điểm rằng cường hoá **đã có tác dụng thật**: cùng một bộ
      giáp, chỉ khác `enhance_level`, máu tối đa đổi đúng hệ số `×(1 + 0,10 × cấp)`.
    - ✅ **`required_level` chặn thật, có câu tiếng Việt đúng**: nhân vật **Cấp 1** bấm Mặc
      `Katana Thép +7` (yêu cầu Cấp 5) nhận "Chưa đủ cấp để mặc vật phẩm này.", ô Vũ khí vẫn
      (Trống). Nâng lên Cấp 5 thì mặc được ngay, không cần đăng nhập lại (gateway đọc cấp từ DB
      mỗi lần).
    - ✅ **Ô túi đồ hiện đúng dòng mới**: "Katana Thép +7 / Chỉ số gốc: Công +18 / Cần Cấp 5".
    - 🔴 **LỖI THẬT DO CHÍNH ĐỢT NÀY SINH RA — vạch máu hiện `139 / 100`.** Trước ADR-036 máu tối đa
      luôn là hằng số 100 nên mẫu số không bao giờ sai; nay `bonus_hp` nâng máu tối đa thật mà
      **không gói nào mang máu tối đa của CHÍNH mình lúc ĐĂNG NHẬP** (`grep current_hp` trong
      `.proto` chỉ ra quái và `CharacterRespawnedEvent`). Hệ quả nhìn thấy được: tử số là số THẬT
      của server (139/209/128) còn mẫu số kẹt ở 100 ⇒ **tử số lớn hơn mẫu số**, thanh máu tràn.
      Tự sửa đúng sau lần hồi sinh ĐẦU TIÊN (`158 / 158`, `89 / 245`) — tức bản vá client đọc
      `max_hp` từ sự kiện hồi sinh chạy đúng, chỉ thiếu đúng đường lúc đăng nhập.
      ✅ **Chủ dự án duyệt và đã sửa cùng ngày**: `SelectCharacterResponse` nay mang
      `current_hp`/`max_hp` (field 3..4, additive, `protocolVersion` KHÔNG tăng, không thêm
      opcode). Công thức máu tối đa gom về MỘT chỗ — `WorldFacade.characterMaxHp` — thay vì để
      gateway tự cộng `100 + bonus_hp` và tạo bản sao thứ hai sẽ lệch trong im lặng khi GDD chốt
      công thức HP thật.
    - ⚠️ **Phát hiện cân bằng: `bonus_def` gần như vô nghĩa với nội dung hiện có, `bonus_hp` áp
      đảo.** Quái duy nhất trong game có `atk = 8`, mà giảm damage là nhân
      (`100/(100+DEF)`) nên trọn bộ Hạ Nhẫn (10.900 vàng) chỉ đổi 7 → 6 sát thương mỗi đòn (−14%),
      trong khi cùng bộ đó cộng **+58 máu** (+58%). Cường hoá lên +15 cũng chỉ đưa 7 → 4 nhưng đưa
      máu lên 245. Nghĩa là **mọi con số DEF/RES đặt trong V22 hiện không kiểm chứng được bằng
      gameplay** — cần quái mạnh hơn trước khi cân DEF, không phải chỉnh DEF trước.
    - ⚠️ **Nội dung mỏng làm tiến trình trang bị chỉ có 2 nấc**: hình nộm 50 máu chết trong **2 đòn**
      tay không và **1 đòn** với Katana +7. Không đủ chỗ để thấy khác biệt giữa các bậc đồ.
    - ⚠️ **Chưa chẩn đoán**: có lúc quái đánh trúng người chơi từ vị trí **client không vẽ gì** và
      không bấm chọn được (thử 6 điểm quanh nhân vật đều "Ngoài tầm đánh"). Có thể chỉ là sprite bị
      nhân vật che, chưa đủ bằng chứng để kết luận là lỗi — ghi lại để lần sau soi.
    - 🔧 **Mẹo công cụ đắt nhất buổi**: `keybd_event` GIỮ phím chỉ ăn nếu cửa sổ được kích hoạt bằng
      một cú **bấm chuột thật** trước đó; chỉ gọi `SetForegroundWindow` thì phím vào nhỏ giọt
      (giữ 3 giây chỉ đi được 0,9m trong khi `MOVE_SPEED_MPS = 5`). Bấm chuột vào vùng trống rồi
      mới giữ phím là chạy đúng tốc độ.

43. **ADR-037 thi hành — quái đọc từ DB, thay bốn chỗ hardcode (2026-08-30)**:
    - ✅ **Migration V23**: `monster_template` (15 cột) + `monster_spawn`. **Cột quan hệ, KHÔNG
      JSON** — đi NGƯỢC đặc tả gốc DATABASE.md §2.2, đã sửa lại tài liệu đó thay vì sửa code theo
      nó. Đo lại đúng Revisit Trigger của ADR-036 ("quá 6 chỉ số thì đo lại JSON") chứ không viện
      tiền lệ suông: cái giá không đổi (một dependency mới, §4), còn `stats_json` gõ sai khoá thì
      **im lặng thành 0**. `CHECK pos_y = 0` biến ràng buộc ADR-033 từ comment thành luật DB.
    - ✅ **Bốn chỗ hardcode gom về một bảng**: `CombatConstants.MONSTER_STATS`/`MONSTER_MAX_HP`/
      `MONSTER_RESPAWN_SECONDS` biến mất; `MONSTER_SPAWN_X` biến mất; **`MonsterExperience` bị XOÁ**
      (bảng tra Java mà javadoc của chính nó gọi là tạm). `AiConstants` là chỗ duy nhất còn lại,
      hoãn có chủ đích (D7) và đã ghi chú vào AI.md §10 vì tài liệu đó mô tả sai hiện trạng.
    - ✅ **Module `feature-monster`** + `PostgresMonsterCatalogAdapter` + `MonsterCatalogLoader`
      (gateway dịch) + `MonsterCatalogSnapshot` (record thuần của world). Hình dạng này KHÔNG tuỳ
      chọn: `server-database` **không phụ thuộc** `server-world`, chỉ gateway phụ thuộc cả hai —
      đúng ràng buộc đã buộc ADR-036 dịch `EquipmentBonus` → `EquipmentBonusSnapshot`.
    - ✅ **EXP đi cùng lượt báo "quái chết"** thay vì tra lại: `onMonsterKilled(..., expReward)`.
      Tra lại ở gateway là mở đường đọc thứ hai cho cùng dữ liệu, **và** là tham số thứ năm cho
      `ConnectedCharacterRegistry` — vốn đã chạm trần Checkstyle với 15 chỗ dựng.
    - ✅ **Protocol**: một field additive `model_key` trên `MonsterSpawnedEvent`, `protocolVersion`
      KHÔNG tăng, không thêm opcode. Field rỗng ⇒ client suy từ `monster_code` như cũ
      (`MonsterModelKey`), nên **server cũ vẫn chạy với client mới** — và boss (giữ hardcode) đi
      đúng nhánh đó.
    - ⚠️ **Chỗ suýt sai, bắt được lúc thi hành**: bản đầu cho `MonsterSpecies` mang thẳng
      `CombatConstants.CombatStats`. Kiểu đó **package-private**, nên đó là một API công khai mà
      gateway không gọi được — và là rò rỉ nội tình combat ra khỏi module. Đổi sang mang SỐ THUẦN,
      `WorldFacade` tự dựng `CombatStats`; phần trăm chia 100 đúng một chỗ.
    - ✅ **Nội dung**: 3 loài. `training_dummy` **giữ NGUYÊN mọi con số cũ** (hàng đối chứng — có
      test Postgres gác riêng); thêm `wandering_bandit` (atk 22) và `stone_ogre` (atk 40, 200 máu)
      để phòng thủ lần đầu đọc được: không giáp chịu 3 đòn, bộ Hạ Nhẫn +0 chịu 6, +15 chịu 11 (so
      với hình nộm: 15/26/61 — ba con số đều là "không bao giờ chết").
    - ⚠️ **Nợ art nhìn thấy được**: cả ba loài dùng CHUNG `mob_training-dummy`, hai loài mới trông y
      hệt hình nộm cho tới khi có `.glb` riêng. Chủ dự án chọn thế để cân bằng chạy được ngay thay
      vì chờ art. Khoá i18n đã thêm ở CẢ HAI bundle.
    - ⚠️ **Số của hai loài mới CHƯA playtest** — đúng thân phận mà `ENHANCE_STEP` và bảng tỉ lệ §7.1
      từng có. Việc tiếp theo là chơi thật đúng bảng "số đòn chịu được" ở ADR-037 D6; đó mới là lúc
      biết DEF/RES của V22 đúng hay sai.

44. **PLAYTEST 3 loài quái mới — bảng D6 của ADR-037 khớp TỪNG Ô (2026-08-30)**:
    - ✅ Chơi thật trên server thật (V23 do Flyway áp lúc khởi động). **Tám phép đo, tám lần khớp
      công thức**, không lần nào lệch:
      | Đo | Kết quả | `100/(100+DEF)` |
      |---|---|---|
      | Cướp lang thang (atk 22) đánh người KHÔNG giáp | **−18** | `22×100/120 = 18,3` ✓ |
      | Quỷ đá (atk 40) đánh người KHÔNG giáp | **−33** | `40×100/120 = 33,3` ✓ |
      | Quỷ đá đánh bộ Hạ Nhẫn **+0** | **−28** | DEF 43 ⇒ `40×100/143 = 28,0` ✓ |
      | Quỷ đá đánh bộ Hạ Nhẫn **+15** | **−22** | DEF 78 ⇒ `40×100/178 = 22,5` ✓ |
      | Cướp lang thang đánh bộ **+15** | **−12** | `22×100/178 = 12,4` ✓ |
      | Máu tối đa, không giáp / +0 / +15 | **100 / 158 / 245** | ✓ |
    - ✅ **Tiến trình phòng thủ lần đầu ĐỌC ĐƯỢC**, đúng bảng ADR-037 D6 dự đoán: chịu được
      **3 → 6 → 11 đòn** của quỷ đá. So với hình nộm (15/26/61 — ba con số đều là "không bao giờ
      chết"), đây là lần đầu mặc giáp có nghĩa trong game. **Số DEF/RES của V22 nay đã kiểm chứng
      được — và chúng đúng.**
    - ✅ Tên i18n loài mới hiện đúng ("Cướp lang thang 90 / 90"), máu tối đa của quái đọc từ
      `monster_template`, `model_key` dùng chung không gây hộp đỏ.
    - ✅ **Xác nhận bản vá vạch máu của ADR-036 chạy trong đời thật**: đăng nhập với bộ giáp +0 hiện
      thẳng `102 / 158`, không còn `102 / 100` như trước — tức field `SelectCharacterResponse.max_hp`
      đi đúng đường.
    - 🔧 **Bẫy mất nhiều thời gian nhất, và là một PHÉP ĐO SAI suýt báo cáo nhầm**: hai lượt đo đầu
      kết luận "quỷ đá KHÔNG spawn" — sai hoàn toàn. Server ghi `character.pos_x` lúc ngắt kết nối
      **MUỘN hơn** vài giây so với lúc tiến trình client bị kill, nên `UPDATE ... pos_x=34` bị bản
      ghi trễ đè lại thành `-7,58`; nhân vật vào world ở chỗ cũ, cách quỷ đá 40m. Cách sửa đã đưa
      vào công thức playtest: **poll `pos_x` tới khi nó NGỪNG đổi** rồi mới UPDATE, và kiểm lại
      ngay trước khi đăng nhập. Bài học chung: camera bám nhân vật nên "không thấy quái" KHÔNG phải
      bằng chứng quái không tồn tại.

45. **ART CHO 2 LOÀI QUÁI MỚI — hết cảnh ba loài trông y hệt nhau (2026-08-30)**:
    - ✅ **`mob_stone-ogre`**: con quái đầu tiên của dự án đi qua Text→3D (Hyper3D Rodin,
      `tier=Detail`, 93 560 tris nguồn → LOD 20 000 / 10 000 / 5 000, texture 2048/1024/512).
      Thân đá xám nứt + vai rêu + mắt hổ phách.
    - ✅ **Script mới `tools/blender/build_text3d_monster.py`** — và đây là phần đáng giữ hơn cả
      model: bước A/B của BLENDER-SESSION-GUIDE §1.5 (sinh + import) xưa nay **bắt buộc phải có
      Blender GUI mở** cho phiên blender-mcp. Job Rodin trả `.glb` về đĩa, nên script tự import và
      chạy trọn bước 2–8 headless bằng MỘT lệnh. 15 nhân vật trước đây đều phải qua phiên GUI.
    - ⚠️ **`mob_wandering-bandit` KHÔNG đi Text→3D, và đó là một hạn chế ngoài tầm**: prompt nhân
      vật người bị Rodin trả `IMAGE_CONTENT_VIOLATION` hai lần, rồi key dùng thử trả
      `API_INSUFFICIENT_FUNDS`. Bản đang ship dựng bằng `kage_mesh_kit` (`build_monster_bandit.py`,
      40 part, rig 11-bone, 7 action) — chất lượng "mesh kit" chứ không phải Text→3D, **bản tạm có
      chủ đích**: thay bằng bản sinh chỉ tốn đúng một lệnh, vì mọi bước sau mesh đều dùng chung.
    - ✅ **Hai lỗi thật của `export_character_lods.py`, đã vá**: (1) `--extras` liệt kê tay 40 part
      thì **thiếu `bandit_SashTail` mà không có gì báo** ⇒ thêm `--extras all`; (2) ngân sách §4.4
      chỉ decimate thân, mà asset kit có thân chiếm 3% số tam giác ⇒ tier `low` ra ~5 800/trần
      5 000. Nay thân và mọi part đủ lớn cùng chịu một tỉ lệ chung, part ≤ 64 tris được miễn (cắt
      4 tam giác của một cái hộp là biến nó thành cái nêm).
    - ⚠️ **Suýt nới trần cho một vi phạm KHÔNG có thật**: import ngược `.glb` vào Blender rồi cộng
      `loop_triangles` ra 5 080 cho quỷ đá tier `low` ("vượt trần 5 000"). Số đúng — thứ
      `TriangleBudgetValidator` thật sự đếm trên file đã ship — là **đúng 5 000**. Chỉ tin
      `./gradlew :tools:asset-pipeline:run`.
    - ✅ **Đã NHÌN THẤY chạy thật**: `./gradlew :client:client-core:runWorldPreview` (công cụ nay
      dựng cả 3 loài cạnh nhau theo thứ tự sát thương 8/22/40 + hộp đỏ `sample_boss` chứng minh
      nhánh fallback còn sống). Ba loài phân biệt được ngay từ silhouette.
    - ⚠️ Còn lại của ADR-037: **tham số AI theo loài (D7)**.

46. **RÀ EXIT CRITERIA PHASE 0-3 — 12/13 ô tick, CÓ BẰNG CHỨNG TỪNG Ô (2026-08-30)**:
    - Nợ sổ sách cũ: bốn phase đầu tuyên bố đóng bằng prose nhưng gần như không ô nào được tick.
      Lượt rà này **không tick bừa** — mỗi ô nay dẫn tên test hoặc tên file thật, đọc ngay tại ô.
    - ✅ **Bằng chứng đã có sẵn, chỉ chưa ai viết vào**: ping/pong, reconnect-backoff, round-trip
      17 file `wire/*PacketsTest`, hai client thấy nhau di chuyển, persist qua shutdown, authz
      chọn nhân vật của tài khoản khác — tất cả đều là test đang chạy trong `./gradlew build`.
    - ✅ **Chống speed hack/teleport hoá ra là chuyện THIẾT KẾ, không phải bộ lọc**: `MoveCommand`
      chỉ mang hướng, `JumpCommand` rỗng, không gói client→server nào mang toạ độ — teleport không
      biểu diễn nổi trên dây. Ghi lại vào chính ô đó để lần sau không ai đi tìm "bộ lọc chống
      teleport" không tồn tại.
    - 🆕 **Ô Phase 1 "fixed timestep hoạt động" nay có test THẬT** (`KageEngineFixedTimestepTest`,
      9 test) — `engine-core` trước đó là module **không có test nào**, xem mục ngay dưới.
    - ⚠️ **Một ô CỐ Ý để trống**: "mọi module ở §1 REPOSITORY-STRUCTURE tồn tại". §1 liệt kê 5
      module chưa tồn tại và bỏ sót 4 module đang có — lệch **tài liệu ↔ thực tế**, và sửa bên nào
      là quyết định của chủ dự án (`docs/README.md` mục 10), không phải của AI.
      ✅ **ĐÃ ĐÓNG 2026-09-06**: chủ dự án chọn sửa tài liệu theo thực tế — xem ô Phase 0 phía trên.

47. **`engine-core` có bộ test đầu tiên (2026-08-30)**:
    - `KageEngineFixedTimestepTest` — 9 test, chạy **không cần GL context**: `Gdx.graphics` là
      interface nên thay bằng `java.lang.reflect.Proxy`, đó chính là lý do module này bỏ trống lâu
      như vậy (ai cũng tưởng phải kéo LWJGL vào mới test được vòng render).
    - Số đo đáng nhớ: 20 FPS ra **59** tick/giây, 120 FPS ra **60** — lệch 1 tick vì `FIXED_DT`
      là float 1/60 **làm tròn LÊN**. Ba test đầu viết theo kiểu "phải bằng đúng bội số" đã ĐỎ vì
      chính lý do đó; sửa test chứ không sửa engine, và ghi luôn con số vào comment. Bài học:
      **test bám đúng ranh giới bội số của một hằng số float là test đang đo lỗi làm tròn của
      chính nó**.
    - Kèm hai test gác `setScreen` (show + resize màn mới, hide + dispose màn cũ) — hai lỗi thật
      đã trả giá: ma trận chiếu suy biến 2026-08-08, và rò rỉ Stage/SpriteBatch mỗi lần chuyển màn.

48. **V24 + PLAYTEST: art quái mới ĐÃ NHÌN THẤY TRONG GAME THẬT (2026-08-30)**:
    - 🔴 **Khoảng trống suýt lọt**: art xong, `.glb` nằm đúng chỗ, `MonsterModelKey` đúng — mà
      trong game vẫn là ba cái cọc gỗ. Lý do: `monster_template.model_key` của V23 ghi
      `mob_training-dummy` cho CẢ BA loài, và client chỉ tự suy khoá từ `monster_code` khi field
      đó RỖNG. Server luôn gửi cột này ⇒ art mới không con quái nào dùng. **Migration V24** (hai
      câu `UPDATE`, không đổi schema) trả hai loài về model của chính chúng.
    - ✅ Test gác đúng khoảng trống đó: `shouldGiveEachSpeciesItsOwnModel` (Postgres thật,
      Testcontainers) — mỗi loài một `model_key` riêng, hình nộm giữ nguyên làm hàng đối chứng.
    - ✅ **Chơi thật**: server bật TLS + Flyway áp V24, client đăng nhập, đi bộ dọc trục X.
      **Cướp lang thang hiện đúng model riêng** (áo da nâu, búi tóc, trường đao) và đánh trúng
      **−18** — khớp `22×100/120` đúng như bảng ADR-037 D6. **Quỷ đá** cũng hiện model đá xám vai
      rêu khi đuổi theo.
    - ⚠️ **Phát hiện phụ về giao diện, chưa sửa**: khung chat chiếm hẳn **một phần ba trái** màn
      hình, nên con quái đang đánh mình từ bên trái **bị che hoàn toàn** — người chơi thấy chữ
      "−18" bay lên mà không thấy ai đánh. Không phải lỗi render (đi qua nó là thấy ngay), nhưng
      là thứ đáng sửa khi làm lại bố cục HUD.
    - 🔧 Công thức playtest bổ sung: `Add-Type` của PowerShell **bị chặn ghi .dll** trên máy này
      (`BadImageFormatException` / `Access to the path ... is denied`), nên bộ điều khiển cửa sổ
      viết lại bằng **Python `ctypes`** (`user32`/`gdi32`, PrintWindow + tự ghi PNG bằng `zlib`) —
      không cần biên dịch gì, chạy ổn định.

49. **Khung chat tự ẩn + client thật CÓ LOG (2026-08-30)**:
    - 🔴 Bug tìm ra bằng chính buổi playtest mục 48: nhật ký chat rộng 420px neo trái-dưới **che
      hẳn một phần ba trái vùng chơi**, và con quái đánh mình từ bên trái nằm gọn sau nó. Người
      chơi thấy "−18" bay lên mà không thấy ai đánh.
    - ✅ `ChatPanel` nay **tự ẩn sau 12 giây im lặng** (ô nhập đóng); dòng mới hoặc phím T là hiện
      lại ngay. Đã chụp trước/sau: cùng một khung cảnh, sau 12 giây thấy rõ hình nộm bên trái và
      cướp lang thang bên phải — hai con trước đó bị che. Luật chung ghi vào UI.md §10.1.
    - ✅ **`client-desktop` nay có backend log** (`runtimeOnly logback-classic`). Trước đó SLF4J rơi
      về NOP: **mọi dòng log của client biến mất**, kể cả câu "Chưa có model '<khoá>' — vẽ bằng
      hộp" — đúng câu cần đọc nhất khi chẩn đoán một con quái ra hình hộp đỏ. Cả buổi playtest
      trước phải đoán mò vì client im lặng tuyệt đối.

50. **ADR-038 — game ĐỔI BỐI CẢNH sang TU TIÊN; Đợt 1 + Đợt 2 + playtest + D8 (2026-09-01)**:
    - Chủ dự án quyết đổi từ "ninja học viện" sang **tu tiên** (kiểu *Phàm Nhân Tu Tiên*).
      Nguyên tắc thi hành là **"re-theme, không re-build"**: chỉ đổi tường thuật + chuỗi hiển thị,
      **KHÔNG** đổi cơ chế / protocol / schema DB / tên class. Không migration nào, `protocolVersion`
      không tăng, không module nào đổi —
      [ADR-038](../01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md) Accepted cùng ngày.
    - ✅ **Đợt 1 (tài liệu)**: GDD v0.3.0 + [GLOSSARY](../GLOSSARY.md) v1.1.0 +
      [STORY.md](../13-game-design/STORY.md) v0.2.0 — **SSOT cốt truyện**, §1–§8 viết đầy đủ; từ
      đây mọi quest/NPC/tên vùng phải map về một arc trong đó (luật viết quest ở §8). Lv1–100 giữ
      nguyên cơ chế, chỉ **hiển thị** thành **6 cảnh giới × 9 tầng** (Luyện Khí → Trúc Cơ → Tử Phủ
      → Kim Đan → Nguyên Anh → Hoá Thần).
    - 🎯 **Phát hiện làm Đợt 2 nhẹ hơn hẳn kế hoạch: DB KHÔNG chứa chữ hiển thị nào.** Mọi bảng seed
      (`item_template`, `monster_template`, `npc_template`) chỉ giữ cột `name_key` trỏ sang bundle
      i18n — xác minh bằng `grep name_key` trên **cả 24 migration**. Kế hoạch ban đầu (D7.2) dựa
      trên giả định sai là phải sửa tên item/quái/NPC trong DB seed. Thực tế: ~90 chuỗi vi/en đổi
      gọn trong hai file `.properties`, **không khoá nào bị đổi tên**.
    - ✅ Kèm theo: hội thoại arc 1 của trưởng lão **Chu Hoành** (`town_elder.js`, thêm 3 node),
      `CultivationRealm` mới (ánh xạ cấp → cảnh giới, thuần tuý, 20 test), và khoá
      `npc.town_elder.name` — nằm trong DB từ **V10** nhưng **chưa từng có dòng i18n**, tức tham
      chiếu treo suốt từ đó tới nay.
    - 🔴 **Ba lỗi tràn bố cục, bắt bằng ẢNH CHỤP chứ không đoán**: (1) tên chiêu "Cuồng Phong Quyết"
      (17 ký tự) làm nở nhãn và đẩy lệch cả cụm nút góc phải — trần thật ~14 ký tự; (2) nút chọn
      nhân vật rộng **cố định 340px** vốn ĐÃ sát trần từ trước ("(Cấp 100)" trên tên 16 ký tự ≈
      330px), thêm cảnh giới thì vượt — nới lên 440px; (3) dòng cửa hàng ghép
      `"<tên> — Mua: X  Bán: Y"` trong một nhãn KHÔNG xuống dòng, nên tên 22 ký tự nuốt luôn giá
      bán ("… Bá…") — rút ba tên dài nhất xuống ≤17 ký tự. Cả ba trần độ dài đã ghi thẳng vào
      bundle để lần sau không phải đo lại.
    - ✅ **Đã PLAYTEST thật** (server + Postgres + Redis thật, ADR-038 D11, ảnh chụp từng bước):
      cảnh giới ở màn chọn nhân vật ("Luyện Khí 1"), điều kiện dùng pháp khí theo tầng ("Cần Luyện
      Khí tầng 3"), trọn 3 node hội thoại Chu Hoành, nhận nhiệm vụ ("Đập Mộc Nhân — Đang làm"),
      "Nhận được Phi Đao x1.", Tàng Bảo Các, túi đồ tính bằng **linh thạch**.
      **1099/1099 test xanh**, lần đầu tính cả **197 test Postgres**.
    - ⚠️ **Một mắt xích CHƯA chứng minh bằng chơi tay**: đủ 3 mạng quái để nhiệm vụ chuyển "Hoàn
      thành" rồi lĩnh thưởng — input giả tuột mục tiêu và chết giữa chừng (giới hạn đã biết của
      công cụ playtest, không phải lỗi game). Chuỗi nhận → đếm → hoàn thành → lĩnh thưởng vẫn được
      `QuestIntegrationTest` kiểm trọn trên Postgres thật với đúng script sản phẩm.
    - ✅ **Hai test mới gác nội dung, cả hai chạy KHÔNG cần Docker** — chính là lỗ mà chúng vá:
      `CultivationRealmTest` ghim bảng cấp → cảnh giới theo từng mốc GDD, và
      `TownElderDialogueTreeTest` chạy **script sản phẩm thật** để bắt node cụt / action trỏ vào hư
      không / lộ nội dung arc sau. Trước đó script sản phẩm chỉ được kiểm bởi
      `DialogueIntegrationTest` vốn cần Postgres, tức **im lặng biến mất mỗi khi máy không bật
      Docker**.
    - ✅ **D8 (quét chữ "ninja" trong docs) xong phần thật sự lệch**: `docs/README` (dòng mô tả dự
      án), `ARCHITECTURE` (sơ đồ), `KAGE-CONSTITUTION` Điều 5, `AI.md`, `RENDERING.md`,
      `RESOURCE.md`, `ART-QUALITY-STANDARD`. **Phần còn lại cố ý GIỮ**: link tới
      `ADR-033-Ninja-School-Style-Movement.md` (tên file thật), các bản ghi playtest cũ trong chính
      tài liệu này nhắc "bộ Hạ Nhẫn"/"Katana Thép +7" (**số đo lịch sử — sửa là làm sai hồ sơ**), và
      `BLENDER-SESSION-GUIDE` (bản ghi lệnh đã chạy thật). Con số "74 chỗ nhắc ninja" ở D8 gộp cả ba
      nhóm đó nên nghe lớn hơn việc thật rất nhiều.

51. **Đợt 3 (art) của ADR-038 — XONG 2026-09-03, còn treo đúng MỘT thứ chỉ chủ dự án làm được**:
    - Cả **7 dòng bảng quyết định §5** của
      [ART-AUDIT-TU-TIEN.md](../14-art/ART-AUDIT-TU-TIEN.md) đã được chủ dự án quyết và đã thi hành.
    - 🎯 **Kết quả ngược hẳn dự đoán của kế hoạch (D7.3 đoán "15 nhân vật + 3 quái + 17 VFX đều là
      phong cách ninja")**: soi từng con một thì **chỉ 1/15 nhân vật từng đội băng trán ninja** —
      `char_hoa-b1`, đúng con pilot Text→3D đầu tiên (2026-08-03); 14 con sinh sau dùng prompt không
      có băng trán. Băng trán **đã xoá**.
    - ✅ Hai icon đã sửa, **mã vật phẩm giữ nguyên** (ADR-038 D5 — không đụng DB/protocol, chỉ đổi
      hình): `icon_genin_headband` từ băng trán hitai-ate → **mũ vải Ngoại Môn** (mũ chóp cụt +
      ngọc + trâm vàng); `icon_steel_katana` từ katana → **trường kiếm thẳng + tua kiếm đỏ**.
    - ✅ `char_town-elder` **dựng lại hoàn toàn** thành trưởng lão Chu Hoành. Lý do là **chất lượng
      chứ không phải đề tài**: bản cũ chưa bao giờ đạt
      [ART-QUALITY-STANDARD §2](../14-art/ART-QUALITY-STANDARD.md) — không mặt, một vùng màu, thân
      hộp trần (đọc `.glb` cũ thấy **thiếu hẳn mảng `images`**, tức nó chưa từng có texture nào) —
      **nợ mang từ 2026-08-01**. Bản mới: **43 part, 7.622 tris nguồn** → LOD **7.622 / 7.622 /
      4.998**.
    - ✅ Giáp/mũ kim loại của `char_tho-b3`: chủ dự án quyết **GIỮ** (tấm bo góc, 4 đinh tán, KHÔNG
      hoạ tiết khắc — không mang dấu hiệu đề tài nào, và hệ Thổ vốn là thể tu).
    - ✅ **Ngân sách tam giác vẫn đạt: 57/57 file** (chuẩn ART-PIPELINE §4.4).
    - 🔴 **Phát hiện đáng ghi nhất: ảnh preview trong Blender KHÔNG thay được
      `./gradlew :client:client-core:runWorldPreview`.** Trong Blender mặt ông lão đẹp; qua bộ dựng
      **thật** của client (camera ortho chúc xuống theo
      [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md)) thì **vòm tóc che kín
      mặt**. Chỉ nhìn qua bộ dựng thật mới thấy — cùng họ với những lần "đường chụp ảnh đánh bại
      phép toán đúng" đã trả giá trước đây (mục 31, 35).
    - ⚠️ **0 lượt Rodin tiêu tốn — nhưng KHÔNG phải vì không cần.** Chủ dự án **ĐÃ đồng ý** tiêu hạn
      mức cho `char_town-elder`; khoá dùng thử `vibecoding` trả `API_INSUFFICIENT_FUNDS`, tức API từ
      chối **trước** khi sinh nên không mất credit nào. Ông lão được dựng bằng đường `kage_mesh_kit`
      (offline, tất định) thay thế.
    - 🔶 **Còn treo, chỉ chủ dự án làm được**: muốn bản Rodin cho `char_town-elder` thì cần **khoá
      Hyper3D trả tiền**. Đây là việc **duy nhất** của Đợt 3 chưa đóng — xem
      [docs/README.md](../README.md) mục 12.

52. **QUEST THỨ HAI của cả dự án — gỡ hardcode TRƯỚC, rồi mới viết nội dung (2026-09-05)**:
    - `ProcessMonsterKilledUseCase` từng **hardcode đúng một mã quest** (`kill_training_dummies`).
      Chính `feature-quest/CLAUDE.md` đã tự đặt điều kiện: *"khi có quest thứ 2 THEO DÕI GIẾT QUÁI,
      bắt buộc tổng quát hoá tra cứu này trước"*. Điều kiện đó xảy ra, và đã làm đúng thứ tự:
      `QuestProgressPort.listInProgress` (lọc `status = 'IN_PROGRESS'` **trong SQL**) trả mọi quest
      đang làm kèm `scriptRef` + `progress_json`; use case chạy `on_event` cho từng cái và trả
      `List<QuestStatusChange>`. **Java KHÔNG lọc theo `monster_code`** — chính script quyết (Điều 27),
      nên thêm quest mới không phải sửa một dòng Java nào.
    - Nội dung: **V28** seed `night_watch_bandit` (`prerequisite_quest_id` tra theo `code`, không
      hardcode id), script `night_watch_bandit.js` (đuổi 1 tán tu → 800 EXP + 1 Hộ Linh Phù), nhánh
      hội thoại mới của Chu Hoành, 4 khoá i18n vi/en. Đúng cao trào arc 1 của
      [STORY.md](../13-game-design/STORY.md) §6.
    - ✅ **Bằng chứng tất định**: `QuestIntegrationTest` +4 test qua **Postgres thật** và **script
      sản phẩm thật** — vòng đời đầy đủ, giết nhầm loài bị bỏ qua, seed V28 đúng, và **một cú
      giết đi tới đúng quest quan tâm nó khi đang làm HAI quest** (chỗ bản cũ sai). 1148 test xanh.
    - ✅ **PLAYTEST SỐNG phần đầu chuỗi**: hỏi Chu Hoành → nhánh mới → nhận quest → DB hiện
      `night_watch_bandit / IN_PROGRESS / {"repelled":0}`, HUD hiện "Phiên Trực Đêm — Đang làm".
      Xếp chồng cũng đúng luật chủ dự án đặt: "Hộ Linh Phù x3"/"Phi Đao x19" gộp một dòng, còn
      6 món trang bị mua cùng lúc nằm 6 dòng riêng.
    - 🔴 **Bốn phát hiện thật từ buổi chơi, test KHÔNG thấy cái nào**:
      1. **Cuộn túi đồ chỉ ăn sau khi ĐÃ BẤM vào khung.** Túi 10 món, khung hiện 3, lăn chuột
         không nhúc ních — `Stage` gửi sự kiện lăn tới `scrollFocus`, mà `ScrollPane` chỉ tự giành
         `scrollFocus` trong `touchDown` của chính nó. **ĐÃ SỬA** (rê chuột vào là giành).
      2. **Bonus trang bị không ăn ngay lúc mặc**: mặc đủ 6 món (+18 máu) mà thanh máu vẫn
         `100/100`; chỉ sau khi hồi sinh mới thành `118/118`. **CHƯA SỮA** — cần truy nguyên.
      3. **Bảng theo dõi quest không hiện lúc ĐĂNG NHẬP**, chỉ hiện sau sự kiện đổi trạng thái
         trong phiên. Vào lại game là mất dấu quest đang làm. **CHƯA SỮA**.
      4. **Nhân vật bò ~0,3 m/s thay vì 5 m/s** ở vài vị trí quanh khu bệ nhảy demo (tái hiện
         được, hai lần, cùng khu). **CHƯA truy ra nguyên nhân** — `TerrainCollisionSystem` đang
         no-op (obstacle rỗng) và `MovementSystem` không có va chạm entity, nên **không đoán
         nguyên nhân ở đây**.
    - ⚠️ **Lỗ hổng ghi nhận, không tự vá**: `prerequisite_quest_id` có từ V11, V28 là dòng ĐẦU
      TIÊN dùng nó, nhưng **không code nào đọc cột này** — nhận được quest "trực đêm" mà chưa
      từng đập mộc nhân. Thi hành nó = thêm outcome từ chối mới = đổi protocol (CLAUDE.md §4)
      → **chờ chủ dự án quyết**. Test
      `shouldStillAcceptNightWatch_becausePrerequisiteIsNotEnforcedYet` khoá hành vi hiện tại để
      nó ĐỎ đúng lúc ai đó sửa.
    - 💡 **Bài học công cụ**: `SetForegroundWindow` **thất bại IM LẶNG** khi tiến trình gọi không ở
      foreground — phím bay sang cửa sổ khác, hai ảnh chụp **giống hệt nhau** và trông y hệt
      "bị vật cản chặn". Đã vá `kage_ui.py` bằng `AttachThreadInput` + **thoát hẳn nếu không chiếm
      được focus**, thay vì báo thành công giả. Đây là lần thứ ba đường chụp/điều khiển ảnh
      đánh lừa phân tích đúng (xem mục 31, 35).

53. **Ba lỗi PLAYTEST SỐNG bắt được ở mục 52 — sửa hết, hai cái phải qua ADR (2026-09-05)**:
    - ✅ **Bảng theo dõi nhiệm vụ trống trơn sau mỗi lần đăng nhập lại.** `QuestTracker` giữ hai cách
      đọc trên cùng một dữ liệu; ô "đang theo dõi" chỉ sống trong phiên, và `onQuestsListed` chỉ đồng
      bộ nó khi nó **đã** trỏ tới một mã — tức sau relog thì không bao giờ. Dữ liệu KHÔNG thiếu
      (nhật ký 611 về đủ), chỉ là không ai đọc nó sang. Nay nhận lại một quest từ nhật ký, **ưu tiên
      quest HOÀN THÀNH** (đang chờ người chơi bấm Lĩnh thưởng) hơn quest đang làm. 3 test + kiểm
      chứng sống.
    - ✅ **Cuộn túi đồ chỉ ăn sau khi đã BẤM vào khung.** Bẫy libGDX: `Stage` gửi sự kiện lăn tới
      `scrollFocus`, mà `ScrollPane` chỉ tự giành `scrollFocus` trong `touchDown` của chính nó. Túi
      10 món, khung hiện 3, lăn chuột không nhúc nhích. Nay rê chuột vào là giành. Kèm một chỉnh
      cấu hình đúng chỗ: Checkstyle `ParameterNumber` nay bỏ qua method `@Override` — chữ ký do thư
      viện đặt, rule này nói về API MÌNH thiết kế.
    - ✅ **[ADR-043](../01-architecture/adr/ADR-043-Push-Max-Hp-On-Equipment-Change.md) Accepted +
      thi hành + kiểm chứng sống**: mặc đồ giữa world mà thanh máu không đổi cho tới khi chết một
      lần. **Server KHÔNG sai** — `applyEquipmentBonus` cập nhật `HealthComponent.max` ngay trong
      tick đó; khoảng trống nằm ở GIAO THỨC (không gói nào chở máu tối đa của chính mình ngoài lúc
      vào world và lúc hồi sinh). Đây **đúng là nửa sau của bug ADR-036** từng sửa (`139 / 100`) —
      lần đó chỉ vá thời điểm vào world. Thêm field additive vào 3 response, `protocolVersion` không
      tăng. Gỡ áo: **118/118 → 110/110 ngay**, không cần chết.
      - 🐛 **Lỗi SERVER bắt được trên đường**: nhánh tôi luyện **chưa bao giờ gọi
        `applyEquipmentBonus`**, nên cường hoá một món ĐANG MẶC không đổi chỉ số thật cho tới lần
        đăng nhập sau — sai ở chính server, không chỉ ở hiển thị. Đã sửa cùng lúc.
      - ⚠️ **Giới hạn có ý thức của phương án (a)** chủ dự án chọn: gateway không đọc được máu HIỆN
        TẠI (nó nằm trong luồng tick, world không có đường đọc đồng bộ), nên TỬ SỐ trễ một nhịp
        (`110 / 118`) tới gói mang HP tiếp theo. Mẫu số — thứ sai trước đó — nay đúng ngay.
    - ⬜ **Lỗi thứ tư TÔI BÁO NHẦM — đã rút lại cùng ngày, xem mục 55.** "Nhân vật bò ~0,3 m/s"
      là suy ra từ ẢNH CHỤP, không phải từ số đo. Đo lại bằng `pos_x` thật: **đúng 5,00 m/s ở cả
      ba phép đo**, kể cả xuyên khu bệ nhảy và ngay sau khi giết quái. Không có lỗi nào ở đây.

54. **[ADR-044](../01-architecture/adr/ADR-044-Quest-Prerequisite-Enforcement.md) — thứ tự quest được
    thi hành, bằng một cột không ai từng đọc (2026-09-05)**:
    - `quest_template.prerequisite_quest_id` có từ **V11** (Phase 6) và suốt từ đó **không dữ liệu
      nào dùng, không code nào đọc**. V28 là dòng đầu tiên dùng nó ⇒ nhu cầu thật đã tới. Trước bản
      này, nhân vật cấp 1 mới tinh nhận được quest "trực đêm" mà chưa đập cái mộc nhân nào.
    - Chủ dự án chốt hai điểm: mốc là **`CLAIMED`** (đánh xong mà chưa quay lại lĩnh thưởng thì chuỗi
      kể chuyện chưa đóng), và **KHÔNG** giấu lựa chọn hội thoại — NPC vẫn hỏi, **server** từ chối
      kèm lý do (Điều 15, và giữ `feature-dialogue` không phải biết gì về quest).
    - Thi hành xuyên 6 tầng, **không migration**: `QuestTemplate` (`OptionalLong`) →
      `AcceptQuestUseCase` → adapter → `ACCEPT_QUEST_OUTCOME_PREREQUISITE_NOT_MET = 4` (chỉ THÊM giá
      trị enum, `protocolVersion` không tăng) → gateway → i18n vi/en.
    - 🐛 **Bẫy JDBC sập ngay trong lần thi hành**: bản mapper đầu gọi `rs.wasNull()` **bên trong lời
      gọi constructor**, tức SAU `getLong("id")` và `getString("script_ref")` — nên nó trả lời về
      `script_ref` (không bao giờ NULL) và **mọi quest trông như có tiên quyết id = 0**, chặn sạch
      cả quest mở màn. **8 test bắt ngay.** `wasNull()` nói về lần đọc cột GẦN NHẤT; chụp lấy NGAY.
    - Test: khẳng định cũ **đã đảo đúng như nó tự hẹn** (`shouldStillAccept...NotEnforcedYet` →
      `shouldRejectNightWatch_when...`), cộng 3 test mới. **1157 test xanh, 0 skip.**
    - ✅ **Kiểm chứng sống**: nhân vật cấp 1 mới tạo hỏi Chu Hoành xin trực đêm → hiện đúng
      **"Phải xong nhiệm vụ trước đã."**, và `character_quest_progress` **0 dòng**.

55. **RÚT LẠI một lỗi tôi tự báo — đo mới biết game không sai (2026-09-05)**:
    - Mục 53 ghi "nhân vật bò ~0,3 m/s thay vì 5 m/s quanh khu bệ nhảy demo, tái hiện được hai lần".
      **Sai.** Cả hai lần đó tôi suy tốc độ từ khoảng dịch chuyển GIỮA HAI ẢNH CHỤP — mà camera bám
      nhân vật nên mọi suy luận vị trí từ ảnh đều không hội tụ (đã tự ghi nhận điều đó ở chính mục
      52, rồi vẫn dựa vào nó để kết luận).
    - **Cách đo đúng**: đặt `character.pos_x` về một giá trị BIẾT TRƯỚC → vào world → giữ D đúng
      2000ms → tắt client → đọc `pos_x` (server ghi vị trí lúc ngắt kết nối). Quãng đường ra bằng
      SỐ, không qua pixel nào.
    - **Ba phép đo, ba lần đúng 5,00 m/s**:

      | Bài đo | Bắt đầu | Sau 2000ms | Quãng đường |
      |---|---|---|---|
      | Đối chứng, xa mọi bệ | `-25` | `-15,00` | **10,00 m** |
      | Xuyên khu bệ nhảy (3–7, 9–13) | `1` | `11,00` | **10,00 m** |
      | Ngay sau khi giết mộc nhân (đúng trạng thái lúc báo kẹt: khung thông báo "Nhận được Phi Đao x2." đang hiện) | `6` | `16,08` | **10,08 m** |

    - **Nguyên nhân thật**: bộ điều khiển cửa sổ của tôi, không phải game — cùng họ với bẫy
      `SetForegroundWindow` thất bại im lặng đã ghi ở mục 52. Trước khi kết luận thì đã kịp đọc
      `TerrainCollisionSystem` (no-op, obstacle rỗng), `MovementSystem` (không có va chạm entity),
      `VerticalMovementSystem` (chỉ chạm trục z) và tầng đối chiếu client — **không cái nào chặn được
      trục x**, và đó lẽ ra đã là dấu hiệu đủ rõ để đi đo thay vì ghi một lỗi vào tài liệu.
    - 💡 **Bài học, và là lần thứ TƯ cùng một họ** (mục 31, 35, 52): *đường quan sát sai đánh bại phân
      tích đúng*. Một lỗi ghi vào tài liệu mà không có số đo sẽ khiến người sau đi săn ma. **Không ghi
      "lỗi" vào ROADMAP nếu chưa có một con số tái lập được.**

56. **QUEST 2 ĐÃ CHẠY TRỌN VÒNG TRONG GAME THẬT — và cân bằng của nó thì chưa đạt (2026-09-05)**:
    - Mục 52 mới kiểm chứng sống được nửa đầu (hỏi NPC → nhận quest). Nửa sau — **giết tán tu →
      script `on_event` → hoàn thành → lĩnh thưởng** — nay đã nhìn thấy chạy thật, đo bằng SQL:

      | Mốc | Bằng chứng |
      |---|---|
      | Giết `wandering_bandit` | `progress_json` `{"repelled":0}` → **`{"repelled":1}`**, status **COMPLETED** |
      | HUD | "Phiên Trực Đêm — **Đã hoàn thành**" + nút **Nhận thưởng** hiện ra |
      | Lĩnh thưởng | EXP **695 → 1495** = **đúng +800**; Hộ Linh Phù **3 → 4** (GỘP vào chồng sẵn có, không sinh dòng mới); status → **CLAIMED** |
      | Ăn theo | `character.level` **4 → 5** và thanh EXP nhảy sang **495/500** ngay trong phiên — đúng hai bản vá của mục 52 (đẩy EXP sau khi lĩnh thưởng, và chữa cột `level` tụt lại) |

    - Đây là lần đầu **cả hai** quest của dự án được nhìn thấy chạy hết vòng đời trong game thật.
    - 🔴 **CÂN BẰNG CHƯA ĐẠT — cần chủ dự án quyết.** Với nhân vật cấp 4 mặc **trọn bộ `novice_*`**
      (Công +7, Giáp +7, Kháng +5, Máu +18 ⇒ 118 máu, ~35 sát thương/nhát), tôi **chết 3 lần liên
      tiếp** trước con tán tu. Số của nó: **90 máu, atk 22, def 10**, và nó đánh nhanh hơn nhịp
      1 đòn/giây của người chơi. Muốn kiểm chứng chuỗi quest, tôi phải **tạm nâng `str`/`vit` lên
      400** rồi trả lại 10 ngay sau đó (đã trả).
      - Ba đường sửa, đều là quyết định gameplay: (a) hạ `atk`/nhịp đánh của `wandering_bandit`,
        (b) đẩy quest 2 ra sau một mốc cấp/trang bị cao hơn, (c) để nguyên và coi đây là tường
        chắn có chủ ý. **Không tự chọn.**
      - ⚠️ Nghịch lý đáng chú ý: `steel_katana +7` (Công +18) **đang nằm sẵn trong túi** nhưng
        **đòi Luyện Khí tầng 3** nên đúng lúc cần nhất thì không mặc được.
    - 💡 **Bẫy thao tác đã ngốn 3 mạng**: khung mục tiêu trên HUD có thể vẫn đang khoá **con quái
      cũ** trong khi mình tưởng đã chọn con mới — lúc đó `Z` bay vào khoảng không và trông y hệt
      "đánh hụt". **Luôn xác nhận tên trên khung mục tiêu TRƯỚC khi đánh.**

57. **Suýt hạ nhầm chỉ số con quái — công thức nói ngược lại lời tôi báo (2026-09-05)**:
    - Mục 56 kết luận "cấp 4 mặc trọn bộ Tạp Dịch vẫn chết 3 lần ⇒ cân bằng chưa đạt", và chủ dự án
      đã chọn phương án **hạ sức tán tu** dựa trên đó. Trước khi đổi một con số nào, tôi hỏi lại
      CÔNG THỨC thay vì hỏi màn hình — và nó **bác bỏ tiền đề của chính tôi**:

      | Tình huống | Người chơi cần | Tán tu cần | Kết quả |
      |---|---|---|---|
      | Cấp 4 **có** trọn bộ Tạp Dịch | **3 nhát** (34 sát thương/nhát) | **7 nhát** (17 sát thương/nhát) | thắng thoải mái |
      | Cấp 4 **tay không** | **4 nhát** (30 → 27 sau giáp) | **6 nhát** (18 sát thương/nhát) | thắng, nhưng sát nút |

      Hai bên dùng CHUNG `ATTACK_INTERVAL_SECONDS = 1` (1 đòn/giây) nên "ít nhát hơn" cũng chính là
      "chết sau" — không cần mô phỏng thời gian.
    - ⇒ **Chỉ số của `wandering_bandit` KHÔNG phải vấn đề.** Hạ nó theo kết luận cũ sẽ làm hỏng một
      trận đấu vốn đã cân, để chữa một triệu chứng có nguyên nhân khác.
    - 🎯 **Nguyên nhân thật, và nó nằm ở HÌNH HỌC chứ không ở chỉ số**: hai điểm spawn
      `wandering_bandit` đặt ở **x=21 và x=27** (V25), đúng bằng **`aggro_radius_meters = 6`** của
      loài này. Nghĩa là **mọi vị trí trong đoạn [21, 27] đều nằm trong tầm aggro của CẢ HAI con** —
      người chơi đi làm quest gần như chắc chắn kéo hai con cùng lúc, và sát thương nhận vào **gấp
      đôi: 34/giây thay vì 17**. Con số đó khớp đúng với cảnh chết trong ~3 giây từ máu gần đầy.
    - ✅ `NightWatchDuelBalanceTest` (3 test, `server-world`) khoá cả phép tính tay đôi lẫn con số
      hình học đó lại — sửa spawn hay sửa `aggro_radius` là test đỏ, buộc người sửa nhìn lại phép
      tính thay vì đổi mù. **1160 test xanh, 0 skip.**
    - 💡 **Bài học, lần thứ HAI trong cùng một ngày** (xem mục 55): tôi lại suýt ghi một kết luận
      thiết kế dựa trên quan sát màn hình. Khác biệt lần này là dừng lại trước khi đổi số. **Với
      câu hỏi cân bằng, hỏi công thức trước — nó tái lập được, còn buổi chơi tay thì không.**

58. **V29 — giãn các điểm spawn CÓ AGGRO; và một giới hạn của tôi cần ghi thẳng ra (2026-09-05)**:
    - Chủ dự án chọn **giãn điểm spawn** (không hạ chỉ số quái, xem mục 57). Luật chốt: hai điểm
      spawn có aggro phải cách nhau **hơn tổng tầm aggro của cả hai** — vì một điểm `p` kéo được cả
      A lẫn B đúng khi `|A-B| <= aggroA + aggroB`.
    - **V29** xếp lại cả cụm, KHÔNG chỉ cặp tán tu: `stone_ogre` (x=34) cũng chỉ cách con tán tu thứ
      hai 7m nên cũng chồng aggro — giãn mỗi cặp tán tu mà bỏ qua nó chỉ là **dời lỗi sang chỗ
      khác**. Bố cục mới: **21 / 34 / 47**. Mộc nhân (aggro 0) giữ nguyên 8 và 14.
    - ✅ Đã chạy qua Postgres thật (Testcontainers, 10/10 xanh) và đã áp lên DB dev — `monster_spawn`
      giờ đọc ra đúng `8, 14, 21, 34, 47`.
    - ✅ Hàng rào mới gác **LUẬT** chứ không gác ba con số:
      `PostgresMonsterCatalogAdapterIntegrationTest.shouldKeepAggroSpawnsApartEnoughToPullOneAtATime`
      duyệt MỌI cặp spawn có aggro đọc từ DB — thêm loài mới hay dời điểm spawn đều bị soi lại.
      **1160 test xanh, 0 skip.**
    - 🔶 **Chưa kiểm chứng được TRONG GAME, và tôi ghi rõ thay vì nói vòng**: vào world ở x=24 (lẽ ra
      chỉ một con trong tầm) thì màn hình vẫn hiện HAI con tán tu cạnh nhau, và tôi **không phân
      giải được** bằng ảnh chụp — chúng có thể là con ở 34 đang đuổi trong bán kính leash 10m, cũng
      có thể là thứ khác. Sau hai lần bị chính ảnh chụp đánh lừa trong cùng ngày (mục 55, mục 57),
      tôi **dừng ở đây thay vì đoán tiếp**.
    - ✅ **ĐÃ ĐÓNG 2026-09-06 bằng số đo thật** — xem mục 61: ở x=24 có hai con tán tu trong tầm
      nhìn nhưng **chỉ một con bị kéo aggro**, con kia đi tuần và gây 0 sát thương.
    - 🛠 **Việc cần làm để đóng dứt điểm** (đã làm, xem mục 61): dựng một client HEADLESS trên nền `tools/load-test`
      (`SimulatedPlayer` đã nói đúng giao thức thật) để ĐỌC `MonsterSpawnedEvent`/`AttackResultEvent`
      thành SỐ — vị trí từng con quái và máu từng đòn. Buổi chơi tay không đủ phân giải cho câu hỏi
      thời gian thực; đây là công cụ đúng cho lớp câu hỏi đó.

59. **QUEST THỨ BA — và lần đầu một quest mới KHÔNG tốn một dòng Java nào (2026-09-06)**:
    - Nội dung: **`clear_old_archive`** — "Dọn Thư Khố Cũ", nhịp **GIEO MẦM** của arc 1
      ([STORY.md](../13-game-design/STORY.md) §6): Chu Hoành sai đệ tử dọn góc tây Tàng Thư Đường bỏ
      hoang, nơi mấy cỗ máy canh sách của người xưa vẫn còn cựa quậy. Hạ **2 thạch vệ** → **1200
      EXP + Mảnh Ngọc Cổ**. Mảnh ngọc chính là **McGuffin xuyên truyện** mà §6 mô tả; arc 1 nó chỉ
      là "vật may mắn", nên **Chu Hoành không biết và không được nhắc tới nó** (§8 quy tắc 2 —
      `TownElderDialogueTreeTest` gác cả cây bằng danh sách từ cấm).
    - `prerequisite_quest_id` trỏ `night_watch_bandit`, mốc CLAIMED (ADR-044). Ba nhịp arc 1 giờ
      chạy đúng thứ tự bằng máy: đập mộc nhân → trực đêm → dọn thư khố.
    - ✅ **KHÔNG SỬA MỘT DÒNG JAVA SẢN PHẨM NÀO** — `git diff --stat` chỉ có migration + 1 script
      quest + 1 nhánh hội thoại + 12 khoá i18n vi/en + test + tài liệu. Đây là thứ đợt gỡ hardcode
      ở mục 52 đã hứa, và quest 3 là lần **kiểm chứng thứ hai** của lời hứa đó — lần này còn kèm
      một **loài quái hoàn toàn mới**, thứ mà ADR-037 hứa cũng chỉ là dữ liệu. Cả hai lời hứa đứng
      vững.
    - **Cân bằng tính bằng CÔNG THỨC TRƯỚC, không phải chọn số rồi mới đi kiểm** (bài học mục 57).
      Loài mới `archive_warden` (HP 120 · ATK 26 · DEF 14 · RES 10, Cấp 5) được chọn từ bảng dưới,
      với `hit = round(atk × 100 / (100 + def))` và hai bên chung nhịp `ATTACK_INTERVAL_SECONDS = 1`:

      | Nhân vật | Đối thủ | người chơi cần | quái cần | kết quả |
      |---|---|---|---|---|
      | trọn bộ Tạp Dịch (700 vàng) | tán tu (quest 2) | 3 nhát | 7 nhát | thắng thoải mái |
      | trọn bộ Tạp Dịch | **thạch vệ (quest 3)** | **4 nhát** | **6 nhát** | **thắng, sát hơn** |
      | TAY KHÔNG | **thạch vệ** | **5 nhát** | **5 nhát** | **hoà = chết** |
      | trọn bộ Tạp Dịch | quỷ đá | 7 nhát | 4 nhát | THUA |

    - ⚠️ **Vì sao KHÔNG dùng lại `stone_ogre`** (loài mạnh nhất đang có, và chưa quest nào dùng):
      hàng cuối bảng. Hạ nó cần trọn bộ "Hạ Nhẫn" = **10.900 vàng** ≈ **900 mạng tán tu** — không
      phải "đường chơi thường" của arc 1 theo bất kỳ nghĩa nào. Phép tính này được khoá lại thành
      một ô test (`OldArchiveDuelBalanceTest.theStoneOgreWouldHaveBeenUnbeatableForThisArc`) để lần
      sau không ai phải tính lại.
    - ✅ **Luật hình học V29 áp nguyên vẹn cho hai điểm spawn mới**: 47 → **60** → **73**, mỗi bước
      13m > tổng aggro 12m. Hàng rào `shouldKeepAggroSpawnsApartEnoughToPullOneAtATime` duyệt cả 5
      điểm có aggro và vẫn xanh — đúng như mục 58 nói nó sẽ làm khi có loài mới.
    - ✅ **1172 test xanh, 0 skip, Docker BẬT** — `server-database` chạy đủ 132 test (17/18 lớp là
      `@Testcontainers`, tức Postgres thật), **0 bị bỏ qua**; đây chính là chỗ `./gradlew build`
      biết báo xanh GIẢ khi Docker tắt, nên số skip được đếm chứ không được đoán. Chi tiết:
      `QuestIntegrationTest` 12→16, `PostgresMonsterCatalogAdapterIntegrationTest` 10→11,
      `TownElderDialogueTreeTest` +1, `OldArchiveDuelBalanceTest` +5.
    - 🔶 **CHƯA playtest sống** — mọi khẳng định ở trên là công thức + test, không phải buổi chơi.
      Ba thứ chỉ buổi chơi mới trả lời được: (1) đi từ x=21 ra x=60 có phải chạy ngang quỷ đá ở 47
      không chết không; (2) 20 giây hồi sinh của thạch vệ có làm quest 3 thành ngồi chờ không;
      (3) mảnh ngọc cổ hiện trong túi ra sao khi **chưa có icon**.
    - 💡 **Hai món nợ ghi thẳng ra, không giấu**: `archive_warden` **mượn model của quỷ đá**
      (`mob_stone-ogre`) nên trong game hai loài trông y hệt nhau — khoá lại bằng test để nợ nằm
      trong CI chứ không nằm trong trí nhớ ai; và `ancient_jade_shard` **chưa có icon**
      (`InventoryPanel` vẽ ô trống, không bịa hộp màu).

60. **[ADR-045](../01-architecture/adr/ADR-045-Appearance-Changed-Event.md) — người ĐANG đứng cạnh
    cũng phải thấy model mới; và ô Exit Criteria cuối cùng của Phase 0 (2026-09-06)**:
    - **Nửa sau của bug đổi hệ phái** (dòng #8 bảng "🔴 ĐANG CHỜ CHỦ DỰ ÁN"). Nửa trước vá 2026-08-29
      chỉ sửa CACHE ngoại hình nên người tới SAU thấy đúng; người **đang quan sát** vẫn thấy model cũ
      vì client cố ý bỏ qua `CharacterSpawnedEvent` lặp (thế bất biến "spawn = tạo mới" — thứ đang
      chặn entity trùng, không phải lỗi).
    - Chủ dự án chọn **phương án C: event riêng** và **Accept ADR-045 cùng ngày** —
      `CharacterAppearanceChangedEvent`, opcode **307**,
      chỉ THÊM nên `protocolVersion` KHÔNG tăng (đúng tiền lệ ADR-035).
    - Đường đi giữ nguyên ranh giới kiến trúc: `server-world` **không biết** "ngoại hình" là gì, nó
      chỉ trả lời "ai đang nhìn thấy ai" (`observersOf`); gateway mới tra hệ phái. Client thêm
      `WorldModelRenderer.replaceCharacterModel` — thay model **tại chỗ**, GIỮ hướng mặt và trạng
      thái chết, nên thế bất biến ở trên còn nguyên.
    - **Chính người đổi KHÔNG nhận event**: model của họ đã đổi từ opcode 207
      (`LocalCharacterAppearance`). Gửi thêm vừa là gói thừa (ngược ADR-042) vừa dựng đường quyền
      lực thứ hai cho cùng một việc.
    - ✅ **Kiểm chứng bằng SỐ chứ không bằng ảnh chụp** (đúng bài học mục 55/57):
      `GatewayServerIntegrationTest.observerShouldGetNewFaction_whenAnotherCharacterChangesItInPlace`
      — hai socket THẬT, A đứng nhìn, B lên Lv10 rồi đổi sang Thuỷ, A nhận đúng opcode 307 mang
      `FACTION_THUY`. Cộng 4 test đơn vị (2 world: gửi cho observer / KHÔNG dội lại người đổi;
      2 gateway: nội dung gói / không gửi khi không tra được ngoại hình).
    - 🔶 **Nửa client chưa nhìn thấy chạy thật**: đường `replaceCharacterModel` mới chỉ có test ở
      tầng dưới nó. Cần playtest hai client song song (công thức bàn giao 2026-08-29) trước khi ghi
      là đã kiểm chứng sống.
    - ✅ **Ô Exit Criteria cuối cùng của Phase 0–3 đã đóng**: "mọi module ở §1 REPOSITORY-STRUCTURE
      tồn tại". Chủ dự án chọn **sửa tài liệu theo thực tế** thay vì dựng 5 module rỗng —
      `REPOSITORY-STRUCTURE.md` §1 nay là cây THẬT (thêm `feature-party`/`feature-trade`/
      `feature-pvp`/`feature-monster`, `quality/architecture-tests`, `third-party/m2/`,
      `tools/blender/`), và 4 module dự kiến chuyển xuống **§1.1** kèm điều kiện để được sinh ra.

61. **Client HEADLESS đọc thế giới thành SỐ — và câu hỏi treo của mục 58 đã có câu trả lời
    (2026-09-06)**:
    - Mục 58 dừng lại ở chỗ *"vào world ở x=24 thì màn hình vẫn hiện HAI con tán tu, và tôi không
      phân giải được bằng ảnh chụp"*. Công cụ đúng cho lớp câu hỏi đó nay đã có: **`tools:load-test`
      thêm nhánh `gameProbe`** — một client không giao diện, nói ĐÚNG giao thức dây thật (TLS +
      protobuf, dùng chung tầng `GameConnection` mới tách ra khỏi `SimulatedPlayer`), giải mã 16
      opcode thành dòng TSV có mốc thời gian, rồi in báo cáo: từng con quái ở đâu, cách bao xa, ai
      đánh ai, mỗi đòn bao nhiêu sát thương.

      ```
      ./gradlew :tools:load-test:gameProbe --args="--username=kagetest --characterId=3 --moveToX=24 --observeSeconds=15"
      ```

    - ✅ **ĐO THẬT ở x=24** (`Kagemaru`, quan sát 8 giây, không đánh trả):

      | id | loài | vị trí cuối | k/c gần nhất cả lượt | có đánh mình không |
      |---|---|---|---|---|
      | -2 | `training_dummy` | (14,00, 0,00) | 10,00 m | không (aggro 0) |
      | **-3** | `wandering_bandit` (spawn 21) | (22,22, −0,91) | **1,19 m** | **có — 9/9 đòn** |
      | **-4** | `wandering_bandit` (spawn 34) | (31,69, 1,41) | **7,82 m** | **không, 0 đòn** |
      | — | `stone_ogre` (spawn 47) | ngoài AoI 15 m | — | không xuất hiện |

    - ⇒ **Ảnh chụp KHÔNG sai — suy luận từ nó mới sai.** Ở x=24 đúng là nhìn thấy hai con tán tu,
      nhưng chỉ **một** con bị kéo aggro; con thứ hai chỉ **đi tuần** (x giảm 34,17→31,69 trong khi
      y tăng 0→1,41, tức đi LỆCH khỏi người chơi đứng ở y=0), không bao giờ vào trong 6 m và không
      gây một điểm sát thương nào. Toàn bộ 9 đòn đều `attacker=-3`, 17 sát thương/đòn, máu 118→16.
      **V29 làm đúng việc nó định làm** — ô 🔶 của mục 58 đóng.
    - Lượt đo thứ hai (đứng x=14, `--attackTarget=nearest`) khoá luôn bảng sát thương hai chiều:
      `3 → -2` 35 sát thương, rồi **53 chí mạng = 35 × 1,5** hạ mộc nhân, kèm `EXPERIENCE_CHANGED
      leveledUp=true` và `ITEM_GRANTED`; sau đó `-3` hạ người chơi và `CHARACTER_RESPAWNED
      hp=118/118`.
    - 💡 **Bài học nối tiếp mục 55/57**: ba lần trong ngày 2026-09-05 tôi bị chính đường quan sát
      đánh lừa, và lần nào cách thoát cũng là *đi tìm một con số tái lập được*. Nay câu hỏi
      "quanh mình có gì" đã có đường đo riêng, không phải đường mắt nhìn.
    - ✅ **Sau khi gộp cả ba luồng của ngày (quest 3 · ADR-045 · gameProbe): `./gradlew build`
      BUILD SUCCESSFUL, 1200 test, 0 fail, 0 skip**, Docker bật (1161 trước đợt này).
    - 🐛 **Một lỗi có sẵn phát hiện trên đường, KHÔNG sửa** (báo theo CLAUDE.md §4):
      `./gradlew build` **ĐỎ nếu biến `DB_PASSWORD` đang có trong shell** — `AssetPipelineIntegrationTest`
      khi đó rẽ vào nhánh upsert `art_asset` và đòi sidecar `<key>.model-meta.json` mà chính fixture
      của nó không bao giờ ghi. Test dễ vỡ ở `tools:asset-pipeline`, không liên quan module nào của
      đợt này.

62. **PLAYTEST SỐNG hai thứ vừa làm — một cái ĐÚNG, một cái lộ ra lỗ cân bằng đo được (2026-09-06)**:

    **(a) ADR-045 — nửa CLIENT nay đã NHÌN THẤY chạy thật.** Hai client song song trên cùng máy:
    client A (`Kagemaru`) đứng yên nhìn, client B (`tanthua44`, Lv10) bấm **H** → "Thức tỉnh linh
    căn" → chọn **Thuỷ**. Trên màn hình A, nhân vật B **đổi từ model Hoả sang model Thuỷ ngay tại
    chỗ**, không biến mất rồi hiện lại, không đổi vị trí. `character.faction` = `THUY` trong DB.
    ⇒ Cả hai nửa của ADR-045 (đường dây + đường hiển thị) đã kiểm chứng xong.

    **(b) Quest 3 `clear_old_archive` — VÒNG ĐỜI NỘI DUNG chạy trọn, đo bằng SQL:**

    | Mốc | Bằng chứng |
    |---|---|
    | Nhận qua hội thoại | Chu Hoành: "Ngươi dọn giúp ta hai cỗ..." → `IN_PROGRESS`, `{"cleared":0}` |
    | HUD | "Dọn Thư Khố Cũ — Đang làm" ở khung theo dõi + nhật ký (J) |
    | Giết 2 thạch vệ | `{"cleared":1}` → `{"cleared":2}` → **COMPLETED** (script `on_event` chạy đúng) |
    | Lĩnh thưởng | EXP **1625 → 2825 = đúng +1200**, cấp **6 → 8**, status **CLAIMED** |
    | Vật phẩm | `ancient_jade_shard` **x1** trong `inventory_item` |

    **(c) 🔴 CÂN BẰNG: quãng ĐƯỜNG ĐI mới là thứ giết người chơi, không phải con quái.** Đo hai
    lần bằng `gameProbe`, **kết quả trùng khít**: đi bộ từ x=0 tới x≈58 với nhân vật cấp 6 mặc trọn
    bộ Tạp Dịch mất **65 máu** trước khi chạm mặt thạch vệ:

    | Nguồn | Sát thương | Máu còn |
    |---|---|---|
    | Tán tu (spawn 21) | 17 | 101 |
    | Tán tu (spawn 34) | 17 | 84 |
    | **Quỷ đá (spawn 47)** | **31** | **53** |
    | Thạch vệ (spawn 60) | 20 × 3 | **0 — chết** |

    - **Phép tính tay đôi thì ĐÚNG y như bảng của V30**: người chơi đánh **32**/nhát (4 nhát hạ 120
      máu), thạch vệ đánh **20**/nhát (6 nhát hạ 118 máu). Ở máu đầy thì thắng. Vấn đề là **tới nơi
      chỉ còn 53 máu**, tức chịu được 2 nhát.
    - **Không có hồi máu ngoài chiến đấu** — `grep` toàn `server-world` + `engine`: KHÔNG có system
      nào hồi máu theo thời gian. Máu chỉ đầy lại khi CHẾT rồi hồi sinh.
    - 🎯 **Và một lỗ hình học mà luật V29 KHÔNG bắt được**: V29 giãn spawn theo **tầm aggro**
      (6+6=12 < 13m), nhưng khi người chơi **ĐỨNG LẠI ĐÁNH** thì thứ quyết định là **tầm đuổi
      (leash) = 10m**. Quỷ đá ở x=47 đuổi được tới **x=57**, mà thạch vệ thứ nhất spawn ở **x=60**
      — lần đo thứ hai, chính **quỷ đá là con ra đòn kết liễu** trong lúc người chơi đang đánh thạch
      vệ. Luật "hai điểm spawn cách nhau > tổng aggro" là điều kiện ĐỦ để không kéo hai con cùng
      lúc, nhưng KHÔNG đủ để có một chỗ đánh nhau yên tĩnh.
    - **Cần chủ dự án quyết** (Điều 15 + bài học mục 57 — không tự đổi số cân bằng): (a) dời điểm
      spawn thạch vệ ra xa quỷ đá hơn (≥ tầm đuổi + tầm đánh), (b) hạ `leash_radius_meters` của quỷ
      đá, (c) thêm hồi máu ngoài chiến đấu, (d) coi đây là tường chắn có chủ ý và để nguyên.
    - ⚠️ Để kiểm chứng phần NỘI DUNG ở (b), tôi tạm nâng `str`/`vit` lên 400 rồi **trả lại 10 ngay
      sau đó** (đã kiểm lại bằng SQL). Mọi con số ở (c) đo TRƯỚC khi nâng, trên chỉ số thật.

63. **Trả hai món nợ art mà V30 tự ghi ra — quái quest 3 có model riêng, mảnh ngọc có icon
    (2026-09-06)**:
    - Mục 59 đóng quest 3 kèm hai nợ nói thẳng: `archive_warden` **mượn model quỷ đá**
      (`mob_stone-ogre`) nên trong game hai loài trông y hệt nhau, và `ancient_jade_shard`
      **chưa có icon** nên `InventoryPanel` vẽ ô trống. Cả hai nay đã trả.
    - ✅ **`mob_archive-warden`** — dựng bằng `kage_mesh_kit`
      (`tools/blender/build_monster_archive_warden.py`), 38 part, **5.140 tam giác nguồn → LOD
      5.140 / 5.140 / 4.988**, không texture, 0,24 MB. **Ngân sách tam giác nay 60/60 file đạt
      chuẩn** §4.4 (57 trước đợt này). **0 lượt Rodin** — khoá vẫn `API_INSUFFICIENT_FUNDS`, đúng
      bức tường `wandering_bandit` và `town_elder` đã gặp; đường kit là bản offline tất định và mọi
      bước sau mesh dùng chung, nên có khoá trả tiền thì đổi art là một lệnh.
    - ✅ **`icon_ancient_jade_shard`** — vào đúng atlas `atlases/{low,med}/main.atlas` mà
      `InventoryPanel` tra (vùng `icon_<code>`), 64×64 ở tier MEDIUM.
    - ✅ **V31**: một câu `UPDATE monster_template SET model_key = 'mob_archive-warden'`. Không đổi
      schema. Ô test khoá nợ (`shouldGiveEachSpeciesItsOwnModel`) đổi theo, và **thêm một khẳng
      định mới**: khoá của thạch vệ phải KHÁC khoá của quỷ đá — đó mới là thứ V31 vừa gỡ.
    - ✅ **Đã NHÌN THẤY, không phải suy ra**: `runWorldPreview` chụp qua đúng
      `MonsterPresenter`/`WorldModelRenderer` mà `WorldScreen` dùng — thạch vệ đứng ngay cạnh quỷ đá
      trong khung và không còn lẫn được; icon thì soi thẳng vùng 64×64 đã đóng gói trong `main.png`.
    - 💡 **Ba bài học, cả ba do ảnh dựng thật bác bỏ chứ không phải Blender** (chi tiết + số đo ở
      [ASSET-LOG.md](../../assets/raw/ASSET-LOG.md) mục 2026-09-06): vòm tóc che kín mặt **lần thứ
      hai**; bỏ vòm rồi thì mặt anime của kit trượt ra hai mép sọ vì nó được dựng cho góc NGHIÊNG
      còn client vẽ gần chính diện; và **thứ gắn sau lưng thì camera chính diện không thấy, đẩy ra
      xa bao nhiêu cũng vậy** — thứ vượt được một cái thân là BỀ NGANG, không phải chiều sâu.
    - 🔶 **CHƯA playtest sống** — bằng chứng là ảnh dựng của client + ảnh atlas, không phải một trận
      đánh thạch vệ và một lần mở túi đồ trong game.
    - 🔶 **Dòng `art_asset` cho hai asset mới CHƯA được ghi vào một DB thật** trong đợt này:
      `ArtRegistryUpserter` chỉ chạy khi có `DB_PASSWORD`, và biến đó lại đang kích hoạt đúng cái lỗi
      có sẵn ở mục 61. Không cần INSERT tay — pipeline làm việc đó ở lần chạy có DB kế tiếp, và
      `OrphanAssetChecker` chạy SAU `ArtRegistryUpserter` nên hai file mới không thể thành mồ côi.

64. **V32 — dời thạch vệ sang PHÍA TÂY, và luật hình học thứ HAI (2026-09-06)**:
    - Chủ dự án chọn phương án dời spawn sau khi xem số đo ở mục 62c. **V32**: `archive_warden`
      từ **60 / 73** sang **-20 / -33**.
    - **Vì sao phía tây**: lời thoại của chính Chu Hoành nói *"góc phía TÂY Tàng Thư Đường"* trong
      khi V30 lại đặt spawn ở phía ĐÔNG, sau lưng cả hai tán tu lẫn quỷ đá. Phía tây chỉ có trưởng
      lão ở x=-5 (NPC, không đánh ai) ⇒ đường đi sạch. Bản đồ nay khớp lời thoại.
    - ✅ **Đo lại bằng `gameProbe`, chạy thật ba lượt**:

      | Lượt | Đường đi | Máu lúc chạm mặt thạch vệ | Kết quả trận |
      |---|---|---|---|
      | Đông (cũ), lượt 1 | 0 → 58 | **53/118** (mất 65 dọc đường) | **chết** |
      | Đông (cũ), lượt 2 | 0 → 58 | **53/118** | **chết** — đòn kết liễu của QUỶ ĐÁ |
      | **Tây (V32), lượt 1** | 0 → -19 | **118/118** — 0 sát thương dọc đường | thua sát nút: **né hụt 2/5 nhát** |
      | **Tây (V32), lượt 2** | 0 → -19 | 118/118 | **THẮNG** — 4 nhát × 32, còn **38/118** |

    - ⇒ Đường đi nay **miễn phí**, và trận đấu đúng như bảng của V30 dự tính: 4 nhát hạ 120 máu,
      chịu lại 4 nhát × 20. **Nhưng nó là trận sát nút**: người chơi bắn 91% trúng (`hitPercent =
      (90 + agi × 0,1)/100`), thạch vệ 93% — hụt hai nhát là thua. Đây là **biên độ may rủi có
      thật**, ghi ra để lần cân bằng sau biết mà đo, không phải để đoán.
    - ✅ **Luật hình học thứ HAI, khoá bằng test** —
      `PostgresMonsterCatalogAdapterIntegrationTest.shouldLeaveEveryAggroSpawnAQuietPlaceToFightIn`:
      với MỌI cặp điểm spawn có aggro, khoảng cách phải lớn hơn **leash của con bên cạnh + tầm đánh
      2m**. Luật V29 (theo tầm AGGRO) chỉ hứa "đi ngang không kéo hai con"; **đứng lại đánh** thì
      tầm ĐUỔI mới là thứ quyết định — chính chỗ đó đã để quỷ đá@47 (leash 10 ⇒ với tới 57) ra đòn
      kết liễu người chơi đang đánh thạch vệ@60.
    - 🔧 Gộp luôn hai luồng song song cùng ngày: **công cụ `gameProbe` nay đo được TRỌN vòng đời
      nhiệm vụ** (hội thoại 602–606, nhật ký 610/611, lĩnh thưởng 608/609, túi đồ 507/508) và
      **lỗi build-đỏ-theo-biến-môi-trường ở mục 61 đã sửa** (cấu hình registry thành THAM SỐ, chỉ
      `main()` đọc môi trường; thêm `AssetPipelineRegistryBranchIntegrationTest` chạy nhánh Postgres
      mà trước nay chưa test nào chạm tới). **1229 test xanh, 0 skip.**
65. **QUEST THỨ TƯ — arc 1 ĐÓNG LẠI, và một lỗi Flyway chỉ có DB dev mới dính (2026-09-06)**:
    - **`awakening_trial` — "Khảo Hạch Thức Tỉnh Linh Căn"** (seed **V33**), nhịp ĐÓNG của arc 1
      theo [STORY.md §6](../13-game-design/STORY.md) nguyên văn: *"được đặc cách dự khảo hạch →
      thức tỉnh linh căn (Lv10)"*, mở khoá *"chọn hệ (Lv10)"*. Tiên quyết `clear_old_archive`, mốc
      `CLAIMED` (ADR-044). Arc 1 nay có đủ bốn nhịp: việc vặt → cao trào đêm trực → gieo mầm →
      khảo hạch.
    - **Quest thứ BA liên tiếp không tốn một dòng Java sản phẩm nào** (V28 → V30 → V33): migration
      + script + nhánh hội thoại + i18n. Lời hứa "thêm quest = dữ liệu" của đợt gỡ hardcode
      2026-09-05 và của ADR-037 vẫn đứng.
    - 🆕 **Quest ĐẦU TIÊN có một mốc CƠ CHẾ nằm sau nó** — và đó là chỗ nó khác ba quest trước.
      Chu Hoành hứa thức tỉnh linh căn; thứ thi hành lời hứa đó là
      `SelectFactionUseCase.MIN_LEVEL = 10`. Nếu lĩnh thưởng xong mà vẫn Cấp 9 thì quest nói dối.
      Nên **1800 EXP là con số CHỌN TỪ RÀNG BUỘC**, không phải chọn rồi mới đi kiểm: đường chơi
      TỐI THIỂU (chỉ EXP quest + đúng số quái mà chính các quest bắt hạ) cộng lại 2.785 trước khi
      lĩnh, cần ≥ 4.500 ⇒ thưởng ≥ 1.715. Khoá bằng
      `QuestIntegrationTest.shouldLandExactlyAtLevelTen_afterTheWholeArcOneChain` — nó chơi cả bốn
      quest qua use case thật, đọc `exp_reward` **thẳng từ DB** và suy cấp bằng chính
      `ExperienceCurve`, nên hạ EXP một loài quái hay sửa `give_exp` đều làm nó đỏ.
    - **Loài mới `trial_construct` — "Trắc Linh Thạch Nhân"**, và nó bẻ trục thiết kế: ba loài biết
      đánh trước nó đi theo một hướng duy nhất là mạnh dần (8 → 22 → 26 → 40); con này **máu cao
      nhất arc 1 (130) mà đòn nhẹ nhất (15)**, `hit_percent` cũng thấp nhất (85). Nó ĐO chứ không
      giết — đúng luận đề của Chu Hoành. Loài đầu tiên **cố ý không rơi gì** (bảng rơi RỖNG chứ
      không phải thiếu bảng: đường chạy thật tra `drop_table.code` bằng **mã quái**, và
      `PostgresDropTableAdapter` phân biệt "rỗng ⇒ INFO, cố ý" với "không có ⇒ WARN, chắc gõ sai").
    - ✅ **Chỉ số chọn từ SỐ ĐO, không từ máu đầy** — đây là bài học V32 được áp dụng TRƯỚC chứ
      không phải sau. Đo bằng `gameProbe` trước khi viết migration: đi bộ x=0 → x=-47 tốn **đúng 40
      máu** (mỗi thạch vệ một nhát × 20) ⇒ tới nơi còn **78/118**. Đó mới là đầu vào của bài toán.

      | Tình huống | người chơi cần | cỗ máy cần | biên |
      |---|---|---|---|
      | máu đầy 118 | 4 nhát | 10 nhát | +6 |
      | **tới nơi với 78 (đường chơi thật)** | **4 nhát** | **7 nhát** | **+3** |
      | tay không, tới nơi với 56 | 5 nhát | 5 nhát | hoà = chết |

      Biên 3 nhát là CHỦ ĐÍCH: mục 64 ghi lại rằng quest 3 thắng sát nút và hụt hai nhát là thua.
      Nhịp đóng của một arc không nên là một lần tung đồng xu. Cái khó của quest 4 nằm ở QUÃNG
      ĐƯỜNG, và **lời thoại nói thẳng điều đó với người chơi** thay vì để họ tự phát hiện giữa
      đường như V30 từng làm.
    - ✅ **PLAYTEST SỐNG, trọn vòng, bằng `gameProbe`**: nhận qua hội thoại thật (`about_town` →
      `awakening_trial` → `accept_quest`) → đi bộ tới -46 → hạ cỗ máy **4 nhát × 34** → `COMPLETED`
      → lĩnh thưởng **+1800 EXP đúng, 0 vật phẩm đúng** → **Cấp 8 → 10**, cột `character.level`
      trong DB cũng thành 10. Kết trận còn **38/118**; đường đi lượt này chỉ tốn 20 (một thạch vệ
      đánh trúng, một hụt — chúng bắn 93%).
    - 🐛 **Bẫy Flyway chỉ DB dev mới dính, tìm ra vì server không khởi động nổi**: `V31` (art thạch
      vệ) **chưa bao giờ chạy** trên DB dev, trong khi `V32` đã chạy — số hiệu V31 được *dành sẵn*
      cho một luồng art song song và luồng đó về đích SAU. Flyway thấy "resolved migration not
      applied: 31" và từ chối chạy. DB mới tinh (CI, deploy) không dính vì Flyway chạy theo thứ tự
      phiên bản. **Hệ quả thật, và nó giải thích một chuyện đang treo**: cột `model_key` của
      `archive_warden` vẫn là `mob_stone-ogre` suốt từ hôm qua, tức **art mới chưa từng lên dây**
      dù file `.glb` đã có và migration đã commit. Đã chữa DB dev bằng cách chạy tay V31 + chèn
      dòng lịch sử với checksum tính đúng thuật toán Flyway (kiểm chứng bằng cách tính lại checksum
      của V30/V32 và so với DB: khớp tuyệt đối). Nay `MONSTER_SPAWNED_EVENT` gửi
      `model=mob_archive-warden` thật — đã nhìn thấy trên dây.
      ⚠️ **Bài học**: giữ chỗ số hiệu migration cho một luồng song song là an toàn với DB mới,
      nhưng **DB đang chạy sẽ kẹt cứng** nếu số hiệu sau về đích trước. Lần sau: đánh số theo thứ
      tự VỀ ĐÍCH, đừng đặt chỗ trước.
    - 🧹 **Một test xanh mô tả bản đồ không còn tồn tại** — `OldArchiveDuelBalanceTest
      .theNewArchiveSpawnsKeepTheDuelOneOnOne` vẫn ghi thạch vệ ở `60`/`73` suốt từ khi V32 dời
      chúng sang `-20`/`-33`. Nó vẫn xanh vì ba phép trừ là số học thuần. Đây là dạng mục ruỗng khó
      thấy nhất: không có gì đỏ để mách. Đã sửa cả số lẫn tên ô test.
    - 📄 [STORY.md §7](../13-game-design/STORY.md) thêm dòng cho loài mới (§8 quy tắc 3 — cùng PR).
      🔴 **Một lỗ hổng hệ thống báo cáo riêng cho chủ dự án** (dòng 17 bảng "ĐANG CHỜ CHỦ DỰ ÁN"):
      **arc 1 KHÔNG có hồi máu nào cả**. Quest 4 né được vì nó chỉ có MỘT trận; quest 3 thì không
      (cần hạ 2 thạch vệ mà hạ xong con đầu còn 38/118).
66. **PLAYTEST bằng CLIENT THẬT — art thạch vệ cuối cùng cũng nhìn thấy, và một lỗ nền đất (2026-09-06)**:
    - Đóng nốt việc treo từ hôm qua (*"art mới chỉ thấy qua ảnh dựng client, chưa thấy trong một
      trận đánh thật"*). Mở server + `client-desktop` thật, đăng nhập, đứng cạnh từng con:
      - ✅ **`mob_archive-warden` dựng ra model đá thật, KHÔNG phải hộp đỏ thay thế** — cho CẢ
        `archive_warden` lẫn `trial_construct` (hai loài dùng chung khoá model, nợ art V33 khai
        sẵn). Đây là lần đầu art đó lên màn hình: nó chỉ vừa lên dây được sau khi chữa lỗi Flyway
        ở mục 65.
      - ✅ Tên tu tiên mới hiện đúng trên khung mục tiêu: **"Trắc Linh Thạch Nhân"**, **"Thạch vệ
        Tàng Thư"**.
      - ✅ Số sát thương khớp bảng cân bằng **trên màn hình**, không phải chỉ trong test: thạch vệ
        **−20**, cỗ khảo hạch **−12**.
      - ✅ Màn chọn nhân vật hiện **"Kagemaru — Hoả (Luyện Khí 5)"** — Cấp 10 ánh xạ đúng thành
        *Luyện Khí tầng 5*, tức khớp CHÍNH lời thoại `about_realm` của Chu Hoành (*"Tới Luyện Khí
        tầng năm thì đo được linh căn"*). Tường thuật và cơ chế gặp nhau trên màn hình.
      - ℹ️ Hộp vàng lơ lửng trên đầu quái **không phải lỗi**: `MonsterPresenter.drawTargetMarker`
        (`Color.GOLD`) — dấu hiệu mục tiêu đang chọn.
    - 🐛 **Lỗi thật bắt được, và nó do chính V32/V33 gây ra**: `DemoPlatforms.GROUND_MIN_X = -30`,
      trong khi V32 dời thạch vệ sang **-33** và V33 đặt sân khảo hạch ở **-47**. Vật lý không hề
      gì (mặt đất `z=0` là VÔ HẠN, `VerticalMovementSystem.GROUND_Z`) nên **không test nào đỏ** —
      nhưng người chơi đi sang tây thì thấy mình và lũ quái **đứng lơ lửng trong khoảng đen**,
      nhìn y như lỗi render. Đã nới đầu tây `-30 → -55` (phủ sân khảo hạch còn dư 8m) và ghi luật
      vào javadoc: **thêm điểm spawn ngoài `[-55, 60]` thì phải nới dải này CÙNG migration**, vì
      `monster_spawn` sống trong DB còn hai số kia là hằng số client — không có gì nối chúng lại.
    - 🔴 **Lỗ hổng hồi máu: nhìn thấy chứ không phải tính ra.** Trong một buổi playtest ~15 phút,
      nhân vật **chết BA lần**, cả ba đều vì đứng cạnh quái mà không có cách nào hồi máu — không
      phải vì đánh thua. Đây là bằng chứng sống cho dòng **17** bảng "🔴 ĐANG CHỜ CHỦ DỰ ÁN".
67. **ART cho cỗ khảo hạch — trả nợ V33 TRONG CÙNG NGÀY, và một hàng rào để không nợ lần thứ tư (2026-09-06)**:
    - `mob_trial-construct` ("Trắc Linh Thạch Nhân") dựng bằng `kage_mesh_kit` chạy headless
      (`tools/blender/build_monster_trial_construct.py`) — **36 part, 4.986 tam giác cả ba tier**,
      dưới ngưỡng low 5.000 của ART-PIPELINE §4.4. **V34** lật `model_key` sang khoá đó.
      Rodin vẫn `API_INSUFFICIENT_FUNDS` (dòng #12 bảng chờ chủ dự án), nên vẫn đi đường kit —
      offline, tất định, và mọi bước sau mesh đều dùng chung nên đổi sang `.glb` thật sau này
      không phải sửa gì khác.
    - 🎨 **Bài học lớn nhất: ở 50px, "xám nhạt hơn một bậc" KHÔNG PHẢI là một khác biệt.**
      Bản đầu chọn đá vôi nhạt hơn granite của thạch vệ đúng một bậc; `runWorldPreview` bác bỏ
      thẳng — hai con vẫn lẫn nhau và cỗ khảo hạch đọc ra như *"thạch vệ bị lấy mất đồ"*. Bản thứ
      hai không nhích màu nữa mà **đảo ngược BỐ CỤC SÁNG-TỐI**: thạch vệ là *thân tối + một mảng
      sáng* (bia đá trắng), cỗ khảo hạch là *thân ngọc sáng + các chi tiết tối* (mũ sắt, kính che,
      tấm ngực, nẹp tay). Đảo bố cục sống sót qua cả ba phép thử mà nhích màu không qua nổi: thu
      nhỏ, chuyển xám, nhìn trong một giây. **Và nó đúng lore hơn** — 測靈 thạch trong thể loại này
      là ngọc nhạt chứ không phải granite.
    - 📏 **Một thứ đã thử, đã ĐO, và đã gỡ**: thêm `cloak` cho đỡ "trơn". `_acc_cloak` treo SAU
      lưng mà camera client nhìn gần chính diện ⇒ **bị che kín trong MỌI khung** (idle/run/attack/
      die), tốn **116 tam giác** và vẽ ra đúng con số không. Đã gỡ, và ghi lại làm câu hỏi để ngỏ:
      bốn nhân vật B3 trong `build_kage_character.py` cũng mặc `cloak` — đáng kiểm xem chúng có
      đang trả cùng cái phí đó không.
    - 🚧 **Hàng rào CI mới, và lý do nó phải là hàng rào CHUNG**: `PostgresMonsterCatalogAdapter
      IntegrationTest.shouldNotLetAnyTwoSpeciesShareAModelKey` — không loài nào được dùng chung
      `model_key`. Ba lần liên tiếp (V23 → V30 → V33) một loài mới ra đời kèm nợ art là đủ để biết
      lần thứ tư sẽ tới, và lần đó sẽ không có ai nhớ mà thêm một dòng khẳng định riêng. Ô test cũ
      chỉ bắt được những món nợ người viết test ĐÃ BIẾT là có; ô mới tự bắt.
    - ✅ `WorldModelPreviewTool` nay dựng CẢ BỐN loài có model cạnh nhau — khung đó sinh ra để trả
      lời đúng một câu, và câu đó vừa khó lên: **ba cỗ máy đá cùng một dòng dõi** có còn phân biệt
      được không, khi lore bắt chúng phải giống nhau ở mức nào đó.
