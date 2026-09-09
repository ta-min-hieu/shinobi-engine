package dev.kage.server.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.TriggerZoneMembershipComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.engine.ecs.system.Aabb;
import dev.kage.engine.ecs.system.MovementSystem;
import dev.kage.engine.ecs.system.NamedZone;
import dev.kage.engine.ecs.system.Platform;
import dev.kage.engine.ecs.system.TerrainCollisionSystem;
import dev.kage.engine.ecs.system.TriggerZoneSystem;
import dev.kage.engine.ecs.system.VerticalMovementSystem;
import dev.kage.server.world.component.AiParamsComponent;
import dev.kage.server.world.component.AiStateComponent;
import dev.kage.server.world.component.AttackCooldownComponent;
import dev.kage.server.world.component.BossAiComponent;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.CharacterLevelComponent;
import dev.kage.server.world.component.ChiComponent;
import dev.kage.server.world.component.CombatRecencyComponent;
import dev.kage.server.world.component.CombatStatsComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MonsterComponent;
import dev.kage.server.world.component.MonsterIdComponent;
import dev.kage.server.world.component.NpcComponent;
import dev.kage.server.world.component.SkillCooldownComponent;
import dev.kage.server.world.component.StatusEffectComponent;
import dev.kage.server.world.component.TargetComponent;

/**
 * Composition root của server-world (SERVER.md §1-3) — sở hữu {@link EcsWorld} + vòng lặp tick
 * single-thread riêng (Platform Thread, KHÔNG Virtual Thread — /CLAUDE.md §6). Đây là API DUY
 * NHẤT server-gateway được gọi vào server-world (module này không biết Netty/DB).
 *
 * <p>{@link #attachCharacter}, {@link #detachCharacter}, {@link #enqueueMoveCommand},
 * {@link #setTarget} được gọi từ Netty EventLoop hoặc Virtual Thread của gateway — CHỈ enqueue
 * (rẻ, thread-safe), không bao giờ chạm {@link EcsWorld} trực tiếp (SERVER.md §2: "Virtual
 * Thread cấm sửa ECS state trực tiếp"). Việc thật xảy ra ở đầu tick kế tiếp trên {@link #tickLoop},
 * đúng pseudocode SERVER.md §3.
 */
