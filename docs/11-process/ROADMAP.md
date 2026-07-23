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
    P6 --> P7["7· Guild · Party<br/>Mail · Trade"]
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
| Dialogue NPC (lát cắt 2 — chưa làm) | — |
| `feature-quest` + script (lát cắt 3 — chưa làm) | [GAMEPLAY.md §9](../05-server/GAMEPLAY.md) |
| Validate mọi script lúc khởi động | [SCRIPTING.md §8](../06-plugin/SCRIPTING.md) |
| Chat + Friend + Mail (lát cắt 4 — chưa làm) | [GAMEPLAY.md §10–11](../05-server/GAMEPLAY.md) |

**Exit Criteria — đây là MVP:**
- [ ] Chơi được: tạo nhân vật → nhận quest → đánh quái → nhặt đồ → cường hoá → hoàn thành quest
- [ ] Toàn bộ Definition of Done (Điều 34)

---

## Phase 7 — Guild · Party · Mail · Trade

| Việc | Ghi chú |
|---|---|
| `feature-guild` | Kho bang, nhiệm vụ bang |
| Party | Cần cho dungeon |
| ⚠️ **Trade** | [GAMEPLAY.md §8](../05-server/GAMEPLAY.md) — **hệ thống nguy hiểm nhất**: transaction + audit + 2-phase confirm + khoá item + idempotency |

⚠️ **Không mở Trade nếu ADR-025 (idempotency) chưa Accepted.** (✅ Đã Accepted 2026-07-19 — điều kiện thoả, nhưng vẫn phải triển khai `idempotencyKey` thật trước khi Trade thật sự mở, không phải chỉ cần ADR tồn tại.)

---

## Phase 8 — Dungeon · Boss · PvP

| Việc | Nguồn |
|---|---|
| Dungeon instance | ⏳ **ADR-026** — cùng process hay riêng? |
| Boss AI (Behavior Tree / Script) | [AI.md §4](../03-engine/AI.md) |
| PvP arena + battlefield | [GDD](../13-game-design/GAME-DESIGN-DOCUMENT.md) |
| 4 hệ phái còn lại | — |

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

Xem [ADR Index](../01-architecture/adr/README.md) — **12 ADR Accepted** (gồm 010/012/018 chốt 2026-07-18); **14 đang chờ** (011, 013–017, 019–026).

## Tài liệu liên quan

- [KAGE-CONSTITUTION.md](../00-governance/KAGE-CONSTITUTION.md) — Điều 33–34
- [ADR Index](../01-architecture/adr/README.md) — ADR chặn từng Phase
- [GIT-WORKFLOW.md](GIT-WORKFLOW.md) — quy trình release
- [GAME-DESIGN-DOCUMENT.md](../13-game-design/GAME-DESIGN-DOCUMENT.md) — nội dung từng Phase

## Việc cần làm tiếp theo

1. ✅ 3 ADR chặn Phase 0 đã chốt (010/012/018). **Có thể mở Phase 0.**
2. Đầu Phase 0: chốt/triển khai **ADR-011** (ArchUnit) như hạng mục kỹ thuật.
3. ✅ **GDD đã bổ sung công thức damage** (2026-07-19) — Phase 4 hết bị chặn về mặt game design.
4. Trả lời các câu hỏi mở còn lại ở [docs/README.md](../README.md).
