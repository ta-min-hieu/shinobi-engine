package dev.kage.server.world;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.engine.ecs.system.MovementSystem;
import dev.kage.server.world.component.AiStateComponent;
import dev.kage.server.world.component.AttackCooldownComponent;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.ChiComponent;
import dev.kage.server.world.component.CombatStatsComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MonsterComponent;
import dev.kage.server.world.component.MonsterIdComponent;
import dev.kage.server.world.component.NpcComponent;
import dev.kage.server.world.component.NpcIdComponent;
import dev.kage.server.world.component.SkillCooldownComponent;
import dev.kage.server.world.component.TargetComponent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final ComponentMapper<PositionComponent> POSITION =
            ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<VelocityComponent> VELOCITY =
            ComponentMapper.getFor(VelocityComponent.class);
    private static final ComponentMapper<TargetComponent> TARGET = ComponentMapper.getFor(TargetComponent.class);
    private static final ComponentMapper<MonsterComponent> MONSTER = ComponentMapper.getFor(MonsterComponent.class);
    private static final ComponentMapper<CombatStatsComponent> COMBAT_STATS =
            ComponentMapper.getFor(CombatStatsComponent.class);

    // Quái bù nhìn tập luyện — lát cắt 1 Phase 4, hardcode 1 quái, chưa có monster_template DB.
    private static final String TRAINING_DUMMY_CODE = "training_dummy";
    private static final float TRAINING_DUMMY_SPAWN_X = 5f;
    private static final float TRAINING_DUMMY_SPAWN_Y = 5f;

    // NPC tĩnh mẫu — lát cắt 1 Phase 6, hardcode 1 NPC, chưa có npc_template DB (cùng lý do
    // TRAINING_DUMMY chưa đọc monster_template). Vị trí khác quái để dễ phân biệt lúc test bằng tay.
    private static final String SAMPLE_NPC_CODE = "town_elder";
    private static final float SAMPLE_NPC_SPAWN_X = -5f;
    private static final float SAMPLE_NPC_SPAWN_Y = -5f;

    private final WorldOutboundSink outboundSink;
    private final Queue<WorldCommand> inbox = new ConcurrentLinkedQueue<>();
    private final EcsWorld ecsWorld = new EcsWorld();
    private final AreaOfInterestTracker aoiTracker;
    private final CombatSystem combatSystem;
    private final SkillSystem skillSystem;

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

    private volatile boolean running;
    private Thread tickThread;
    private final boolean spawnOverworldContent;

    /**
     * Toàn bộ resource script skill (hardcode, chưa DB-driven — xem {@link SkillConstants}) —
     * dùng cho validate script lúc khởi động (SCRIPTING.md §8). {@code static} vì đây là config
     * cố định, không phụ thuộc instance {@link WorldFacade} nào.
     */
    public static List<String> listSkillScriptResources() {
        return List.of(SkillConstants.FIREBALL_SCRIPT_RESOURCE);
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
        this.outboundSink = outboundSink;
        this.spawnOverworldContent = spawnOverworldContent;
        this.aoiTracker = new AreaOfInterestTracker(outboundSink);
        this.combatSystem = new CombatSystem(this::lookupEntity);
        this.skillSystem = new SkillSystem(this::lookupEntity);
        // Thứ tự ĐÚNG ECS.md §6 (InputCommandSystem -> AISystem -> MovementSystem -> ... ->
        // CombatSystem): AI set vận tốc/mục tiêu trước, Movement tích luỹ vị trí, Combat mới xét
        // tầm đánh — đổi thứ tự = đổi behavior (engine/CLAUDE.md). SkillSystem chỉ đếm ngược
        // cooldown mỗi tick (tryCast là lệnh rời rạc, gọi trực tiếp từ drainInbox) — thứ tự với
        // CombatSystem không quan trọng.
        ecsWorld.addSystem(new AiSystem(entitiesByCharacterId::values, this::lookupEntity));
        ecsWorld.addSystem(new MovementSystem());
        ecsWorld.addSystem(combatSystem);
        ecsWorld.addSystem(skillSystem);
    }

    /** Bắt đầu tick loop trên thread riêng — gọi 1 lần lúc khởi động server. */
    public void start() {
        running = true;
        tickThread = new Thread(this::tickLoop, "world-tick");
        tickThread.start();
    }

    /** Dừng tick loop (SERVER.md §10 — graceful shutdown, không {@code kill -9}). */
    public void stop() {
        running = false;
        if (tickThread != null) {
            tickThread.interrupt();
            try {
                tickThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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

    private void tickLoop() {
        if (spawnOverworldContent) {
            spawnTrainingDummy();
            spawnNpc(); // NPC tĩnh, không chết/hồi sinh — spawn 1 lần duy nhất, khác quái.
        }
        long fixedDtNanos = (long) (WorldConstants.FIXED_DT * 1_000_000_000L);
        while (running) {
            long start = System.nanoTime();

            tickOnce();

            long elapsed = System.nanoTime() - start;
            long sleepNanos = fixedDtNanos - elapsed;
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            } else {
                LOG.warn("World tick quá hạn: {}ms", elapsed / 1_000_000);
            }
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
            ecsWorld.update(WorldConstants.FIXED_DT);
            handleCombatResults(combatSystem.drainResults());
            handleSkillResults(skillSystem.drainResults());
            tickMonsterRespawn(WorldConstants.FIXED_DT);
            aoiTracker.sync(entitiesByCharacterId, entitiesByMonsterId, entitiesByNpcId);
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
            }
        }
    }

    private void handleAttach(long characterId, float spawnX, float spawnY, CharacterBaseStats baseStats) {
        if (entitiesByCharacterId.containsKey(characterId)) {
            return; // đã attach — bỏ qua an toàn thay vì tạo entity trùng.
        }
        var entity = ecsWorld.createEntity();

        var position = new PositionComponent();
        position.x = spawnX;
        position.y = spawnY;
        position.prevX = position.x;
        position.prevY = position.y;
        entity.add(position);
        entity.add(new VelocityComponent());

        var characterIdComponent = new CharacterIdComponent();
        characterIdComponent.characterId = characterId;
        entity.add(characterIdComponent);

        var health = new HealthComponent();
        health.current = CombatConstants.CHARACTER_MAX_HP; // placeholder — GDD chưa chốt công thức HP tối đa
        health.max = CombatConstants.CHARACTER_MAX_HP;
        entity.add(health);
        entity.add(toComponent(CombatConstants.deriveCombatStats(baseStats)));
        entity.add(new TargetComponent());
        entity.add(new AttackCooldownComponent());
        entity.add(new SkillCooldownComponent());

        var chi = new ChiComponent();
        chi.current = SkillConstants.CHARACTER_MAX_CHI; // placeholder — chưa nối character_stat.current_chi DB
        chi.max = SkillConstants.CHARACTER_MAX_CHI;
        entity.add(chi);

        entitiesByCharacterId.put(characterId, entity);
        baseStatsByCharacterId.put(characterId, baseStats);
        aoiTracker.onAttach(characterId);
    }

    private void handleDetach(long characterId) {
        var entity = entitiesByCharacterId.remove(characterId);
        baseStatsByCharacterId.remove(characterId);
        if (entity == null) {
            return;
        }
        aoiTracker.onDetach(characterId);
        ecsWorld.removeEntity(entity);
    }

    private void handleMoveIntent(WorldCommand.MoveIntentCommand intent) {
        var entity = entitiesByCharacterId.get(intent.characterId());
        if (entity == null) {
            // Race hiếm: MoveCommand tới trước khi AttachCharacterCommand cùng batch được xử lý
            // xong — bỏ qua an toàn, client sẽ gửi lại lệnh tiếp theo khi hướng đổi lần sau.
            return;
        }

        float[] direction = clampDirection(intent.dirX(), intent.dirY());
        var velocity = VELOCITY.get(entity);
        velocity.x = direction[0] * WorldConstants.MOVE_SPEED_MPS;
        velocity.y = direction[1] * WorldConstants.MOVE_SPEED_MPS;

        var position = POSITION.get(entity);
        outboundSink.onMoveAccepted(intent.characterId(), intent.sequenceId(), position.x, position.y);
    }

    private void handleSetTarget(long characterId, long targetEntityId) {
        var entity = entitiesByCharacterId.get(characterId);
        if (entity == null) {
            return;
        }
        if (targetEntityId == characterId || lookupEntity(targetEntityId) == null) {
            return; // không tự nhắm chính mình; mục tiêu không tồn tại — bỏ qua an toàn.
        }
        TARGET.get(entity).targetEntityId = targetEntityId;
    }

    private void handleUseSkill(long characterId, String skillCode, long targetEntityId) {
        var caster = entitiesByCharacterId.get(characterId);
        if (caster == null) {
            return;
        }
        if (targetEntityId == characterId || lookupEntity(targetEntityId) == null) {
            return; // không tự nhắm chính mình; mục tiêu không tồn tại — bỏ qua an toàn (giống handleSetTarget).
        }
        skillSystem.tryCast(characterId, skillCode, targetEntityId);
    }

    private void handleAreaChatMessage(long characterId, String senderName, String body) {
        if (!entitiesByCharacterId.containsKey(characterId)) {
            return; // đã ngắt kết nối giữa lúc lệnh còn trong hàng đợi — bỏ qua an toàn.
        }
        var observers = aoiTracker.observersOf(characterId);
        observers.add(characterId); // người gửi luôn thấy tin nhắn của chính mình.
        for (long observerId : observers) {
            outboundSink.onAreaChatMessage(observerId, characterId, senderName, body);
        }
    }

    private void handleApplyEquipmentBonus(long characterId, int bonusAtk) {
        var entity = entitiesByCharacterId.get(characterId);
        var baseStats = baseStatsByCharacterId.get(characterId);
        if (entity == null || baseStats == null) {
            return; // chưa attach xong hoặc đã detach giữa lúc lệnh còn trong hàng đợi — bỏ qua an toàn.
        }
        var combatStats = COMBAT_STATS.get(entity);
        var derived = CombatConstants.deriveCombatStats(baseStats);
        combatStats.atk = derived.atk() + bonusAtk; // LUÔN tính lại từ nền, không cộng dồn (xem javadoc)
    }

    private void handleCombatResults(List<CombatSystem.AttackOutcome> results) {
        for (var outcome : results) {
            var sinkOutcome = new WorldOutboundSink.AttackOutcome(
                    outcome.attackerEntityId(),
                    outcome.targetEntityId(),
                    outcome.damage(),
                    outcome.crit(),
                    outcome.miss(),
                    outcome.targetCurrentHp(),
                    outcome.targetDied());

            var observers = aoiTracker.observersOf(outcome.targetEntityId());
            observers.add(outcome.attackerEntityId()); // người đánh luôn thấy kết quả đòn của chính mình.
            for (long observerId : observers) {
                outboundSink.onAttackResult(observerId, sinkOutcome);
            }
            if (outcome.targetDied() && outcome.targetEntityId() < 0) {
                handleMonsterDeath(outcome.targetEntityId(), outcome.attackerEntityId());
            }
        }
    }

    private void handleSkillResults(List<SkillSystem.SkillOutcome> results) {
        for (var outcome : results) {
            var sinkOutcome = new WorldOutboundSink.SkillOutcome(
                    outcome.skillCode(),
                    outcome.attackerEntityId(),
                    outcome.targetEntityId(),
                    outcome.damage(),
                    outcome.crit(),
                    outcome.miss(),
                    outcome.targetCurrentHp(),
                    outcome.targetDied());

            var observers = aoiTracker.observersOf(outcome.targetEntityId());
            observers.add(outcome.attackerEntityId()); // người dùng skill luôn thấy kết quả của chính mình.
            for (long observerId : observers) {
                outboundSink.onSkillResult(observerId, sinkOutcome);
            }
            if (outcome.targetDied() && outcome.targetEntityId() < 0) {
                handleMonsterDeath(outcome.targetEntityId(), outcome.attackerEntityId());
            }
        }
    }

    private void handleMonsterDeath(long monsterId, long killerEntityId) {
        var entity = entitiesByMonsterId.remove(monsterId);
        if (entity == null) {
            return; // đã xử lý (nhiều đòn hạ gục cùng tick — CombatSystem tự chặn đòn thứ 2, xem javadoc).
        }
        String monsterCode = MONSTER.get(entity).monsterCode; // đọc TRƯỚC removeEntity — sau đó component mất.
        aoiTracker.onMonsterDetach(monsterId);
        ecsWorld.removeEntity(entity);
        monsterRespawnCountdownSeconds = CombatConstants.MONSTER_RESPAWN_SECONDS;

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
        var entity = ecsWorld.createEntity();
        long monsterId = nextMonsterOrNpcId--;

        var position = new PositionComponent();
        position.x = TRAINING_DUMMY_SPAWN_X;
        position.y = TRAINING_DUMMY_SPAWN_Y;
        position.prevX = position.x;
        position.prevY = position.y;
        entity.add(position);
        entity.add(new VelocityComponent()); // AiSystem set vận tốc theo FSM (AI.md §3)

        var health = new HealthComponent();
        health.current = CombatConstants.MONSTER_MAX_HP;
        health.max = CombatConstants.MONSTER_MAX_HP;
        entity.add(health);

        var monster = new MonsterComponent();
        monster.monsterCode = TRAINING_DUMMY_CODE;
        entity.add(monster);
        entity.add(toComponent(CombatConstants.MONSTER_STATS));

        var monsterIdComponent = new MonsterIdComponent();
        monsterIdComponent.monsterId = monsterId;
        entity.add(monsterIdComponent);

        var aiState = new AiStateComponent();
        aiState.spawnX = TRAINING_DUMMY_SPAWN_X;
        aiState.spawnY = TRAINING_DUMMY_SPAWN_Y;
        entity.add(aiState);
        entity.add(new TargetComponent()); // AiSystem ghi khi Chase/Attack; CombatSystem đọc để đánh trả
        entity.add(new AttackCooldownComponent());

        entitiesByMonsterId.put(monsterId, entity);
        // AoI sync() ở CHÍNH tick này tự phát hiện nhân vật đã ở gần + gửi MonsterSpawnedEvent —
        // không cần thông báo thủ công (giống hệt attach nhân vật, xem AreaOfInterestTracker).
    }

    private void spawnNpc() {
        var entity = ecsWorld.createEntity();
        long npcId = nextMonsterOrNpcId--;

        var position = new PositionComponent();
        position.x = SAMPLE_NPC_SPAWN_X;
        position.y = SAMPLE_NPC_SPAWN_Y;
        position.prevX = position.x;
        position.prevY = position.y;
        entity.add(position);
        // KHÔNG VelocityComponent/HealthComponent/AiStateComponent — NPC tĩnh, không di chuyển,
        // không combat (khác spawnTrainingDummy).

        var npc = new NpcComponent();
        npc.npcCode = SAMPLE_NPC_CODE;
        entity.add(npc);

        var npcIdComponent = new NpcIdComponent();
        npcIdComponent.npcId = npcId;
        entity.add(npcIdComponent);

        entitiesByNpcId.put(npcId, entity);
        // AoI sync() ở CHÍNH tick này tự phát hiện nhân vật đã ở gần + gửi NpcSpawnedEvent —
        // không cần thông báo thủ công (giống hệt quái/nhân vật, xem AreaOfInterestTracker).
    }

    /** {@code CombatConstants.CombatStats} (record, không mutable) → component ECS (mutable). */
    private static CombatStatsComponent toComponent(CombatConstants.CombatStats stats) {
        var component = new CombatStatsComponent();
        component.atk = stats.atk();
        component.skillDmg = stats.skillDmg();
        component.def = stats.def();
        component.res = stats.res();
        component.hitPercent = stats.hitPercent();
        component.dodgePercent = stats.dodgePercent();
        component.critPercent = stats.critPercent();
        component.critDamageMultiplier = stats.critDamageMultiplier();
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
