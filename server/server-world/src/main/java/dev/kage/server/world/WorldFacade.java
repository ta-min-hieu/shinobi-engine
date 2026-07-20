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
import dev.kage.server.world.component.CombatStatsComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MonsterComponent;
import dev.kage.server.world.component.MonsterIdComponent;
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

    // Quái bù nhìn tập luyện — lát cắt 1 Phase 4, hardcode 1 quái, chưa có monster_template DB.
    private static final String TRAINING_DUMMY_CODE = "training_dummy";
    private static final float TRAINING_DUMMY_SPAWN_X = 5f;
    private static final float TRAINING_DUMMY_SPAWN_Y = 5f;

    private final WorldOutboundSink outboundSink;
    private final Queue<WorldCommand> inbox = new ConcurrentLinkedQueue<>();
    private final EcsWorld ecsWorld = new EcsWorld();
    private final AreaOfInterestTracker aoiTracker;
    private final CombatSystem combatSystem;

    // Chỉ tick thread được đọc/ghi các map/field này (không đồng bộ hoá) — đúng ranh giới "ECS
    // state chỉ sửa trên tick thread" (SERVER.md §2).
    private final Map<Long, KEntity> entitiesByCharacterId = new HashMap<>();
    private final Map<Long, KEntity> entitiesByMonsterId = new HashMap<>();
    private long nextMonsterId = -1; // ID quái luôn ÂM — không đụng characterId (luôn DƯƠNG, DB auto-increment).
    private float monsterRespawnCountdownSeconds = -1f; // < 0 = quái đang sống, không đếm ngược.

    private volatile boolean running;
    private Thread tickThread;

    public WorldFacade(WorldOutboundSink outboundSink) {
        this.outboundSink = outboundSink;
        this.aoiTracker = new AreaOfInterestTracker(outboundSink);
        this.combatSystem = new CombatSystem(this::lookupEntity);
        // Thứ tự ĐÚNG ECS.md §6 (InputCommandSystem -> AISystem -> MovementSystem -> ... ->
        // CombatSystem): AI set vận tốc/mục tiêu trước, Movement tích luỹ vị trí, Combat mới xét
        // tầm đánh — đổi thứ tự = đổi behavior (engine/CLAUDE.md).
        ecsWorld.addSystem(new AiSystem(entitiesByCharacterId::values, this::lookupEntity));
        ecsWorld.addSystem(new MovementSystem());
        ecsWorld.addSystem(combatSystem);
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

    private void tickLoop() {
        spawnTrainingDummy();
        long fixedDtNanos = (long) (WorldConstants.FIXED_DT * 1_000_000_000L);
        while (running) {
            long start = System.nanoTime();

            drainInbox();
            ecsWorld.update(WorldConstants.FIXED_DT);
            handleCombatResults(combatSystem.drainResults());
            tickMonsterRespawn(WorldConstants.FIXED_DT);
            aoiTracker.sync(entitiesByCharacterId, entitiesByMonsterId);

            long elapsed = System.nanoTime() - start;
            long sleepNanos = fixedDtNanos - elapsed;
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            } else {
                LOG.warn("World tick quá hạn: {}ms", elapsed / 1_000_000);
            }
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

        entitiesByCharacterId.put(characterId, entity);
        aoiTracker.onAttach(characterId);
    }

    private void handleDetach(long characterId) {
        var entity = entitiesByCharacterId.remove(characterId);
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
                handleMonsterDeath(outcome.targetEntityId());
            }
        }
    }

    private void handleMonsterDeath(long monsterId) {
        var entity = entitiesByMonsterId.remove(monsterId);
        if (entity == null) {
            return; // đã xử lý (nhiều đòn hạ gục cùng tick — CombatSystem tự chặn đòn thứ 2, xem javadoc).
        }
        aoiTracker.onMonsterDetach(monsterId);
        ecsWorld.removeEntity(entity);
        monsterRespawnCountdownSeconds = CombatConstants.MONSTER_RESPAWN_SECONDS;
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
        long monsterId = nextMonsterId--;

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

    /** {@code CombatConstants.CombatStats} (record, không mutable) → component ECS (mutable). */
    private static CombatStatsComponent toComponent(CombatConstants.CombatStats stats) {
        var component = new CombatStatsComponent();
        component.atk = stats.atk();
        component.def = stats.def();
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
