package dev.kage.server.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.Family;
import dev.kage.engine.ecs.IteratingKSystem;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.TriggerZoneMembershipComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.server.world.component.AiParamsComponent;
import dev.kage.server.world.component.AiState;
import dev.kage.server.world.component.AiStateComponent;
import dev.kage.server.world.component.BossAiComponent;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.CombatRecencyComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MonsterIdComponent;
import dev.kage.server.world.component.StatusEffectComponent;
import dev.kage.server.world.component.TargetComponent;

/**
 * FSM quái thường (AI.md §3): Idle &lt;-&gt; Patrol (hết giờ chờ/tới điểm), Idle/Patrol -&gt; Chase
 * (phát hiện người chơi trong {@link AiParamsComponent#aggroRadiusMeters()}) -&gt; Attack (vào tầm
 * đánh) -&gt; Chase (mục tiêu lùi xa) hoặc Return (ra khỏi
 * {@link AiParamsComponent#leashRadiusMeters()} tính từ điểm spawn) -&gt; Idle. KHÔNG có trạng thái
 * {@code DEAD} riêng — quái chết bị {@code WorldFacade} xoá entity ngay trong tick đó,
 * {@code AiSystem} tự nhiên dừng xử lý entity đã biến mất, không cần chuyển trạng thái thủ công.
 *
 * <p><b>Tham số đọc THEO LOÀI</b> kể từ ADR-039: mọi con số điều khiển FSM lấy từ
 * {@link AiParamsComponent} của chính entity đang xét, không còn từ hằng số dùng chung.
 * {@link AiConstants} chỉ còn giữ hai hằng CƠ CHẾ (epsilon "đã tới nơi" + van chống kẹt) và bộ mặc
 * định. {@code AiParamsComponent} nằm trong {@code Family} một cách có chủ đích: một entity có
 * {@code AiStateComponent} mà thiếu tham số không phải "quái chạy bằng mặc định" — nó là entity
 * dựng sai, và ECS gác giúp bằng cách đơn giản không coi nó là entity AI.
 *
 * <p>AiSystem CHỈ set {@link VelocityComponent} (di chuyển) và {@link TargetComponent} (chọn mục
 * tiêu) — {@code MovementSystem} tích luỹ vị trí từ vận tốc, {@code CombatSystem} thực sự gây
 * damage khi đã ở Attack (quái có đủ {@code TargetComponent}/{@code AttackCooldownComponent} như
 * nhân vật nên tự bị 2 System đó "cuốn vào", không cần sửa gì ở 2 System kia — xem
 * {@code WorldFacade.spawnMonster}).
 */
