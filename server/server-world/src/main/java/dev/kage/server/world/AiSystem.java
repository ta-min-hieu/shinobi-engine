package dev.kage.server.world;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.Family;
import dev.kage.engine.ecs.IteratingKSystem;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.server.world.component.AiState;
import dev.kage.server.world.component.AiStateComponent;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.StatusEffectComponent;
import dev.kage.server.world.component.TargetComponent;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongFunction;
import java.util.function.Supplier;

/**
 * FSM quái thường (AI.md §3): Idle &lt;-&gt; Patrol (hết giờ chờ/tới điểm), Idle/Patrol -&gt; Chase
 * (phát hiện người chơi trong {@link AiConstants#AGGRO_RADIUS_METERS}) -&gt; Attack (vào tầm đánh)
 * -&gt; Chase (mục tiêu lùi xa) hoặc Return (ra khỏi {@link AiConstants#LEASH_RADIUS_METERS} tính
 * từ điểm spawn) -&gt; Idle. KHÔNG có trạng thái {@code DEAD} riêng — quái chết bị
 * {@code WorldFacade} xoá entity ngay trong tick đó, {@code AiSystem} tự nhiên dừng xử lý entity
 * đã biến mất, không cần chuyển trạng thái thủ công.
 *
 * <p>AiSystem CHỈ set {@link VelocityComponent} (di chuyển) và {@link TargetComponent} (chọn mục
 * tiêu) — {@code MovementSystem} tích luỹ vị trí từ vận tốc, {@code CombatSystem} thực sự gây
 * damage khi đã ở Attack (quái có đủ {@code TargetComponent}/{@code AttackCooldownComponent} như
 * nhân vật nên tự bị 2 System đó "cuốn vào", không cần sửa gì ở 2 System kia — xem
 * {@code WorldFacade.spawnTrainingDummy}).
 */
final class AiSystem extends IteratingKSystem {

