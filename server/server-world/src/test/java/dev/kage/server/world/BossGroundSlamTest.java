package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.HashMap;
import java.util.List;
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
import dev.kage.server.world.component.StatusEffectComponent;
import dev.kage.server.world.component.TargetComponent;

/**
 * ADR-145 — dậm đất của Cổ Thạch Khổng Lồ: gồng 1,5 giây (vòng báo trước, boss tự choáng, chưa ai mất máu), rồi nổ
 * vào MỌI người còn đứng trong bán kính 4 m — người ở ngoài vòng, hoặc bước ra kịp, không mất gì.
 */
class BossGroundSlamTest {

    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<BossAiComponent> BOSS_AI = ComponentMapper.getFor(BossAiComponent.class);

    private static final float TICK = 1f / 60f;
    private static final long NEAR_ID = 1L;
    private static final long FAR_ID = 2L;

    @Test
    void shouldTelegraphFirst_thenHitEveryoneStillInsideTheRing() {
        EcsWorld world = new EcsWorld();
        KEntity boss = buildBoss(world);
        KEntity near = buildCharacter(world, NEAR_ID, 2f);
        KEntity far = buildCharacter(world, FAR_ID, 6f);
        BossAiSystem system = system(near, far);
        world.addSystem(system);

        world.update(TICK);

        List<BossAiSystem.BossTelegraph> telegraphs = system.drainTelegraphs();
        assertThat(telegraphs).hasSize(1);
        assertThat(telegraphs.get(0).radius()).isEqualTo(BossConstants.SLAM_RADIUS_METERS);
        assertThat(system.drainResults()).isEmpty();
        assertThat(BOSS_AI.get(boss).slamSecondsLeft).isPositive();

        runFor(world, BossConstants.SLAM_TELEGRAPH_SECONDS + TICK);

        assertThat(system.drainResults())
                .extracting(BossAiSystem.BossActionOutcome::targetEntityId, BossAiSystem.BossActionOutcome::actionCode)
                .containsExactly(tuple(NEAR_ID, "ground_slam"));
        assertThat(HEALTH.get(near).current).isLessThan(500);
        assertThat(HEALTH.get(far).current).isEqualTo(500);
    }

    @Test
    void shouldMissSomeoneWhoStepsOutBeforeItLands() {
        EcsWorld world = new EcsWorld();
        buildBoss(world);
        KEntity near = buildCharacter(world, NEAR_ID, 2f);
        BossAiSystem system = system(near);
        world.addSystem(system);
        world.update(TICK);

        POSITION.get(near).x = 5f; // bước ra khỏi vòng 4 m trong lúc boss gồng
        runFor(world, BossConstants.SLAM_TELEGRAPH_SECONDS + TICK);

        assertThat(system.drainResults()).isEmpty();
        assertThat(HEALTH.get(near).current).isEqualTo(500);
    }

    private static BossAiSystem system(KEntity... characters) {
        Map<Long, KEntity> byId = new HashMap<>();
        for (KEntity character : characters) {
            byId.put(ComponentMapper.getFor(CharacterIdComponent.class).get(character).characterId, character);
        }
        return new BossAiSystem(byId::get, ThreadLocalRandomSource.INSTANCE, () -> List.of(characters));
    }

    private static void runFor(EcsWorld world, float seconds) {
        for (float t = 0f; t < seconds; t += TICK) {
            world.update(TICK);
        }
    }

    private static KEntity buildBoss(EcsWorld world) {
        KEntity entity = world.createEntity();
        entity.add(new PositionComponent());
        AiStateComponent ai = new AiStateComponent();
        ai.state = AiState.ATTACK;
        entity.add(ai);
        TargetComponent target = new TargetComponent();
        target.targetEntityId = NEAR_ID;
        entity.add(target);
        HealthComponent health = new HealthComponent();
        health.current = 640;
        health.max = 640;
        entity.add(health);
        CombatStatsComponent stats = new CombatStatsComponent();
        stats.atk = 88;
        stats.hitPercent = 1f;
        stats.critPercent = 0f;
        stats.critDamageMultiplier = 1.5f;
        stats.element = Element.NONE;
        entity.add(stats);
        entity.add(new StatusEffectComponent());
        BossAiComponent bossAi = new BossAiComponent();
        bossAi.scriptRef = "/scripts/boss/ancient_stone_colossus.js";
        bossAi.decisionIntervalSeconds = 0.5f;
        bossAi.skillCooldownSeconds = 10f;
        entity.add(bossAi);
        MonsterIdComponent id = new MonsterIdComponent();
        id.monsterId = -1L;
        entity.add(id);
        return entity;
    }

    private static KEntity buildCharacter(EcsWorld world, long characterId, float x) {
        KEntity entity = world.createEntity();
        PositionComponent position = new PositionComponent();
        position.x = x;
        entity.add(position);
        CharacterIdComponent id = new CharacterIdComponent();
        id.characterId = characterId;
        entity.add(id);
        HealthComponent health = new HealthComponent();
        health.current = 500;
        health.max = 500;
        entity.add(health);
        CombatStatsComponent stats = new CombatStatsComponent();
        stats.element = Element.NONE;
        entity.add(stats);
        return entity;
    }
}
