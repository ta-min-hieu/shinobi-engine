package dev.kage.server.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.Family;
import dev.kage.engine.ecs.IteratingKSystem;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.scripting.ScriptExecutionException;
import dev.kage.engine.scripting.ScriptSandbox;
import dev.kage.server.world.component.AiState;
import dev.kage.server.world.component.AiStateComponent;
import dev.kage.server.world.component.BossAiComponent;
import dev.kage.server.world.component.CombatStatsComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MonsterIdComponent;
import dev.kage.server.world.component.TargetComponent;

/**
 * Boss AI (Phase 8 lát cắt 2, AI.md §4) — CƠ CHẾ (cooldown, throttle, tính damage) là JAVA; NỘI
 * DUNG (pha theo %HP, chọn chiêu) nằm ở SCRIPT thật qua {@link ScriptSandbox} (Điều 27, ADR-016) —
 * cùng cách {@link SkillSystem} tách cơ chế/nội dung cho skill người chơi.
 *
 * <p>KHÔNG thay thế {@link AiSystem} — boss vẫn có đủ {@link AiStateComponent}/
 * {@link TargetComponent}/{@code AttackCooldownComponent} như quái thường nên di chuyển/aggro/đánh
 * thường {@code AiSystem}/{@code CombatSystem} tự lo, KHÔNG đổi gì ở 2 System đó. System này CHỈ
 * cộng thêm quyết định "dùng chiêu đặc biệt ngay bây giờ không" khi boss đang {@link AiState#ATTACK}
 * (đã vào tầm, do {@code AiSystem} tự set).
 *
 * <p>Throttle hỏi script mỗi {@link BossConstants#DECISION_INTERVAL_SECONDS} (AI.md §9 "không giả
 * định chạy mỗi tick"), KHÔNG mỗi tick 60Hz — script vài dòng if/return vẫn rẻ nhưng số boss có thể
 * tăng, thiết kế cho phép throttle ngay từ đầu.
 */
final class BossAiSystem extends IteratingKSystem {

    private static final Logger LOG = LoggerFactory.getLogger(BossAiSystem.class);

