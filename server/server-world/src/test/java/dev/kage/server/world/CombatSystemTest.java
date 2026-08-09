package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.server.world.component.AttackCooldownComponent;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.CombatStatsComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.StatusEffectComponent;
import dev.kage.server.world.component.TargetComponent;

/**
 * ADR-033 Decision #4 — nhân vật đang lơ lửng (grounded=false) né TUYỆT ĐỐI đòn đánh thường, bất
 * kể hit%/dodge% (xem {@code CombatSystem.resolveAndRecordAttack}). Test trực tiếp
 * {@link CombatSystem} (không qua {@code WorldFacade}/tick loop thật) để tránh phụ thuộc thời gian
 * cửa sổ nhảy (~0.7s, xem {@code WorldConstants.JUMP_VELOCITY_MPS}/{@code GRAVITY_MPS2}) trùng đúng
 * lúc quái hết cooldown đánh (1s) — rất dễ flaky nếu test qua tick loop thật.
 */
class CombatSystemTest {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);

    private static final long ATTACKER_ID = 1L;
    private static final long DEFENDER_ID = 2L;

    private KEntity buildAttacker(EcsWorld world) {
        KEntity entity = world.createEntity();
        entity.add(new PositionComponent());
        AttackCooldownComponent cooldown = new AttackCooldownComponent();
        cooldown.remainingSeconds = 0f;
        entity.add(cooldown);
        TargetComponent target = new TargetComponent();
        target.targetEntityId = DEFENDER_ID;
        entity.add(target);
        CombatStatsComponent stats = new CombatStatsComponent();
        stats.atk = 50; // đủ cao để chắc chắn damage > 0 khi trúng (defender.def = 0).
        stats.hitPercent = 1f;
        stats.critPercent = 0f;
        stats.critDamageMultiplier = 1.5f;
        stats.element = Element.NONE;
        entity.add(stats);
        CharacterIdComponent id = new CharacterIdComponent();
        id.characterId = ATTACKER_ID;
        entity.add(id);
        entity.add(new StatusEffectComponent());
        return entity;
    }

    private KEntity buildDefender(EcsWorld world, boolean grounded) {
        KEntity entity = world.createEntity();
        entity.add(new PositionComponent());
        POSITION.get(entity).grounded = grounded;
        HealthComponent health = new HealthComponent();
        health.current = 100;
        health.max = 100;
        entity.add(health);
        CombatStatsComponent stats = new CombatStatsComponent();
        stats.def = 0;
        stats.dodgePercent = 0f;
        stats.element = Element.NONE;
        entity.add(stats);
        CharacterIdComponent id = new CharacterIdComponent();
        id.characterId = DEFENDER_ID;
        entity.add(id);
        entity.add(new StatusEffectComponent());
        return entity;
    }

    @Test
    void shouldAlwaysMissAutoAttack_whenDefenderAirborne() {
        EcsWorld world = new EcsWorld();
        KEntity attacker = buildAttacker(world);
        KEntity defender = buildDefender(world, false); // grounded=false — đang lơ lửng (ADR-033)
        Map<Long, KEntity> byId = Map.of(ATTACKER_ID, attacker, DEFENDER_ID, defender);
        CombatSystem combatSystem = new CombatSystem(byId::get);
        world.addSystem(combatSystem);

        world.update(0.016f);

        List<CombatSystem.AttackOutcome> results = combatSystem.drainResults();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).miss()).isTrue();
        assertThat(results.get(0).damage()).isZero();
        assertThat(HEALTH.get(defender).current).isEqualTo(100); // HP không đổi — đòn không hề trừ.
    }

    @Test
    void shouldResolveNormally_whenDefenderGrounded() {
        EcsWorld world = new EcsWorld();
        KEntity attacker = buildAttacker(world);
        KEntity defender = buildDefender(world, true); // grounded=true — hành vi cũ, không đổi
        Map<Long, KEntity> byId = Map.of(ATTACKER_ID, attacker, DEFENDER_ID, defender);
        CombatSystem combatSystem = new CombatSystem(byId::get);
        world.addSystem(combatSystem);

        world.update(0.016f);

        List<CombatSystem.AttackOutcome> results = combatSystem.drainResults();
        assertThat(results).hasSize(1);
        // hitPercent=1, dodgePercent=0 ở buildDefender/buildAttacker — đòn LUÔN trúng khi grounded.
        assertThat(results.get(0).miss()).isFalse();
        assertThat(results.get(0).damage()).isGreaterThan(0);
    }
}
