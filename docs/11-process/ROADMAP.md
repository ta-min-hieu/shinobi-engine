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
- [ ] `./gradlew build` xanh trên CI
- [ ] Mọi module ở §1 REPOSITORY-STRUCTURE tồn tại (có thể rỗng)
- [ ] ArchUnit chạy (dù chưa có gì để kiểm)
- [ ] `docker compose up` dựng được PostgreSQL + cache
- [ ] Secret scanning hoạt động

---

## Phase 1 — Engine Core

**Mục tiêu**: mở được cửa sổ, nhân vật di chuyển. **Không network.**

| Việc | Nguồn |
|---|---|
| `engine-core`: render loop + Screen/State | [ENGINE.md §3](../03-engine/ENGINE.md) |
| ⚠️ **Fixed timestep + MAX_FRAME_TIME** | [ENGINE.md §4](../03-engine/ENGINE.md) |
| `engine-ecs`: bọc Ashley ở mức **khái niệm** | [ECS.md §10](../03-engine/ECS.md) |
| ⚠️ **World unit = mét** (không pixel) | [RENDERING.md §4](../03-engine/RENDERING.md) |
| Input abstraction (`GameAction`, không phím) | [CLIENT.md §5](../04-client/CLIENT.md) |
| `PlatformAdapter` | [CLIENT.md §11](../04-client/CLIENT.md) |
| ⚠️ **Test font tiếng Việt có dấu** | [RESOURCE.md §7](../04-client/RESOURCE.md) |
| ⚠️ **Test atlas 2048 trên Android thật** | [RESOURCE.md §5](../04-client/RESOURCE.md) |
| Hạ tầng i18n | [UI.md §7](../04-client/UI.md) |
| `tools/asset-pipeline` + validate | [RESOURCE.md §11](../04-client/RESOURCE.md) |
| JMH harness | [BENCHMARK.md](../09-performance/BENCHMARK.md) |

**ADR cần trước Phase 1**: ✅ ADR-018 (tick rate 60Hz — đã chốt) · ⏳ ADR-020 (viewport)

**Exit Criteria:**
- [ ] Chạy `client-desktop` → nhân vật hiển thị, di chuyển bằng bàn phím
- [ ] Asset load qua pipeline thật (không file tạm)
- [ ] Tiếng Việt có dấu hiển thị đúng
- [ ] Fixed timestep hoạt động (FPS đổi không ảnh hưởng tốc độ nhân vật)

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
- [ ] Client kết nối server, ping/pong hoạt động
- [ ] Mất kết nối → reconnect có backoff
- [ ] Protocol round-trip test pass

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
- [ ] 2 client kết nối cùng server, thấy nhau di chuyển
- [ ] Dữ liệu persist qua restart server
- [ ] Speed hack / teleport bị server từ chối
- [ ] Test authz pass (client A không điều khiển được nhân vật B)

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
| PvP arena + battlefield | [GDD](../13-game-design/GAME-DESIGN-DOCUMENT.md) |
| ✅ 4 hệ phái còn lại lát cắt 3 (2026-07-25 — Thủy/Phong/Lôi/Thổ chơi được thật qua socket: khắc chế nguyên tố, status-effect framework mới (slow/stun/shield/buff/debuff), 16 skill script mới + passive, chọn hệ Lv10) | [GDD §4](../13-game-design/GAME-DESIGN-DOCUMENT.md) |

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

**Scope cut có chủ đích** (ghi lại để không lẫn với "quên làm"): (a) Hỏa vẫn chỉ có 1 skill active —
hoàn thiện nốt 2 active/2 passive/Tuyệt Kỹ là việc lát cắt sau; (b) "Đá Chuyển Hệ" sau lần đổi hệ
miễn phí đầu tiên (GDD §4) CHƯA enforce — `SelectFactionUseCase` chỉ gate `level>=10`, chưa đếm số
lần đổi/tốn item (cần thêm cột DB + item ở feature-inventory, để dành lát cắt sau).

**1 bug thật phát hiện khi viết test E2E** (không phải đoán trước): `WorldFacade.handleUseSkill` có
guard cũ từ Phase 4 (`targetEntityId == characterId` → bỏ qua) chặn NHẦM mọi skill SELF/ALLY mới
(heal/buff/khiên tự thân) — guard này đúng cho `fireball` (chỉ có ENEMY-target) nhưng sai hoàn toàn
với thiết kế đa-loại-mục-tiêu mới. Phát hiện qua `shouldHealAlly_whenHealSkillCast`/
`shouldAbsorbDamageWithShield_beforeReducingHp` treo timeout dù logic `SkillSystem.tryCast` đúng.
Sửa: bỏ guard tự nhắm ở `WorldFacade`, chuyển validate vào `SkillSystem.tryCast` (chặn tự nhắm CHỈ
với `SkillTargetKind.ENEMY`, cho phép SELF/ALLY tự nhắm có chủ đích).

---

## Phase 9 — Optimization

⚠️ **Đây là Phase đầu tiên được phép tối ưu** (Điều 30 — đo trước).

| Việc | Nguồn |
|---|---|
| 🔴 **Load test + Soak test ≥ 2h** | [BENCHMARK.md §5](../09-performance/BENCHMARK.md) |
| Đo & cập nhật **toàn bộ ngân sách** | [PERFORMANCE.md §2](../09-performance/PERFORMANCE.md) |
| 🔴 **Đánh giá lại ADR-006 (Ashley)** | [ECS.md §9](../03-engine/ECS.md) |
| Chốt ADR-019 (GC) | [JVM.md §2](../09-performance/JVM.md) |
| Prometheus + Grafana + Alerts + Tracing | [OBSERVABILITY.md §10](../07-infrastructure/OBSERVABILITY.md) |
| Chốt ADR-015, ADR-014 | — |

**Exit Criteria:**
- [ ] Tick p99 ≤ ngân sách dưới tải mục tiêu
- [ ] **Không leak sau soak 2h** (heap sau major GC không tăng đều)
- [ ] Observability đầy đủ (Điều 44)

⚠️ **Phase 9 không đóng nếu chưa có observability** — Điều 44: không có Observability thì Production không hoàn chỉnh.

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

Xem [ADR Index](../01-architecture/adr/README.md) — **16 ADR Accepted** (gồm 010/012/018 chốt 2026-07-18, ADR-026 chốt 2026-07-24); **10 đang chờ** (007, 011 Proposed; 014–015, 019–024 chưa viết).

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
5. ✅ **4 hệ phái còn lại xong** (2026-07-25) — chỉ còn PvP arena/battlefield để đóng Phase 8 hoàn
   toàn (5/5 hệ phái đã chơi được; PvP là hạng mục riêng, chưa chọn làm).
6. Trả lời các câu hỏi mở còn lại ở [docs/README.md](../README.md).
