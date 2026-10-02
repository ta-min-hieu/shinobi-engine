package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.server.world.component.AiState;
import dev.kage.server.world.component.AiStateComponent;
import dev.kage.server.world.component.BossAiComponent;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.CombatStatsComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MonsterIdComponent;
import dev.kage.server.world.component.StatusEffect;
import dev.kage.server.world.component.StatusEffectComponent;
import dev.kage.server.world.component.StatusEffectType;
import dev.kage.server.world.component.TargetComponent;

/**
 * Chiêu đặc biệt của boss phải tôn trọng hiệu ứng trạng thái y như đòn thường và kỹ năng người chơi
 * (sửa 2026-10-02). Trước đó {@code BossAiSystem} không đọc {@link StatusEffectComponent} ở đâu cả:
 *
 * <ul>
 *   <li>Boss bị STUN vẫn tung {@code heavy_strike}/{@code enrage} — {@code AiSystem} đứng yên và
 *       {@code CombatSystem} chặn đòn thường khi bị stun, nhưng FSM vẫn ở {@code ATTACK} nên nhịp
 *       quyết định của boss cứ chạy.
 *   <li>KHIÊN của mục tiêu (hệ Thổ) không hấp thụ chiêu boss — {@code CombatSystem}/{@code SkillSystem}
 *       đều trừ khiên trước, riêng đường này trừ thẳng vào máu.
 * </ul>
 */
class BossAiSystemStatusEffectTest {

    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<StatusEffectComponent> STATUS =
            ComponentMapper.getFor(StatusEffectComponent.class);

    private static final long CHARACTER_ID = 1L;
    private static final long BOSS_ID = -1L;

    private static KEntity buildBoss(EcsWorld world, int hp) {
        KEntity entity = world.createEntity();
        entity.add(new PositionComponent());
        AiStateComponent ai = new AiStateComponent();
        ai.state = AiState.ATTACK;
        entity.add(ai);
        TargetComponent target = new TargetComponent();
        target.targetEntityId = CHARACTER_ID;
        entity.add(target);
        HealthComponent health = new HealthComponent();
        health.current = hp;
        health.max = 320;
        entity.add(health);
        CombatStatsComponent stats = new CombatStatsComponent();
        stats.atk = 50;
        stats.hitPercent = 1f;
        stats.critPercent = 0f;
        stats.critDamageMultiplier = 1.5f;
        stats.element = Element.NONE;
        entity.add(stats);
        BossAiComponent bossAi = new BossAiComponent();
        bossAi.scriptRef = "/scripts/boss/sample_boss.js";
        bossAi.decisionIntervalSeconds = 0.5f;
        bossAi.skillCooldownSeconds = 3f;
        entity.add(bossAi); // decision/heavy-strike cooldown 0 ⇒ quyết định NGAY tick này.
        MonsterIdComponent id = new MonsterIdComponent();
        id.monsterId = BOSS_ID;
        entity.add(id);
        return entity;
    }

    private static KEntity buildCharacter(EcsWorld world) {
        KEntity entity = world.createEntity();
        entity.add(new PositionComponent());
        CharacterIdComponent id = new CharacterIdComponent();
        id.characterId = CHARACTER_ID;
        entity.add(id);
        HealthComponent health = new HealthComponent();
        health.current = 100;
        health.max = 100;
        entity.add(health);
        CombatStatsComponent stats = new CombatStatsComponent();
        stats.element = Element.NONE;
        entity.add(stats);
        return entity;
    }

    /** Boss còn sống dưới 30% máu ⇒ không bị gì thì tung enrage NGAY tick đầu (xem lớp kia). */
    @Test
    void shouldNotCastAnything_whileTheBossIsStunned() {
        EcsWorld world = new EcsWorld();
        KEntity boss = buildBoss(world, 10);
        StatusEffectComponent bossStatus = new StatusEffectComponent();
        bossStatus.slots[0] = new StatusEffect(StatusEffectType.STUN, 0f, 2.5f);
        boss.add(bossStatus);
        KEntity character = buildCharacter(world);
        BossAiSystem system = new BossAiSystem(Map.of(CHARACTER_ID, character)::get);
        world.addSystem(system);

        world.update(1f / 60f);

        assertThat(system.drainResults()).isEmpty();
        assertThat(HEALTH.get(character).current).isEqualTo(100);
    }

    @Test
    void shouldDrainTheTargetsShieldBeforeItsHealth_whenTheBossCasts() {
        EcsWorld world = new EcsWorld();
        buildBoss(world, 10);
        KEntity character = buildCharacter(world);
        StatusEffectComponent shield = new StatusEffectComponent();
        shield.slots[0] = new StatusEffect(StatusEffectType.SHIELD, 1000f, 8f);
        character.add(shield);
        BossAiSystem system = new BossAiSystem(Map.of(CHARACTER_ID, character)::get);
        world.addSystem(system);

        world.update(1f / 60f);

        assertThat(system.drainResults())
                .extracting(BossAiSystem.BossActionOutcome::actionCode)
                .containsExactly("enrage");
        assertThat(HEALTH.get(character).current).isEqualTo(100);
        assertThat(STATUS.get(character).slots[0].magnitude).isLessThan(1000f);
    }
}