// PMD CyclomaticComplexity (class-level, ngưỡng mặc định 80): PMD 7.10.0 chưa từng chạy được thật
// trên JDK 25 (StackOverflowError type-inference — xem STATIC-ANALYSIS.md), nên đây là lần đầu
// class này bị đo. Composition root nhiều method nhỏ (wiring + tick loop) — KHÔNG phải 1 method
// phình to; tách nhỏ hơn nữa là thay đổi kiến trúc composition-root, để chủ dự án quyết định.
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class WorldFacade {

    private static final Logger LOG = LoggerFactory.getLogger(WorldFacade.class);

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<VelocityComponent> VELOCITY = ComponentMapper.getFor(VelocityComponent.class);
    private static final ComponentMapper<TargetComponent> TARGET = ComponentMapper.getFor(TargetComponent.class);
    private static final ComponentMapper<MonsterComponent> MONSTER = ComponentMapper.getFor(MonsterComponent.class);
    private static final ComponentMapper<CombatStatsComponent> COMBAT_STATS =
            ComponentMapper.getFor(CombatStatsComponent.class);
    private static final ComponentMapper<StatusEffectComponent> STATUS_EFFECT =
            ComponentMapper.getFor(StatusEffectComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<ChiComponent> CHI = ComponentMapper.getFor(ChiComponent.class);
    private static final ComponentMapper<CharacterIdComponent> CHARACTER_ID =
            ComponentMapper.getFor(CharacterIdComponent.class);

    // Vị trí quái KHÔNG còn nằm ở đây: từ ADR-037 chúng đọc từ `monster_spawn` và tới đây qua
    // MonsterCatalogSnapshot. Hai ràng buộc CŨ vẫn nguyên hiệu lực và nay do DB gác (CHECK +
    // comment ở migration V23), không phải do hằng số Java: quái phải nằm trên đường chơi
    // (y = CHARACTER_PLAY_LINE_Y — ADR-033 khoá di chuyển vào trục X, quái lệch y là quái người
    // chơi không bao giờ chạm tới), và phải cách điểm hồi sinh (0,0) xa hơn tầm aggro của loài
    // đặt ở đó (monster_template.aggro_radius_meters, ADR-039) để nhân vật vừa hồi sinh không bị
    // đánh ngay — ràng buộc này NHẸ đi kể từ ADR-039 D4: `training_dummy` nay có aggro = 0.

    // Điểm hồi sinh mặc định (chưa có khái niệm "điểm hồi sinh theo khu vực"/thị trấn — MVP đầu
    // tiên, xem ROADMAP.md Phase 10). Quái gần nhất ở x=8, tức 8m — ngoài tầm aggro mặc định (6m),
    // và từ ADR-039 D4 con quái ở đó còn là loài THỤ ĐỘNG (aggro 0) nên không đuổi theo gì cả.
    private static final float CHARACTER_RESPAWN_X = 0f;
    private static final float CHARACTER_RESPAWN_Y = 0f;

    /**
     * Toạ độ Y DUY NHẤT mà nhân vật người chơi tồn tại (ADR-033 — di chuyển chỉ còn trục X). Mọi
     * địa hình/bệ định nghĩa quanh giá trị này; xem {@link #handleAttach} về lý do ép cứng.
     */
    private static final float CHARACTER_PLAY_LINE_Y = 0f;

    // Mã + vị trí NPC KHÔNG còn nằm ở đây: từ ADR-040 chúng đọc từ `npc_spawn` và tới đây qua
    // NpcCatalogSnapshot. Ràng buộc CŨ vẫn nguyên hiệu lực và nay do DB gác (CHECK ở migration
    // V26), không phải do hằng số Java: NPC phải nằm trên đường chơi (y = CHARACTER_PLAY_LINE_Y —
    // ADR-033 khoá di chuyển vào trục X; NPC là entity TĨNH nên đặt lệch y nghĩa là người chơi
    // vĩnh viễn không bao giờ tới đủ gần để tương tác).

    // Bệ demo (ADR-033 "Việc cần làm tiếp theo" #7 — hardcode thủ công để chủ dự án TỰ TEST chạy
    // nhảy leo bậc, KHÔNG phải map pilot thật/art chốt; việc đó vẫn còn mở).
    //
    // THANG BẬC NỔI, đi lên được bằng nhảy nối tiếp — số liệu tính từ WorldConstants: đỉnh nhảy lý
    // thuyết = JUMP_VELOCITY_MPS²/(2·GRAVITY_MPS2) = 7²/40 = 1.225m mỗi lần nhảy, tầm bay ngang
    // ≈ MOVE_SPEED_MPS × 2·JUMP_VELOCITY_MPS/GRAVITY_MPS2 = 5 × 0.7 = 3.5m. Nên: bậc 1 (0.8m) với
    // tới từ mặt đất; bậc 2 (1.5m) với tới từ bậc 1 (0.8+1.225=2.02); bậc 3 (2.2m) từ bậc 2
    // (1.5+1.225=2.72). Khe hở 2m giữa các bậc < 3.5m tầm bay ⇒ nhảy qua được.
    //
    // ⚠️ Bệ NỔI trên không, cố ý: VerticalMovementSystem chỉ chặn khi entity RƠI TỪ TRÊN XUỐNG
    // (one-way platform, giống Ninja School), còn TerrainCollisionSystem thì chưa biết trục Z nên
    // không chặn được cạnh bên theo độ cao. Bệ đặt sát đất sẽ khiến nhân vật đi XUYÊN qua khối đặc
    // trông như lỗi; bệ nổi thì "đi bên dưới" là hành vi đúng và dễ hiểu.
    // Bề sâu theo Y chỉ 1.5m (±0.75 quanh đường chơi y=0): đủ để contains(x, 0) luôn đúng, nhưng
    // không dày tới mức khối chiếu ra màn hình thành mảng cao che mất nhân vật (camera ngẩng 30°
    // biến bề sâu Y thành chiều cao trên màn — đã thấy thật qua ảnh chụp lúc để ±2m).
    private static final List<Platform> DEMO_PLATFORMS = List.of(
            new Platform(new Aabb(3f, -0.75f, 7f, 0.75f), 0.8f),
            new Platform(new Aabb(9f, -0.75f, 13f, 0.75f), 1.5f),
            new Platform(new Aabb(15f, -0.75f, 19f, 0.75f), 2.2f));

    /**
     * Chỉ đọc — ba bệ nhảy demo của world chính (ADR-033 #7).
     *
     * <p><b>Vì sao lộ ra ngoài (2026-09-09).</b> Chúng là KHỐI 3D thật có depth test, nên một điểm
     * spawn rơi vào khoảng {@code x} của một bệ sẽ bị bệ VẼ ĐÈ LÊN trong client. Chuyện đó vừa xảy
     * ra thật: V37 bản đầu đặt NPC {@code senior_sister} ở {@code x=4}, tức giữa bệ thứ nhất
     * {@code [3,7]}, và trong client cô bị vùi tới cổ — trong khi mọi test đều xanh và
     * {@code runWorldPreview} cũng không thấy (công cụ đó đặt entity theo toạ độ của RIÊNG nó,
     * không đọc {@code npc_spawn}).
     *
     * <p>Hằng số này là thứ DUY NHẤT nối "bệ ở đâu" với dữ liệu spawn trong DB, nên
     * {@code SpawnGeometryIntegrationTest} (server-gateway — module duy nhất thấy được cả hai) đọc
     * nó để gác mọi dòng {@code npc_spawn}/{@code monster_spawn}. Cùng họ với bài học dải nền đất
     * {@code [-55, 60]}: một hằng số hình học không nối gì với DB thì sớm muộn cũng lệch.
     *
     * <p>⚠️ Trả về danh sách BẤT BIẾN của chính cấu hình đang chạy — không phải bản sao chép tay.
     * Khi có map pilot thật thì cả hằng số này lẫn hàng rào kia đều biến mất cùng nhau.
     */
    public static List<Platform> demoPlatforms() {
        return DEMO_PLATFORMS;
    }

    // Boss mẫu (Phase 8 lát cắt 2) — spawn trong tầm aggro mặc định (AiConstants.DEFAULTS, 6m) từ
    // điểm vào dungeon (DungeonInstanceRegistry.DUNGEON_ENTRY_SPAWN, 0,0) để party aggro ngay khi
    // tới gần, không cần cơ chế "instant aggro lúc mở dungeon" riêng.
    private static final float BOSS_SPAWN_X = 3f;
    private static final float BOSS_SPAWN_Y = 0f;

    private final WorldOutboundSink outboundSink;
    private final TickMetricsPort tickMetricsPort;
    // Method reference tạo 1 LẦN — tránh allocation trên tick thread (Điều 31, cùng lý do
    // combatSystem/skillSystem không new lại mỗi tick).
    private final EcsWorld.SystemTimingListener systemTimingListener;
    private final Queue<WorldCommand> inbox = new ConcurrentLinkedQueue<>();
    private final EcsWorld ecsWorld = new EcsWorld();
    private final AreaOfInterestTracker aoiTracker;
    private final CombatSystem combatSystem;
    private final SkillSystem skillSystem;
    private final BossAiSystem bossAiSystem;
    private final HealthRegenSystem healthRegenSystem;

    /**
     * Giữ tham chiếu (khác trước: dựng thẳng trong {@code addSystem}) vì ADR-046 D4 cần drain
     * "quái nào vừa về nhà và lành lại" sau mỗi tick, y hệt {@link #combatSystem}.
     */
    private final AiSystem aiSystem;

    /**
     * Máu vừa đổi bởi thứ KHÔNG phải một System (ADR-046): vùng an toàn (D3 — {@code
     * TriggerZoneSystem} gọi callback NGAY trong tick) và đan dược (D5 — lệnh từ hàng đợi). Tích ở
     * đây rồi phát sau {@code ecsWorld.update()}, như mọi kết quả tick khác.
     */
    private final List<HealthChange> pendingHealthChanges = new ArrayList<>();

    // Chỉ tick thread được đọc/ghi các map/field này (không đồng bộ hoá) — đúng ranh giới "ECS
    // state chỉ sửa trên tick thread" (SERVER.md §2).
    private final Map<Long, KEntity> entitiesByCharacterId = new HashMap<>();
    private final Map<Long, KEntity> entitiesByMonsterId = new HashMap<>();
    private final Map<Long, KEntity> entitiesByNpcId = new HashMap<>();
    // Chỉ số NỀN (STR/AGI/INT/VIT) gốc — cần giữ lại để applyEquipmentBonus tính lại ATK từ đầu
    // (base + bonus trang bị hiện tại) mỗi lần, KHÔNG cộng dồn bonus cũ (equip/gỡ đồ nhiều lần
    // không được làm ATK trôi dần sai).
    private final Map<Long, CharacterBaseStats> baseStatsByCharacterId = new HashMap<>();
    // ID quái/NPC luôn ÂM — không đụng characterId (luôn DƯƠNG, DB auto-increment). DÙNG CHUNG 1
    // bộ đếm cho CẢ quái lẫn NPC (KHÔNG phải 2 bộ đếm riêng) — PHÁT HIỆN THẬT (Phase 6 lát cắt 1):
    // 2 bộ đếm riêng cùng bắt đầu ở -1 khiến quái và NPC có thể trùng ID số; `AreaOfInterestTracker`
    // dùng CHUNG 1 `Set<Long>` (`visibleTo`) để lưu "mọi ID nhân vật này đang thấy" bất kể loại
    // entity, nên 2 entity khác loại nhưng trùng ID sẽ GHI ĐÈ lẫn nhau trong set đó — quái vừa
    // được thêm vào "đang thấy" thì NPC (ID trùng số, ngoài tầm nhìn) lại xoá luôn ngay trong CÙNG
    // tick, khiến quái không bao giờ được coi là "đã thấy từ trước" nên không bao giờ phát
    // MonsterMovedEvent. Bắt bằng test thật (`shouldChaseAndAttackBack_whenCharacterEntersAggroRange`
    // đột nhiên treo vô hạn chờ MonsterMovedEvent sau khi thêm NPC), không phải đoán trước.
    private long nextMonsterOrNpcId = -1;
    // Đếm ngược hồi sinh THEO TỪNG điểm spawn (< 0 = quái ở đó đang sống). Mảng cố định, không cấp
    // phát lại — cùng tinh thần "không allocation trong tick" (Điều 31).
    private final float[] monsterRespawnCountdownSeconds;
    // monsterId -> chỉ số điểm spawn, để hồi sinh đúng chỗ cũ. Boss KHÔNG có entry ở đây.
    private final Map<Long, Integer> spawnIndexByMonsterId = new HashMap<>();

    private static float[] newAliveCountdowns(int spawnPointCount) {
        float[] countdowns = new float[spawnPointCount];
        java.util.Arrays.fill(countdowns, -1f);
        return countdowns;
    }

    // AoI sync chạy chậm hơn tick loop (WorldConstants.AOI_SYNC_INTERVAL_TICKS) — chỉ đếm tick,
    // KHÔNG dùng đồng hồ thật (đơn giản, không trôi lệch tích luỹ như Timer/Duration).
    private long tickCounter;

    private volatile boolean running;
    // Thay "1 OS thread riêng/World" (Phase 9, xem WorldTickScheduler javadoc) — pool CỐ ĐỊNH dùng
    // chung, số thread không tăng theo số kênh/dungeon/duel đang chạy.
    private ScheduledFuture<?> tickFuture;
    // scheduleAtFixedRate tự đảm bảo KHÔNG chồng lấn 2 lần chạy CÙNG task, nên lock này gần như
    // không bao giờ bị tranh chấp lúc chạy bình thường — chỉ dùng để stop() CHỜ ĐÚNG lần tick đang
    // chạy dở (nếu có) xong hẳn trước khi trả về, mirror lại đúng ngữ nghĩa "interrupt + join" cũ.
    private final Lock tickLock = new ReentrantLock();
    private boolean contentSpawned;
    private long fixedDtNanos;
    private final boolean spawnOverworldContent;
    /**
     * Loài quái + điểm spawn world này được phép dựng (ADR-037). {@link MonsterCatalogSnapshot#EMPTY}
     * ⇒ world không có con quái nào — đúng hành vi dungeon/arena, và là mặc định của mọi overload
     * constructor cũ để chỗ gọi phải NÓI RÕ khi muốn có quái.
     */
    private final MonsterCatalogSnapshot monsterCatalog;

    /**
     * NPC tĩnh world này đặt (ADR-040). {@link NpcCatalogSnapshot#EMPTY} ⇒ world không có NPC nào —
     * đúng hành vi dungeon/arena, và là mặc định của mọi overload constructor cũ.
     */
    private final NpcCatalogSnapshot npcCatalog;

    /**
     * Toàn bộ resource script skill (hardcode, chưa DB-driven — xem {@link SkillConstants}) —
     * dùng cho validate script lúc khởi động (SCRIPTING.md §8). {@code static} vì đây là config
     * cố định, không phụ thuộc instance {@link WorldFacade} nào.
     */
    public static List<String> listSkillScriptResources() {
        return SkillRegistry.allScriptResources();
    }

    /**
     * Toàn bộ resource script boss (hardcode, chưa DB-driven — xem {@link BossConstants}) — dùng
     * cho validate script lúc khởi động (SCRIPTING.md §8), cùng lý do {@link #listSkillScriptResources}.
     */
    public static List<String> listBossScriptResources() {
        return List.of(BossConstants.BOSS_SCRIPT_RESOURCE);
    }

    public WorldFacade(WorldOutboundSink outboundSink) {
        this(outboundSink, true);
    }

    /**
     * {@code spawnOverworldContent = false} — World khởi động RỖNG (không quái bù nhìn, không NPC
     * tĩnh), dùng cho dungeon instance (ADR-026: mỗi instance là 1 World riêng cùng process). Hệ
     * thống combat/skill/AI/AoI vẫn đầy đủ — lát cắt Boss AI sau chỉ cần gọi 1 hàm spawn mới, không
     * cần sửa lại World.
     */
    public WorldFacade(WorldOutboundSink outboundSink, boolean spawnOverworldContent) {
        this(outboundSink, spawnOverworldContent, TickMetricsPort.NO_OP);
    }

    /**
     * {@code tickMetricsPort} đo {@code kage.tick.duration} (ADR-015, Phase 9 lát cắt 1) — chỉ
     * World chính truyền implementation thật (Micrometer, xem {@code GatewayBootstrap}); dungeon/
     * arena instance vẫn dùng {@link TickMetricsPort#NO_OP} qua overload trên (scope cut lát cắt
     * này — capacity planning ưu tiên World chính trước, xem ROADMAP.md Phase 9).
     */
    public WorldFacade(WorldOutboundSink outboundSink, boolean spawnOverworldContent, TickMetricsPort tickMetricsPort) {
        this(outboundSink, spawnOverworldContent, tickMetricsPort, WorldContentCatalog.EMPTY);
    }

    /**
     * {@code content} (ADR-037 cho quái, ADR-040 cho NPC) — nội dung tĩnh của world, ĐỌC TỪ DB bởi
     * {@code server-gateway} rồi dịch sang record thuần. {@code server-world} không được biết DB
     * (Điều 23) và {@code server-database} thậm chí không phụ thuộc module này, nên tiêm vào là
     * đường DUY NHẤT — đúng khuôn ADR-036 đã dựng cho {@link EquipmentBonusSnapshot}.
     *
     * <p>Quái và NPC đi CHUNG một tham số vì constructor này đã dùng hết 4 tham số, đúng trần
     * Checkstyle {@code ParameterNumber} (ADR-040 D6) — xem {@link WorldContentCatalog}.
     *
     * <p>Catalog rỗng ⇒ không spawn quái/NPC nào, kể cả khi {@code spawnOverworldContent = true}:
     * hai cờ này trả lời hai câu khác nhau (có bệ/hồi sinh nhân vật hay không, và có nội dung gì).
     */
    public WorldFacade(
            WorldOutboundSink outboundSink,
            boolean spawnOverworldContent,
            TickMetricsPort tickMetricsPort,
            WorldContentCatalog content) {
        this.outboundSink = outboundSink;
        this.monsterCatalog = content.monsters();
        this.npcCatalog = content.npcs();
        this.monsterRespawnCountdownSeconds =
                newAliveCountdowns(monsterCatalog.spawnPoints().size());
        this.spawnOverworldContent = spawnOverworldContent;
        this.tickMetricsPort = tickMetricsPort;
        this.systemTimingListener = tickMetricsPort::recordSystemDuration;
        this.aoiTracker = new AreaOfInterestTracker(outboundSink);
        this.combatSystem = new CombatSystem(this::lookupEntity);
        this.healthRegenSystem = new HealthRegenSystem();
        this.aiSystem = new AiSystem(entitiesByCharacterId::values, this::lookupEntity);
        this.skillSystem = new SkillSystem(this::lookupEntity);
        this.bossAiSystem = new BossAiSystem(this::lookupEntity);
        // Thứ tự ĐÚNG ECS.md §6 (InputCommandSystem -> AISystem -> MovementSystem -> ... ->
        // CombatSystem): AI set vận tốc/mục tiêu trước, Movement tích luỹ vị trí, Combat mới xét
        // tầm đánh — đổi thứ tự = đổi behavior (engine/CLAUDE.md). SkillSystem/BossAiSystem chỉ
        // đếm ngược cooldown mỗi tick (hành động thật là lệnh rời rạc/điều kiện nội bộ, không phải
        // hành vi lặp mỗi tick như đánh thường) — thứ tự với CombatSystem không quan trọng.
        ecsWorld.addSystem(aiSystem);
        ecsWorld.addSystem(new MovementSystem());
        // Trục dọc (ADR-033) — chạy ngay sau MovementSystem (ngang), TRƯỚC TerrainCollisionSystem,
        // cùng nhóm "áp chuyển động" của tick trước khi xử va chạm. World chính (spawnOverworldContent)
        // nạp DEMO_PLATFORMS để chủ dự án tự test nhảy được — dungeon/arena vẫn RỖNG (chưa có map
        // pilot thật, ADR-033 #7), no-op an toàn ở đó (mọi entity rơi về z=0 phẳng như trước).
        VerticalMovementSystem verticalMovementSystem = new VerticalMovementSystem(WorldConstants.GRAVITY_MPS2);
        if (spawnOverworldContent) {
            verticalMovementSystem.setPlatforms(DEMO_PLATFORMS);
        }
        ecsWorld.addSystem(verticalMovementSystem);
        // Obstacle mặc định RỖNG — chưa có map/asset thật (ADR-021). No-op an toàn cho tới khi có
        // nguồn map thật gọi setObstacles(...); giữ đúng thứ tự PHYSICS.md §4 (sau MovementSystem).
        ecsWorld.addSystem(new TerrainCollisionSystem());
        ecsWorld.addSystem(newSafeZoneSystem(content.safeZones()));
        ecsWorld.addSystem(combatSystem);
        ecsWorld.addSystem(skillSystem);
        ecsWorld.addSystem(bossAiSystem);
        // Đếm ngược buff/debuff (Phase 8, 4 hệ phái) — không phụ thuộc thứ tự với Combat/Skill vì
        // chỉ mutate remainingSeconds/magnitude tại chỗ, các System khác ĐỌC StatusEffectComponent
        // lúc resolve riêng (StatusEffects helper), không qua processEntity của System này.
        ecsWorld.addSystem(new StatusEffectSystem());
        // Hồi máu ngoài chiến đấu (ADR-046 D2) — chạy SAU Combat/Skill/Boss trong cùng tick: các
        // System đó vừa đặt secondsSinceCombat = 0 cho ai vừa dính damage, nên nhánh "chưa tới giờ
        // hồi" thấy đúng số của tick này chứ không phải của tick trước.
        ecsWorld.addSystem(healthRegenSystem);
    }

    /** Bắt đầu lịch tick trên {@link WorldTickScheduler} — gọi 1 lần lúc khởi động server. */
    public void start() {
        running = true;
        fixedDtNanos = (long) (WorldConstants.FIXED_DT * 1_000_000_000L);
        tickFuture = WorldTickScheduler.scheduleAtFixedRate(this::tickTask, fixedDtNanos);
    }

    /** Dừng tick (SERVER.md §10 — graceful shutdown, không {@code kill -9}). */
    public void stop() {
        running = false;
        if (tickFuture != null) {
            tickFuture.cancel(false);
            // Khoá rồi nhả ngay: nếu 1 lần tick đang chạy dở (giữ tickLock trong tickTask), dòng
            // này CHẶN tới khi lần đó xong hẳn — đảm bảo stop() chỉ trả về sau khi World đã dừng
            // tick THẬT SỰ, đúng ngữ nghĩa đồng bộ mà ArenaInstanceRegistry/DungeonInstanceRegistry
            // đang phụ thuộc (gọi stop() rồi coi World là đã ngừng hẳn ngay sau đó).
            tickLock.lock();
            tickLock.unlock();
        }
    }

    public void attachCharacter(long characterId, float spawnX, float spawnY, CharacterBaseStats baseStats) {
        inbox.add(new WorldCommand.AttachCharacterCommand(characterId, spawnX, spawnY, baseStats));
    }

    public void detachCharacter(long characterId) {
        inbox.add(new WorldCommand.DetachCharacterCommand(characterId));
    }

    public void enqueueMoveCommand(long characterId, int sequenceId, float dirX, float dirY) {
        inbox.add(new WorldCommand.MoveIntentCommand(characterId, sequenceId, dirX, dirY));
    }

    /** ADR-033 — ý định nhảy, world tự kiểm tra grounded lúc xử lý (xem {@link #handleJumpIntent}). */
    public void enqueueJumpCommand(long characterId, int sequenceId) {
        inbox.add(new WorldCommand.JumpIntentCommand(characterId, sequenceId));
    }

    /** Chỉ NHẮM mục tiêu — không tự đánh nữa từ 2026-08-09, xem {@link #attack} ({@code CombatSystem}). */
    public void setTarget(long characterId, long targetEntityId) {
        inbox.add(new WorldCommand.SetTargetCommand(characterId, targetEntityId));
    }

    /** Ra MỘT đòn đánh thường lên mục tiêu đang nhắm — server tự kiểm cooldown/tầm/stun. */
    public void attack(long characterId) {
        inbox.add(new WorldCommand.AttackIntentCommand(characterId));
    }

    /** GAMEPLAY.md §3-4 — dùng skill nhắm mục tiêu, server tự kiểm tra mọi điều kiện ({@code SkillSystem}). */
    public void useSkill(long characterId, String skillCode, long targetEntityId) {
        inbox.add(new WorldCommand.UseSkillCommand(characterId, skillCode, targetEntityId));
    }

    /**
     * GAMEPLAY.md §2/§7 "tính lại chỉ số dẫn xuất khi đổi trang bị" — gọi lại MỖI lần trang bị đổi
     * (mặc/gỡ đồ) VÀ ngay sau khi attach (lúc chọn nhân vật, cùng lúc chỉ số nền được gửi vào —
     * {@code GatewayChannelHandler} chịu trách nhiệm gọi đúng lúc, server-world không tự biết
     * feature-inventory). Cả 4 chỉ số là TỔNG cộng dồn hiện tại từ MỌI item đang mặc (không phải
     * delta, và ĐÃ nhân hệ số cường hoá của từng món — ADR-036 D2/D3) — feature-inventory tính lại
     * từ đầu mỗi lần, tránh trôi số nếu gọi sai thứ tự.
     */
    public void applyEquipmentBonus(long characterId, EquipmentBonusSnapshot bonus) {
        inbox.add(new WorldCommand.ApplyEquipmentBonusCommand(characterId, bonus));
    }

    /**
     * Máu TỐI ĐA của một nhân vật với chỉ số trang bị đã cho — nguồn chân lý DUY NHẤT của con số
     * này (ADR-036: {@code CHARACTER_MAX_HP} + {@code bonus_hp}, đã tính cường hoá).
     *
     * <p>Công khai vì {@code server-gateway} phải gửi con số đó cho client NGAY trong
     * {@code SelectCharacterResponse}: entity ECS mới chỉ được xếp hàng lúc đó, chưa tồn tại để hỏi.
     * Để gateway tự cộng {@code 100 + bonusHp} là tạo ra bản sao thứ hai của công thức, và bản sao
     * đó sẽ lệch trong im lặng ngay lần đầu GDD chốt công thức HP thật.
     */
    public static int characterMaxHp(EquipmentBonusSnapshot bonus) {
        return characterMaxHp(bonus.hp());
    }

    private static int characterMaxHp(int bonusHp) {
        return Math.max(1, CombatConstants.CHARACTER_MAX_HP + bonusHp);
    }

    /** GAMEPLAY.md §10 — chat kênh Khu vực, cần {@code aoiTracker} nên phải qua tick thread. */
    public void enqueueAreaChatMessage(long characterId, String senderName, String body) {
        inbox.add(new WorldCommand.AreaChatMessageCommand(characterId, senderName, body));
    }

    /**
     * ADR-045 — ngoại hình vừa đổi; cần {@code aoiTracker} nên phải qua tick thread, y hệt chat
     * kênh Khu vực. Gọi từ {@code server-gateway} ngay sau khi đổi hệ phái thành công.
     */
    public void enqueueAppearanceChanged(long characterId) {
        inbox.add(new WorldCommand.AppearanceChangedCommand(characterId));
    }

    /**
     * ADR-046 D5 — đan dược: hồi {@code amount} máu cho nhân vật. Gọi từ {@code server-gateway}
     * SAU khi {@code feature-inventory} đã trừ số lượng trong transaction của nó.
     *
     * <p>Không trả về máu mới: {@code inbox} là bất đồng bộ nên lúc hàm này return, lệnh còn chưa
     * chạy. Client nhận số mới qua {@code HealthChangedEvent} (opcode 408) — xem ADR-046 D5.
     */
    public void enqueueHealCharacter(long characterId, int amount) {
        inbox.add(new WorldCommand.HealCharacterCommand(characterId, amount));
    }

    /**
     * Spawn 1 boss hardcode (Phase 8 lát cắt 2, {@link BossConstants#BOSS_CODE}) — gọi bởi
     * {@code DungeonInstanceRegistry} ngay sau khi tạo dungeon World mới. World chính KHÔNG tự
     * gọi hàm này (giữ đúng ranh giới lát cắt 1: server-world không biết khái niệm "dungeon", chỉ
     * biết "có ai bảo spawn boss hay không").
     */
    public void spawnBoss() {
        inbox.add(new WorldCommand.SpawnBossCommand());
    }

    /**
     * Chạy trên {@link WorldTickScheduler} — có thể bị pool thread KHÁC NHAU chạy mỗi lần gọi
     * (khác thread cố định của mô hình cũ), nhưng {@code scheduleAtFixedRate} tự đảm bảo không bao
     * giờ có 2 lần gọi CÙNG task chồng lấn, nên vẫn giữ đúng bất biến "chỉ 1 thread chạm ECS state
     * tại 1 thời điểm" (SERVER.md §3).
     *
     * <p>⚠️ Bọc {@code Throwable} (không chỉ {@code RuntimeException} như {@link #tickOnce()} đã tự
     * bắt bên trong) — {@code scheduleAtFixedRate} âm thầm NGỪNG lặp lại vĩnh viễn, không log gì,
     * nếu Runnable để lọt exception ra ngoài (xem javadoc {@link WorldTickScheduler}). Đây là lưới
     * an toàn tầng ngoài cùng, không thay thế cách ly lỗi đã có trong {@code tickOnce()}.
     */
    // PMD rule thật trong config/pmd/ruleset.xml là AvoidCatchingGenericException (không phải
    // AvoidCatchingThrowable — tên cũ ghi nhầm từ trước khi PMD chạy được thật, xem STATIC-ANALYSIS.md).
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void tickTask() {
        if (!running) {
            return; // cancel(false) không huỷ lần đã dispatch dở — bỏ qua an toàn nếu lỡ chạy tới đây.
        }
        tickLock.lock();
        try {
            if (!contentSpawned) {
                contentSpawned = true;
                if (spawnOverworldContent) {
                    for (int i = 0; i < monsterCatalog.spawnPoints().size(); i++) {
                        spawnMonster(i);
                    }
                    // NPC tĩnh, không chết/hồi sinh — spawn 1 lần duy nhất, khác quái (không có
                    // spawnIndex nào phải nhớ lại, xem NpcCatalogSnapshot).
                    for (int i = 0; i < npcCatalog.placements().size(); i++) {
                        spawnNpc(npcCatalog.placements().get(i));
                    }
                }
            }

            long start = System.nanoTime();
            tickOnce();
            long elapsed = System.nanoTime() - start;
            tickMetricsPort.recordTickDuration(elapsed);
            if (elapsed > fixedDtNanos) {
                LOG.warn("World tick quá hạn: {}ms", elapsed / 1_000_000);
                tickMetricsPort.recordTickOverrun();
            }
        } catch (Throwable t) {
            LOG.error("Lỗi nghiêm trọng ngoài tickOnce() trong World tick — world này dừng tick", t);
        } finally {
            tickLock.unlock();
        }
    }

    /**
     * Cách ly lỗi (ADR-026): 1 exception trong tick KHÔNG được âm thầm giết {@link #tickThread} —
     * trước đây không bắt, tick lỗi 1 lần là World đóng băng vĩnh viễn không ai biết. Log rồi tick
     * tiếp, cùng tinh thần {@code CharacterPositionPersister.savePositionSafely} (1 lỗi không dừng
     * cả vòng lặp).
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void tickOnce() {
        try {
            drainInbox();
            ecsWorld.update(WorldConstants.FIXED_DT, systemTimingListener);
            tickMetricsPort.recordEntityCount(ecsWorld.entityCount());
            handleCombatResults(combatSystem.drainResults());
            handleSkillResults(skillSystem.drainResults());
            handleBossResults(bossAiSystem.drainResults());
            handleHealthChanges(healthRegenSystem.drainResults()); // ADR-046 D2
            handleHealthChanges(aiSystem.drainHealthChanges()); // ADR-046 D4
            handleHealthChanges(drainHealthChangeQueue()); // ADR-046 D3 + D5
            tickMonsterRespawn(WorldConstants.FIXED_DT);
            // O(n²) — chạy chậm hơn tick loop (WorldConstants.AOI_SYNC_INTERVAL_TICKS), xem
            // javadoc AreaOfInterestTracker + WorldConstants.AOI_SYNC_RATE_HZ. Combat/skill/boss
            // result ở trên KHÔNG bị ảnh hưởng — vẫn phát ngay mỗi tick qua observersOf().
            tickCounter++;
            if (tickCounter % WorldConstants.AOI_SYNC_INTERVAL_TICKS == 0) {
                aoiTracker.sync(entitiesByCharacterId, entitiesByMonsterId, entitiesByNpcId);
            }
        } catch (RuntimeException e) {
            LOG.error("Lỗi trong World tick — bỏ qua tick này, KHÔNG dừng tick loop", e);
        }
    }

    private void drainInbox() {
        WorldCommand command;
        while ((command = inbox.poll()) != null) {
            switch (command) {
                case WorldCommand.AttachCharacterCommand c ->
                    handleAttach(c.characterId(), c.spawnX(), c.spawnY(), c.baseStats());
                case WorldCommand.DetachCharacterCommand c -> handleDetach(c.characterId());
                case WorldCommand.MoveIntentCommand c -> handleMoveIntent(c);
                case WorldCommand.JumpIntentCommand c -> handleJumpIntent(c);
                case WorldCommand.SetTargetCommand c -> handleSetTarget(c.characterId(), c.targetEntityId());
                case WorldCommand.AttackIntentCommand c -> handleAttackIntent(c.characterId());
                case WorldCommand.UseSkillCommand c ->
                    handleUseSkill(c.characterId(), c.skillCode(), c.targetEntityId());
                case WorldCommand.ApplyEquipmentBonusCommand c -> handleApplyEquipmentBonus(c);
                case WorldCommand.AreaChatMessageCommand c ->
                    handleAreaChatMessage(c.characterId(), c.senderName(), c.body());
                case WorldCommand.AppearanceChangedCommand c -> handleAppearanceChanged(c.characterId());
                case WorldCommand.HealCharacterCommand c -> handleHealCharacter(c.characterId(), c.amount());
                case WorldCommand.SpawnBossCommand ignored -> spawnBossEntity();
            }
        }
    }

    private void handleAttach(long characterId, float spawnX, float spawnY, CharacterBaseStats baseStats) {
        if (entitiesByCharacterId.containsKey(characterId)) {
            return; // đã attach — bỏ qua an toàn thay vì tạo entity trùng.
        }
        KEntity entity = ecsWorld.createEntity();

        PositionComponent position = new PositionComponent();
        position.x = spawnX;
        // ADR-033 — KÉO VỀ ĐƯỜNG CHƠI: di chuyển giờ khoá vào đúng trục X, không lệnh nào đổi được
        // Y nữa. Nhân vật có `pos_y` đã lưu khác 0 (từ thời đi tự do 4 hướng, DB vẫn giữ nguyên) sẽ
        // kẹt VĨNH VIỄN ngoài đường chơi — không bao giờ chạm được bệ/địa hình nào vốn đều định
        // nghĩa quanh y=0. Bỏ qua `spawnY` là hệ quả bắt buộc của việc thu trục, không phải mất dữ
        // liệu tuỳ tiện: giá trị cũ không còn ý nghĩa gameplay nào trong mô hình mới.
        position.y = CHARACTER_PLAY_LINE_Y;
        position.prevX = position.x;
        position.prevY = position.y;
        entity.add(position);
        entity.add(new VelocityComponent());

        CharacterIdComponent characterIdComponent = new CharacterIdComponent();
        characterIdComponent.characterId = characterId;
        entity.add(characterIdComponent);

        HealthComponent health = new HealthComponent();
        health.current = CombatConstants.CHARACTER_MAX_HP; // placeholder — GDD chưa chốt công thức HP tối đa
        health.max = CombatConstants.CHARACTER_MAX_HP;
        entity.add(health);
        entity.add(toComponent(CombatConstants.deriveCombatStats(baseStats)));
        entity.add(new TargetComponent());
        entity.add(new AttackCooldownComponent());
        entity.add(new SkillCooldownComponent());
        entity.add(new StatusEffectComponent());
        CharacterLevelComponent levelComponent = new CharacterLevelComponent();
        levelComponent.level = baseStats.level();
        entity.add(levelComponent);

        ChiComponent chi = new ChiComponent();
        chi.current = SkillConstants.CHARACTER_MAX_CHI; // placeholder — chưa nối character_stat.current_chi DB
        chi.max = SkillConstants.CHARACTER_MAX_CHI;
        entity.add(chi);

        addRecoveryComponents(entity);

        entitiesByCharacterId.put(characterId, entity);
        baseStatsByCharacterId.put(characterId, baseStats);
        aoiTracker.onAttach(characterId);
    }

    private void handleDetach(long characterId) {
        KEntity entity = entitiesByCharacterId.remove(characterId);
        baseStatsByCharacterId.remove(characterId);
        if (entity == null) {
            return;
        }
        aoiTracker.onDetach(characterId);
        ecsWorld.removeEntity(entity);
    }

    private void handleMoveIntent(WorldCommand.MoveIntentCommand intent) {
        KEntity entity = entitiesByCharacterId.get(intent.characterId());
        if (entity == null) {
            // Race hiếm: MoveCommand tới trước khi AttachCharacterCommand cùng batch được xử lý
            // xong — bỏ qua an toàn, client sẽ gửi lại lệnh tiếp theo khi hướng đổi lần sau.
            return;
        }

        VelocityComponent velocity = VELOCITY.get(entity);
        if (StatusEffects.isStunned(STATUS_EFFECT.get(entity))) {
            // Bị stun (Phase 8, hệ Thủy) — không di chuyển, nhưng VẪN ack lệnh (client cần reconcile
            // sequenceId dù bị từ chối, khác các lệnh "âm thầm bỏ qua" không cần phản hồi).
            velocity.x = 0f;
            velocity.y = 0f;
        } else {
            // ADR-033: người chơi chỉ còn di chuyển ngang (trái/phải) — dirY của intent BỊ BỎ QUA
            // có chủ đích dù field còn tồn tại trên dây (movement.proto không được xoá field đang
            // dùng, shared/CLAUDE.md). Server Authority: không tin client dù client cũ/hack còn gửi
            // dirY khác 0. clampDirection vẫn nhận đủ 2 tham số để tái dùng nguyên logic chuẩn hoá.
            float[] direction = clampDirection(intent.dirX(), 0f);
            float speed = WorldConstants.MOVE_SPEED_MPS * StatusEffects.moveSpeedMultiplier(STATUS_EFFECT.get(entity));
            velocity.x = direction[0] * speed;
            velocity.y = direction[1] * speed;
        }

        PositionComponent position = POSITION.get(entity);
        outboundSink.onMoveAccepted(new WorldOutboundSink.MoveAcceptedSnapshot(
                intent.characterId(), intent.sequenceId(), position.x, position.y, position.z, position.grounded));
    }

    /**
     * ADR-033 — áp lực nhảy. {@code VerticalMovementSystem} tự tích luỹ z/vz mỗi tick sau đó, hàm
     * này chỉ đặt vận tốc ban đầu — cùng phân chia trách nhiệm với {@link #handleMoveIntent} (world
     * đặt {@code VelocityComponent}, {@code MovementSystem} mới thật sự tích luỹ vị trí).
     *
     * <p>Hai loại nhảy, cùng một {@code JumpCommand} (client không nói loại nào — Server Authority,
     * server tự suy từ trạng thái THẬT của entity):
     *
     * <ul>
     *   <li>Đang đứng đất → cú nhảy thường, {@link WorldConstants#JUMP_VELOCITY_MPS}.
     *   <li>Đang lơ lửng và còn hạn mức → nhảy đôi kiểu Ninja School Online (chủ dự án yêu cầu
     *       2026-08-09), {@link WorldConstants#AIR_JUMP_VELOCITY_MPS} CAO HƠN. Hạn mức
     *       {@link WorldConstants#MAX_AIR_JUMPS} tính theo mỗi lần rời đất, tự reset lúc đáp đất
     *       ({@code PositionComponent#airJumpsUsed}) — đây là thứ chặn client sửa đổi bay vô hạn.
     *   <li>Hết hạn mức / đang bị stun → bỏ qua an toàn, vẫn ack để client reconcile sequenceId.
     * </ul>
     */
    private void handleJumpIntent(WorldCommand.JumpIntentCommand intent) {
        KEntity entity = entitiesByCharacterId.get(intent.characterId());
        if (entity == null) {
            return; // Race hiếm — cùng lý do handleMoveIntent.
        }
        PositionComponent position = POSITION.get(entity);
        if (!StatusEffects.isStunned(STATUS_EFFECT.get(entity))) {
            if (position.grounded) {
                position.grounded = false;
                position.vz = WorldConstants.JUMP_VELOCITY_MPS;
                // Phòng thủ: hạn mức đã được VerticalMovementSystem xoá lúc đáp đất, đặt lại ở đây
                // để cú nhảy đầu tiên sau khi attach (chưa đáp đất lần nào) cũng bắt đầu từ 0.
                position.airJumpsUsed = 0;
            } else if (position.airJumpsUsed < WorldConstants.MAX_AIR_JUMPS) {
                position.airJumpsUsed++;
                position.vz = WorldConstants.AIR_JUMP_VELOCITY_MPS;
            }
        }
        outboundSink.onMoveAccepted(new WorldOutboundSink.MoveAcceptedSnapshot(
                intent.characterId(), intent.sequenceId(), position.x, position.y, position.z, position.grounded));
    }

    private void handleSetTarget(long characterId, long targetEntityId) {
        KEntity entity = entitiesByCharacterId.get(characterId);
        if (entity == null) {
            return;
        }
        if (targetEntityId == characterId || lookupEntity(targetEntityId) == null) {
            return; // không tự nhắm chính mình; mục tiêu không tồn tại — bỏ qua an toàn.
        }
        TARGET.get(entity).targetEntityId = targetEntityId;
    }

    /**
     * Người chơi bấm nút đánh (2026-08-09). Không kiểm gì thêm ở đây — {@code CombatSystem} sở hữu
     * toàn bộ luật đánh thường (cooldown/tầm/stun/mục tiêu), cùng phân chia trách nhiệm với
     * {@link #handleUseSkill} (đẩy thẳng cho {@code SkillSystem} tự validate).
     */
    private void handleAttackIntent(long characterId) {
        KEntity entity = entitiesByCharacterId.get(characterId);
        if (entity == null) {
            return; // Race hiếm — cùng lý do handleMoveIntent.
        }
        combatSystem.requestAttack(entity);
    }

    private void handleUseSkill(long characterId, String skillCode, long targetEntityId) {
        if (!entitiesByCharacterId.containsKey(characterId)) {
            return;
        }
        // KHÔNG chặn targetEntityId == characterId ở đây nữa (Phase 8) — skill SELF/ALLY (buff/
        // khiên/heal) hợp lệ khi tự nhắm chính mình; SkillSystem.tryCast tự validate đầy đủ theo
        // SkillTargetKind của TỪNG skill (kể cả chặn ENEMY tự nhắm chính mình).
        skillSystem.tryCast(characterId, skillCode, targetEntityId);
    }

    private void handleAreaChatMessage(long characterId, String senderName, String body) {
        if (!entitiesByCharacterId.containsKey(characterId)) {
            return; // đã ngắt kết nối giữa lúc lệnh còn trong hàng đợi — bỏ qua an toàn.
        }
        Set<Long> observers = aoiTracker.observersOf(characterId);
        observers.add(characterId); // người gửi luôn thấy tin nhắn của chính mình.
        for (long observerId : observers) {
            outboundSink.onAreaChatMessage(observerId, characterId, senderName, body);
        }
    }

    /**
     * ADR-045 — báo cho mọi người đang NHÌN THẤY nhân vật này rằng ngoại hình vừa đổi.
     *
     * <p>Khác {@link #handleAreaChatMessage} ở đúng một điểm: KHÔNG tự thêm chính nhân vật vào tập
     * nhận. Người vừa đổi hệ phái đã thay model của mình ngay khi nhận {@code SelectFactionResponse}
     * (opcode 207) — gửi thêm một gói nữa cho họ vừa thừa vừa dựng lên đường quyền lực thứ hai cho
     * cùng một việc.
     */
    private void handleAppearanceChanged(long characterId) {
        if (!entitiesByCharacterId.containsKey(characterId)) {
            return; // đã ngắt kết nối giữa lúc lệnh còn trong hàng đợi — bỏ qua an toàn.
        }
        for (long observerId : aoiTracker.observersOf(characterId)) {
            outboundSink.onCharacterAppearanceChanged(observerId, characterId);
        }
    }

    private void handleApplyEquipmentBonus(WorldCommand.ApplyEquipmentBonusCommand command) {
        long characterId = command.characterId();
        KEntity entity = entitiesByCharacterId.get(characterId);
        CharacterBaseStats baseStats = baseStatsByCharacterId.get(characterId);
        if (entity == null || baseStats == null) {
            return; // chưa attach xong hoặc đã detach giữa lúc lệnh còn trong hàng đợi — bỏ qua an toàn.
        }
        CombatStatsComponent combatStats = COMBAT_STATS.get(entity);
        CombatConstants.CombatStats derived = CombatConstants.deriveCombatStats(baseStats);
        // LUÔN tính lại từ nền, không cộng dồn (xem javadoc applyEquipmentBonus).
        EquipmentBonusSnapshot bonus = command.bonus();
        combatStats.atk = derived.atk() + bonus.atk();
        combatStats.def = derived.def() + bonus.def();
        combatStats.res = derived.res() + bonus.res();
        applyMaxHpBonus(entity, bonus.hp());
    }

    /**
     * ADR-036: {@code bonus_hp} cộng vào MÁU TỐI ĐA, trên nền
     * {@link CombatConstants#CHARACTER_MAX_HP} (vẫn là placeholder — GDD chưa chốt công thức HP;
     * chủ dự án chấp nhận {@code maxHp = 100 + bonusHp} như placeholder mới khi duyệt ADR).
     *
     * <p>Máu HIỆN TẠI dịch theo đúng ĐỘ CHÊNH của máu tối đa, không đặt lại về đầy: mặc đồ +HP
     * không được là một nút hồi máu miễn phí giữa trận, và gỡ đồ +HP không được giết người chơi
     * (chặn dưới ở 1). Nhân vật đang chết ({@code current <= 0}) thì KHÔNG đụng vào máu hiện tại —
     * {@code respawnCharacter} mới là chỗ hồi sinh, và nó tự đọc {@code health.max} mới.
     */
    private void applyMaxHpBonus(KEntity entity, int bonusHp) {
        HealthComponent health = HEALTH.get(entity);
        int newMax = characterMaxHp(bonusHp);
        int delta = newMax - health.max;
        health.max = newMax;
        if (delta != 0 && health.current > 0) {
            health.current = Math.clamp((long) health.current + delta, 1, newMax);
        }
    }

    private void handleCombatResults(List<CombatSystem.AttackOutcome> results) {
        for (CombatSystem.AttackOutcome outcome : results) {
            WorldOutboundSink.AttackOutcome sinkOutcome = new WorldOutboundSink.AttackOutcome(
                    outcome.attackerEntityId(),
                    outcome.targetEntityId(),
                    outcome.damage(),
                    outcome.crit(),
                    outcome.miss(),
                    outcome.targetCurrentHp(),
                    outcome.targetDied());

            Set<Long> observers = aoiTracker.observersOf(outcome.targetEntityId());
            observers.add(outcome.attackerEntityId()); // người đánh luôn thấy kết quả đòn của chính mình.
            // PHÁT HIỆN THẬT (Phase 8 lát cắt 2, viết test E2E thật mới lộ ra): khi mục tiêu là
            // NHÂN VẬT (dương), mục tiêu cũng phải LUÔN thấy đòn nhắm vào mình, không chỉ dựa vào
            // observersOf() — observersOf chỉ tìm được nhân vật nào đó KHÁC đang "thấy" mục tiêu
            // qua AoI (visibleTo chỉ khớp cặp NHÂN VẬT-NHÂN VẬT — xem AreaOfInterestTracker). Nhân
            // vật chơi SOLO (không ai khác gần đó) không có ai "vouch" cho mình trong visibleTo,
            // nên khi QUÁI đánh lại (attacker âm, không phải client thật) sự kiện bị rơi mất hoàn
            // toàn. CHỈ áp dụng khi target dương (nhân vật) — target âm (quái) đã được
            // observersOf() tìm đúng nhân vật đang đánh nó rồi (nhân vật luôn thấy quái nó nhắm
            // tới), thêm quá tay ở đây sẽ tạo observerId "quái" giả (không phải client thật, vô hại
            // ở production vì ConnectedCharacterRegistry tự no-op ID lạ, nhưng gây trùng lặp giả
            // trong test đơn vị dùng sink ghi thẳng không lọc).
            if (outcome.targetEntityId() > 0) {
                observers.add(outcome.targetEntityId());
            }
            for (long observerId : observers) {
                outboundSink.onAttackResult(observerId, sinkOutcome);
            }
            if (outcome.targetDied() && outcome.targetEntityId() < 0) {
                handleMonsterDeath(outcome.targetEntityId(), outcome.attackerEntityId());
            } else if (outcome.targetDied() && outcome.targetEntityId() > 0) {
                // Arena (Phase 8 lát cắt 4) chốt kết quả trận đấu qua ArenaOutboundSink override —
                // world CHÍNH hồi sinh tại chỗ ngay sau đó.
                outboundSink.onCharacterDefeated(outcome.targetEntityId(), outcome.attackerEntityId());
                // Nhân vật KHÔNG bị removeEntity() khi chết (khác quái — xem handleMonsterDeath),
                // nên CombatSystem không tự nhận ra "mục tiêu đã biến mất" ở lần xử lý kế (targetHealth
                // không còn null cũng không còn isDead() sau respawnCharacter reset HP). PHẢI tự xoá
                // target của kẻ hạ gục ở đây, nếu không nó tự đánh lại y hệt nhân vật vừa hồi sinh mỗi
                // khi hết cooldown — PHÁT HIỆN THẬT lúc thêm hồi sinh (unit test đột nhiên thấy
                // onCharacterDefeated bắn 2 lần thay vì 1 trong lúc sleepQuietly chờ xác nhận).
                clearAttackerTarget(outcome.attackerEntityId());
                if (spawnOverworldContent) {
                    respawnCharacter(outcome.targetEntityId());
                }
            }
        }
    }

    /**
     * ADR-046 D3 — consumer THẬT đầu tiên của {@link TriggerZoneSystem} (dựng từ 2026-08-01, tới
     * giờ vẫn no-op): bước vào vùng an toàn tông môn thì hồi đầy máu.
     *
     * <p>World nào không khai vùng nào (dungeon/arena, và mọi test không quan tâm) vẫn no-op y như
     * trước — {@code TriggerZoneSystem} thoát sớm khi danh sách zone rỗng.
     */
    private TriggerZoneSystem newSafeZoneSystem(SafeZoneCatalogSnapshot safeZones) {
        TriggerZoneSystem system = new TriggerZoneSystem(this::onSafeZoneEntered, (entity, zoneId) -> {});
        system.setZones(toNamedZones(safeZones));
        return system;
    }

    /**
     * ADR-046 — hồi máu ngoài chiến đấu (D2) và vùng an toàn (D3) đều là chuyện của NHÂN VẬT; quái
     * không mang hai component này (chúng lành lại theo D4, và không ai quan tâm quái đứng trong
     * vùng nào).
     */
    private static void addRecoveryComponents(KEntity entity) {
        entity.add(new CombatRecencyComponent());
        entity.add(new TriggerZoneMembershipComponent());
    }

    /** ADR-046 D3 — dịch vùng an toàn của world sang kiểu của {@code engine-ecs} (một lần, lúc dựng). */
    private static List<NamedZone> toNamedZones(SafeZoneCatalogSnapshot safeZones) {
        return safeZones.zones().stream()
                .map(zone -> new NamedZone(zone.code(), new Aabb(zone.minX(), zone.minY(), zone.maxX(), zone.maxY())))
                .toList();
    }

    /**
     * ADR-046 D3 — bước VÀO vùng an toàn thì hồi đầy máu ngay (chủ dự án chọn "về tông môn hồi
     * đầy", không phải hồi dần). Callback này chạy NGAY trong tick ({@code TriggerZoneSystem}), nên
     * chỉ được sửa component + xếp hàng, tuyệt đối không gọi sink (SERVER.md §2).
     *
     * <p>Đã đầy máu thì KHÔNG phát gói nào: đi qua đi lại cổng tông môn là chuyện thường ngày, và
     * mỗi lần bước vào lại đẻ một gói là đúng thứ ADR-042 đang muốn tránh.
     */
    private void onSafeZoneEntered(KEntity entity, String zoneId) {
        HealthComponent health = HEALTH.get(entity);
        if (health == null || health.isDead() || health.current >= health.max) {
            return;
        }
        health.current = health.max;
        pendingHealthChanges.add(new HealthChange(CHARACTER_ID.get(entity).characterId, health.current, health.max));
    }

    /**
     * ADR-046 D5 — cộng máu từ đan dược. Không vượt {@code max}, không cứu người đã chết (hồi sinh
     * là việc của {@link #respawnCharacter}), không phát gói nếu máu không thực sự đổi (uống thuốc
     * lúc đầy máu thì {@code feature-inventory} đã từ chối trước rồi — đây là hàng rào thứ hai).
     */
    private void handleHealCharacter(long characterId, int amount) {
        KEntity entity = entitiesByCharacterId.get(characterId);
        if (entity == null || amount <= 0) {
            return;
        }
        HealthComponent health = HEALTH.get(entity);
        if (health.isDead() || health.current >= health.max) {
            return;
        }
        health.current = Math.min(health.max, health.current + amount);
        pendingHealthChanges.add(new HealthChange(characterId, health.current, health.max));
    }

    private List<HealthChange> drainHealthChangeQueue() {
        if (pendingHealthChanges.isEmpty()) {
            return List.of();
        }
        List<HealthChange> results = List.copyOf(pendingHealthChanges);
        pendingHealthChanges.clear();
        return results;
    }

    /**
     * ADR-046 D1 — phát {@code HealthChangedEvent} cho mọi người đang thấy entity đó trong AoI.
     *
     * <p>Cùng luật quan sát viên như {@code handleCombatResults}: {@code observersOf} chỉ khớp cặp
     * NHÂN VẬT–NHÂN VẬT, nên nhân vật chơi solo phải được thêm vào tay — không thì chính người vừa
     * hồi máu là người duy nhất KHÔNG biết mình vừa hồi máu.
     */
    private void handleHealthChanges(List<HealthChange> changes) {
        for (HealthChange change : changes) {
            WorldOutboundSink.HealthSnapshot snapshot =
                    new WorldOutboundSink.HealthSnapshot(change.entityId(), change.currentHp(), change.maxHp());
            Set<Long> observers = aoiTracker.observersOf(change.entityId());
            if (change.entityId() > 0) {
                observers.add(change.entityId());
            }
            for (long observerId : observers) {
                outboundSink.onHealthChanged(observerId, snapshot);
            }
        }
    }

    private void handleSkillResults(List<SkillSystem.SkillOutcome> results) {
        for (SkillSystem.SkillOutcome outcome : results) {
            WorldOutboundSink.SkillOutcome sinkOutcome = new WorldOutboundSink.SkillOutcome(
                    outcome.skillCode(),
                    outcome.attackerEntityId(),
                    outcome.targetEntityId(),
                    outcome.damage(),
                    outcome.crit(),
                    outcome.miss(),
                    outcome.targetCurrentHp(),
                    outcome.targetDied(),
                    outcome.effectKind());

            Set<Long> observers = aoiTracker.observersOf(outcome.targetEntityId());
            observers.add(outcome.attackerEntityId()); // người dùng skill luôn thấy kết quả của chính mình.
            // Chỉ target dương (nhân vật) — xem giải thích đầy đủ ở handleCombatResults.
            if (outcome.targetEntityId() > 0) {
                observers.add(outcome.targetEntityId());
            }
            for (long observerId : observers) {
                outboundSink.onSkillResult(observerId, sinkOutcome);
            }
            if (outcome.targetDied() && outcome.targetEntityId() < 0) {
                handleMonsterDeath(outcome.targetEntityId(), outcome.attackerEntityId());
            } else if (outcome.targetDied() && outcome.targetEntityId() > 0) {
                // Xem giải thích đầy đủ ở handleCombatResults — cùng lý do, khác nguồn đòn (skill).
                outboundSink.onCharacterDefeated(outcome.targetEntityId(), outcome.attackerEntityId());
                clearAttackerTarget(outcome.attackerEntityId());
                if (spawnOverworldContent) {
                    respawnCharacter(outcome.targetEntityId());
                }
            }
        }
    }

    /**
     * Xoá mục tiêu đang nhắm của {@code attackerEntityId} ngay sau khi nó hạ gục 1 NHÂN VẬT — xem
     * giải thích đầy đủ ở lời gọi trong {@link #handleCombatResults}.
     */
    private void clearAttackerTarget(long attackerEntityId) {
        KEntity attacker = lookupEntity(attackerEntityId);
        if (attacker != null) {
            TARGET.get(attacker).targetEntityId = 0L;
        }
    }

    /**
     * Hồi sinh {@code characterId} (luôn DƯƠNG) tại {@link #CHARACTER_RESPAWN_X}/{@code Y} với
     * HP/Chi đầy — gọi ngay sau {@link WorldOutboundSink#onCharacterDefeated} khi
     * {@link #spawnOverworldContent} (Arena/Dungeon tự có luồng kết thúc trận riêng, không đi qua
     * đây). Xoá mục tiêu đang nhắm để CombatSystem không tự đánh trả ngay lập tức tại điểm hồi
     * sinh (quái mất mục tiêu tự bỏ đuổi qua leash range của AiSystem, không cần sửa AiSystem).
     */
    private void respawnCharacter(long characterId) {
        KEntity entity = entitiesByCharacterId.get(characterId);
        if (entity == null) {
            return; // đã ngắt kết nối giữa lúc đòn hạ gục còn đang xử lý — bỏ qua an toàn.
        }
        PositionComponent position = POSITION.get(entity);
        position.x = CHARACTER_RESPAWN_X;
        position.y = CHARACTER_RESPAWN_Y;
        position.prevX = position.x;
        position.prevY = position.y;
        // ADR-033: nhân vật có thể chết giữa lúc đang lơ lửng (z>0, grounded=false) — hồi sinh PHẢI
        // đặt lại cả trục dọc, không chỉ x/y, nếu không nhân vật hồi sinh lơ lửng giữa không trung.
        position.z = 0f;
        position.prevZ = 0f;
        position.vz = 0f;
        position.grounded = true;

        HealthComponent health = HEALTH.get(entity);
        health.current = health.max;

        ChiComponent chi = CHI.get(entity);
        chi.current = chi.max;

        TARGET.get(entity).targetEntityId = 0L;

        outboundSink.onCharacterRespawned(new WorldOutboundSink.RespawnSnapshot(
                characterId, position.x, position.y, health.current, health.max, chi.current, chi.max));
    }

    /**
     * Chiêu đặc biệt của boss (Phase 8 lát cắt 2) — TÁI DÙNG nguyên {@code SkillOutcome}/
     * {@code onSkillResult} đã có (KHÔNG đổi protocol): {@code skillCode} là {@code "heavy_strike"}
     * hoặc {@code "enrage"}, client phân biệt qua đó nếu cần hiệu ứng riêng sau này.
     */
    private void handleBossResults(List<BossAiSystem.BossActionOutcome> results) {
        for (BossAiSystem.BossActionOutcome outcome : results) {
            WorldOutboundSink.SkillOutcome sinkOutcome = new WorldOutboundSink.SkillOutcome(
                    outcome.actionCode(),
                    outcome.attackerEntityId(),
                    outcome.targetEntityId(),
                    outcome.damage(),
                    outcome.crit(),
                    outcome.miss(),
                    outcome.targetCurrentHp(),
                    outcome.targetDied(),
                    SkillEffectKind.DAMAGE); // boss chỉ có chiêu damage ở lát cắt này (chưa heal/khiên/buff).

            Set<Long> observers = aoiTracker.observersOf(outcome.targetEntityId());
            observers.add(outcome.attackerEntityId()); // boss "thấy" đòn của chính nó, giống người dùng skill.
            // Chỉ target dương (nhân vật) — xem giải thích đầy đủ ở handleCombatResults.
            if (outcome.targetEntityId() > 0) {
                observers.add(outcome.targetEntityId());
            }
            for (long observerId : observers) {
                outboundSink.onSkillResult(observerId, sinkOutcome);
            }
            // targetEntityId của chiêu boss luôn DƯƠNG (nhân vật) — không nhánh handleMonsterDeath
            // như handleCombatResults/handleSkillResults (chưa có cơ chế tử vong nhân vật, cùng
            // hiện trạng đánh thường của quái qua AiSystem/CombatSystem, ngoài phạm vi lát cắt này).
        }
    }

    private void handleMonsterDeath(long monsterId, long killerEntityId) {
        KEntity entity = entitiesByMonsterId.remove(monsterId);
        if (entity == null) {
            return; // đã xử lý (nhiều đòn hạ gục cùng tick — CombatSystem tự chặn đòn thứ 2, xem javadoc).
        }
        MonsterComponent killedMonster = MONSTER.get(entity); // đọc TRƯỚC removeEntity — sau đó component mất.
        String monsterCode = killedMonster.monsterCode;
        long expReward = killedMonster.expReward;
        aoiTracker.onMonsterDetach(monsterId);
        ecsWorld.removeEntity(entity);
        // Chỉ world chính hồi sinh quái bù nhìn — dungeon (spawnOverworldContent=false) không có
        // khái niệm training dummy; PHÁT HIỆN THẬT (Phase 8 lát cắt 2): thiếu guard này, boss chết
        // trong dungeon sẽ vô tình lên lịch spawnMonster() (sai nội dung, sai world).
        // `spawnIndex == null` với BOSS (spawn qua spawnBossEntity, không thuộc điểm spawn nào).
        Integer spawnIndex = spawnIndexByMonsterId.remove(monsterId);
        if (spawnOverworldContent && spawnIndex != null) {
            monsterRespawnCountdownSeconds[spawnIndex] =
                    monsterCatalog.spawnPoints().get(spawnIndex).respawnSeconds();
        }

        // killerEntityId ÂM nghĩa là quái khác hạ gục quái này — chưa từng xảy ra (quái không nhắm
        // quái, xem AiSystem) nhưng chặn phòng thủ, rơi đồ chỉ dành cho NGƯỜI CHƠI.
        if (killerEntityId > 0) {
            outboundSink.onMonsterKilled(killerEntityId, monsterCode, expReward);
        }
    }

    /** Đếm ngược hồi sinh RIÊNG cho từng điểm spawn — quái này chết không kéo theo quái khác. */
    private void tickMonsterRespawn(float deltaTime) {
        for (int i = 0; i < monsterRespawnCountdownSeconds.length; i++) {
            if (monsterRespawnCountdownSeconds[i] < 0f) {
                continue; // quái ở điểm này đang sống — không đếm ngược.
            }
            monsterRespawnCountdownSeconds[i] -= deltaTime;
            if (monsterRespawnCountdownSeconds[i] <= 0f) {
                monsterRespawnCountdownSeconds[i] = -1f;
                spawnMonster(i);
            }
        }
    }

    /**
     * Spawn con quái của điểm spawn thứ {@code spawnIndex} (ADR-037 — loài, chỉ số, vị trí và thời
     * gian hồi sinh đều đọc từ {@link #monsterCatalog}, không còn hằng số nào ở đây).
     *
     * @param spawnIndex chỉ số trong {@code monsterCatalog.spawnPoints()} — nhớ lại để hồi sinh
     *     ĐÚNG chỗ cũ, đúng loài cũ
     */
    private void spawnMonster(int spawnIndex) {
        MonsterCatalogSnapshot.MonsterSpawnDefinition spawn =
                monsterCatalog.spawnPoints().get(spawnIndex);
        MonsterCatalogSnapshot.MonsterSpecies loai =
                monsterCatalog.find(spawn.monsterCode()).orElse(null);
        if (loai == null) {
            // MonsterCatalogSnapshot.of đã lọc điểm spawn trỏ tới loài lạ, nên tới đây nghĩa là
            // catalog được dựng tay không nhất quán — bỏ qua an toàn thay vì làm chết tick thread.
            return;
        }
        KEntity entity = ecsWorld.createEntity();
        long monsterId = nextMonsterOrNpcId--;
        float spawnX = spawn.posX();

        PositionComponent position = new PositionComponent();
        position.x = spawnX;
        position.y = spawn.posY();
        position.prevX = position.x;
        position.prevY = position.y;
        entity.add(position);
        entity.add(new VelocityComponent()); // AiSystem set vận tốc theo FSM (AI.md §3)

        HealthComponent health = new HealthComponent();
        health.current = loai.maxHp();
        health.max = loai.maxHp();
        entity.add(health);

        entity.add(toMonsterComponent(loai));
        entity.add(toComponent(toStats(loai.combat())));

        MonsterIdComponent monsterIdComponent = new MonsterIdComponent();
        monsterIdComponent.monsterId = monsterId;
        entity.add(monsterIdComponent);

        entity.add(newAiState(spawnX, spawn.posY()));
        entity.add(toAiParams(loai.ai())); // ADR-039 — tham số FSM THEO LOÀI, không còn hằng số chung
        entity.add(new TargetComponent()); // AiSystem ghi khi Chase/Attack; CombatSystem đọc để đánh trả
        entity.add(new AttackCooldownComponent());
        entity.add(new StatusEffectComponent());

        entitiesByMonsterId.put(monsterId, entity);
        spawnIndexByMonsterId.put(monsterId, spawnIndex);
        // AoI sync() ở CHÍNH tick này tự phát hiện nhân vật đã ở gần + gửi MonsterSpawnedEvent —
        // không cần thông báo thủ công (giống hệt attach nhân vật, xem AreaOfInterestTracker).
    }

    /** Trạng thái FSM ban đầu — nhớ điểm spawn để {@code AiSystem} biết chỗ quay về (Return). */
    private static AiStateComponent newAiState(float spawnX, float spawnY) {
        AiStateComponent aiState = new AiStateComponent();
        aiState.spawnX = spawnX;
        aiState.spawnY = spawnY;
        return aiState;
    }

    /**
     * Số thuần tham số AI của một loài → Component bất biến gắn lên entity (ADR-039 D5).
     *
     * <p>Dựng đúng MỘT LẦN lúc spawn, cố ý: {@code AiSystem} chạy cho mọi quái mỗi tick ở 60Hz, nên
     * tra tham số theo mã loài trong tick sẽ là một lần tra {@code HashMap} cộng một
     * {@code Optional} mới cho mỗi con quái mỗi tick — /CLAUDE.md §6 cấm thẳng.
     */
    private static AiParamsComponent toAiParams(MonsterCatalogSnapshot.MonsterAiNumbers ai) {
        return new AiParamsComponent(
                ai.aggroRadiusMeters(),
                ai.leashRadiusMeters(),
                ai.chaseSpeedMps(),
                ai.patrolSpeedMps(),
                ai.patrolRadiusMeters(),
                ai.idleDurationSeconds());
    }

    /** Danh tính loài trên entity: mã trên dây, khoá art, và EXP mà world chỉ chở ra ngoài. */
    private static MonsterComponent toMonsterComponent(MonsterCatalogSnapshot.MonsterSpecies loai) {
        MonsterComponent monster = new MonsterComponent();
        monster.monsterCode = loai.code();
        monster.modelKey = loai.modelKey();
        monster.expReward = loai.expReward();
        return monster;
    }

    /**
     * Số thuần của một loài ({@link MonsterCatalogSnapshot.MonsterCombatNumbers}) → chỉ số chiến
     * đấu nội bộ. Phần trăm chia 100 ở ĐÂY, đúng một chỗ.
     *
     * <p>{@code element} lạ (dữ liệu config gõ sai) bị coi là {@link Element#NONE} thay vì ném
     * lỗi: một chuỗi sai chính tả trong bảng cân bằng không đáng làm cả world không khởi động
     * được — cùng tinh thần "điểm spawn trỏ tới loài lạ thì bỏ qua" ở
     * {@link MonsterCatalogSnapshot#of}.
     */
    private static CombatConstants.CombatStats toStats(MonsterCatalogSnapshot.MonsterCombatNumbers combat) {
        Element element;
        try {
            element = Element.valueOf(combat.element());
        } catch (IllegalArgumentException e) {
            LOG.warn("monster_template.element không hợp lệ: {} — dùng NONE", combat.element(), e);
            element = Element.NONE;
        }
        return new CombatConstants.CombatStats(
                combat.atk(),
                combat.skillDmg(),
                combat.def(),
                combat.res(),
                combat.hitPercent() / 100f,
                combat.dodgePercent() / 100f,
                combat.critPercent() / 100f,
                CombatConstants.CRIT_DAMAGE_MULTIPLIER_DEFAULT,
                element);
    }

    private void spawnBossEntity() {
        KEntity entity = ecsWorld.createEntity();
        long monsterId = nextMonsterOrNpcId--;

        PositionComponent position = new PositionComponent();
        position.x = BOSS_SPAWN_X;
        position.y = BOSS_SPAWN_Y;
        position.prevX = position.x;
        position.prevY = position.y;
        entity.add(position);
        entity.add(new VelocityComponent()); // AiSystem set vận tốc theo FSM (AI.md §3)

        HealthComponent health = new HealthComponent();
        health.current = CombatConstants.BOSS_MAX_HP;
        health.max = CombatConstants.BOSS_MAX_HP;
        entity.add(health);

        MonsterComponent monster = new MonsterComponent();
        monster.monsterCode = BossConstants.BOSS_CODE;
        // Boss giữ hardcode (ADR-037 D7) nên không có model_key từ DB — để rỗng, client tự suy
        // bằng MonsterModelKey đúng như trước ADR-037.
        monster.modelKey = "";
        monster.expReward = BossConstants.BOSS_EXP_REWARD;
        entity.add(monster);
        entity.add(toComponent(CombatConstants.BOSS_STATS));

        MonsterIdComponent monsterIdComponent = new MonsterIdComponent();
        monsterIdComponent.monsterId = monsterId;
        entity.add(monsterIdComponent);

        entity.add(newAiState(BOSS_SPAWN_X, BOSS_SPAWN_Y));
        // Boss KHÔNG có hàng trong monster_template (ADR-037 D7 giữ boss hardcode: nó có
        // BossAiSystem + script GraalJS riêng), nên tham số FSM lấy thẳng bộ mặc định — TƯỜNG MINH
        // ở đây thay vì một đường lui ngầm trong AiSystem (ADR-039 D6).
        entity.add(AiConstants.DEFAULTS);
        entity.add(new TargetComponent()); // AiSystem ghi khi Chase/Attack; CombatSystem đọc để đánh trả
        entity.add(new AttackCooldownComponent());
        entity.add(new StatusEffectComponent());
        entity.add(new BossAiComponent()); // BossAiSystem đọc/ghi — cooldown chiêu đặc biệt + cờ enrage

        entitiesByMonsterId.put(monsterId, entity);
        // AoI sync() ở CHÍNH tick này tự phát hiện nhân vật đã ở gần + gửi MonsterSpawnedEvent —
        // không cần thông báo thủ công (giống hệt spawnMonster).
    }

    /**
     * Đặt một NPC tĩnh (ADR-040 — mã và vị trí đọc từ {@link #npcCatalog}, không còn hằng số nào ở
     * đây).
     *
     * @param placement một hàng của {@code npc_spawn} đã dịch sang record thuần
     */
    private void spawnNpc(NpcCatalogSnapshot.NpcPlacementDefinition placement) {
        KEntity entity = ecsWorld.createEntity();
        long npcId = nextMonsterOrNpcId--;

        PositionComponent position = new PositionComponent();
        position.x = placement.posX();
        position.y = placement.posY();
        position.prevX = position.x;
        position.prevY = position.y;
        entity.add(position);
        // KHÔNG VelocityComponent/HealthComponent/AiStateComponent — NPC tĩnh, không di chuyển,
        // không combat (khác spawnMonster).

        NpcComponent npc = new NpcComponent();
        npc.npcCode = placement.npcCode();
        entity.add(npc);

        entitiesByNpcId.put(npcId, entity);
        // AoI sync() ở CHÍNH tick này tự phát hiện nhân vật đã ở gần + gửi NpcSpawnedEvent —
        // không cần thông báo thủ công (giống hệt quái/nhân vật, xem AreaOfInterestTracker).
    }

    /** {@code CombatConstants.CombatStats} (record, không mutable) → component ECS (mutable). */
    private static CombatStatsComponent toComponent(CombatConstants.CombatStats stats) {
        CombatStatsComponent component = new CombatStatsComponent();
        component.atk = stats.atk();
        component.skillDmg = stats.skillDmg();
        component.def = stats.def();
        component.res = stats.res();
        component.hitPercent = stats.hitPercent();
        component.dodgePercent = stats.dodgePercent();
        component.critPercent = stats.critPercent();
        component.critDamageMultiplier = stats.critDamageMultiplier();
        component.element = stats.element();
        return component;
    }

    /** Tra KEntity theo entity ID — DƯƠNG = nhân vật, ÂM = quái (combat.proto). */
    private KEntity lookupEntity(long entityId) {
        return entityId > 0 ? entitiesByCharacterId.get(entityId) : entitiesByMonsterId.get(entityId);
    }

    /** Server SỞ HỮU tốc độ — client chỉ gửi hướng, clamp về unit vector chặn speed-hack. */
    private static float[] clampDirection(float dirX, float dirY) {
        float lengthSquared = (dirX * dirX) + (dirY * dirY);
        if (lengthSquared > 1f) {
            float length = (float) Math.sqrt(lengthSquared);
            return new float[] {dirX / length, dirY / length};
        }
        return new float[] {dirX, dirY};
    }
}
