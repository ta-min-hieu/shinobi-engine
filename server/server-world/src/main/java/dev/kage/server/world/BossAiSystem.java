package dev.kage.server.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import dev.kage.server.world.component.CombatRecencyComponent;
import dev.kage.server.world.component.CombatStatsComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MonsterIdComponent;
import dev.kage.server.world.component.StatusEffectComponent;
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
 * <p>Throttle hỏi script mỗi {@code ai_decision_interval_seconds} — nhịp RIÊNG của từng boss, đọc
 * từ {@code monster_template} và mang theo trên {@link BossAiComponent} (ADR-048 D4), KHÔNG mỗi
 * tick 60Hz (AI.md §9 "không giả định chạy mỗi tick"). Trước ADR-048 đây là một hằng số Java, nên
 * MỌI boss buộc phải suy nghĩ cùng một nhịp — con thứ hai của cốt truyện là kẻ cải trang, hắn cần
 * nhịp khác con máy móc của Phase 8.
 */
final class BossAiSystem extends IteratingKSystem {

    private static final Logger LOG = LoggerFactory.getLogger(BossAiSystem.class);

    private static final ComponentMapper<AiStateComponent> AI_STATE = ComponentMapper.getFor(AiStateComponent.class);
    private static final ComponentMapper<TargetComponent> TARGET = ComponentMapper.getFor(TargetComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<CombatRecencyComponent> RECENCY =
            ComponentMapper.getFor(CombatRecencyComponent.class);
    private static final ComponentMapper<CombatStatsComponent> COMBAT_STATS =
            ComponentMapper.getFor(CombatStatsComponent.class);
    private static final ComponentMapper<BossAiComponent> BOSS_AI = ComponentMapper.getFor(BossAiComponent.class);
    private static final ComponentMapper<MonsterIdComponent> MONSTER_ID =
            ComponentMapper.getFor(MonsterIdComponent.class);
    private static final ComponentMapper<StatusEffectComponent> STATUS_EFFECT =
            ComponentMapper.getFor(StatusEffectComponent.class);

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

    /**
     * Script theo ĐƯỜNG DẪN, không phải một chuỗi duy nhất (ADR-048 D3).
     *
     * <p>Trước đây lớp này giữ `bossScript` nạp lúc khởi tạo, nên cả tiến trình chỉ có MỘT hành vi
     * boss — boss thứ hai không có chỗ nào khai hành vi riêng, và cách "sửa" hiển nhiên là một
     * {@code if (monsterCode.equals(...))}, đúng thứ Điều 27 sinh ra để tránh. Cache theo ref cùng
     * khuôn {@code QuestScriptRunner.scriptSourceCache}: script không đổi lúc chạy.
     */
    private final Map<String, String> scriptByRef = new ConcurrentHashMap<>();

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
        // ⚠️ KHÔNG còn validate script ở đây, và đó là hệ quả có ý thức của ADR-048: lúc dựng system
        // chưa biết boss nào sẽ spawn nên chưa có ref nào để nạp. Fail fast chuyển sang
        // `ScriptValidator` lúc khởi động server — cùng chỗ đã duyệt script quest và hội thoại
        // (SCRIPTING.md §8).
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
        bossAi.decisionCooldownSeconds = bossAi.decisionIntervalSeconds;

        if (AI_STATE.get(entity).state != AiState.ATTACK || HEALTH.get(entity).isDead() || isStunned(entity)) {
            // Chưa vào tầm/chưa có mục tiêu hợp lệ — AiSystem lo phần này. Hoặc boss vừa bị hạ gục
            // từ hàng đợi lệnh tick này: entity chỉ bị xoá SAU update(), cái xác không được ra chiêu.
            // Hoặc đang bị stun (sửa 2026-10-02): AiSystem giữ nguyên state ATTACK suốt cơn choáng,
            // nên thiếu kiểm này boss đứng im, không đánh thường được, mà vẫn tung chiêu nặng.
            return;
        }
        TargetComponent target = TARGET.get(entity);
        KEntity targetEntity = entityLookup.apply(target.targetEntityId);
        long targetEntityId = target.targetEntityId;
        HealthComponent targetHealth = targetEntity == null ? null : HEALTH.get(targetEntity);
        if (targetHealth == null || targetHealth.isDead()) {
            return;
        }

        ScriptDecision decision = evalScript(
                bossAi,
                healthPercent(HEALTH.get(entity)),
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
        bossAi.heavyStrikeCooldownSeconds = bossAi.skillCooldownSeconds;

        // Buff/debuff + khiên của CẢ HAI bên, cùng luật với CombatSystem/SkillSystem (sửa 2026-10-02 —
        // trước đó chiêu boss đọc chỉ số gốc và trừ thẳng vào máu, khiên hệ Thổ vô dụng trước boss).
        CombatConstants.CombatStats attackerStats = withModifiers(entity);
        CombatConstants.CombatStats defenderStats = withModifiers(targetEntity);
        CombatConstants.AttackResult result =
                CombatConstants.resolveSkillAttack(attackerStats, defenderStats, decision.rawDamage());
        int damage = STATUS_EFFECT.has(targetEntity)
                ? StatusEffects.absorbWithShield(STATUS_EFFECT.get(targetEntity), result.damage())
                : result.damage();
        HealthComponent targetHealth = HEALTH.get(targetEntity);
        targetHealth.current = Math.max(0, targetHealth.current - damage);
        // ADR-046 D2 — chiêu boss cũng chặn hồi máu ngoài chiến đấu, y hệt đòn thường. Boss là quái
        // (không mang component này), nên chỉ đánh dấu bên chịu đòn.
        if (RECENCY.has(targetEntity)) {
            RECENCY.get(targetEntity).secondsSinceCombat = 0f;
        }

        pendingResults.add(new BossActionOutcome(
                decision.action(),
                MONSTER_ID.get(entity).monsterId,
                targetEntityId,
                damage, // damage THẬT trừ vào máu (sau khiên) — cùng quy ước với AttackOutcome.
                result.crit(),
                result.miss(),
                targetHealth.current,
                targetHealth.isDead()));
    }

    /**
     * Nhận thẳng {@link BossAiComponent} thay vì rải {@code scriptRef} + {@code alreadyEnraged}
     * thành hai tham số rời — cùng cách {@link #applyDecision} đã tự tra lại component để giữ dưới
     * trần 4 tham số (Checkstyle ParameterNumber). ADR-048 thêm {@code scriptRef} vào đây và đó là
     * tham số thứ NĂM; gói cả hai về component là đường duy nhất không dựng thêm một kiểu chỉ để
     * đếm tham số.
     */
    private ScriptDecision evalScript(
            BossAiComponent bossAi, double hpPercent, boolean heavyStrikeReady, double baseAtk) {
        Map<String, Object> ctx = Map.<String, Object>of(
                "hp_percent", hpPercent,
                "already_enraged", bossAi.enraged,
                "heavy_strike_ready", heavyStrikeReady,
                "base_atk", baseAtk);
        try {
            return scriptSandbox.eval(
                    scriptByRef.computeIfAbsent(bossAi.scriptRef, BossAiSystem::loadScript),
                    Map.of("ctx", ctx),
                    // Sai hình dạng ⇒ ScriptExecutionException (ScriptResults), không NPE/CCE lọt khỏi tick.
                    value -> new ScriptDecision(
                            ScriptResults.requireString(value, "action"),
                            ScriptResults.requireFiniteNumber(value, "raw_damage")));
        } catch (ScriptExecutionException e) {
            LOG.error("Script boss '{}' lỗi lúc chạy — coi như basic_attack", bossAi.scriptRef, e);
            return new ScriptDecision("basic_attack", 0.0);
        }
    }

    private static boolean isStunned(KEntity entity) {
        return STATUS_EFFECT.has(entity) && StatusEffects.isStunned(STATUS_EFFECT.get(entity));
    }

    private static CombatConstants.CombatStats withModifiers(KEntity entity) {
        CombatConstants.CombatStats base = CombatConstants.toStats(COMBAT_STATS.get(entity));
        return STATUS_EFFECT.has(entity) ? StatusEffects.applyModifiers(base, STATUS_EFFECT.get(entity)) : base;
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
