package dev.kage.server.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.Family;
import dev.kage.engine.ecs.IteratingKSystem;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.scripting.ScriptExecutionException;
import dev.kage.engine.scripting.ScriptSandbox;
import dev.kage.server.world.component.CharacterLevelComponent;
import dev.kage.server.world.component.ChiComponent;
import dev.kage.server.world.component.CombatRecencyComponent;
import dev.kage.server.world.component.CombatStatsComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.SkillCooldownComponent;
import dev.kage.server.world.component.StatusEffectComponent;
import dev.kage.server.world.component.StatusEffectType;

/**
 * Dùng kỹ năng (GAMEPLAY.md §3-4) — registry nhiều skill (Phase 8, 4 hệ phái, {@link SkillRegistry}
 * thay {@code SkillConstants} hardcode 1 skill duy nhất của Phase 4). Cơ chế (cooldown/Khí/tầm/hệ
 * phái/level Tuyệt Kỹ) là JAVA; nội dung (damage/heal/khiên/buff/debuff) nằm ở SCRIPT thật qua
 * {@link ScriptSandbox} (Điều 27, ADR-016) — không hardcode công thức skill trong Java.
 *
 * <p>{@link #processEntity} CHỈ đếm ngược cooldown mỗi tick (mọi entity có
 * {@link SkillCooldownComponent}). {@link #tryCast} là hành động RỜI RẠC theo lệnh client
 * ({@code UseSkillCommand}), {@code WorldFacade} gọi trực tiếp — không qua {@code processEntity}.
 */
final class SkillSystem extends IteratingKSystem {

