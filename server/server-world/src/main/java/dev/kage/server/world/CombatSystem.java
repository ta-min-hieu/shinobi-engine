package dev.kage.server.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.Family;
import dev.kage.engine.ecs.IteratingKSystem;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.server.world.component.AttackCooldownComponent;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.CombatRecencyComponent;
import dev.kage.server.world.component.CombatStatsComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MonsterIdComponent;
import dev.kage.server.world.component.StatusEffectComponent;
import dev.kage.server.world.component.TargetComponent;

/**
 * Đánh thường — không phải {@code UseSkillCommand} (script sandbox GraalJS theo ADR-016).
 *
 * <p><b>Hai chế độ, có chủ đích khác nhau</b> (đổi 2026-08-09 theo yêu cầu chủ dự án sau
 * playtest — trước đó MỌI entity đều tự đánh, GDD §6 "Auto-target đánh thường"):
 *
 * <ul>
 *   <li><b>Người chơi</b> ({@link CharacterIdComponent}) — chỉ đánh khi có {@code AttackCommand}
 *       ({@link #requestAttack}). Chủ dự án thấy máu quái tự tụt dù chưa bấm gì và quyết định
 *       "phải bấm mới đánh". Cooldown vẫn đếm lùi mỗi tick như cũ.
 *   <li><b>Quái/boss</b> — GIỮ NGUYÊN tự đánh mỗi khi hết cooldown ({@code AiSystem} là bên GHI
 *       {@code targetEntityId} theo FSM, AI.md §3). Không có ai bấm nút hộ chúng.
 * </ul>
 *
 * <p>Cả hai đi qua CÙNG một đường kiểm tra (cooldown → stun → mục tiêu còn sống → trong tầm) và
 * cùng pipeline damage đã chốt ({@link CombatConstants#resolveAttack}).
 *
 * <p>KHÔNG gọi thẳng {@code WorldOutboundSink} — theo đúng pattern {@code WorldFacade} gọi sink
 * SAU khi cả tick chạy xong; kết quả tích trong hàng đợi nội bộ, {@link #drainResults()} lấy ra
 * sau {@code ecsWorld.update()} mỗi tick.
 */
