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
import dev.kage.server.world.component.TargetComponent;

/**
 * Boss đã chết không được tung chiêu (AI.md §3: {@code Attack --> Dead: HP <= 0}).
 *
 * <p>Đòn kết liễu của người chơi đi vào từ hàng đợi lệnh ({@code drainInbox}) TRƯỚC
 * {@code ecsWorld.update()}, còn {@code WorldFacade.handleMonsterDeath} chỉ xoá entity SAU
 * {@code update()}. Trong khoảng giữa đó boss nằm 0 máu nhưng vẫn ở trong ECS. {@code CombatSystem}
 * đã chặn kẻ đánh đã chết từ 2026-09-24 ({@code isAttackerReady}); {@code BossAiSystem} thì chưa,
 * nên đúng tick bị hạ gục mà tới nhịp quyết định, cái xác vẫn tung {@code heavy_strike}/{@code
 * enrage} — có thể hạ luôn người vừa thắng.
 */
class BossAiSystemDeadBossTest {

    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);

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

    @Test
    void shouldNotCastAnything_whenTheBossWasKilledEarlierInTheSameTick() {
        EcsWorld world = new EcsWorld();
        buildBoss(world, 0);
        KEntity character = buildCharacter(world);
        Map<Long, KEntity> byId = Map.of(CHARACTER_ID, character);
        BossAiSystem system = new BossAiSystem(byId::get);
        world.addSystem(system);

        world.update(1f / 60f);

        assertThat(system.drainResults()).isEmpty();
        assertThat(HEALTH.get(character).current).isEqualTo(100);
    }

    /** Đối chứng: cùng dựng như trên nhưng boss CÒN SỐNG (dưới 30% máu) thì phải tung enrage thật. */
    @Test
    void shouldStillCast_whenTheBossIsAlive() {
        EcsWorld world = new EcsWorld();
        buildBoss(world, 10);
        KEntity character = buildCharacter(world);
        Map<Long, KEntity> byId = Map.of(CHARACTER_ID, character);
        BossAiSystem system = new BossAiSystem(byId::get);
        world.addSystem(system);

        world.update(1f / 60f);

        assertThat(system.drainResults())
                .extracting(BossAiSystem.BossActionOutcome::actionCode)
                .containsExactly("enrage");
    }
}