    private static final Logger LOG = LoggerFactory.getLogger(SkillSystem.class);

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<SkillCooldownComponent> COOLDOWN =
            ComponentMapper.getFor(SkillCooldownComponent.class);
    private static final ComponentMapper<ChiComponent> CHI = ComponentMapper.getFor(ChiComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<CombatStatsComponent> COMBAT_STATS =
            ComponentMapper.getFor(CombatStatsComponent.class);
    private static final ComponentMapper<StatusEffectComponent> STATUS_EFFECT =
            ComponentMapper.getFor(StatusEffectComponent.class);
    private static final ComponentMapper<CharacterLevelComponent> LEVEL =
            ComponentMapper.getFor(CharacterLevelComponent.class);

    /** Kết quả 1 lần dùng skill đã xảy ra thật trong tick này — {@code WorldFacade} drain để phát AoI. */
    record SkillOutcome(
            String skillCode,
            long attackerEntityId,
            long targetEntityId,
            int damage,
            boolean crit,
            boolean miss,
            int targetCurrentHp,
            boolean targetDied,
            SkillEffectKind effectKind) {}

    /**
     * Kết quả script skill trả về, ĐÃ PARSE — {@code effect}: "damage"|"heal"|"shield"|"buff"|
     * "debuff" (mọi giá trị khác coi như "damage", an toàn nếu script trả sai). {@code stat}/
     * {@code duration}/{@code armorPierce} chỉ có ý nghĩa với 1 số {@code effect} nhất định, mặc
     * định rỗng/0 nếu script không set.
     */
    private record ScriptEffect(String effect, double value, String stat, double duration, double armorPierce) {}

    /** Gộp tham số 1 lần cast ĐÃ resolve xong mục tiêu — tránh vượt giới hạn 4 tham số/method. */
    private record CastContext(
            SkillDefinition definition, long casterId, long targetId, KEntity caster, KEntity target) {}

    private static final ComponentMapper<CombatRecencyComponent> RECENCY =
            ComponentMapper.getFor(CombatRecencyComponent.class);

    private final LongFunction<KEntity> entityLookup;
    private final ScriptSandbox scriptSandbox = new ScriptSandbox(SkillConstants.SCRIPT_MAX_STATEMENTS);
    private final Map<String, String> scriptsByCode;
    private final List<SkillOutcome> pendingResults = new ArrayList<>();

    /** @param entityLookup tra KEntity theo entity ID (nhân vật DƯƠNG hoặc quái ÂM — combat.proto). */
    SkillSystem(LongFunction<KEntity> entityLookup) {
        super(Family.all(SkillCooldownComponent.class).get());
        this.entityLookup = entityLookup;
        this.scriptsByCode = SkillRegistry.BY_CODE.values().stream()
                .collect(Collectors.toMap(SkillDefinition::code, def -> loadScript(def.scriptResource())));
        // SCRIPTING.md §8: validate MỌI script lúc khởi động, fail fast (ném thẳng nếu lỗi — server
        // không nên khởi động với 1 skill hỏng script/sai hợp đồng trả về).
        Map<String, Object> dummyCtx = Map.of("skill_dmg", 0.0, "target_hp_percent", 1.0);
        for (String script : scriptsByCode.values()) {
            scriptSandbox.eval(script, Map.of("ctx", dummyCtx), SkillSystem::parseScriptEffect);
        }
    }

    List<SkillOutcome> drainResults() {
        if (pendingResults.isEmpty()) {
            return List.of();
        }
        List<SkillOutcome> results = List.copyOf(pendingResults);
        pendingResults.clear();
        return results;
    }

    @Override
    protected void processEntity(KEntity entity, float deltaTime) {
        SkillCooldownComponent cooldown = COOLDOWN.get(entity);
        for (int i = 0; i < cooldown.remainingSeconds.length; i++) {
            if (cooldown.remainingSeconds[i] > 0f) {
                cooldown.remainingSeconds[i] -= deltaTime;
            }
        }
    }

    /**
     * GAMEPLAY.md §3 pipeline — sai bất kỳ điều kiện nào (skill không tồn tại, sai hệ phái, chưa
     * đủ level Tuyệt Kỹ, bị stun, cooldown chưa xong, thiếu Khí, ngoài tầm, mục tiêu không hợp lệ)
     * → ÂM THẦM bỏ qua (không rò rỉ lý do cụ thể cho client gian lận dò ngưỡng — SECURITY.md).
     */
    void tryCast(long casterId, String skillCode, long targetEntityId) {
        SkillDefinition definition = SkillRegistry.BY_CODE.get(skillCode);
        if (definition == null) {
            return;
        }
        KEntity caster = entityLookup.apply(casterId);
        if (caster == null || !COOLDOWN.has(caster) || !isCasterEligible(caster, definition)) {
            return;
        }
        CastContext ctx = resolveTarget(casterId, caster, definition, targetEntityId);
        if (ctx == null) {
            return;
        }

        if (!consumeCooldownAndChi(caster, definition)) {
            return;
        }

        CombatConstants.CombatStats casterStats = StatusEffects.applyModifiers(
                CombatConstants.toStats(COMBAT_STATS.get(caster)), STATUS_EFFECT.get(caster));
        HealthComponent targetHealth = HEALTH.get(ctx.target());
        double targetHpPercent = targetHealth.max <= 0 ? 0.0 : (double) targetHealth.current / targetHealth.max;
        ScriptEffect scriptEffect = evalSkillScript(definition, casterStats.skillDmg(), targetHpPercent);
        applyScriptEffect(ctx, casterStats, scriptEffect);
    }

    /**
     * Kiểm cooldown + Khí VÀ trừ luôn nếu đủ điều kiện (gộp 1 chỗ — tách khỏi {@link #tryCast} chỉ
     * để giữ độ phức tạp method dưới ngưỡng CyclomaticComplexity, không đổi thứ tự/điều kiện gốc).
     *
     * @return {@code false} nếu còn cooldown hoặc không đủ Khí (KHÔNG trừ gì) — caller bỏ qua âm thầm.
     */
    private boolean consumeCooldownAndChi(KEntity caster, SkillDefinition definition) {
        SkillCooldownComponent cooldown = COOLDOWN.get(caster);
        ChiComponent chi = CHI.get(caster);
        int chiCost = definition.isUltimate() ? chi.current : definition.chiCost();
        // Tuyệt Kỹ cần > 0 Khí hiện có (GDD §4 "tốn TOÀN BỘ Khí"); skill thường cần đủ chiCost cố định.
        if (cooldown.remainingSeconds[definition.slotIndex()] > 0f || chiCost <= 0 || chi.current < chiCost) {
            return false;
        }
        cooldown.remainingSeconds[definition.slotIndex()] = definition.cooldownSeconds();
        chi.current -= chiCost;
        return true;
    }

    private boolean isCasterEligible(KEntity caster, SkillDefinition definition) {
        CombatStatsComponent casterStatsComponent = COMBAT_STATS.get(caster);
        if (casterStatsComponent.element != definition.faction()) {
            return false; // sai hệ phái — chống dùng skill hệ khác (client gửi skillCode tuỳ ý).
        }
        if (definition.requiredLevel() > 1) {
            CharacterLevelComponent level = LEVEL.get(caster);
            if (level == null || level.level < definition.requiredLevel()) {
                return false;
            }
        }
        return !StatusEffects.isStunned(STATUS_EFFECT.get(caster));
    }

    /**
     * ALLY phải là nhân vật (ID dương, không cho "hồi máu" quái); ENEMY không được tự nhắm chính
     * mình (khác SELF/ALLY — cố ý cho phép tự buff/heal). Tách khỏi {@link #resolveTarget} chỉ để
     * giữ độ phức tạp method dưới ngưỡng CyclomaticComplexity, không đổi điều kiện gốc.
     */
    private boolean isTargetKindAllowed(SkillDefinition definition, long casterId, long resolvedTargetId) {
        if (definition.targetKind() == SkillTargetKind.ALLY && resolvedTargetId <= 0) {
            return false;
        }
        return definition.targetKind() != SkillTargetKind.ENEMY || resolvedTargetId != casterId;
    }

    /** @return {@code null} nếu mục tiêu không hợp lệ theo {@code definition.targetKind()} (bỏ qua âm thầm). */
    private CastContext resolveTarget(long casterId, KEntity caster, SkillDefinition definition, long targetEntityId) {
        long resolvedTargetId = definition.targetKind() == SkillTargetKind.SELF ? casterId : targetEntityId;
        if (!isTargetKindAllowed(definition, casterId, resolvedTargetId)) {
            return null;
        }
        KEntity target = entityLookup.apply(resolvedTargetId);
        if (target == null) {
            return null;
        }
        HealthComponent targetHealth = HEALTH.get(target);
        if (targetHealth == null || targetHealth.isDead()) {
            return null;
        }
        if (definition.targetKind() != SkillTargetKind.SELF
                && distance(POSITION.get(caster), POSITION.get(target)) > definition.rangeMeters()) {
            return null;
        }
        return new CastContext(definition, casterId, resolvedTargetId, caster, target);
    }

    private void applyScriptEffect(
            CastContext ctx, CombatConstants.CombatStats casterStats, ScriptEffect scriptEffect) {
        switch (scriptEffect.effect()) {
            case "heal" -> applyHeal(ctx, scriptEffect);
            case "shield" -> applyShield(ctx, scriptEffect);
            case "buff" -> applyStatusOutcome(ctx, scriptEffect, SkillEffectKind.BUFF);
            case "debuff" -> applyStatusOutcome(ctx, scriptEffect, SkillEffectKind.DEBUFF);
            default -> applyDamage(ctx, casterStats, scriptEffect);
        }
    }

    private void applyDamage(CastContext ctx, CombatConstants.CombatStats casterStats, ScriptEffect scriptEffect) {
        StatusEffectComponent targetStatus = STATUS_EFFECT.get(ctx.target());
        CombatConstants.CombatStats targetStats =
                StatusEffects.applyModifiers(CombatConstants.toStats(COMBAT_STATS.get(ctx.target())), targetStatus);
        CombatConstants.AttackResult result = CombatConstants.resolveSkillAttack(
                casterStats, targetStats, scriptEffect.value(), (float) scriptEffect.armorPierce());
        HealthComponent targetHealth = HEALTH.get(ctx.target());
        int damageAfterShield = StatusEffects.absorbWithShield(targetStatus, result.damage());
        targetHealth.current = Math.max(0, targetHealth.current - damageAfterShield);
        // ADR-046 D2 — chỉ nhánh DAMAGE chặn hồi máu, cùng luật với đòn đánh thường
        // (CombatSystem.markInCombat). Buff/khiên/hồi máu cho đồng đội KHÔNG tính là đang đánh
        // nhau: chúng không đụng tới máu của ai theo hướng xấu, và tính vào đó thì một người hệ
        // Thuỷ tự khiên cho mình lại tự hoãn hồi máu của chính mình.
        markInCombat(ctx.caster());
        markInCombat(ctx.target());
        // ADR-050 D3 — chiêu gây damage cũng là RA ĐÒN, cùng luật với đòn thường.
        markAttackDealt(ctx.caster());
        // ADR-049 D5 — áp cho CẢ đòn kỹ năng, không riêng đòn thường: một con boss chỉ đánh trả khi
        // bị đánh thường còn đứng im khi ăn kỹ năng là luật chơi không ai đoán được.
        Retaliation.onAttacked(ctx.target(), ctx.caster());

        pendingResults.add(new SkillOutcome(
                ctx.definition().code(),
                ctx.casterId(),
                ctx.targetId(),
                damageAfterShield,
                result.crit(),
                result.miss(),
                targetHealth.current,
                targetHealth.isDead(),
                SkillEffectKind.DAMAGE));
    }

    /** Xem {@code CombatSystem.markInCombat} — quái KHÔNG mang component này (ADR-046 D4). */
    private static void markInCombat(KEntity entity) {
        if (RECENCY.has(entity)) {
            RECENCY.get(entity).secondsSinceCombat = 0f;
        }
    }

    /** ADR-050 D3 — xem {@code CombatRecencyComponent.secondsSinceAttackDealt}. */
    private static void markAttackDealt(KEntity entity) {
        if (RECENCY.has(entity)) {
            RECENCY.get(entity).secondsSinceAttackDealt = 0f;
        }
    }

    private void applyHeal(CastContext ctx, ScriptEffect scriptEffect) {
        HealthComponent targetHealth = HEALTH.get(ctx.target());
        int healAmount = Math.max(0, Math.round((float) scriptEffect.value()));
        int before = targetHealth.current;
        targetHealth.current = Math.min(targetHealth.max, targetHealth.current + healAmount);

        pendingResults.add(new SkillOutcome(
                ctx.definition().code(),
                ctx.casterId(),
                ctx.targetId(),
                targetHealth.current - before,
                false,
                false,
                targetHealth.current,
                false,
                SkillEffectKind.HEAL));
    }

    private void applyShield(CastContext ctx, ScriptEffect scriptEffect) {
        StatusEffects.applyEffect(
                STATUS_EFFECT.get(ctx.target()), StatusEffectType.SHIELD, (float) scriptEffect.value(), (float)
                        scriptEffect.duration());
        HealthComponent targetHealth = HEALTH.get(ctx.target());
        pendingResults.add(new SkillOutcome(
                ctx.definition().code(),
                ctx.casterId(),
                ctx.targetId(),
                Math.round((float) scriptEffect.value()),
                false,
                false,
                targetHealth.current,
                false,
                SkillEffectKind.SHIELD));
    }

    private void applyStatusOutcome(CastContext ctx, ScriptEffect scriptEffect, SkillEffectKind kind) {
        StatusEffectType type = parseStatusEffectType(scriptEffect.stat());
        if (type != null) {
            StatusEffects.applyEffect(STATUS_EFFECT.get(ctx.target()), type, (float) scriptEffect.value(), (float)
                    scriptEffect.duration());
        }
        HealthComponent targetHealth = HEALTH.get(ctx.target());
        pendingResults.add(new SkillOutcome(
                ctx.definition().code(),
                ctx.casterId(),
                ctx.targetId(),
                Math.round((float) scriptEffect.value() * 100),
                false,
                false,
                targetHealth.current,
                false,
                kind));
    }

    private static StatusEffectType parseStatusEffectType(String stat) {
        return switch (stat) {
            case "move_speed" -> StatusEffectType.MOVE_SPEED_MOD;
            case "stun" -> StatusEffectType.STUN;
            case "def" -> StatusEffectType.DEF_MOD;
            case "res" -> StatusEffectType.RES_MOD;
            case "crit" -> StatusEffectType.CRIT_MOD;
            default -> null; // script sai giá trị "stat" — bỏ qua ghi status, vẫn phát outcome (an toàn).
        };
    }

    private ScriptEffect evalSkillScript(SkillDefinition definition, double skillDmg, double targetHpPercent) {
        Map<String, Object> ctx = Map.<String, Object>of("skill_dmg", skillDmg, "target_hp_percent", targetHpPercent);
        try {
            String script = scriptsByCode.get(definition.code());
            return scriptSandbox.eval(script, Map.of("ctx", ctx), SkillSystem::parseScriptEffect);
        } catch (ScriptExecutionException e) {
            LOG.error("Script skill '{}' lỗi lúc chạy — coi như effect=damage, value=0", definition.code(), e);
            return new ScriptEffect("damage", 0.0, "", 0.0, 0.0);
        }
    }

    private static ScriptEffect parseScriptEffect(org.graalvm.polyglot.Value value) {
        return new ScriptEffect(
                value.getMember("effect").asString(),
                value.getMember("value").asDouble(),
                value.hasMember("stat") ? value.getMember("stat").asString() : "",
                value.hasMember("duration") ? value.getMember("duration").asDouble() : 0.0,
                value.hasMember("armor_pierce")
                        ? value.getMember("armor_pierce").asDouble()
                        : 0.0);
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
