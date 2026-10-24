package dev.kage.server.world;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.random.RandomGenerator;

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
import dev.kage.server.world.component.MoveIntentComponent;
import dev.kage.server.world.component.NpcComponent;
import dev.kage.server.world.component.SkillCooldownComponent;
import dev.kage.server.world.component.SkillRankComponent;
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
 * Thread cấm sửa ECS state trực tiếp"). Việc thật xảy ra ở đầu tick kế tiếp trên {@link #tickTask},
 * đúng pseudocode SERVER.md §3.
 */
// PMD CyclomaticComplexity (class-level, ngưỡng mặc định 80): PMD 7.10.0 chưa từng chạy được thật
// trên JDK 25 (StackOverflowError type-inference — xem STATIC-ANALYSIS.md), nên đây là lần đầu
// class này bị đo. Composition root nhiều method nhỏ (wiring + tick loop) — KHÔNG phải 1 method
// phình to; tách nhỏ hơn nữa là thay đổi kiến trúc composition-root, để chủ dự án quyết định.
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class WorldFacade {

    private static final Logger LOG = LoggerFactory.getLogger(WorldFacade.class);

    static {
        // Trước MỌI World: sổ ComponentType tĩnh của Ashley chỉ còn ĐỌC từ đây (dòng 73(d)).
        WorldComponentTypes.registerAll();
    }

    /** Trần chờ lần tick dở dang lúc {@link #stop()} — xem {@link #awaitTickInProgress}. */
    private static final Duration STOP_TICK_WAIT = Duration.ofSeconds(5);

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<VelocityComponent> VELOCITY = ComponentMapper.getFor(VelocityComponent.class);
    private static final ComponentMapper<TargetComponent> TARGET = ComponentMapper.getFor(TargetComponent.class);
    private static final ComponentMapper<SkillRankComponent> SKILL_RANK =
            ComponentMapper.getFor(SkillRankComponent.class);
    private static final ComponentMapper<MonsterComponent> MONSTER = ComponentMapper.getFor(MonsterComponent.class);
    private static final ComponentMapper<BossAiComponent> BOSS_AI = ComponentMapper.getFor(BossAiComponent.class);
    private static final ComponentMapper<SkillCooldownComponent> SKILL_COOLDOWN =
            ComponentMapper.getFor(SkillCooldownComponent.class);
    private static final ComponentMapper<CombatStatsComponent> COMBAT_STATS =
            ComponentMapper.getFor(CombatStatsComponent.class);
    private static final ComponentMapper<StatusEffectComponent> STATUS_EFFECT =
            ComponentMapper.getFor(StatusEffectComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<ChiComponent> CHI = ComponentMapper.getFor(ChiComponent.class);
    private static final ComponentMapper<MoveIntentComponent> MOVE_INTENT =
            ComponentMapper.getFor(MoveIntentComponent.class);
    private static final ComponentMapper<CharacterLevelComponent> LEVEL =
            ComponentMapper.getFor(CharacterLevelComponent.class);
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

    /**
     * Người chơi có được gây hại cho người chơi KHÁC trong World này không (2026-09-24).
     *
     * <p><b>TẠM THỜI</b> — mặc định {@code false} ở MỌI World, chỉ Arena bật qua
     * {@link #allowPlayerVersusPlayer()}. Thiết kế đã duyệt (STORY.md §7, GDD) là PvP THEO VÙNG: cấm
     * trong tông môn, MỞ ở Hoang Vực. Code chưa có vùng Hoang Vực nào, nên chủ dự án chọn cấm hết
     * cho tới khi dựng nó — lúc đó mở bằng một loại zone mới, không phải bật cờ này cho World chính.
     *
     * <p>{@code volatile}: ghi trên luồng dựng World (gateway), đọc trên tick thread.
     */
    private volatile boolean pvpAllowed;

    /**
     * Sổ hồi chiêu sống lâu hơn thực thể (README dòng 37iii) — mặc định là sổ không nhớ gì, xem
     * {@link #rememberSkillCooldownsIn}. {@code volatile}: cùng lý do {@link #pvpAllowed}.
     */
    private volatile SkillCooldownLedger skillCooldownLedger = SkillCooldownLedger.forgetful();

    private final BossAiSystem bossAiSystem;
    private final HealthRegenSystem healthRegenSystem;
    private final CharacterLocomotionSystem locomotionSystem = new CharacterLocomotionSystem();

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
    private final WorldBossDamageLedger worldBossLedger = new WorldBossDamageLedger();
    private volatile WorldBossListener worldBossListener = WorldBossListener.NONE;

    private static float[] newAliveCountdowns(int spawnPointCount) {
        float[] countdowns = new float[spawnPointCount];
        java.util.Arrays.fill(countdowns, -1f);
        return countdowns;
    }

    /** Có thực thể mới (hoặc tới nhịp) chưa được đồng bộ AoI — chỉ tick thread đọc/ghi. */
    private boolean aoiDirty;

    // AoI sync chạy chậm hơn tick loop (WorldConstants.AOI_SYNC_INTERVAL_TICKS) — chỉ đếm tick,
    // KHÔNG dùng đồng hồ thật (đơn giản, không trôi lệch tích luỹ như Timer/Duration).
    private long tickCounter;

    private volatile boolean running;
    // Ghi TRƯỚC running = false trong stop() — ai đọc running == false rồi đọc cờ này thấy đúng là
    // "đã/đang dừng" chứ không phải "chưa từng start". Xem inboxBarrier().
    private volatile boolean stopRequested;
    // Xong khi stop() đã chờ lần tick dở dang chạy hết — mốc chặn lấy giữa lúc stop() đang chờ phải
    // đợi cái này, không được xong ngay (xem inboxBarrier()).
    private final java.util.concurrent.CompletableFuture<Void> stopped = new java.util.concurrent.CompletableFuture<>();
    // Thay "1 OS thread riêng/World" (Phase 9, xem WorldTickScheduler javadoc) — pool CỐ ĐỊNH dùng
    // chung, số thread không tăng theo số kênh/dungeon/duel đang chạy.
    private ScheduledFuture<?> tickFuture;
    // scheduleAtFixedRate tự đảm bảo KHÔNG chồng lấn 2 lần chạy CÙNG task, nên lock này gần như
    // không bao giờ bị tranh chấp lúc chạy bình thường — chỉ dùng để stop() CHỜ ĐÚNG lần tick đang
    // chạy dở (nếu có) xong hẳn trước khi trả về, mirror lại đúng ngữ nghĩa "interrupt + join" cũ.
    private final Lock tickLock = new ReentrantLock();
    private volatile Duration stopTickWait = STOP_TICK_WAIT;
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
     * Mã những vùng an toàn khai {@code heals = true} (ADR-050 D4). Tông môn hồi ĐẦY máu lúc bước
     * vào (ADR-046 D3, không đổi); trạm tiếp ứng dọc trục thì KHÔNG — chúng chỉ che chở, người chơi
     * tự hồi bằng hồi máu ngoài chiến đấu.
     *
     * <p>Vì sao giữ TẬP MÃ chứ không nhét cờ vào {@code NamedZone}: engine chỉ cần biết biên và tên
     * vùng để báo vào/ra, còn "vùng này làm gì" là luật của server-world. Nhét cờ xuống engine là
     * đẩy luật gameplay vào tầng không được biết gameplay.
     */
    private final Set<String> healingZoneCodes;

    /**
     * Toàn bộ resource script skill (hardcode, chưa DB-driven — xem {@link SkillConstants}) —
     * dùng cho validate script lúc khởi động (SCRIPTING.md §8). {@code static} vì đây là config
     * cố định, không phụ thuộc instance {@link WorldFacade} nào.
     */
    public static List<String> listSkillScriptResources() {
        return SkillRegistry.allScriptResources();
    }

    /** World chính: có nội dung overworld (quái/NPC) và hồi sinh nhân vật tại chỗ. */
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
        this(
                new Wiring(outboundSink, spawnOverworldContent, tickMetricsPort, content),
                ThreadLocalRandomSource.INSTANCE);
    }

    /**
     * Như constructor public 4 tham số (không đo tick) nhưng nguồn random gameplay — trúng/né/bạo
     * kích của đòn thường, skill, chiêu boss và hướng tuần tra quái — do caller cấp. CHỈ cho test
     * cần cùng seed ⇒ cùng kết quả ({@code WorldSimulationPropertyTest}); production luôn đi qua
     * constructor public, tức {@link ThreadLocalRandomSource} của luồng tick, đúng như trước 2026-10-06.
     *
     * <p>{@code random} chỉ được dùng trên luồng tick — một {@link java.util.SplittableRandom} không
     * an toàn đa luồng, nên World đã {@link #start()} không được dùng chung nguồn với World khác.
     */
    WorldFacade(
            WorldOutboundSink outboundSink,
            boolean spawnOverworldContent,
            WorldContentCatalog content,
            RandomGenerator random) {
        this(new Wiring(outboundSink, spawnOverworldContent, TickMetricsPort.NO_OP, content), random);
    }

    /** Gom 4 tham số constructor public — constructor chung nhận thêm nguồn random mà không vượt trần 4 tham số. */
    private record Wiring(
            WorldOutboundSink outboundSink,
            boolean spawnOverworldContent,
            TickMetricsPort tickMetricsPort,
            WorldContentCatalog content) {}

    private WorldFacade(Wiring wiring, RandomGenerator random) {
        this.outboundSink = wiring.outboundSink();
        this.monsterCatalog = wiring.content().monsters();
        this.npcCatalog = wiring.content().npcs();
        this.healingZoneCodes = healingZoneCodesOf(wiring.content().safeZones());
        this.monsterRespawnCountdownSeconds =
                newAliveCountdowns(monsterCatalog.spawnPoints().size());
        this.spawnOverworldContent = wiring.spawnOverworldContent();
        this.tickMetricsPort = wiring.tickMetricsPort();
        this.systemTimingListener = tickMetricsPort::recordSystemDuration;
        this.aoiTracker = new AreaOfInterestTracker(outboundSink);
        this.combatSystem = new CombatSystem(this::lookupEntity, this::isPvpAllowed, random);
        this.healthRegenSystem = new HealthRegenSystem(this::isPvpAllowed);
        this.aiSystem = new AiSystem(entitiesByCharacterId::values, this::lookupEntity, random);
        this.skillSystem = new SkillSystem(this::lookupEntity, this::isPvpAllowed, random);
        this.bossAiSystem = new BossAiSystem(this::lookupEntity, random);
        // Thứ tự ĐÚNG ECS.md §6 (InputCommandSystem -> AISystem -> MovementSystem -> ... ->
        // CombatSystem): AI set vận tốc/mục tiêu trước, Movement tích luỹ vị trí, Combat mới xét
        // tầm đánh — đổi thứ tự = đổi behavior (engine/CLAUDE.md). SkillSystem/BossAiSystem chỉ
        // đếm ngược cooldown mỗi tick (hành động thật là lệnh rời rạc/điều kiện nội bộ, không phải
        // hành vi lặp mỗi tick như đánh thường) — thứ tự với CombatSystem không quan trọng.
        ecsWorld.addSystem(aiSystem);
        // Vận tốc nhân vật theo stun/slow/buff HIỆN HÀNH — phải trước MovementSystem (2026-10-02).
        ecsWorld.addSystem(locomotionSystem);
        ecsWorld.addSystem(new MovementSystem());
        addPositionSystems(wiring.content().safeZones());
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

    /**
     * Nhóm System "áp chuyển động rồi xử vị trí" chạy ngay sau {@code MovementSystem} — tách khỏi
     * constructor vì Checkstyle {@code MethodLength}. Thứ tự bên trong giữ nguyên PHYSICS.md §4.
     */
    private void addPositionSystems(SafeZoneCatalogSnapshot safeZones) {
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
        // Biên trục X chỉ ở world chính (dòng 67(c)) — sau mọi hệ áp chuyển động, trước mọi hệ đọc vị trí.
        if (spawnOverworldContent) {
            ecsWorld.addSystem(new MainWorldBoundsSystem());
        }
        ecsWorld.addSystem(newSafeZoneSystem(safeZones));
    }

    /** Bắt đầu lịch tick trên {@link WorldTickScheduler} — gọi 1 lần lúc khởi động server. */
    public void start() {
        running = true;
        fixedDtNanos = (long) (WorldConstants.FIXED_DT * 1_000_000_000L);
        tickFuture = WorldTickScheduler.scheduleAtFixedRate(this::tickTask, fixedDtNanos);
    }

    /** Dừng tick (SERVER.md §10 — graceful shutdown, không {@code kill -9}). */
    public void stop() {
        stopRequested = true;
        running = false;
        if (tickFuture != null) {
            tickFuture.cancel(false);
            awaitTickInProgress();
        }
        completePendingBarriers();
        stopped.complete(null);
    }

    /**
     * Chờ lần tick đang chạy dở (nếu có) xong hẳn — đảm bảo {@link #stop()} chỉ trả về sau khi World
     * đã dừng tick THẬT SỰ, đúng ngữ nghĩa đồng bộ mà ArenaInstanceRegistry/DungeonInstanceRegistry
     * đang phụ thuộc (gọi stop() rồi coi World là đã ngừng hẳn ngay sau đó).
     *
     * <p><b>Có trần</b> (dòng 56, chủ dự án duyệt 2026-10-17): một tick treo — vd script kẹt — từng làm
     * cả trình tự tắt treo theo tới khi Docker giết tiến trình sau {@code stop_grace_period}. Quá
     * {@link #stopTickWait} thì đi tiếp kèm WARN: lần tick treo đó có thể còn phát gói muộn, nhưng
     * phần còn lại của trình tự tắt (lưu vị trí, đóng DB) quan trọng hơn.
     */
    // LockNotBeforeTry: khoá rồi nhả NGAY là cố ý — không có đoạn găng nào để bọc try/finally, chỉ dùng
    // khoá làm điểm CHỜ lần tick dở dang.
    @SuppressWarnings("LockNotBeforeTry")
    private void awaitTickInProgress() {
        try {
            if (tickLock.tryLock(stopTickWait.toNanos(), TimeUnit.NANOSECONDS)) {
                tickLock.unlock();
                return;
            }
            LOG.warn(
                    "World.stop(): lần tick đang chạy chưa xong sau {}ms — dừng tiếp, không chờ nữa",
                    stopTickWait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("World.stop() bị ngắt khi chờ lần tick đang chạy — dừng tiếp, không chờ nữa", e);
        }
    }

    /** Đổi trần chờ của {@link #awaitTickInProgress} — chỉ cho test (mặc định {@link #STOP_TICK_WAIT}). */
    void stopTickWait(Duration wait) {
        this.stopTickWait = wait;
    }

    /**
     * Mốc chặn hàng đợi: xong khi tick thread đã xử lý hết MỌI lệnh xếp trước lời gọi này.
     *
     * <p><b>Vì sao cần (quyết định chủ dự án 2026-09-25 — một nhân vật chỉ một phiên sống).</b> Khi
     * phiên mới đá phiên cũ, lệnh di chuyển của phiên cũ có thể còn nằm trong hàng đợi TRƯỚC lệnh
     * detach. World xử lý chúng sau, và {@code onMoveAccepted} tra kết nối theo {@code characterId} —
     * nếu phiên mới đã đăng ký thì gói xác nhận muộn của phiên cũ rơi vào kết nối MỚI. Gateway chờ
     * mốc này trước khi đăng ký phiên mới, nên thứ tự đó không thể xảy ra.
     *
     * <p>World không tick (chưa {@link #start()}, hoặc đã {@link #stop()}) thì không lệnh nào còn
     * có thể phát gói ra ngoài — mốc xong NGAY. Lệnh xếp vào đúng lúc {@link #stop()} chạy cũng không
     * treo: {@link #stop()} tự hoàn tất mọi mốc còn nằm lại trong hàng đợi.
     *
     * <p><b>Gọi đúng lúc {@link #stop()} đang chờ lần tick dở dang (sửa 2026-10-02).</b> {@code running}
     * đã là {@code false} nhưng lần tick đó vẫn có thể đang phát gói ra kênh. Bản cũ coi
     * {@code !running} là "đã im" và xong mốc NGAY — sai đúng thứ mốc này hứa. Nay mốc chờ tới khi
     * {@link #stop()} trả về. Trước đây khe này gần như không chạm được vì gateway luôn gọi
     * {@link #stop()} đồng bộ trước khi lấy mốc; nay gateway dừng World trên luồng khác (không chiếm
     * EventLoop), nên khe đó có thật.
     *
     * <p>Không chạy trong vòng tick; không cấp phát gì trên tick thread ngoài việc hoàn tất future.
     */
    // FutureReturnValueIgnored: whenComplete chỉ dùng làm callback hoàn tất mốc; lỗi (nếu có) của
    // stopped không đổi gì — mốc vẫn phải xong để không ai treo.
    @SuppressWarnings("FutureReturnValueIgnored")
    public java.util.concurrent.CompletableFuture<Void> inboxBarrier() {
        if (!running) {
            return afterStopOrNow();
        }
        java.util.concurrent.CompletableFuture<Void> reached = new java.util.concurrent.CompletableFuture<>();
        inbox.add(new WorldCommand.InboxBarrierCommand(() -> reached.complete(null)));
        if (!running) {
            // stop() chen vào giữa lần kiểm ở trên và lúc xếp hàng — có thể nó đã quét hàng đợi
            // xong trước khi mốc này vào. Không tick nữa thì mốc "tới" khi stop() xong.
            stopped.whenComplete((ignored, error) -> reached.complete(null));
        }
        return reached;
    }

    /** Mốc cho World không tick: chưa từng start ⇒ xong ngay; đã/đang dừng ⇒ xong khi stop() xong. */
    private java.util.concurrent.CompletableFuture<Void> afterStopOrNow() {
        // running đọc trước, stopRequested đọc sau; stop() ghi theo thứ tự ngược lại ⇒ đã thấy
        // running == false do stop() thì chắc chắn thấy stopRequested.
        return stopRequested ? stopped.copy() : java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    /** Hoàn tất mọi mốc chặn còn nằm lại sau khi world ngừng tick — xem {@link #inboxBarrier()}. */
    private void completePendingBarriers() {
        for (WorldCommand command : inbox) {
            if (command instanceof WorldCommand.InboxBarrierCommand barrier) {
                barrier.onReached().run();
            }
        }
    }

    public void attachCharacter(long characterId, float spawnX, float spawnY, CharacterBaseStats baseStats) {
        attachCharacter(CharacterEntry.withoutEquipment(characterId, spawnX, spawnY, baseStats));
    }

    /**
     * Gắn nhân vật vào world KÈM chỉ số trang bị — một lệnh duy nhất (sửa 2026-09-18).
     *
     * <p>⚠️ Vì sao không gọi {@link #applyEquipmentBonus} ngay sau {@code attachCharacter}: hai
     * lệnh rời nằm trong cùng hàng đợi, nên giữa chúng có ít nhất một tick mà entity đã ở trong
     * world với chỉ số NỀN — quái nhắm được và {@code CombatSystem} giải quyết đòn được. Đo thật
     * 2026-09-18: đăng nhập cạnh {@code wasteland_beast} ăn 37 sát thương (giáp chưa mặc) trên
     * thanh máu 100, trong khi mặc đủ bộ là 27 trên thanh 206 — và nhát đó rơi trước cả
     * {@code SelectCharacterResponse}.
     *
     * <p>Mọi đường attach của gateway (world chính, hầm ngục, đấu trường, đường về) đều đi qua
     * overload này từ 2026-09-29 — overload 4 tham số ở trên chỉ còn cho test/nhân vật không trang bị.
     */
    public void attachCharacter(CharacterEntry entry) {
        inbox.add(new WorldCommand.AttachCharacterCommand(entry));
    }

    public void detachCharacter(long characterId) {
        detachCharacter(characterId, FinalPositionListener.NONE);
    }

    /**
     * Gỡ nhân vật và báo vị trí thật của nó lúc gỡ cho {@code finalPosition} — gọi trên tick thread,
     * xem {@link FinalPositionListener}. Gateway dùng khi ngắt kết nối để lưu đúng chỗ đang đứng.
     */
    public void detachCharacter(long characterId, FinalPositionListener finalPosition) {
        inbox.add(new WorldCommand.DetachCharacterCommand(characterId, finalPosition));
    }

    public void enqueueMoveCommand(long characterId, int sequenceId, float dirX, float dirY) {
        inbox.add(new WorldCommand.MoveIntentCommand(characterId, sequenceId, dirX, dirY));
    }

    /** ADR-033 — ý định nhảy, world tự kiểm tra grounded lúc xử lý (xem {@link #handleJumpIntent}). */
    public void enqueueJumpCommand(long characterId, int sequenceId) {
        inbox.add(new WorldCommand.JumpIntentCommand(characterId, sequenceId));
    }

    /**
     * Cho phép người chơi gây hại cho nhau trong World này — CHỈ World đấu Arena gọi (xem
     * {@link #pvpAllowed}). Gọi TRƯỚC {@link #start()}. Kéo theo: World này không hồi máu ngoài chiến
     * đấu (xem {@link HealthRegenSystem}).
     */
    public void allowPlayerVersusPlayer() {
        pvpAllowed = true;
    }

    /**
     * Trao sổ hồi chiêu dùng chung của tiến trình: nhân vật rời World này thì hồi chiêu được ghi vào
     * sổ, vào World này thì đọc lại — thoát ra vào lại không còn xoá hồi chiêu. Gọi trước khi nhân
     * vật đầu tiên vào World.
     */
    public void rememberSkillCooldownsIn(SkillCooldownLedger ledger) {
        skillCooldownLedger = ledger;
    }

    private boolean isPvpAllowed() {
        return pvpAllowed;
    }

    /**
     * Chỉ NHẮM mục tiêu — không tự đánh nữa từ 2026-08-09, xem {@link #attack} ({@code CombatSystem}).
     *
     * <p>Nhắm một người chơi khác vẫn được kể cả khi {@link #pvpAllowed} tắt (xem thông tin / máu của
     * họ) — cổng thật nằm ở {@code CombatSystem}/{@code SkillSystem}: đòn không bao giờ ra.
     */
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
     * này ({@link CombatConstants#characterMaxHp}: nền theo cấp — dòng 65(d) — cộng {@code bonus_hp} của
     * ADR-036, đã tính cường hoá).
     *
     * <p>Công khai vì {@code server-gateway} phải gửi con số đó cho client NGAY trong
     * {@code SelectCharacterResponse}: entity ECS mới chỉ được xếp hàng lúc đó, chưa tồn tại để hỏi.
     * Để gateway tự cộng {@code 100 + bonusHp} là tạo ra bản sao thứ hai của công thức, và bản sao
     * đó sẽ lệch trong im lặng ngay lần đầu GDD chốt công thức HP thật.
     */
    public static int characterMaxHp(int level, EquipmentBonusSnapshot bonus) {
        return CombatConstants.characterMaxHp(level, bonus.hp());
    }

    /**
     * Bảng chỉ số + Lực Chiến (ADR-056) — hàm THUẦN, gọi được từ bất kỳ luồng nào (không đọc ECS).
     * Linh Lực tối đa là hằng số của World ({@code SkillConstants.CHARACTER_MAX_CHI}).
     */
    public static CharacterStatSheet statSheet(CharacterBaseStats baseStats, EquipmentBonusSnapshot bonus) {
        return CombatConstants.statSheet(baseStats, bonus, SkillConstants.CHARACTER_MAX_CHI);
    }

    /**
     * ADR-092 — gọi {@code action} với id từng người đang nhìn thấy {@code characterId} (kèm chính họ), TRÊN tick
     * thread ở tick kế. {@code action} không được chặn (chỉ xếp gói ra dây). Nhân vật không còn trong World thì bỏ qua.
     */
    public void enqueueForObservers(long characterId, java.util.function.LongConsumer action) {
        inbox.add(new WorldCommand.ObserversCommand(characterId, action));
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
     * Nhân vật vừa lên cấp giữa phiên — nâng cấp mà mọi luật trong World này đọc (hiện là cổng Tuyệt
     * Kỹ, {@code SkillSystem}) lên {@code level}, không chờ lần attach sau. Gọi từ
     * {@code server-gateway} sau khi {@code GrantExperienceUseCase} báo {@code leveledUp}.
     *
     * <p>Chỉ NÂNG: {@code level} không cao hơn cấp đang có thì bị bỏ qua (lệnh đến muộn, xem
     * {@link WorldCommand.RaiseCharacterLevelCommand}). Nhân vật không ở World này — chưa attach
     * xong, hoặc đã rời — cũng bị bỏ qua êm: lần attach kế đọc cấp từ DB. Nhảy nhiều cấp một lần
     * là một lệnh mang cấp cuối.
     *
     * <p>Chỉ số chiến đấu KHÔNG đổi theo cấp ở đây: {@code CombatConstants.deriveCombatStats} không
     * đọc cấp, và lên cấp hiện không cộng STR/AGI/INT/VIT.
     *
     * @param characterId nhân vật vừa lên cấp
     * @param level cấp MỚI (tuyệt đối, không phải số cấp tăng thêm)
     */
    public void enqueueCharacterLevel(long characterId, int level) {
        inbox.add(new WorldCommand.RaiseCharacterLevelCommand(characterId, level));
    }

    /**
     * ADR-089 — chuyển thế: cấp về {@code level} dù thấp hơn cấp hiện tại. Máu tối đa theo cấp được tính lại ở lệnh
     * {@link #applyEquipmentBonus} gateway gửi ngay sau (máu hiện tại giữ tỉ lệ như khi gỡ đồ).
     */
    public void resetCharacterLevel(long characterId, int level) {
        inbox.add(new WorldCommand.ResetCharacterLevelCommand(characterId, level));
    }

    /**
     * Nhân vật vừa đổi hệ phái giữa phiên (quyết định chủ dự án 2026-10-11) — nguyên tố và passive
     * của hệ có hiệu lực NGAY trong World này. Trước đó chỉ ngoại hình đổi (ADR-045): người chơi thấy
     * hệ mới mà đánh, chịu đòn và bị xét chiêu như hệ cũ tới khi đăng nhập lại. Gọi từ
     * {@code server-gateway} sau khi {@code SelectFactionUseCase} ghi DB thành công.
     *
     * <p>Chỉ số từ trang bị được giữ nguyên; máu, Khí, cooldown và hiệu ứng đang mang không đổi. Nhân
     * vật không ở World này thì lệnh bị bỏ qua êm — lần attach kế đọc hệ từ DB.
     */
    public void enqueueCharacterElement(long characterId, Element element) {
        inbox.add(new WorldCommand.ChangeCharacterElementCommand(characterId, element));
    }

    /** ADR-071 — bậc chiêu mới có hiệu lực ngay (nâng bậc, đổi linh căn). */
    public void enqueueSkillRanks(long characterId, SkillRankSnapshot ranks) {
        inbox.add(new WorldCommand.SetSkillRanksCommand(characterId, ranks));
    }

    /** ADR-071 — chiêu của một linh căn theo thứ tự ô, cho gateway kiểm lệnh nâng bậc. Hàm thuần. */
    public static List<FactionSkillSlot> skillsOf(Element element) {
        List<FactionSkillSlot> skills = new ArrayList<>();
        for (SkillDefinition definition : SkillRegistry.BY_CODE.values()) {
            if (definition.faction() == element) {
                skills.add(new FactionSkillSlot(definition.code(), definition.slotIndex(), definition.requiredLevel()));
            }
        }
        skills.sort(Comparator.comparingInt(FactionSkillSlot::slotIndex));
        return skills;
    }

    /**
     * Spawn boss theo MÃ LOÀI (ADR-048 D6) — gọi bởi {@code DungeonInstanceRegistry} ngay sau khi
     * tạo dungeon World mới. World chính KHÔNG tự gọi hàm này (giữ đúng ranh giới: server-world
     * không biết khái niệm "dungeon", chỉ biết "có ai bảo spawn con nào").
     *
     * <p>Mã phải có trong catalog tiêm vào constructor và phải là một hàng {@code is_boss} — nếu
     * không, lệnh bị bỏ qua kèm log lỗi thay vì dựng một thực thể nửa vời.
     *
     * @param bossCode mã trong {@code monster_template}, ví dụ {@code sample_boss}
     */
    public void spawnBoss(String bossCode) {
        inbox.add(new WorldCommand.SpawnBossCommand(bossCode, BOSS_SPAWN_X, BOSS_SPAWN_Y, false));
    }

    /**
     * Boss thế giới (ADR-081) — như {@link #spawnBoss} nhưng tại {@code (x, y)} và có sổ sát thương; lúc nó chết,
     * {@link WorldBossListener} nhận sổ. Trần một con mỗi mã vẫn áp dụng (bỏ lệnh nếu con trước còn sống).
     */
    public void spawnWorldBoss(String bossCode, float x, float y) {
        inbox.add(new WorldCommand.SpawnBossCommand(bossCode, x, y, true));
    }

    /** Ai nghe boss thế giới chết (ADR-081) — gọi TRÊN tick thread, phải trả về ngay (việc nặng sang luồng khác). */
    public void setWorldBossListener(WorldBossListener listener) {
        this.worldBossListener = listener;
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
            if (!running) {
                // Kiểm LẠI sau khi có khoá (sửa 2026-10-02): lần kiểm ở trên và lock() không nguyên tử.
                // Lần tick đã qua kiểm nhưng chưa kịp lấy khoá khi stop() chạy thì stop() lấy-nhả khoá
                // không vướng gì và TRẢ VỀ — rồi lần tick này mới lấy khoá và chạy trọn một tick SAU
                // stop(): xử lý hàng đợi, phát gói ra kênh. Đúng thứ ArenaInstanceRegistry/
                // DungeonInstanceRegistry dựa vào stop() để chặn (gói World cũ tới sau 308).
                return;
            }
            spawnContentOnce();

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
     * Cách ly lỗi (ADR-026): 1 exception trong tick KHÔNG được âm thầm giết tick thread —
     * trước đây không bắt, tick lỗi 1 lần là World đóng băng vĩnh viễn không ai biết. Log rồi tick
     * tiếp, cùng tinh thần {@code CharacterPositionPersister.savePositionSafely} (1 lỗi không dừng
     * cả vòng lặp).
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void tickOnce() {
        try {
            simulateTick();
        } catch (RuntimeException e) {
            LOG.error("Lỗi trong World tick — bỏ qua tick này, KHÔNG dừng tick loop", e);
        }
    }

    /** Dựng quái/NPC tĩnh đúng MỘT lần, ngay trước tick đầu tiên (chạy trên tick thread). */
    private void spawnContentOnce() {
        if (contentSpawned) {
            return;
        }
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

    /**
     * Một tick mô phỏng trọn vẹn — thân của {@link #tickOnce()} khi KHÔNG bọc cách ly lỗi.
     *
     * <p>Tách ra (2026-10-04) để bộ kiểm thuộc tính {@code WorldSimulationPropertyTest} chạy được
     * từng tick một cách XÁC ĐỊNH trên luồng gọi và thấy exception thay vì chỉ một dòng log — xem
     * {@link #runOneTickOnCallerThread()}. Hành vi của tick thật không đổi.
     */
    private void simulateTick() {
        drainInbox();
        syncAoiIfDirty(); // người vừa vào World được thấy/được thấy TRƯỚC lượt mô phỏng đầu tiên của họ
        ecsWorld.update(WorldConstants.FIXED_DT, systemTimingListener);
        tickMetricsPort.recordEntityCount(ecsWorld.entityCount());
        handleCombatResults(combatSystem.drainResults());
        handleSkillResults(skillSystem.drainResults());
        handleBossResults(bossAiSystem.drainResults());
        handleHealthChanges(healthRegenSystem.drainResults()); // ADR-046 D2
        handleHealthChanges(aiSystem.drainHealthChanges()); // ADR-046 D4
        handleHealthChanges(drainHealthChangeQueue()); // ADR-046 D3 + D5
        announceMovementModifiers(locomotionSystem.drainChanges()); // ADR-073
        tickMonsterRespawn(WorldConstants.FIXED_DT);
        // O(n²) — chạy chậm hơn tick loop (WorldConstants.AOI_SYNC_INTERVAL_TICKS), xem
        // javadoc AreaOfInterestTracker + WorldConstants.AOI_SYNC_RATE_HZ. Combat/skill/boss
        // result ở trên KHÔNG bị ảnh hưởng — vẫn phát ngay mỗi tick qua observersOf().
        tickCounter++;
        if (tickCounter % WorldConstants.AOI_SYNC_INTERVAL_TICKS == 0) {
            aoiDirty = true;
        }
        syncAoiIfDirty(); // nhịp 20Hz, hoặc quái vừa hồi sinh ở tickMonsterRespawn ngay trên
        if (tickCounter % WorldConstants.POSITION_SAMPLE_INTERVAL_TICKS == 0) {
            samplePositions();
        }
    }

    /**
     * Đồng bộ AoI nếu có thực thể MỚI kể từ lần sync trước, hoặc tới nhịp 20Hz (README dòng 67(d),
     * chủ dự án duyệt 2026-10-17). Trước đó thực thể mới chỉ được ai đó "thấy" ở nhịp kế tiếp (≤ 2 tick):
     * quái vừa hồi sinh hay người vừa vào ra/nhận đòn trong khe đó thì người quan sát nhận kết quả từ
     * một thực thể client chưa biết. Sync thêm chỉ khi có thực thể mới — không đổi chi phí tick thường.
     */
    private void syncAoiIfDirty() {
        if (!aoiDirty) {
            return;
        }
        aoiDirty = false;
        aoiTracker.sync(entitiesByCharacterId, entitiesByMonsterId, entitiesByNpcId);
    }

    /** Báo vị trí thật của mọi nhân vật — xem {@link WorldOutboundSink#onCharacterPositionSampled}. */
    private void samplePositions() {
        for (Map.Entry<Long, KEntity> entry : entitiesByCharacterId.entrySet()) {
            PositionComponent position = POSITION.get(entry.getValue());
            outboundSink.onCharacterPositionSampled(entry.getKey(), position.x, position.y);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Khe kiểm thử (package-private — chỉ test cùng package thấy; gateway KHÔNG gọi được).
    // ---------------------------------------------------------------------------------------------

    /**
     * Chạy đúng MỘT tick trên luồng GỌI, không qua {@link WorldTickScheduler}, không nuốt exception.
     *
     * <p>Chỉ dùng cho World CHƯA {@link #start()}: hai luồng cùng chạm ECS state là vi phạm đúng
     * ranh giới SERVER.md §2 mà tick thread tồn tại để giữ.
     */
    void runOneTickOnCallerThread() {
        if (running) {
            throw new IllegalStateException("World đang tự tick — không được tick tay song song");
        }
        // Cùng khoá với tickTask — giữ đúng bất biến "một luồng chạm ECS state tại một thời điểm".
        tickLock.lock();
        try {
            spawnContentOnce();
            simulateTick();
        } finally {
            tickLock.unlock();
        }
    }

    /** Chỉ đọc — entity nhân vật đang gắn, theo {@code characterId}. Chỉ hợp lệ giữa hai tick tay. */
    Map<Long, KEntity> characterEntitiesView() {
        return Collections.unmodifiableMap(entitiesByCharacterId);
    }

    /** Chỉ đọc — entity quái/boss còn sống, theo {@code monsterId} (âm). */
    Map<Long, KEntity> monsterEntitiesView() {
        return Collections.unmodifiableMap(entitiesByMonsterId);
    }

    /** Chỉ đọc — entity NPC tĩnh, theo {@code npcId} (âm). */
    Map<Long, KEntity> npcEntitiesView() {
        return Collections.unmodifiableMap(entitiesByNpcId);
    }

    /** Tổng entity trong {@link EcsWorld} — để đối chiếu với ba map trên (không entity mồ côi). */
    int ecsEntityCount() {
        return ecsWorld.entityCount();
    }

    private void drainInbox() {
        WorldCommand command;
        while ((command = inbox.poll()) != null) {
            switch (command) {
                case WorldCommand.AttachCharacterCommand c -> handleAttach(c.entry());
                case WorldCommand.DetachCharacterCommand c -> handleDetach(c.characterId(), c.finalPosition());
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
                case WorldCommand.RaiseCharacterLevelCommand c -> handleRaiseCharacterLevel(c.characterId(), c.level());
                case WorldCommand.ChangeCharacterElementCommand c ->
                    handleChangeCharacterElement(c.characterId(), c.element());
                case WorldCommand.SetSkillRanksCommand c -> handleSetSkillRanks(c.characterId(), c.ranks());
                case WorldCommand.SpawnBossCommand c -> spawnBossEntity(c);
                case WorldCommand.ResetCharacterLevelCommand c -> handleResetCharacterLevel(c.characterId(), c.level());
                case WorldCommand.ObserversCommand c -> handleObservers(c.characterId(), c.action());
                case WorldCommand.InboxBarrierCommand c -> c.onReached().run();
            }
        }
    }

    private void handleAttach(CharacterEntry entry) {
        if (entitiesByCharacterId.containsKey(entry.characterId())) {
            return; // đã attach — bỏ qua an toàn thay vì tạo entity trùng.
        }
        KEntity entity = ecsWorld.createEntity();

        PositionComponent position = new PositionComponent();
        position.x = finiteSpawnX(entry.spawnX());
        // ADR-033 — KÉO VỀ ĐƯỜNG CHƠI: di chuyển khoá vào trục X, không lệnh nào đổi được Y. Nhân vật có
        // `pos_y` đã lưu khác 0 (thời đi tự do 4 hướng) sẽ kẹt VĨNH VIỄN ngoài đường chơi, không chạm được
        // bệ/địa hình nào (đều quanh y=0). Bỏ qua `spawnY` là hệ quả bắt buộc của việc thu trục, không
        // phải mất dữ liệu tuỳ tiện: giá trị cũ không còn ý nghĩa gameplay nào trong mô hình mới.
        position.y = CHARACTER_PLAY_LINE_Y;
        position.prevX = position.x;
        position.prevY = position.y;
        entity.add(position);
        entity.add(new VelocityComponent());
        entity.add(new MoveIntentComponent());

        CharacterIdComponent characterIdComponent = new CharacterIdComponent();
        characterIdComponent.characterId = entry.characterId();
        entity.add(characterIdComponent);

        HealthComponent health = new HealthComponent();
        health.current = CombatConstants.CHARACTER_MAX_HP; // nền Cấp 1 — applyBonusTo (sau khi gắn cấp) nâng lên
        health.max = CombatConstants.CHARACTER_MAX_HP;
        entity.add(health);
        entity.add(toComponent(CombatConstants.deriveCombatStats(entry.baseStats())));
        entity.add(new TargetComponent());
        entity.add(new AttackCooldownComponent());
        entity.add(newSkillCooldown(entry.characterId()));
        entity.add(skillRanksOf(entry));
        entity.add(new StatusEffectComponent());
        CharacterLevelComponent levelComponent = new CharacterLevelComponent();
        levelComponent.level = entry.baseStats().level();
        entity.add(levelComponent);

        ChiComponent chi = new ChiComponent();
        chi.current = SkillConstants.CHARACTER_MAX_CHI; // placeholder — chưa nối character_stat.current_chi DB
        chi.max = SkillConstants.CHARACTER_MAX_CHI;
        entity.add(chi);
        addRecoveryComponents(entity);
        entitiesByCharacterId.put(entry.characterId(), entity);
        aoiDirty = true; // thực thể mới — xem syncAoiIfDirty
        baseStatsByCharacterId.put(entry.characterId(), entry.baseStats());
        // TRƯỚC onAttach: entity không được có một tick nào vừa nhìn thấy được vừa mang chỉ số nền.
        applyBonusTo(entity, entry.baseStats(), entry.bonus());
        aoiTracker.onAttach(entry.characterId());
    }

    /**
     * Vị trí đã lưu KHÔNG hữu hạn ⇒ về điểm hồi sinh (sửa 2026-10-03). Trước bản vá hướng NaN
     * (2026-09-24, {@link #clampDirection}) một gói MoveCommand đủ để nhân vật mang {@code x = NaN},
     * và vị trí đó được ghi xuống DB lúc thoát (cột REAL nhận NaN). Không chặn ở đây thì nhân vật ấy
     * kẹt NaN VĨNH VIỄN mỗi lần vào lại: NaN cộng gì cũng ra NaN, so khoảng cách nào cũng false.
     */
    private static float finiteSpawnX(float savedX) {
        return Float.isFinite(savedX) ? savedX : CHARACTER_RESPAWN_X;
    }

    /** Hồi chiêu của nhân vật vừa vào: đọc lại từ sổ nếu họ rời World khi chiêu còn đang hồi (README dòng 37iii). */
    private SkillCooldownComponent newSkillCooldown(long characterId) {
        SkillCooldownComponent skillCooldown = new SkillCooldownComponent();
        skillCooldownLedger.restoreInto(characterId, skillCooldown.remainingSeconds);
        return skillCooldown;
    }

    private void handleDetach(long characterId, FinalPositionListener finalPosition) {
        // Máu đổi bởi lệnh xếp TRƯỚC lệnh rời này (đan dược, đổi trang bị) bình thường chỉ phát sau
        // ecsWorld.update(). Phát NGAY, theo đúng thứ tự lệnh (sửa 2026-10-04, WorldSimulationPropertyTest):
        // không thì người rời — mà gateway có thể đã đăng ký phiên mới ngay khi inboxBarrier xong — vẫn
        // được gửi gói của phiên cũ. Đòn đánh/skill thì đã phát ngay lúc giải (emitCommandResults).
        handleHealthChanges(drainHealthChangeQueue());
        KEntity entity = entitiesByCharacterId.remove(characterId);
        baseStatsByCharacterId.remove(characterId);
        if (entity == null) {
            return;
        }
        PositionComponent position = POSITION.get(entity);
        finalPosition.onFinalPosition(position.x, position.y);
        aoiTracker.onDetach(characterId);
        if (SKILL_COOLDOWN.has(entity)) {
            skillCooldownLedger.remember(characterId, SKILL_COOLDOWN.get(entity).remainingSeconds);
        }
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
        // Nhớ hướng đang giữ CẢ khi đang bị stun/đã chết: CharacterLocomotionSystem dùng nó để chạy
        // tiếp khi hết choáng mà phím vẫn giữ (client không gửi lại lệnh cùng hướng).
        MOVE_INTENT.get(entity).dirX = clampDirection(intent.dirX(), 0f)[0];
        // Người đã chết (World hầm ngục không tự hồi sinh — xem handleCombatResults) đứng yên như bị
        // stun: vẫn ack để client reconcile sequenceId.
        if (isIncapacitated(entity)) {
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
            float speed = WorldConstants.MOVE_SPEED_MPS
                    * MOVE_INTENT.get(entity).speedMultiplier
                    * StatusEffects.moveSpeedMultiplier(STATUS_EFFECT.get(entity));
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
        // Người đã chết cũng không nhảy (2026-10-02) — trước đó chỉ chặn stun, nên trong World hầm
        // ngục (không tự hồi sinh) một client sửa đổi vẫn nhảy được bằng cái xác 0 máu.
        if (!isIncapacitated(entity)) {
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

    /** Bị stun hoặc đã chết — không tự di chuyển/nhảy được (cổng chung của move và jump). */
    private static boolean isIncapacitated(KEntity entity) {
        HealthComponent health = HEALTH.get(entity);
        return StatusEffects.isStunned(STATUS_EFFECT.get(entity)) || (health != null && health.isDead());
    }

    private void handleSetTarget(long characterId, long targetEntityId) {
        KEntity entity = entitiesByCharacterId.get(characterId);
        if (entity == null) {
            return;
        }
        KEntity target = lookupEntity(targetEntityId);
        if (targetEntityId == characterId || target == null) {
            return; // không tự nhắm chính mình; mục tiêu không tồn tại — bỏ qua an toàn.
        }
        TARGET.get(entity).targetEntityId = targetEntityId;
        announceTargetHealth(characterId, targetEntityId, target);
    }

    /**
     * Nhắm một NHÂN VẬT khác ⇒ gửi ngay máu hiện tại/tối đa của người đó cho người nhắm (dòng 32,
     * chủ dự án duyệt 2026-10-18).
     *
     * <p>Máu tối đa của người khác chỉ đi trên {@code HealthChangedEvent}; đòn đánh/skill chỉ mang máu
     * còn lại. Trong đấu trường không có hồi máu (dòng 36(iv)) nên gói đó không bao giờ tới, và HUD
     * mục tiêu đứng ở "máu / ?" suốt trận. Dùng lại đúng đường ống 408 — không đổi protocol. Quái thì
     * không cần: {@code MonsterSpawnedEvent} đã mang máu tối đa.
     *
     * <p>Chỉ khi người nhắm ĐANG THẤY mục tiêu (AoI): gói về một thực thể đã despawn khỏi máy họ là vi
     * phạm bất biến luồng sự kiện — {@code WorldSimulationPropertyTest} bắt được đúng chỗ này ở bản đầu
     * (nhắm một id còn trong World nhưng đã ra khỏi tầm nhìn).
     */
    private void announceTargetHealth(long characterId, long targetEntityId, KEntity target) {
        HealthComponent health = HEALTH.get(target);
        if (targetEntityId <= 0L || health == null || !aoiTracker.sees(characterId, targetEntityId)) {
            return;
        }
        outboundSink.onHealthChanged(
                characterId, new WorldOutboundSink.HealthSnapshot(targetEntityId, health.current, health.max));
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
        emitCommandResults();
    }

    /**
     * Phát NGAY kết quả của đòn/skill vừa giải từ một lệnh trong hàng đợi (sửa 2026-10-04,
     * {@code WorldSimulationPropertyTest}).
     *
     * <p>{@code requestAttack}/{@code tryCast} giải đòn lúc rút lệnh, nhưng trước đây kết quả nằm chờ
     * tới sau {@code ecsWorld.update()} rồi phát theo LOẠI — mọi đòn thường trước, mọi skill sau — chứ
     * không theo thứ tự lệnh. Hai hệ quả đo được: (1) skill của A trúng con quái rồi đòn thường của B
     * hạ nó trong cùng tick ⇒ người xem nhận despawn con quái rồi mới nhận "skill trúng nó"; (2) đòn
     * xếp trước lệnh RỜI world của nạn nhân/người đánh ⇒ người xem nhận despawn rồi mới nhận đòn trúng
     * người đã rời, và người đã rời — gateway có thể đã đăng ký phiên mới ngay khi {@link #inboxBarrier}
     * xong — vẫn được gửi gói của phiên cũ. Phát ngay thì thứ tự sự kiện là thứ tự lệnh.
     */
    private void emitCommandResults() {
        handleCombatResults(combatSystem.drainResults());
        handleSkillResults(skillSystem.drainResults());
    }

    private void handleUseSkill(long characterId, String skillCode, long targetEntityId) {
        if (!entitiesByCharacterId.containsKey(characterId)) {
            return;
        }
        // KHÔNG chặn targetEntityId == characterId ở đây nữa (Phase 8) — skill SELF/ALLY (buff/
        // khiên/heal) hợp lệ khi tự nhắm chính mình; SkillSystem.tryCast tự validate đầy đủ theo
        // SkillTargetKind của TỪNG skill (kể cả chặn ENEMY tự nhắm chính mình).
        skillSystem.tryCast(characterId, skillCode, targetEntityId);
        emitCommandResults();
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

    private void handleObservers(long characterId, java.util.function.LongConsumer action) {
        if (!entitiesByCharacterId.containsKey(characterId)) {
            return; // đã ngắt kết nối giữa lúc lệnh còn trong hàng đợi — bỏ qua an toàn.
        }
        Set<Long> observers = aoiTracker.observersOf(characterId);
        observers.add(characterId);
        for (long observerId : observers) {
            action.accept(observerId);
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

    /**
     * ⚠️ Phát {@code HealthChange} khi máu THỰC SỰ đổi. Trước 2026-09-19 đường này im lặng và vạch
     * máu của client NÓI SAI: gói {@code EquipItemResponse} chỉ chở {@code max_hp}, còn
     * {@code CombatHudController.onOwnMaxHpChanged} chỉ đổi mẫu số rồi {@code min(hp, max)} — nó
     * KHÔNG dịch máu hiện tại theo độ chênh như {@link #applyMaxHpBonus} vừa làm ở server. Hệ quả
     * đo được: đang 50/100 mà mặc bộ +106 máu thì server thành 156/206 còn HUD vẽ 50/206; gỡ ra
     * thì server về 50/100 còn HUD vẽ **100/100** — người chơi tưởng mình đầy máu ngay trước trận.
     *
     * <p>Sửa ở ĐÂY chứ không ở gateway vì gateway không đọc được máu hiện tại: {@code
     * applyEquipmentBonus} chỉ xếp lệnh vào {@code inbox}, kết quả nằm sau một tick. Đường ống
     * {@code HealthChangedEvent} (ADR-046 D1) đã là nơi DUY NHẤT máu đi ra ngoài world, và client
     * đã đọc được nó từ trước — không opcode mới, không field mới.
     *
     * <p>KHÔNG phát ở {@link #handleAttach} dù nó gọi cùng {@code applyBonusTo}: lúc đó
     * {@code SelectCharacterResponse} đã chở cả máu hiện tại lẫn máu tối đa, nên thêm một gói nữa
     * là đúng thứ ADR-042 đang muốn bớt.
     */
    private void handleApplyEquipmentBonus(WorldCommand.ApplyEquipmentBonusCommand command) {
        long characterId = command.characterId();
        KEntity entity = entitiesByCharacterId.get(characterId);
        CharacterBaseStats baseStats = baseStatsByCharacterId.get(characterId);
        if (entity == null || baseStats == null) {
            return; // chưa attach xong hoặc đã detach giữa lúc lệnh còn trong hàng đợi — bỏ qua an toàn.
        }
        HealthComponent health = HEALTH.get(entity);
        int currentBefore = health.current;
        int maxBefore = health.max;
        applyBonusTo(entity, baseStats, command.bonus());
        if (health.current != currentBefore || health.max != maxBefore) {
            pendingHealthChanges.add(new HealthChange(characterId, health.current, health.max));
        }
    }

    /**
     * Xem {@link #enqueueCharacterLevel}. Chép cấp vào cả {@link #baseStatsByCharacterId} để World
     * không giữ hai bản cấp lệch nhau của cùng một nhân vật — một record mới mỗi lần LÊN CẤP, không
     * phải mỗi tick.
     */
    private void handleResetCharacterLevel(long characterId, int level) {
        KEntity entity = entitiesByCharacterId.get(characterId);
        CharacterBaseStats baseStats = baseStatsByCharacterId.get(characterId);
        if (entity == null || baseStats == null) {
            return; // không ở World này — lần attach kế đọc cấp từ DB.
        }
        LEVEL.get(entity).level = level;
        baseStatsByCharacterId.put(
                characterId,
                new CharacterBaseStats(
                        baseStats.str(),
                        baseStats.agi(),
                        baseStats.intelligence(),
                        baseStats.vit(),
                        baseStats.element(),
                        level));
    }

    private void handleRaiseCharacterLevel(long characterId, int level) {
        KEntity entity = entitiesByCharacterId.get(characterId);
        CharacterBaseStats baseStats = baseStatsByCharacterId.get(characterId);
        if (entity == null || baseStats == null) {
            return; // không ở World này (chưa attach xong / đã rời) — lần attach kế đọc cấp từ DB.
        }
        CharacterLevelComponent levelComponent = LEVEL.get(entity);
        if (level <= levelComponent.level) {
            return; // lệnh đến muộn — cấp không bao giờ lùi.
        }
        int gained = CombatConstants.levelHpGain(levelComponent.level, level);
        levelComponent.level = level;
        raiseMaxHpForLevel(characterId, entity, gained);
        baseStatsByCharacterId.put(
                characterId,
                new CharacterBaseStats(
                        baseStats.str(),
                        baseStats.agi(),
                        baseStats.intelligence(),
                        baseStats.vit(),
                        baseStats.element(),
                        level));
    }

    /**
     * Dòng 65(d): lên cấp nâng máu tối đa thêm {@code gained} — và máu HIỆN TẠI cũng thêm đúng chừng
     * đó, không theo tỉ lệ như mặc/gỡ đồ ({@link #applyMaxHpBonus}). Tỉ lệ ở đó là để gỡ-rồi-mặc không
     * thành nút hồi máu; cấp thì chỉ tăng, nên không có vòng nào để lặp, và người vừa lên cấp không bị
     * vạch máu tụt về phần trăm. Đang chết thì chỉ đổi máu tối đa — hồi sinh tự đọc nó.
     */
    private void raiseMaxHpForLevel(long characterId, KEntity entity, int gained) {
        if (gained <= 0) {
            return;
        }
        HealthComponent health = HEALTH.get(entity);
        health.max += gained;
        if (health.current > 0) {
            health.current = Math.min(health.max, health.current + gained);
        }
        pendingHealthChanges.add(new HealthChange(characterId, health.current, health.max));
    }

    /**
     * Xem {@link #enqueueCharacterElement}. Phần trang bị cộng thêm không được lưu riêng, nên đọc lại
     * nó từ chính component: thứ nằm TRÊN chỉ số derive của hệ cũ là bonus trang bị
     * ({@link #applyBonusTo} luôn ghi {@code derive + bonus}), rồi đặt lên chỉ số derive của hệ mới.
     */
    private void handleSetSkillRanks(long characterId, SkillRankSnapshot ranks) {
        KEntity entity = entitiesByCharacterId.get(characterId);
        if (entity != null) {
            copyRanks(ranks, SKILL_RANK.get(entity));
        }
    }

    private static SkillRankComponent skillRanksOf(CharacterEntry entry) {
        SkillRankComponent ranks = new SkillRankComponent();
        copyRanks(entry.skillRanks(), ranks);
        return ranks;
    }

    private static void copyRanks(SkillRankSnapshot from, SkillRankComponent to) {
        for (int slot = 0; slot < to.ranks.length; slot++) {
            to.ranks[slot] = from.rankOf(slot);
        }
    }

    private void handleChangeCharacterElement(long characterId, Element element) {
        KEntity entity = entitiesByCharacterId.get(characterId);
        CharacterBaseStats baseStats = baseStatsByCharacterId.get(characterId);
        if (entity == null || baseStats == null || baseStats.element() == element) {
            return; // không ở World này, hoặc lệnh lặp — không có gì để đổi.
        }
        CharacterBaseStats changed = new CharacterBaseStats(
                baseStats.str(),
                baseStats.agi(),
                baseStats.intelligence(),
                baseStats.vit(),
                element,
                baseStats.level());
        CombatConstants.CombatStats before = CombatConstants.deriveCombatStats(baseStats);
        CombatConstants.CombatStats after = CombatConstants.deriveCombatStats(changed);
        CombatStatsComponent combatStats = COMBAT_STATS.get(entity);
        combatStats.atk = after.atk() + (combatStats.atk - before.atk());
        combatStats.def = after.def() + (combatStats.def - before.def());
        combatStats.res = after.res() + (combatStats.res - before.res());
        combatStats.skillDmg = after.skillDmg();
        combatStats.hitPercent = after.hitPercent();
        combatStats.dodgePercent = after.dodgePercent();
        combatStats.critPercent = after.critPercent();
        combatStats.critDamageMultiplier = after.critDamageMultiplier();
        combatStats.element = after.element();
        baseStatsByCharacterId.put(characterId, changed);
    }

    /**
     * Dùng chung bởi {@link #handleAttach} (lúc vào world) và {@link #handleApplyEquipmentBonus}
     * (lúc mặc/gỡ đồ) — LUÔN tính lại từ nền, không cộng dồn (xem javadoc applyEquipmentBonus).
     */
    private void applyBonusTo(KEntity entity, CharacterBaseStats baseStats, EquipmentBonusSnapshot bonus) {
        CombatStatsComponent combatStats = COMBAT_STATS.get(entity);
        CombatConstants.CombatStats derived = CombatConstants.deriveCombatStats(baseStats);
        combatStats.atk = derived.atk() + bonus.atk();
        combatStats.def = derived.def() + bonus.def();
        combatStats.res = derived.res() + bonus.res();
        MOVE_INTENT.get(entity).speedMultiplier = bonus.moveSpeedMultiplier();
        combatStats.petStrikePermille = bonus.extras().petStrikePermille();
        combatStats.petGuardPermille = bonus.extras().petGuardPermille();
        applyMaxHpBonus(entity, bonus.hp());
    }

    /**
     * ADR-036: {@code bonus_hp} cộng vào MÁU TỐI ĐA, trên nền
     * {@link CombatConstants#CHARACTER_MAX_HP} (vẫn là placeholder — GDD chưa chốt công thức HP;
     * chủ dự án chấp nhận {@code maxHp = 100 + bonusHp} như placeholder mới khi duyệt ADR).
     *
     * <p>Máu HIỆN TẠI giữ nguyên TỈ LỆ so với máu tối đa (quyết định chủ dự án 2026-10-11, thay cách
     * "dịch theo độ chênh" của ADR-043). Cách cũ biến việc gỡ rồi mặc lại đồ +HP thành nút hồi máu
     * miễn phí: 5/118 gỡ áo +18 bị chặn dưới thành 1/100, mặc lại là 19/118. Nay 5/118 thành 4/100 rồi
     * về lại 4/118.
     *
     * <p>Làm tròn XUỐNG ở cả hai chiều, có chủ đích: bật/tắt đồ bao nhiêu lần máu cũng không bao giờ
     * tăng (làm tròn lên hay làm tròn gần nhất thì mỗi lượt có thể lời một điểm). Người còn sống không
     * bị làm tròn về 0 — gỡ đồ không được giết người chơi. Đang đầy máu thì vẫn đầy. Nhân vật đang chết
     * ({@code current <= 0}) thì KHÔNG đụng vào máu hiện tại — {@code respawnCharacter} mới là chỗ hồi
     * sinh, và nó tự đọc {@code health.max} mới.
     */
    private void applyMaxHpBonus(KEntity entity, int bonusHp) {
        HealthComponent health = HEALTH.get(entity);
        int oldMax = health.max;
        int newMax = CombatConstants.characterMaxHp(LEVEL.get(entity).level, bonusHp);
        health.max = newMax;
        if (newMax != oldMax && health.current > 0) {
            long scaled = oldMax > 0 ? (long) health.current * newMax / oldMax : newMax;
            health.current = Math.clamp(scaled, 1, newMax);
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

            worldBossLedger.record(
                    outcome.targetEntityId(), outcome.attackerEntityId(), outcome.damage(), outcome.targetCurrentHp());
            Set<Long> observers = aoiTracker.observersOf(outcome.targetEntityId());
            addIfCharacter(observers, outcome.attackerEntityId()); // người đánh luôn thấy đòn của chính mình.
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
                respawnOrHaltCorpse(outcome.targetEntityId());
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

    /**
     * ADR-050 D4 — mã những vùng khai {@code heals}. Tách khỏi constructor vì nó là phép LỌC, không
     * phải phép nối dây; và constructor đã chạm trần 50 dòng của Checkstyle.
     *
     * <p>Vòng lặp thường thay vì stream: chạy đúng MỘT lần lúc dựng world, nên không có gì để tối ưu,
     * mà đọc ra thì thẳng hơn.
     */
    private static Set<String> healingZoneCodesOf(SafeZoneCatalogSnapshot safeZones) {
        List<String> codes = new ArrayList<String>();
        for (SafeZoneCatalogSnapshot.SafeZoneDefinition zone : safeZones.zones()) {
            if (zone.heals()) {
                codes.add(zone.code());
            }
        }
        return Set.copyOf(codes);
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
        if (!healingZoneCodes.contains(zoneId)) {
            return; // ADR-050 D4 — trạm tiếp ứng CHE CHỞ chứ không hồi máu; nghỉ vẫn tốn thời gian.
        }
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
        // Cộng kiểu long (sửa 2026-10-04, WorldSimulationPropertyTest): `current + amount` kiểu int tràn
        // thành ÂM với lượng hồi lớn, và min(max, âm) giữ nguyên số âm — cùng cách SkillSystem.applyHeal.
        health.current = (int) Math.min(health.max, (long) health.current + amount);
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

    /** ADR-073 — chỉ người dính hiệu ứng nhận, để client dự đoán di chuyển đúng. */
    private void announceMovementModifiers(List<CharacterLocomotionSystem.MovementModifierChange> changes) {
        for (CharacterLocomotionSystem.MovementModifierChange change : changes) {
            outboundSink.onMovementModifierChanged(
                    change.characterId(), change.statusSpeedMultiplier(), change.immobilized());
        }
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

            worldBossLedger.record(
                    outcome.targetEntityId(), outcome.attackerEntityId(), outcome.damage(), outcome.targetCurrentHp());
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
                respawnOrHaltCorpse(outcome.targetEntityId());
            }
        }
    }

    /**
     * Thêm {@code entityId} vào tập người nhận CHỈ khi nó là nhân vật (dương) — người nhận của
     * {@link WorldOutboundSink} là client, mà quái thì không có client.
     *
     * <p>Sửa 2026-10-04 ({@code WorldSimulationPropertyTest}): trước đó người ĐÁNH được thêm vô điều
     * kiện, nên mỗi đòn của quái/boss phát thêm một sự kiện cho "người nhận" {@code -1}, {@code -2}...
     * Gateway tự bỏ qua id lạ nên không client nào thấy sai, nhưng mỗi đòn quái tốn thêm một lần tra
     * kênh vô ích, và hợp đồng sink ({@code targetCharacterId}) bị vi phạm đúng kiểu mà nhánh
     * {@code targetEntityId > 0} ngay dưới đã cố tránh.
     */
    private static void addIfCharacter(Set<Long> observers, long entityId) {
        if (entityId > 0) {
            observers.add(entityId);
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
     * World chính hồi sinh ngay; World không tự hồi sinh (hầm ngục/Arena) thì cái xác phải ĐỨNG YÊN.
     *
     * <p>Sửa 2026-10-02: vận tốc ngang chỉ được xoá khi có {@code MoveCommand} kế tiếp (nhánh "đã
     * chết" của {@link #handleMoveIntent}), nên một client sửa đổi chết giữa lúc đang chạy rồi KHÔNG
     * gửi lệnh nào nữa thì cái xác 0 máu cứ trôi đi mãi theo hướng cũ — server tự chạy vị trí đó.
     */
    private void respawnOrHaltCorpse(long characterId) {
        if (spawnOverworldContent) {
            respawnCharacter(characterId);
            return;
        }
        KEntity entity = entitiesByCharacterId.get(characterId);
        if (entity != null) {
            VelocityComponent velocity = VELOCITY.get(entity);
            velocity.x = 0f;
            velocity.y = 0f;
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

        // Hồi sinh sạch hiệu ứng (sửa 2026-10-02) — trước đó stun/slow/khiên/buff cũ đi theo sang
        // mạng mới, hồi sinh xong vẫn bị choáng hoặc chạy chậm.
        Arrays.fill(STATUS_EFFECT.get(entity).slots, null);

        TARGET.get(entity).targetEntityId = 0L;

        // Người ĐANG NHÌN nhân vật này cũng phải biết họ đã sống lại (quyết định chủ dự án 2026-10-11).
        // onCharacterRespawned bên dưới chỉ tới chính người hồi sinh; người quan sát trước đó chỉ suy ra
        // được từ gói di chuyển đầu tiên, nên người gục NGAY TẠI điểm hồi sinh (không gói di chuyển nào)
        // nằm tư thế chết mãi trên máy họ. Máu đầy đi qua đường ống HealthChangedEvent sẵn có (ADR-046
        // D1 — phát cho mọi người đang thấy entity): không opcode mới, không field mới.
        pendingHealthChanges.add(new HealthChange(characterId, health.current, health.max));

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
            // Người đánh ở đây luôn là BOSS (id âm) — không phải client nào, không thêm (xem addIfCharacter).
            // Chỉ target dương (nhân vật) — xem giải thích đầy đủ ở handleCombatResults.
            if (outcome.targetEntityId() > 0) {
                observers.add(outcome.targetEntityId());
            }
            for (long observerId : observers) {
                outboundSink.onSkillResult(observerId, sinkOutcome);
            }
            // targetEntityId của chiêu boss luôn DƯƠNG (nhân vật) — không nhánh handleMonsterDeath.
            // Sửa 2026-10-02: trước đây nhánh "nhân vật chết" cũng vắng mặt, nên nhân vật bị CHIÊU
            // boss hạ gục không bao giờ bắn onCharacterDefeated ⇒ hầm ngục không đưa họ về world
            // chính (ADR-051 D5), họ nằm 0 máu tới khi tự rời. Cùng xử lý như handleSkillResults.
            if (outcome.targetDied() && outcome.targetEntityId() > 0) {
                outboundSink.onCharacterDefeated(outcome.targetEntityId(), outcome.attackerEntityId());
                clearAttackerTarget(outcome.attackerEntityId());
                respawnOrHaltCorpse(outcome.targetEntityId());
            }
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
        worldBossLedger
                .close(monsterId)
                .ifPresent(defeat ->
                        worldBossListener.onWorldBossDefeated(defeat.monsterCode(), defeat.damageByCharacter()));
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
        addBossAiIfBoss(entity, loai);

        entitiesByMonsterId.put(monsterId, entity);
        aoiDirty = true; // thực thể mới — xem syncAoiIfDirty
        spawnIndexByMonsterId.put(monsterId, spawnIndex);
        // AoI sync() ở CHÍNH tick này tự phát hiện nhân vật đã ở gần + gửi MonsterSpawnedEvent —
        // không cần thông báo thủ công (giống hệt attach nhân vật, xem AreaOfInterestTracker).
    }

    /**
     * ADR-048 — loài khai {@code is_boss} thì có AI kịch bản, kể cả khi nó đứng ở một điểm
     * {@code monster_spawn} bình thường của world chính. Đây là chỗ boss world chính ra đời mà
     * không cần một đường spawn riêng nào.
     *
     * <p>Tách khỏi {@link #spawnMonster(int)} để giữ hàm đó dưới trần Checkstyle MethodLength —
     * cùng lý do {@code BossAiSystem.applyDecision} đã tách khỏi {@code processEntity}.
     */
    private void addBossAiIfBoss(KEntity entity, MonsterCatalogSnapshot.MonsterSpecies loai) {
        if (loai.isBoss()) {
            entity.add(newBossAi(loai.boss()));
        }
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
                ai.idleDurationSeconds(),
                ai.retaliates());
    }

    /** Chép hành vi boss từ catalog vào component — {@code BossAiSystem} chỉ đọc từ đây (ADR-048 D3). */
    private static BossAiComponent newBossAi(MonsterCatalogSnapshot.MonsterBossNumbers boss) {
        BossAiComponent component = new BossAiComponent();
        component.scriptRef = boss.scriptRef();
        component.decisionIntervalSeconds = boss.decisionIntervalSeconds();
        component.skillCooldownSeconds = boss.skillCooldownSeconds();
        return component;
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

    /**
     * Spawn boss theo MÃ LOÀI, tại chỗ cố định của instance (ADR-048 D6).
     *
     * <p>Dùng cho hầm ngục — nơi KHÔNG có {@code monster_spawn} nào. Boss của world chính thì đi
     * đường bình thường qua {@link #spawnMonster}, và {@link BossAiComponent} được gắn ở đó
     * theo {@code species.isBoss()}; đây không phải hai cơ chế, chỉ là hai nguồn toạ độ.
     *
     * <p>⚠️ Mọi chỉ số nay đọc từ catalog. World nhận catalog RỖNG (hầm ngục trước ADR-048) sẽ
     * không tra được mã ⇒ không spawn gì, và log nói rõ lý do thay vì im lặng.
     */
    private void spawnBossEntity(WorldCommand.SpawnBossCommand command) {
        String bossCode = command.bossCode();
        MonsterCatalogSnapshot.MonsterSpecies loai =
                monsterCatalog.find(bossCode).orElse(null);
        if (bossSpawnRefused(bossCode, loai)) {
            return;
        }
        KEntity entity = ecsWorld.createEntity();
        long monsterId = nextMonsterOrNpcId--;

        PositionComponent position = new PositionComponent();
        position.x = command.x();
        position.y = command.y();
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

        entity.add(newAiState(command.x(), command.y()));
        entity.add(toAiParams(loai.ai()));
        entity.add(new TargetComponent()); // AiSystem ghi khi Chase/Attack; CombatSystem đọc để đánh trả
        entity.add(new AttackCooldownComponent());
        entity.add(new StatusEffectComponent());
        entity.add(newBossAi(loai.boss()));

        entitiesByMonsterId.put(monsterId, entity);
        if (command.worldBoss()) {
            worldBossLedger.track(monsterId, bossCode, loai.maxHp());
        }
        aoiDirty = true; // thực thể mới — xem syncAoiIfDirty
        // AoI sync() ở CHÍNH tick này tự phát hiện nhân vật đã ở gần + gửi MonsterSpawnedEvent —
        // không cần thông báo thủ công (giống hệt spawnMonster).
    }

    /** Lệnh spawn boss phải bỏ không — mã sai/không phải boss, hoặc đã có một con sống (dòng 67(e)). */
    private boolean bossSpawnRefused(String bossCode, MonsterCatalogSnapshot.MonsterSpecies loai) {
        if (loai == null || !loai.isBoss()) {
            LOG.error(
                    "Không spawn được boss '{}': {} — kiểm monster_template.is_boss/ai_script_ref và"
                            + " catalog truyền vào World này",
                    bossCode,
                    loai == null ? "không có trong catalog" : "loài này không phải boss");
            return true;
        }
        if (isBossAlive(bossCode)) {
            // Trần một con mỗi mã mỗi World (dòng 67(e), chủ dự án duyệt 2026-10-17) — bản chạy dài từng
            // thấy 6 boss dồn một chỗ ở cùng điểm spawn, cộng dồn sát thương lên người vào sau.
            LOG.warn("Bỏ lệnh spawn boss '{}': World này đã có một con còn sống", bossCode);
            return true;
        }
        return false;
    }

    /** World này đang có một boss mã {@code bossCode} chưa chết không — xem {@link #spawnBossEntity}. */
    private boolean isBossAlive(String bossCode) {
        for (KEntity monster : entitiesByMonsterId.values()) {
            if (BOSS_AI.has(monster)
                    && bossCode.equals(MONSTER.get(monster).monsterCode)
                    && !HEALTH.get(monster).isDead()) {
                return true;
            }
        }
        return false;
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
        aoiDirty = true; // thực thể mới — xem syncAoiIfDirty
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

    /**
     * Server SỞ HỮU tốc độ — client chỉ gửi hướng, clamp về unit vector chặn speed-hack.
     *
     * <p>Hướng không hữu hạn ({@code NaN}/{@code Infinity}) ⇒ đứng yên (sửa 2026-09-24): bản cũ so
     * {@code lengthSquared > 1f}, mà {@code NaN > 1f} là {@code false}, nên {@code NaN} đi thẳng vào
     * vận tốc rồi vào vị trí — một gói là nhân vật mang toạ độ {@code NaN} vĩnh viễn. Độ dài tính
     * bằng {@link Math#hypot} (double) thay vì bình phương float: {@code 1e30f} bình phương tràn thành
     * {@code Infinity} và bản cũ biến một hướng "sang phải" hợp lệ thành đứng yên.
     */
    private static float[] clampDirection(float dirX, float dirY) {
        if (!Float.isFinite(dirX) || !Float.isFinite(dirY)) {
            return new float[] {0f, 0f};
        }
        double length = Math.hypot(dirX, dirY);
        if (length > 1.0) {
            return new float[] {(float) (dirX / length), (float) (dirY / length)};
        }
        return new float[] {dirX, dirY};
    }
}