final class AiSystem extends IteratingKSystem {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<VelocityComponent> VELOCITY = ComponentMapper.getFor(VelocityComponent.class);
    private static final ComponentMapper<AiStateComponent> AI_STATE = ComponentMapper.getFor(AiStateComponent.class);
    private static final ComponentMapper<AiParamsComponent> AI_PARAMS = ComponentMapper.getFor(AiParamsComponent.class);
    private static final ComponentMapper<TargetComponent> TARGET = ComponentMapper.getFor(TargetComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<CharacterIdComponent> CHARACTER_ID =
            ComponentMapper.getFor(CharacterIdComponent.class);
    private static final ComponentMapper<StatusEffectComponent> STATUS_EFFECT =
            ComponentMapper.getFor(StatusEffectComponent.class);

    private static final ComponentMapper<MonsterIdComponent> MONSTER_ID =
            ComponentMapper.getFor(MonsterIdComponent.class);
    private static final ComponentMapper<BossAiComponent> BOSS_AI = ComponentMapper.getFor(BossAiComponent.class);

    // ADR-050 D1/D3 — hai thứ quyết định một nhân vật có đang được vùng an toàn che hay không.
    // Cả hai component này mọi nhân vật đều mang sẵn từ ADR-046 (WorldFacade.addRecoveryComponents),
    // nên luật mới KHÔNG cần plumbing: không đổi constructor, không đụng WorldContentCatalog.
    private static final ComponentMapper<TriggerZoneMembershipComponent> ZONE_MEMBERSHIP =
            ComponentMapper.getFor(TriggerZoneMembershipComponent.class);
    private static final ComponentMapper<CombatRecencyComponent> COMBAT_RECENCY =
            ComponentMapper.getFor(CombatRecencyComponent.class);

    private final Supplier<Collection<KEntity>> characterEntities;
    private final LongFunction<KEntity> entityLookup;

    /** Quái vừa về tới điểm spawn và lành lại (ADR-046 D4) — {@code WorldFacade} drain mỗi tick. */
    private final List<HealthChange> pendingHealthChanges = new ArrayList<>();

    /**
     * @param characterEntities quét MỌI nhân vật đang online để tìm mục tiêu aggro gần nhất — chấp
     *     nhận O(n) ở quy mô này (AI.md §7: không A-star/spatial-grid ở MVP).
     * @param entityLookup tra KEntity theo entity ID (dương = nhân vật, âm = quái), dùng để đọc vị
     *     trí/HP của mục tiêu đã chọn.
     */
    AiSystem(Supplier<Collection<KEntity>> characterEntities, LongFunction<KEntity> entityLookup) {
        super(Family.all(
                        PositionComponent.class,
                        VelocityComponent.class,
                        AiStateComponent.class,
                        AiParamsComponent.class,
                        TargetComponent.class)
                .get());
        this.characterEntities = characterEntities;
        this.entityLookup = entityLookup;
    }

    /** Cùng khuôn {@code CombatSystem.drainResults} — kết quả tick lấy ra SAU {@code ecsWorld.update()}. */
    List<HealthChange> drainHealthChanges() {
        if (pendingHealthChanges.isEmpty()) {
            return List.of();
        }
        List<HealthChange> results = List.copyOf(pendingHealthChanges);
        pendingHealthChanges.clear();
        return results;
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
        // Tra tham số theo loài: ComponentMapper, O(1), KHÔNG cấp phát (/CLAUDE.md §6) — khác hẳn
        // với việc tra catalog theo chuỗi mỗi tick, xem javadoc AiParamsComponent.
        AiParamsComponent params = AI_PARAMS.get(entity);
        switch (ai.state) {
            case IDLE -> tickIdle(entity, ai, params, deltaTime);
            case PATROL -> tickPatrol(entity, ai, params, deltaTime);
            case CHASE -> tickChase(entity, ai, params);
            case ATTACK -> tickAttack(entity, ai);
            case RETURN -> tickReturn(entity, ai, params);
            default -> throw new IllegalStateException("AiState không xử lý: " + ai.state);
        }
    }

    private void tickIdle(KEntity entity, AiStateComponent ai, AiParamsComponent params, float deltaTime) {
        VelocityComponent velocity = VELOCITY.get(entity);
        velocity.x = 0f;
        velocity.y = 0f;

        KEntity nearestPlayer = findNearestCharacterInRange(POSITION.get(entity), params.aggroRadiusMeters());
        if (nearestPlayer != null) {
            startChasing(ai, TARGET.get(entity), nearestPlayer);
            return;
        }

        ai.stateTimer += deltaTime;
        if (ai.stateTimer >= params.idleDurationSeconds()) {
            startPatrolling(ai, params);
        }
    }

    private void tickPatrol(KEntity entity, AiStateComponent ai, AiParamsComponent params, float deltaTime) {
        PositionComponent position = POSITION.get(entity);
        VelocityComponent velocity = VELOCITY.get(entity);

        KEntity nearestPlayer = findNearestCharacterInRange(position, params.aggroRadiusMeters());
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
        moveToward(entity, ai.patrolTargetX, ai.patrolTargetY, params.patrolSpeedMps());
    }

    private void tickChase(KEntity entity, AiStateComponent ai, AiParamsComponent params) {
        TargetComponent target = TARGET.get(entity);
        KEntity targetEntity = entityLookup.apply(target.targetEntityId);
        if (targetEntity == null || HEALTH.get(targetEntity).isDead()) {
            startReturning(ai, target, VELOCITY.get(entity));
            return;
        }

        if (isShieldedBySafeZone(targetEntity)) {
            startReturning(ai, target, VELOCITY.get(entity)); // ADR-050 D2
            return;
        }

        PositionComponent position = POSITION.get(entity);
        if (distance(position.x, position.y, ai.spawnX, ai.spawnY) > params.leashRadiusMeters()) {
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
        moveToward(entity, targetPosition.x, targetPosition.y, params.chaseSpeedMps());
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

        // ADR-050 D2 — phải gác ở CẢ trạng thái ĐÁNH, không chỉ ĐUỔI: tickAttack chỉ tụt về CHASE
        // khi mục tiêu ra ngoài tầm, nên một con đang đứng sát biên vùng sẽ đánh mãi vào trong.
        if (isShieldedBySafeZone(targetEntity)) {
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

    private void tickReturn(KEntity entity, AiStateComponent ai, AiParamsComponent params) {
        PositionComponent position = POSITION.get(entity);
        if (distance(position.x, position.y, ai.spawnX, ai.spawnY) <= AiConstants.ARRIVAL_THRESHOLD_METERS) {
            VelocityComponent velocity = VELOCITY.get(entity);
            velocity.x = 0f;
            velocity.y = 0f;
            position.x = ai.spawnX; // chốt đúng điểm spawn — tránh trôi dần qua nhiều lần Return
            position.y = ai.spawnY;
            ai.state = AiState.IDLE;
            ai.stateTimer = 0f;
            healToFullOnArrival(entity);
            resetBossPhaseOnArrival(entity);
            return;
        }
        moveToward(entity, ai.spawnX, ai.spawnY, params.chaseSpeedMps());
    }

    /**
     * ADR-046 D4 — quái về tới điểm spawn thì lành lại HẾT. Không có bước này thì "đánh vài nhát,
     * chạy ra khỏi leash, quay lại đánh tiếp con máu thấp" là chiến thuật tối ưu của MỌI trận, và
     * nó trở thành lỗ hổng thật đúng lúc người chơi được hồi máu ngoài chiến đấu (D2).
     *
     * <p>Chỉ phát {@code HealthChange} khi máu THỰC SỰ thiếu: con quái tuần tra qua lại mà chưa ai
     * đụng tới không được phép sinh ra gói nào (cam kết trần số gói với ADR-042).
     */
    private void healToFullOnArrival(KEntity entity) {
        HealthComponent health = HEALTH.get(entity);
        if (health == null || health.current >= health.max) {
            return;
        }
        health.current = health.max;
        pendingHealthChanges.add(new HealthChange(MONSTER_ID.get(entity).monsterId, health.current, health.max));
    }

    /**
     * Về nhà là "trận đấu reset" (ADR-046 D4) — với boss, reset cả PHA: script khai
     * {@code already_enraged} là "chỉ kích hoạt 1 lần/TRẬN". Không có bước này thì boss lành đầy
     * máu mà vẫn mang cờ enrage của trận trước: bỏ hẳn đòn enrage, và đánh hệ số pha cuối từ 70%.
     */
    private static void resetBossPhaseOnArrival(KEntity entity) {
        BossAiComponent bossAi = BOSS_AI.get(entity);
        if (bossAi != null) {
            bossAi.enraged = false;
        }
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

    private void startPatrolling(AiStateComponent ai, AiParamsComponent params) {
        // patrolRadiusMeters = 0 ⇒ điểm tuần tra TRÙNG điểm spawn ⇒ tickPatrol thấy "đã tới nơi"
        // ngay tick sau và về IDLE với vận tốc 0: con quái đứng nguyên tại chỗ, không cần trạng
        // thái mới hay nhánh riêng (ADR-039 D4 — cây cọc luyện công).
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        ai.patrolTargetX = ai.spawnX + (float) (Math.cos(angle) * params.patrolRadiusMeters());
        ai.patrolTargetY = ai.spawnY + (float) (Math.sin(angle) * params.patrolRadiusMeters());
        ai.state = AiState.PATROL;
        ai.stateTimer = 0f;
    }

    /**
     * ADR-050 D1/D2/D3 — nhân vật này có đang được một vùng an toàn che hay không.
     *
     * <p>Hai điều kiện, và điều kiện thứ hai mới là thứ giữ cho trạm tiếp ứng không thành chòi bắn
     * tỉa: <b>(1)</b> đang đứng trong ít nhất một vùng, <b>(2)</b> chưa TỰ RA ĐÒN trong
     * {@code REGEN_DELAY_SECONDS} giây. Ai vừa đánh ra thì tự bỏ lá chắn của mình.
     *
     * <p>⚠️ Phải là {@code secondsSinceAttackDealt}, KHÔNG phải {@code secondsSinceCombat}. Đồng hồ
     * kia reset cho cả bên đánh lẫn bên CHỊU (ADR-046 D2, cố ý), nên dùng nó thì người đang bị đuổi
     * không bao giờ đạt ngưỡng — và D3 sẽ vô hiệu hoá chính D2 ở đúng tình huống D2 sinh ra để giải.
     * Bản đầu của ADR-050 mắc đúng lỗi đó; ô test
     * {@code shouldBreakOffTheChase_whenTheTargetStepsIntoASafeZone} là chỗ nó lộ ra.
     *
     * <p>⚠️ Trễ MỘT tick: {@code TriggerZoneSystem} chạy sau {@code AiSystem} trong cùng tick
     * ({@code WorldFacade}), nên đây đọc membership của tick trước — 16,7ms, tức ≤ 0,08m ở tốc độ
     * chạy 5 m/s.
     *
     * <p>Quái không mang hai component này (ADR-046 chỉ gắn cho nhân vật), nên hàm trả
     * {@code false} cho chúng — đúng ý: vùng an toàn không che quái.
     */
    private static boolean isShieldedBySafeZone(KEntity character) {
        TriggerZoneMembershipComponent membership = ZONE_MEMBERSHIP.get(character);
        if (membership == null || membership.occupiedZoneIds.isEmpty()) {
            return false;
        }
        CombatRecencyComponent recency = COMBAT_RECENCY.get(character);
        return recency != null && recency.secondsSinceAttackDealt >= CombatConstants.REGEN_DELAY_SECONDS;
    }

    /**
     * @param range 0 (hoặc âm) = loài THỤ ĐỘNG, không bao giờ gây hấn (ADR-039 D4). Nhánh này phải
     *     tường minh chứ không được để nó "tự đúng" nhờ phép so sánh bên dưới: với
     *     {@code range == 0} vòng lặp vẫn aggro một người chơi đứng ĐÚNG toạ độ con quái, và vẫn
     *     quét toàn bộ danh sách người chơi mỗi tick cho mỗi con quái thụ động.
     */
    private KEntity findNearestCharacterInRange(PositionComponent position, float range) {
        if (range <= 0f) {
            return null;
        }
        KEntity nearest = null;
        double nearestDistance = range;
        for (KEntity character : characterEntities.get()) {
            if (isShieldedBySafeZone(character) || isDead(character)) {
                // ADR-050 D1; và cái xác của World không tự hồi sinh (hầm ngục/Arena) — chọn nó thì
                // nó che người còn sống ở xa hơn, còn quái lật CHASE→RETURN→IDLE mãi mãi.
                continue;
            }
            PositionComponent characterPosition = POSITION.get(character);
            double d = distance(position.x, position.y, characterPosition.x, characterPosition.y);
            if (d <= nearestDistance) {
                nearest = character;
                nearestDistance = d;
            }
        }
        return nearest;
    }

    private static boolean isDead(KEntity character) {
        HealthComponent health = HEALTH.get(character);
        return health != null && health.isDead();
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