    private static final ComponentMapper<PositionComponent> POSITION =
            ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<VelocityComponent> VELOCITY =
            ComponentMapper.getFor(VelocityComponent.class);
    private static final ComponentMapper<AiStateComponent> AI_STATE = ComponentMapper.getFor(AiStateComponent.class);
    private static final ComponentMapper<TargetComponent> TARGET = ComponentMapper.getFor(TargetComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<CharacterIdComponent> CHARACTER_ID =
            ComponentMapper.getFor(CharacterIdComponent.class);
    private static final ComponentMapper<StatusEffectComponent> STATUS_EFFECT =
            ComponentMapper.getFor(StatusEffectComponent.class);

    private final Supplier<Collection<KEntity>> characterEntities;
    private final LongFunction<KEntity> entityLookup;

    /**
     * @param characterEntities quét MỌI nhân vật đang online để tìm mục tiêu aggro gần nhất — chấp
     *     nhận O(n) ở quy mô này (AI.md §7: không A-star/spatial-grid ở MVP).
     * @param entityLookup tra KEntity theo entity ID (dương = nhân vật, âm = quái), dùng để đọc vị
     *     trí/HP của mục tiêu đã chọn.
     */
    AiSystem(Supplier<Collection<KEntity>> characterEntities, LongFunction<KEntity> entityLookup) {
        super(Family.all(PositionComponent.class, VelocityComponent.class, AiStateComponent.class,
                TargetComponent.class).get());
        this.characterEntities = characterEntities;
        this.entityLookup = entityLookup;
    }

    @Override
    protected void processEntity(KEntity entity, float deltaTime) {
        if (STATUS_EFFECT.has(entity) && StatusEffects.isStunned(STATUS_EFFECT.get(entity))) {
            // Bị stun (Phase 8, hệ Lôi/Thủy) — đứng yên, không tiến FSM (giữ nguyên state/timer,
            // tiếp tục đúng chỗ đang dở khi hết choáng).
            VelocityComponent velocity = VELOCITY.get(entity);
            velocity.x = 0f;
            velocity.y = 0f;
            return;
        }
        AiStateComponent ai = AI_STATE.get(entity);
        switch (ai.state) {
            case IDLE -> tickIdle(entity, ai, deltaTime);
            case PATROL -> tickPatrol(entity, ai, deltaTime);
            case CHASE -> tickChase(entity, ai);
            case ATTACK -> tickAttack(entity, ai);
            case RETURN -> tickReturn(entity, ai);
            default -> throw new IllegalStateException("AiState không xử lý: " + ai.state);
        }
    }

    private void tickIdle(KEntity entity, AiStateComponent ai, float deltaTime) {
        VelocityComponent velocity = VELOCITY.get(entity);
        velocity.x = 0f;
        velocity.y = 0f;

        KEntity nearestPlayer = findNearestCharacterInRange(POSITION.get(entity), AiConstants.AGGRO_RADIUS_METERS);
        if (nearestPlayer != null) {
            startChasing(ai, TARGET.get(entity), nearestPlayer);
            return;
        }

        ai.stateTimer += deltaTime;
        if (ai.stateTimer >= AiConstants.IDLE_DURATION_SECONDS) {
            startPatrolling(ai);
        }
    }

    private void tickPatrol(KEntity entity, AiStateComponent ai, float deltaTime) {
        PositionComponent position = POSITION.get(entity);
        VelocityComponent velocity = VELOCITY.get(entity);

        KEntity nearestPlayer = findNearestCharacterInRange(position, AiConstants.AGGRO_RADIUS_METERS);
        if (nearestPlayer != null) {
            startChasing(ai, TARGET.get(entity), nearestPlayer);
            return;
        }

        ai.stateTimer += deltaTime;
        boolean arrived = distance(position.x, position.y, ai.patrolTargetX, ai.patrolTargetY)
                <= AiConstants.ARRIVAL_THRESHOLD_METERS;
        if (arrived || ai.stateTimer >= AiConstants.MAX_PATROL_DURATION_SECONDS) {
            velocity.x = 0f;
            velocity.y = 0f;
            ai.state = AiState.IDLE;
            ai.stateTimer = 0f;
            return;
        }
        moveToward(entity, ai.patrolTargetX, ai.patrolTargetY, AiConstants.PATROL_SPEED_MPS);
    }

    private void tickChase(KEntity entity, AiStateComponent ai) {
        TargetComponent target = TARGET.get(entity);
        KEntity targetEntity = entityLookup.apply(target.targetEntityId);
        if (targetEntity == null || HEALTH.get(targetEntity).isDead()) {
            startReturning(ai, target, VELOCITY.get(entity));
            return;
        }

        PositionComponent position = POSITION.get(entity);
        if (distance(position.x, position.y, ai.spawnX, ai.spawnY) > AiConstants.LEASH_RADIUS_METERS) {
            startReturning(ai, target, VELOCITY.get(entity));
            return;
        }

        PositionComponent targetPosition = POSITION.get(targetEntity);
        if (distance(position.x, position.y, targetPosition.x, targetPosition.y)
                <= CombatConstants.ATTACK_RANGE_METERS) {
            VelocityComponent velocity = VELOCITY.get(entity);
            velocity.x = 0f;
            velocity.y = 0f;
            ai.state = AiState.ATTACK;
            return;
        }
        moveToward(entity, targetPosition.x, targetPosition.y, AiConstants.CHASE_SPEED_MPS);
    }

    private void tickAttack(KEntity entity, AiStateComponent ai) {
        // Damage thật do CombatSystem xử lý (quái có đủ Target+AttackCooldown) — ở đây chỉ theo
        // dõi mục tiêu còn hợp lệ/còn trong tầm hay không.
        TargetComponent target = TARGET.get(entity);
        KEntity targetEntity = entityLookup.apply(target.targetEntityId);
        if (targetEntity == null || HEALTH.get(targetEntity).isDead()) {
            startReturning(ai, target, VELOCITY.get(entity));
            return;
        }

        PositionComponent position = POSITION.get(entity);
        PositionComponent targetPosition = POSITION.get(targetEntity);
        if (distance(position.x, position.y, targetPosition.x, targetPosition.y)
                > CombatConstants.ATTACK_RANGE_METERS) {
            ai.state = AiState.CHASE;
        }
    }

    private void tickReturn(KEntity entity, AiStateComponent ai) {
        PositionComponent position = POSITION.get(entity);
        if (distance(position.x, position.y, ai.spawnX, ai.spawnY) <= AiConstants.ARRIVAL_THRESHOLD_METERS) {
            VelocityComponent velocity = VELOCITY.get(entity);
            velocity.x = 0f;
            velocity.y = 0f;
            position.x = ai.spawnX; // chốt đúng điểm spawn — tránh trôi dần qua nhiều lần Return
            position.y = ai.spawnY;
            ai.state = AiState.IDLE;
            ai.stateTimer = 0f;
            return;
        }
        moveToward(entity, ai.spawnX, ai.spawnY, AiConstants.CHASE_SPEED_MPS);
    }

    private void startChasing(AiStateComponent ai, TargetComponent target, KEntity characterEntity) {
        target.targetEntityId = CHARACTER_ID.get(characterEntity).characterId;
        ai.state = AiState.CHASE;
        ai.stateTimer = 0f;
    }

    private void startReturning(AiStateComponent ai, TargetComponent target, VelocityComponent velocity) {
        target.targetEntityId = 0L;
        ai.state = AiState.RETURN;
        velocity.x = 0f; // tickReturn tự set lại đúng hướng ở tick sau
        velocity.y = 0f;
    }

    private void startPatrolling(AiStateComponent ai) {
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        ai.patrolTargetX = ai.spawnX + (float) (Math.cos(angle) * AiConstants.PATROL_RADIUS_METERS);
        ai.patrolTargetY = ai.spawnY + (float) (Math.sin(angle) * AiConstants.PATROL_RADIUS_METERS);
        ai.state = AiState.PATROL;
        ai.stateTimer = 0f;
    }

    private KEntity findNearestCharacterInRange(PositionComponent position, float range) {
        KEntity nearest = null;
        double nearestDistance = range;
        for (KEntity character : characterEntities.get()) {
            PositionComponent characterPosition = POSITION.get(character);
            double d = distance(position.x, position.y, characterPosition.x, characterPosition.y);
            if (d <= nearestDistance) {
                nearest = character;
                nearestDistance = d;
            }
        }
        return nearest;
    }

    private static void moveToward(KEntity entity, float toX, float toY, float speed) {
        PositionComponent position = POSITION.get(entity);
        VelocityComponent velocity = VELOCITY.get(entity);
        float dx = toX - position.x;
        float dy = toY - position.y;
        float length = (float) Math.sqrt((dx * dx) + (dy * dy));
        if (length < 0.0001f) {
            velocity.x = 0f;
            velocity.y = 0f;
            return;
        }
        // Phase 8 (hệ Thủy "khống chế") — SLOW/buff tốc độ áp dụng cho CẢ quái, không chỉ nhân vật
        // (khác WorldFacade.handleMoveIntent chỉ có nhân vật gửi MoveCommand).
        float effectiveSpeed =
                speed * (STATUS_EFFECT.has(entity) ? StatusEffects.moveSpeedMultiplier(STATUS_EFFECT.get(entity)) : 1f);
        velocity.x = (dx / length) * effectiveSpeed;
        velocity.y = (dy / length) * effectiveSpeed;
    }

    private static double distance(float x1, float y1, float x2, float y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt((dx * dx) + (dy * dy));
    }
}
