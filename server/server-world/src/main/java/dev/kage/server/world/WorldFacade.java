package dev.kage.server.world;

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
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.engine.ecs.system.MovementSystem;
import dev.kage.engine.ecs.system.TerrainCollisionSystem;
import dev.kage.server.world.component.AiStateComponent;
import dev.kage.server.world.component.AttackCooldownComponent;
import dev.kage.server.world.component.BossAiComponent;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.CharacterLevelComponent;
import dev.kage.server.world.component.ChiComponent;
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

    // Quái bù nhìn tập luyện — lát cắt 1 Phase 4, hardcode 1 quái, chưa có monster_template DB.
    private static final String TRAINING_DUMMY_CODE = "training_dummy";
    private static final float TRAINING_DUMMY_SPAWN_X = 5f;
    private static final float TRAINING_DUMMY_SPAWN_Y = 5f;

    // NPC tĩnh mẫu — lát cắt 1 Phase 6, hardcode 1 NPC, chưa có npc_template DB (cùng lý do
    // TRAINING_DUMMY chưa đọc monster_template). Vị trí khác quái để dễ phân biệt lúc test bằng tay.
    private static final String SAMPLE_NPC_CODE = "town_elder";
    private static final float SAMPLE_NPC_SPAWN_X = -5f;
    private static final float SAMPLE_NPC_SPAWN_Y = -5f;

    // Boss mẫu (Phase 8 lát cắt 2) — spawn trong tầm AiConstants.AGGRO_RADIUS_METERS (6m) tính từ
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
    private float monsterRespawnCountdownSeconds = -1f; // < 0 = quái đang sống, không đếm ngược.

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
        this.outboundSink = outboundSink;
        this.spawnOverworldContent = spawnOverworldContent;
        this.tickMetricsPort = tickMetricsPort;
        this.systemTimingListener = tickMetricsPort::recordSystemDuration;
        this.aoiTracker = new AreaOfInterestTracker(outboundSink);
        this.combatSystem = new CombatSystem(this::lookupEntity);
        this.skillSystem = new SkillSystem(this::lookupEntity);
        this.bossAiSystem = new BossAiSystem(this::lookupEntity);
        // Thứ tự ĐÚNG ECS.md §6 (InputCommandSystem -> AISystem -> MovementSystem -> ... ->
        // CombatSystem): AI set vận tốc/mục tiêu trước, Movement tích luỹ vị trí, Combat mới xét
        // tầm đánh — đổi thứ tự = đổi behavior (engine/CLAUDE.md). SkillSystem/BossAiSystem chỉ
        // đếm ngược cooldown mỗi tick (hành động thật là lệnh rời rạc/điều kiện nội bộ, không phải
        // hành vi lặp mỗi tick như đánh thường) — thứ tự với CombatSystem không quan trọng.
        ecsWorld.addSystem(new AiSystem(entitiesByCharacterId::values, this::lookupEntity));
        ecsWorld.addSystem(new MovementSystem());
        // Obstacle mặc định RỖNG — chưa có map/asset thật (ADR-021). No-op an toàn cho tới khi có
        // nguồn map thật gọi setObstacles(...); giữ đúng thứ tự PHYSICS.md §4 (sau MovementSystem).
        ecsWorld.addSystem(new TerrainCollisionSystem());
        ecsWorld.addSystem(combatSystem);
        ecsWorld.addSystem(skillSystem);
        ecsWorld.addSystem(bossAiSystem);
        // Đếm ngược buff/debuff (Phase 8, 4 hệ phái) — không phụ thuộc thứ tự với Combat/Skill vì
        // chỉ mutate remainingSeconds/magnitude tại chỗ, các System khác ĐỌC StatusEffectComponent
        // lúc resolve riêng (StatusEffects helper), không qua processEntity của System này.
        ecsWorld.addSystem(new StatusEffectSystem());
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

    /** GDD §6 "Auto-target đánh thường" — server tự đánh {@code targetEntityId} khi hết cooldown. */
    public void setTarget(long characterId, long targetEntityId) {
        inbox.add(new WorldCommand.SetTargetCommand(characterId, targetEntityId));
    }

    /** GAMEPLAY.md §3-4 — dùng skill nhắm mục tiêu, server tự kiểm tra mọi điều kiện ({@code SkillSystem}). */
    public void useSkill(long characterId, String skillCode, long targetEntityId) {
        inbox.add(new WorldCommand.UseSkillCommand(characterId, skillCode, targetEntityId));
    }

    /**
     * GAMEPLAY.md §2/§7 "tính lại chỉ số dẫn xuất khi đổi trang bị" — gọi lại MỖI lần trang bị đổi
     * (mặc/gỡ đồ) VÀ ngay sau khi attach (lúc chọn nhân vật, cùng lúc chỉ số nền được gửi vào —
     * {@code GatewayChannelHandler} chịu trách nhiệm gọi đúng lúc, server-world không tự biết
     * feature-inventory). {@code bonusAtk} là TỔNG cộng dồn hiện tại từ MỌI item đang mặc (không
     * phải delta) — feature-inventory tính lại từ đầu mỗi lần, tránh trôi số nếu gọi sai thứ tự.
     */
    public void applyEquipmentBonus(long characterId, int bonusAtk) {
        inbox.add(new WorldCommand.ApplyEquipmentBonusCommand(characterId, bonusAtk));
    }

    /** GAMEPLAY.md §10 — chat kênh Khu vực, cần {@code aoiTracker} nên phải qua tick thread. */
    public void enqueueAreaChatMessage(long characterId, String senderName, String body) {
        inbox.add(new WorldCommand.AreaChatMessageCommand(characterId, senderName, body));
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
    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    private void tickTask() {
        if (!running) {
            return; // cancel(false) không huỷ lần đã dispatch dở — bỏ qua an toàn nếu lỡ chạy tới đây.
        }
        tickLock.lock();
        try {
            if (!contentSpawned) {
                contentSpawned = true;
                if (spawnOverworldContent) {
                    spawnTrainingDummy();
                    spawnNpc(); // NPC tĩnh, không chết/hồi sinh — spawn 1 lần duy nhất, khác quái.
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
                case WorldCommand.SetTargetCommand c -> handleSetTarget(c.characterId(), c.targetEntityId());
                case WorldCommand.UseSkillCommand c ->
                    handleUseSkill(c.characterId(), c.skillCode(), c.targetEntityId());
                case WorldCommand.ApplyEquipmentBonusCommand c ->
                    handleApplyEquipmentBonus(c.characterId(), c.bonusAtk());
                case WorldCommand.AreaChatMessageCommand c ->
                    handleAreaChatMessage(c.characterId(), c.senderName(), c.body());
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
        position.y = spawnY;
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
            float[] direction = clampDirection(intent.dirX(), intent.dirY());
            float speed = WorldConstants.MOVE_SPEED_MPS * StatusEffects.moveSpeedMultiplier(STATUS_EFFECT.get(entity));
            velocity.x = direction[0] * speed;
            velocity.y = direction[1] * speed;
        }

        PositionComponent position = POSITION.get(entity);
        outboundSink.onMoveAccepted(intent.characterId(), intent.sequenceId(), position.x, position.y);
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

    private void handleApplyEquipmentBonus(long characterId, int bonusAtk) {
        KEntity entity = entitiesByCharacterId.get(characterId);
        CharacterBaseStats baseStats = baseStatsByCharacterId.get(characterId);
        if (entity == null || baseStats == null) {
            return; // chưa attach xong hoặc đã detach giữa lúc lệnh còn trong hàng đợi — bỏ qua an toàn.
        }
        CombatStatsComponent combatStats = COMBAT_STATS.get(entity);
        CombatConstants.CombatStats derived = CombatConstants.deriveCombatStats(baseStats);
        combatStats.atk = derived.atk() + bonusAtk; // LUÔN tính lại từ nền, không cộng dồn (xem javadoc)
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
                // Phase 8 lát cắt 4 (Arena) — chỉ có ý nghĩa thật trong duel instance, xem javadoc
                // WorldOutboundSink#onCharacterDefeated. CombatSystem tự chặn đòn lặp lại (target
                // đã chết → attacker tự bỏ chọn target ở lần xử lý kế), nên chỉ bắn đúng 1 lần.
                outboundSink.onCharacterDefeated(outcome.targetEntityId(), outcome.attackerEntityId());
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
            }
        }
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
        String monsterCode = MONSTER.get(entity).monsterCode; // đọc TRƯỚC removeEntity — sau đó component mất.
        aoiTracker.onMonsterDetach(monsterId);
        ecsWorld.removeEntity(entity);
        // Chỉ world chính hồi sinh quái bù nhìn — dungeon (spawnOverworldContent=false) không có
        // khái niệm training dummy; PHÁT HIỆN THẬT (Phase 8 lát cắt 2): thiếu guard này, boss chết
        // trong dungeon sẽ vô tình lên lịch spawnTrainingDummy() (sai nội dung, sai world).
        if (spawnOverworldContent) {
            monsterRespawnCountdownSeconds = CombatConstants.MONSTER_RESPAWN_SECONDS;
        }

        // killerEntityId ÂM nghĩa là quái khác hạ gục quái này — chưa từng xảy ra (quái không nhắm
        // quái, xem AiSystem) nhưng chặn phòng thủ, rơi đồ chỉ dành cho NGƯỜI CHƠI.
        if (killerEntityId > 0) {
            outboundSink.onMonsterKilled(killerEntityId, monsterCode);
        }
    }

    private void tickMonsterRespawn(float deltaTime) {
        if (monsterRespawnCountdownSeconds < 0f) {
            return; // quái đang sống — không đếm ngược.
        }
        monsterRespawnCountdownSeconds -= deltaTime;
        if (monsterRespawnCountdownSeconds <= 0f) {
            monsterRespawnCountdownSeconds = -1f;
            spawnTrainingDummy();
        }
    }

    private void spawnTrainingDummy() {
        KEntity entity = ecsWorld.createEntity();
        long monsterId = nextMonsterOrNpcId--;

        PositionComponent position = new PositionComponent();
        position.x = TRAINING_DUMMY_SPAWN_X;
        position.y = TRAINING_DUMMY_SPAWN_Y;
        position.prevX = position.x;
        position.prevY = position.y;
        entity.add(position);
        entity.add(new VelocityComponent()); // AiSystem set vận tốc theo FSM (AI.md §3)

        HealthComponent health = new HealthComponent();
        health.current = CombatConstants.MONSTER_MAX_HP;
        health.max = CombatConstants.MONSTER_MAX_HP;
        entity.add(health);

        MonsterComponent monster = new MonsterComponent();
        monster.monsterCode = TRAINING_DUMMY_CODE;
        entity.add(monster);
        entity.add(toComponent(CombatConstants.MONSTER_STATS));

        MonsterIdComponent monsterIdComponent = new MonsterIdComponent();
        monsterIdComponent.monsterId = monsterId;
        entity.add(monsterIdComponent);

        AiStateComponent aiState = new AiStateComponent();
        aiState.spawnX = TRAINING_DUMMY_SPAWN_X;
        aiState.spawnY = TRAINING_DUMMY_SPAWN_Y;
        entity.add(aiState);
        entity.add(new TargetComponent()); // AiSystem ghi khi Chase/Attack; CombatSystem đọc để đánh trả
        entity.add(new AttackCooldownComponent());
        entity.add(new StatusEffectComponent());

        entitiesByMonsterId.put(monsterId, entity);
        // AoI sync() ở CHÍNH tick này tự phát hiện nhân vật đã ở gần + gửi MonsterSpawnedEvent —
        // không cần thông báo thủ công (giống hệt attach nhân vật, xem AreaOfInterestTracker).
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
        entity.add(monster);
        entity.add(toComponent(CombatConstants.BOSS_STATS));

        MonsterIdComponent monsterIdComponent = new MonsterIdComponent();
        monsterIdComponent.monsterId = monsterId;
        entity.add(monsterIdComponent);

        AiStateComponent aiState = new AiStateComponent();
        aiState.spawnX = BOSS_SPAWN_X;
        aiState.spawnY = BOSS_SPAWN_Y;
        entity.add(aiState);
        entity.add(new TargetComponent()); // AiSystem ghi khi Chase/Attack; CombatSystem đọc để đánh trả
        entity.add(new AttackCooldownComponent());
        entity.add(new StatusEffectComponent());
        entity.add(new BossAiComponent()); // BossAiSystem đọc/ghi — cooldown chiêu đặc biệt + cờ enrage

        entitiesByMonsterId.put(monsterId, entity);
        // AoI sync() ở CHÍNH tick này tự phát hiện nhân vật đã ở gần + gửi MonsterSpawnedEvent —
        // không cần thông báo thủ công (giống hệt spawnTrainingDummy).
    }

    private void spawnNpc() {
        KEntity entity = ecsWorld.createEntity();
        long npcId = nextMonsterOrNpcId--;

        PositionComponent position = new PositionComponent();
        position.x = SAMPLE_NPC_SPAWN_X;
        position.y = SAMPLE_NPC_SPAWN_Y;
        position.prevX = position.x;
        position.prevY = position.y;
        entity.add(position);
        // KHÔNG VelocityComponent/HealthComponent/AiStateComponent — NPC tĩnh, không di chuyển,
        // không combat (khác spawnTrainingDummy).

        NpcComponent npc = new NpcComponent();
        npc.npcCode = SAMPLE_NPC_CODE;
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