final class CombatSystem extends IteratingKSystem {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<TargetComponent> TARGET = ComponentMapper.getFor(TargetComponent.class);
    private static final ComponentMapper<AttackCooldownComponent> COOLDOWN =
            ComponentMapper.getFor(AttackCooldownComponent.class);
    private static final ComponentMapper<CharacterIdComponent> CHARACTER_ID =
            ComponentMapper.getFor(CharacterIdComponent.class);
    private static final ComponentMapper<MonsterIdComponent> MONSTER_ID =
            ComponentMapper.getFor(MonsterIdComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<CombatStatsComponent> COMBAT_STATS =
            ComponentMapper.getFor(CombatStatsComponent.class);
    private static final ComponentMapper<StatusEffectComponent> STATUS_EFFECT =
            ComponentMapper.getFor(StatusEffectComponent.class);
    private static final ComponentMapper<CombatRecencyComponent> RECENCY =
            ComponentMapper.getFor(CombatRecencyComponent.class);

    /** Kết quả 1 đòn đánh đã xảy ra thật trong tick này — {@code WorldFacade} drain để phát AoI. */
    record AttackOutcome(
            long attackerEntityId,
            long targetEntityId,
            int damage,
            boolean crit,
            boolean miss,
            int targetCurrentHp,
            boolean targetDied) {}

    private final LongFunction<KEntity> entityLookup;
    private final List<AttackOutcome> pendingResults = new ArrayList<>();

    /** @param entityLookup tra KEntity theo entity ID (nhân vật DƯƠNG hoặc quái ÂM — combat.proto). */
    CombatSystem(LongFunction<KEntity> entityLookup) {
        super(Family.all(PositionComponent.class, TargetComponent.class, AttackCooldownComponent.class)
                .get());
        this.entityLookup = entityLookup;
    }

    List<AttackOutcome> drainResults() {
        if (pendingResults.isEmpty()) {
            return List.of();
        }
        List<AttackOutcome> results = List.copyOf(pendingResults);
        pendingResults.clear();
        return results;
    }

    @Override
    protected void processEntity(KEntity entity, float deltaTime) {
        AttackCooldownComponent cooldown = COOLDOWN.get(entity);
        cooldown.remainingSeconds -= deltaTime;
        // Người chơi KHÔNG còn tự đánh (chủ dự án 2026-08-09) — chỉ đếm lùi cooldown ở đây, đòn
        // thật ra khi có AttackCommand ({@link #requestAttack}). Quái/boss thì giữ nguyên tự đánh:
        // chúng không có ai bấm nút hộ, bỏ auto ở đây là không con nào đánh người chơi nữa.
        if (CHARACTER_ID.has(entity)) {
            return;
        }
        tryAttackNow(entity, cooldown);
    }

    /**
     * Người chơi bấm nút đánh ({@code AttackCommand}) — ra ĐÚNG một đòn nếu mọi điều kiện server
     * kiểm đều đạt, ngược lại bỏ qua âm thầm (SECURITY.md: không trả lỗi cụ thể, tránh lộ ngưỡng).
     *
     * <p>Dùng CHUNG {@link #tryAttackNow} với đường tự đánh của quái — cooldown/tầm/stun/mục tiêu
     * chết đều kiểm y hệt, nên spam gói không đánh nhanh hơn được: cooldown là của server.
     */
    void requestAttack(KEntity entity) {
        tryAttackNow(entity, COOLDOWN.get(entity));
    }

    private void tryAttackNow(KEntity entity, AttackCooldownComponent cooldown) {
        if (cooldown.remainingSeconds > 0f) {
            return;
        }
        if (StatusEffects.isStunned(STATUS_EFFECT.get(entity))) {
            return; // bị stun (Phase 8, hệ Thủy) — không đánh thường tick này, cooldown giữ nguyên <=0.
        }

        TargetComponent target = TARGET.get(entity);
        if (target.targetEntityId == 0L) {
            return; // chưa chọn mục tiêu
        }

        KEntity targetEntity = entityLookup.apply(target.targetEntityId);
        HealthComponent targetHealth = targetEntity == null ? null : HEALTH.get(targetEntity);
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
        CombatConstants.CombatStats attackerStats = StatusEffects.applyModifiers(
                CombatConstants.toStats(COMBAT_STATS.get(attacker)), STATUS_EFFECT.get(attacker));
        CombatConstants.CombatStats defenderStats = StatusEffects.applyModifiers(
                CombatConstants.toStats(COMBAT_STATS.get(target)), STATUS_EFFECT.get(target));
        CombatConstants.AttackResult result = CombatConstants.resolveAttack(attackerStats, defenderStats);
        // ADR-033 Decision #4/#5: đánh thường (KHÔNG phải skill, xem SkillSystem) LUÔN bị coi là
        // "đòn tầm thấp" ở lát cắt MVP này — nhân vật đang lơ lửng (grounded=false, đang nhảy) né
        // TUYỆT ĐỐI, ghi đè RNG hit/dodge (xem CombatConstants.resolveAttack) thay vì cộng dồn xác
        // suất. Phân loại "đòn nào coi là tầm thấp" chi tiết hơn (skill nào, chiêu boss nào) vẫn MỞ
        // (ADR-033 Technical Decision #5 — cần đối chiếu GDD), chỉ áp cho auto-attack ở lát cắt này.
        if (!POSITION.get(target).grounded) {
            result = new CombatConstants.AttackResult(true, false, 0);
        }
        int damageAfterShield = StatusEffects.absorbWithShield(STATUS_EFFECT.get(target), result.damage());
        targetHealth.current = Math.max(0, targetHealth.current - damageAfterShield);
        // ADR-046 D2 — CẢ HAI bên vừa dính tới chiến đấu, kể cả khi đòn này hụt: đứng cạnh một con
        // quái đang vung trượt vẫn là đang đánh nhau, không phải đang nghỉ.
        markInCombat(attacker);
        markInCombat(target);
        // ADR-049 — loài khai `retaliates` bị đánh thì đuổi theo kẻ vừa đánh, kể cả khi aggro = 0.
        // Đây là đường THỨ HAI đặt TargetComponent; AiSystem không còn độc quyền chuyện đó.
        Retaliation.onAttacked(target, attacker);

        long attackerId = entityId(attacker);
        pendingResults.add(new AttackOutcome(
                attackerId,
                targetEntityId,
                damageAfterShield, // damage THẬT trừ vào HP (sau khiên) — client thấy đúng số đã hấp thụ
                result.crit(),
                result.miss(),
                targetHealth.current,
                targetHealth.isDead()));
    }

    /**
     * Chặn hồi máu ngoài chiến đấu trong {@link CombatConstants#REGEN_DELAY_SECONDS} giây tới
     * (ADR-046 D2). Quái KHÔNG mang component này (chúng lành lại bằng đường khác — ADR-046 D4),
     * nên phải hỏi {@code has} trước.
     */
    private static void markInCombat(KEntity entity) {
        if (RECENCY.has(entity)) {
            RECENCY.get(entity).secondsSinceCombat = 0f;
        }
    }

    /** ID nghiệp vụ THẬT của entity (dương = characterId, âm = monsterId) — attacker có thể là 1 trong 2. */
    private static long entityId(KEntity entity) {
        return CHARACTER_ID.has(entity) ? CHARACTER_ID.get(entity).characterId : MONSTER_ID.get(entity).monsterId;
    }

    private static double distance(PositionComponent a, PositionComponent b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt((dx * dx) + (dy * dy));
    }
}
