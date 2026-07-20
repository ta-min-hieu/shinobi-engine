package dev.kage.server.world;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.Family;
import dev.kage.engine.ecs.IteratingKSystem;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.server.world.component.AttackCooldownComponent;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.CombatStatsComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.TargetComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

/**
 * Đánh thường TỰ ĐỘNG (GDD §6 "Auto-target đánh thường") — không phải {@code UseSkillCommand}
 * (chờ ADR-016 script sandbox, lát cắt sau). Entity có {@link TargetComponent} +
 * {@link AttackCooldownComponent} (hiện chỉ nhân vật người chơi — quái bù nhìn không có 2
 * component này nên không tự đánh trả, xem {@code WorldFacade}) tự đánh mục tiêu khi hết cooldown
 * và còn trong tầm, theo pipeline damage đã chốt ({@link CombatConstants#resolveAttack}).
 *
 * <p>KHÔNG gọi thẳng {@code WorldOutboundSink} — theo đúng pattern {@code WorldFacade} gọi sink
 * SAU khi cả tick chạy xong; kết quả tích trong hàng đợi nội bộ, {@link #drainResults()} lấy ra
 * sau {@code ecsWorld.update()} mỗi tick.
 */
final class CombatSystem extends IteratingKSystem {

    private static final ComponentMapper<PositionComponent> POSITION =
            ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<TargetComponent> TARGET =
            ComponentMapper.getFor(TargetComponent.class);
    private static final ComponentMapper<AttackCooldownComponent> COOLDOWN =
            ComponentMapper.getFor(AttackCooldownComponent.class);
    private static final ComponentMapper<CharacterIdComponent> CHARACTER_ID =
            ComponentMapper.getFor(CharacterIdComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<CombatStatsComponent> COMBAT_STATS =
            ComponentMapper.getFor(CombatStatsComponent.class);

    /** Kết quả 1 đòn đánh đã xảy ra thật trong tick này — {@code WorldFacade} drain để phát AoI. */
    record AttackOutcome(
            long attackerEntityId,
            long targetEntityId,
            int damage,
            boolean crit,
            boolean miss,
            int targetCurrentHp,
            boolean targetDied) {
    }

    private final LongFunction<KEntity> entityLookup;
    private final List<AttackOutcome> pendingResults = new ArrayList<>();

    /** @param entityLookup tra KEntity theo entity ID (nhân vật DƯƠNG hoặc quái ÂM — combat.proto). */
    CombatSystem(LongFunction<KEntity> entityLookup) {
        super(Family.all(PositionComponent.class, TargetComponent.class, AttackCooldownComponent.class).get());
        this.entityLookup = entityLookup;
    }

    List<AttackOutcome> drainResults() {
        if (pendingResults.isEmpty()) {
            return List.of();
        }
        var results = List.copyOf(pendingResults);
        pendingResults.clear();
        return results;
    }

    @Override
    protected void processEntity(KEntity entity, float deltaTime) {
        var cooldown = COOLDOWN.get(entity);
        cooldown.remainingSeconds -= deltaTime;
        if (cooldown.remainingSeconds > 0f) {
            return;
        }

        var target = TARGET.get(entity);
        if (target.targetEntityId == 0L) {
            return; // chưa chọn mục tiêu
        }

        var targetEntity = entityLookup.apply(target.targetEntityId);
        var targetHealth = targetEntity == null ? null : HEALTH.get(targetEntity);
        if (targetHealth == null || targetHealth.isDead()) {
            target.targetEntityId = 0L; // mục tiêu đã biến mất/chết — bỏ chọn, chờ chọn lại
            return;
        }

        if (distance(POSITION.get(entity), POSITION.get(targetEntity)) > CombatConstants.ATTACK_RANGE_METERS) {
            return; // ngoài tầm — chờ, KHÔNG reset cooldown (không "tích luỹ" đòn khi đứng ngoài tầm)
        }

        cooldown.remainingSeconds = CombatConstants.ATTACK_INTERVAL_SECONDS;
        resolveAndRecordAttack(entity, targetEntity, target.targetEntityId, targetHealth);
    }

    private void resolveAndRecordAttack(
            KEntity attacker, KEntity target, long targetEntityId, HealthComponent targetHealth) {
        var result = CombatConstants.resolveAttack(
                toStats(COMBAT_STATS.get(attacker)), toStats(COMBAT_STATS.get(target)));
        targetHealth.current = Math.max(0, targetHealth.current - result.damage());

        long attackerId = CHARACTER_ID.get(attacker).characterId;
        pendingResults.add(new AttackOutcome(
                attackerId,
                targetEntityId,
                result.damage(),
                result.crit(),
                result.miss(),
                targetHealth.current,
                targetHealth.isDead()));
    }

    /** Component ECS (mutable) → record bất biến {@code resolveAttack} cần (giữ pipeline thuần). */
    private static CombatConstants.CombatStats toStats(CombatStatsComponent c) {
        return new CombatConstants.CombatStats(
                c.atk, c.def, c.hitPercent, c.dodgePercent, c.critPercent, c.critDamageMultiplier);
    }

    private static double distance(PositionComponent a, PositionComponent b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt((dx * dx) + (dy * dy));
    }
}
