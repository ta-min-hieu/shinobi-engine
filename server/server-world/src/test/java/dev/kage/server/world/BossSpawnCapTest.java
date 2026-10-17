package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.server.world.component.HealthComponent;

/** Dòng 67(e) (2026-10-17) — mỗi mã boss chỉ một con còn sống trong một World. */
class BossSpawnCapTest {

    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);

    @Test
    void shouldRefuseASecondBossOfTheSameCode_whileTheFirstIsAlive() {
        WorldFacade dungeon = newDungeon();

        for (int i = 0; i < 6; i++) {
            dungeon.spawnBoss(TestMonsterCatalogs.SAMPLE_BOSS_CODE);
            dungeon.runOneTickOnCallerThread();
        }

        assertThat(dungeon.monsterEntitiesView()).hasSize(1);
    }

    @Test
    void shouldAllowANewBoss_onceTheOldOneIsDead() {
        WorldFacade dungeon = newDungeon();
        dungeon.spawnBoss(TestMonsterCatalogs.SAMPLE_BOSS_CODE);
        dungeon.runOneTickOnCallerThread();
        HEALTH.get(dungeon.monsterEntitiesView().values().iterator().next()).current = 0;

        dungeon.spawnBoss(TestMonsterCatalogs.SAMPLE_BOSS_CODE);
        dungeon.runOneTickOnCallerThread();

        assertThat(dungeon.monsterEntitiesView().values())
                .as("đúng một con còn sống")
                .filteredOn(boss -> !HEALTH.get(boss).isDead())
                .hasSize(1);
    }

    private static WorldFacade newDungeon() {
        return new WorldFacade(
                SilentWorldSink.create(), false, TickMetricsPort.NO_OP, TestWorldContents.dungeonWithBoss());
    }
}
