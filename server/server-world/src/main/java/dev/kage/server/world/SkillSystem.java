package dev.kage.server.world;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.Family;
import dev.kage.engine.ecs.IteratingKSystem;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.scripting.ScriptExecutionException;
import dev.kage.engine.scripting.ScriptSandbox;
import dev.kage.server.world.component.ChiComponent;
import dev.kage.server.world.component.CombatStatsComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.SkillCooldownComponent;
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

/**
 * Dùng kỹ năng (GAMEPLAY.md §3-4) — MỘT skill duy nhất ở lát cắt này ({@link SkillConstants},
 * "fireball"), chưa có {@code skill_template}/{@code character_skill} DB (giống
 * {@code monster_template} chưa DB-driven). Cơ chế (cooldown/Khí/tầm) là JAVA; nội dung damage
 * (hệ số + "đòn kết liễu") nằm ở SCRIPT thật qua {@link ScriptSandbox} (Điều 27, ADR-016) — không
 * hardcode công thức skill trong Java (GAMEPLAY.md §4).
 *
 * <p>{@link #processEntity} CHỈ đếm ngược cooldown mỗi tick (mọi entity có
 * {@link SkillCooldownComponent} — hiện chỉ nhân vật). {@link #tryCast} là hành động RỜI RẠC theo
 * lệnh client ({@code UseSkillCommand}), {@code WorldFacade} gọi trực tiếp — không qua
 * {@code processEntity} vì đây không phải hành vi lặp mỗi tick như đánh thường tự động
 * ({@code CombatSystem}).
 */
final class SkillSystem extends IteratingKSystem {

    private static final Logger LOG = LoggerFactory.getLogger(SkillSystem.class);

    private static final ComponentMapper<PositionComponent> POSITION =
            ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<SkillCooldownComponent> COOLDOWN =
            ComponentMapper.getFor(SkillCooldownComponent.class);
    private static final ComponentMapper<ChiComponent> CHI = ComponentMapper.getFor(ChiComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<CombatStatsComponent> COMBAT_STATS =
            ComponentMapper.getFor(CombatStatsComponent.class);

    /** Kết quả 1 lần dùng skill đã xảy ra thật trong tick này — {@code WorldFacade} drain để phát AoI. */
    record SkillOutcome(
            String skillCode,
            long attackerEntityId,
            long targetEntityId,
            int damage,
            boolean crit,
            boolean miss,
            int targetCurrentHp,
            boolean targetDied) {
    }

    private final LongFunction<KEntity> entityLookup;
    private final ScriptSandbox scriptSandbox = new ScriptSandbox(SkillConstants.SCRIPT_MAX_STATEMENTS);
    private final String fireballScript = loadScript(SkillConstants.FIREBALL_SCRIPT_RESOURCE);
    private final List<SkillOutcome> pendingResults = new ArrayList<>();

    /** @param entityLookup tra KEntity theo entity ID (nhân vật DƯƠNG hoặc quái ÂM — combat.proto). */
    SkillSystem(LongFunction<KEntity> entityLookup) {
        super(Family.all(SkillCooldownComponent.class).get());
        this.entityLookup = entityLookup;
        // SCRIPTING.md §8: validate script lúc khởi động, fail fast — không đợi người chơi bấm
        // skill mới phát hiện lỗi cú pháp. Ném thẳng nếu lỗi (server không nên khởi động với 1
        // skill hỏng script).
        scriptSandbox.eval(fireballScript, Map.of("ctx", Map.of("skill_dmg", 0.0, "target_hp_percent", 1.0)));
    }

    List<SkillOutcome> drainResults() {
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
        if (cooldown.remainingSeconds > 0f) {
            cooldown.remainingSeconds -= deltaTime;
        }
    }

    /**
     * GAMEPLAY.md §3 pipeline — sai bất kỳ điều kiện nào (skill không tồn tại, cooldown chưa
     * xong, thiếu Khí, ngoài tầm, mục tiêu không hợp lệ/đã chết) → ÂM THẦM bỏ qua, giống
     * {@code CombatSystem}/{@code WorldFacade.handleSetTarget} (không rò rỉ lý do cụ thể cho
     * client gian lận dò ngưỡng — SECURITY.md).
     */
    void tryCast(long casterId, String skillCode, long targetEntityId) {
        if (!SkillConstants.FIREBALL_SKILL_CODE.equals(skillCode)) {
            return; // chỉ 1 skill hardcode ở lát cắt này (SkillConstants)
        }
        var caster = entityLookup.apply(casterId);
        if (caster == null || !COOLDOWN.has(caster)) {
            return;
        }
        var cooldown = COOLDOWN.get(caster);
        if (cooldown.remainingSeconds > 0f) {
            return;
        }
        var chi = CHI.get(caster);
        if (chi.current < SkillConstants.FIREBALL_CHI_COST) {
            return;
        }
        var target = entityLookup.apply(targetEntityId);
        if (target == null) {
            return;
        }
        var targetHealth = HEALTH.get(target);
        if (targetHealth.isDead()) {
            return;
        }
        if (distance(POSITION.get(caster), POSITION.get(target)) > SkillConstants.FIREBALL_RANGE_METERS) {
            return;
        }

        cooldown.remainingSeconds = SkillConstants.FIREBALL_COOLDOWN_SECONDS;
        chi.current -= SkillConstants.FIREBALL_CHI_COST;

        var attackerStats = CombatConstants.toStats(COMBAT_STATS.get(caster));
        var defenderStats = CombatConstants.toStats(COMBAT_STATS.get(target));
        double scriptDamage = computeScriptDamage(attackerStats, targetHealth);
        var result = CombatConstants.resolveSkillAttack(attackerStats, defenderStats, scriptDamage);
        targetHealth.current = Math.max(0, targetHealth.current - result.damage());

        pendingResults.add(new SkillOutcome(
                skillCode,
                casterId,
                targetEntityId,
                result.damage(),
                result.crit(),
                result.miss(),
                targetHealth.current,
                targetHealth.isDead()));
    }

    private double computeScriptDamage(CombatConstants.CombatStats attacker, HealthComponent targetHealth) {
        double targetHpPercent = targetHealth.max <= 0 ? 0.0 : (double) targetHealth.current / targetHealth.max;
        var ctx = Map.<String, Object>of(
                "skill_dmg", (double) attacker.skillDmg(),
                "target_hp_percent", targetHpPercent);
        try {
            return scriptSandbox.eval(fireballScript, Map.of("ctx", ctx)).asDouble();
        } catch (ScriptExecutionException e) {
            LOG.error("Script skill '{}' lỗi lúc chạy — coi như damage=0", SkillConstants.FIREBALL_SKILL_CODE, e);
            return 0.0;
        }
    }

    private static double distance(PositionComponent a, PositionComponent b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt((dx * dx) + (dy * dy));
    }

    private static String loadScript(String resourcePath) {
        try (InputStream in = SkillSystem.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Không tìm thấy script: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Đọc script thất bại: " + resourcePath, e);
        }
    }
}