    private static final ComponentMapper<AiStateComponent> AI_STATE = ComponentMapper.getFor(AiStateComponent.class);
    private static final ComponentMapper<TargetComponent> TARGET = ComponentMapper.getFor(TargetComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<CombatStatsComponent> COMBAT_STATS =
            ComponentMapper.getFor(CombatStatsComponent.class);
    private static final ComponentMapper<BossAiComponent> BOSS_AI = ComponentMapper.getFor(BossAiComponent.class);
    private static final ComponentMapper<MonsterIdComponent> MONSTER_ID =
            ComponentMapper.getFor(MonsterIdComponent.class);

    /** Kết quả 1 chiêu đặc biệt đã xảy ra thật trong tick này — {@code WorldFacade} drain để phát AoI. */
    record BossActionOutcome(
            String actionCode,
            long attackerEntityId,
            long targetEntityId,
            int damage,
            boolean crit,
            boolean miss,
            int targetCurrentHp,
            boolean targetDied) {}

    private record ScriptDecision(String action, double rawDamage) {}

    private final LongFunction<KEntity> entityLookup;
    private final ScriptSandbox scriptSandbox = new ScriptSandbox(BossConstants.SCRIPT_MAX_STATEMENTS);
    private final String bossScript = loadScript(BossConstants.BOSS_SCRIPT_RESOURCE);
    private final List<BossActionOutcome> pendingResults = new ArrayList<>();

    /** @param entityLookup tra KEntity theo entity ID (nhân vật DƯƠNG hoặc quái ÂM — combat.proto). */
    BossAiSystem(LongFunction<KEntity> entityLookup) {
        super(Family.all(
                        BossAiComponent.class,
                        AiStateComponent.class,
                        TargetComponent.class,
                        HealthComponent.class,
                        CombatStatsComponent.class,
                        PositionComponent.class)
                .get());
        this.entityLookup = entityLookup;
        // SCRIPTING.md §8: validate script lúc khởi động, fail fast — cùng lý do SkillSystem.
        evalScript(0.0, false, false, 0.0);
    }

    List<BossActionOutcome> drainResults() {
        if (pendingResults.isEmpty()) {
            return List.of();
        }
        List<BossActionOutcome> results = List.copyOf(pendingResults);
        pendingResults.clear();
        return results;
    }

    @Override
    protected void processEntity(KEntity entity, float deltaTime) {
        BossAiComponent bossAi = BOSS_AI.get(entity);
        if (bossAi.heavyStrikeCooldownSeconds > 0f) {
            bossAi.heavyStrikeCooldownSeconds -= deltaTime;
        }
        if (bossAi.decisionCooldownSeconds > 0f) {
            bossAi.decisionCooldownSeconds -= deltaTime;
            return;
        }
        bossAi.decisionCooldownSeconds = BossConstants.DECISION_INTERVAL_SECONDS;

        if (AI_STATE.get(entity).state != AiState.ATTACK) {
            return; // chưa vào tầm/chưa có mục tiêu hợp lệ — AiSystem lo phần này.
        }
        TargetComponent target = TARGET.get(entity);
        KEntity targetEntity = entityLookup.apply(target.targetEntityId);
        long targetEntityId = target.targetEntityId;
        HealthComponent targetHealth = targetEntity == null ? null : HEALTH.get(targetEntity);
        if (targetHealth == null || targetHealth.isDead()) {
            return;
        }

        ScriptDecision decision = evalScript(
                healthPercent(HEALTH.get(entity)),
                bossAi.enraged,
                bossAi.heavyStrikeCooldownSeconds <= 0f,
                CombatConstants.toStats(COMBAT_STATS.get(entity)).atk());
        applyDecision(entity, targetEntity, targetEntityId, decision);
    }

    /**
     * Tách khỏi {@link #processEntity} để giữ dưới giới hạn Checkstyle MethodLength — tự tra lại
     * component qua mapper thay vì nhận thêm tham số (giữ dưới 4 tham số, ParameterNumber).
     */
    private void applyDecision(KEntity entity, KEntity targetEntity, long targetEntityId, ScriptDecision decision) {
        if ("basic_attack".equals(decision.action())) {
            return; // đánh thường vẫn chạy độc lập qua CombatSystem — không làm gì thêm ở đây.
        }
        BossAiComponent bossAi = BOSS_AI.get(entity);
        if ("enrage".equals(decision.action())) {
            bossAi.enraged = true;
        } else if (!"heavy_strike".equals(decision.action())) {
            return; // script trả action lạ (lỗi/không mong đợi) — bỏ qua an toàn, không hardcode giả định.
        }
        bossAi.heavyStrikeCooldownSeconds = BossConstants.HEAVY_STRIKE_COOLDOWN_SECONDS;

        CombatConstants.CombatStats attackerStats = CombatConstants.toStats(COMBAT_STATS.get(entity));
        CombatConstants.CombatStats defenderStats = CombatConstants.toStats(COMBAT_STATS.get(targetEntity));
        CombatConstants.AttackResult result =
                CombatConstants.resolveSkillAttack(attackerStats, defenderStats, decision.rawDamage());
        HealthComponent targetHealth = HEALTH.get(targetEntity);
        targetHealth.current = Math.max(0, targetHealth.current - result.damage());

        pendingResults.add(new BossActionOutcome(
                decision.action(),
                MONSTER_ID.get(entity).monsterId,
                targetEntityId,
                result.damage(),
                result.crit(),
                result.miss(),
                targetHealth.current,
                targetHealth.isDead()));
    }

    private ScriptDecision evalScript(
            double hpPercent, boolean alreadyEnraged, boolean heavyStrikeReady, double baseAtk) {
        Map<String, Object> ctx = Map.<String, Object>of(
                "hp_percent", hpPercent,
                "already_enraged", alreadyEnraged,
                "heavy_strike_ready", heavyStrikeReady,
                "base_atk", baseAtk);
        try {
            return scriptSandbox.eval(
                    bossScript,
                    Map.of("ctx", ctx),
                    value -> new ScriptDecision(
                            value.getMember("action").asString(),
                            value.getMember("raw_damage").asDouble()));
        } catch (ScriptExecutionException e) {
            LOG.error("Script boss '{}' lỗi lúc chạy — coi như basic_attack", BossConstants.BOSS_CODE, e);
            return new ScriptDecision("basic_attack", 0.0);
        }
    }

    private static double healthPercent(HealthComponent health) {
        return health.max <= 0 ? 0.0 : (double) health.current / health.max;
    }

    private static String loadScript(String resourcePath) {
        try (InputStream in = BossAiSystem.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Không tìm thấy script: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Đọc script thất bại: " + resourcePath, e);
        }
    }
}
