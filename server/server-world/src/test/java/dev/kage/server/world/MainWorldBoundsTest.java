package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.component.PositionComponent;

/** Dòng 67(c) (2026-10-17) — server giữ nhân vật trong dải nền đất của world chính. */
class MainWorldBoundsTest {

    private static final CharacterBaseStats STATS = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 1);
    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final long CHARACTER_ID = 11L;

    @Test
    void shouldStopTheCharacterAtTheEastEdge_whileTheMoveKeyIsStillHeld() {
        WorldFacade world = new WorldFacade(SilentWorldSink.create(), true);
        world.attachCharacter(CHARACTER_ID, WorldConstants.MAIN_WORLD_MAX_X - 1f, 0f, STATS);
        world.enqueueMoveCommand(CHARACTER_ID, 1, 1f, 0f);

        runTicks(world, 60); // 1 giây ở 5 m/s — đủ vượt mép 4 m nếu không có biên

        assertThat(x(world)).isEqualTo(WorldConstants.MAIN_WORLD_MAX_X);

        world.enqueueMoveCommand(CHARACTER_ID, 2, -1f, 0f);
        runTicks(world, 12);
        assertThat(x(world)).as("đổi hướng là đi tiếp được ngay").isLessThan(WorldConstants.MAIN_WORLD_MAX_X - 0.5f);
    }

    @Test
    void shouldPullACharacterSavedOutsideTheBoundsBackToTheEdge_onTheFirstTick() {
        WorldFacade world = new WorldFacade(SilentWorldSink.create(), true);
        world.attachCharacter(CHARACTER_ID, -80f, 0f, STATS);

        runTicks(world, 1);

        assertThat(x(world)).isEqualTo(WorldConstants.MAIN_WORLD_MIN_X);
    }

    @Test
    void instanceWorldsShouldHaveNoBounds() {
        WorldFacade dungeon = new WorldFacade(SilentWorldSink.create(), false);
        dungeon.attachCharacter(CHARACTER_ID, -80f, 0f, STATS);

        runTicks(dungeon, 1);

        assertThat(x(dungeon)).isEqualTo(-80f);
    }

    @Test
    void clampShouldLeaveEveryPointInsideTheBoundsUntouched() {
        assertThat(MainWorldBoundsSystem.clamp(0f)).isEqualTo(0f);
        assertThat(MainWorldBoundsSystem.clamp(WorldConstants.MAIN_WORLD_MIN_X))
                .isEqualTo(WorldConstants.MAIN_WORLD_MIN_X);
        assertThat(MainWorldBoundsSystem.clamp(WorldConstants.MAIN_WORLD_MAX_X))
                .isEqualTo(WorldConstants.MAIN_WORLD_MAX_X);
        assertThat(MainWorldBoundsSystem.clamp(1_000f)).isEqualTo(WorldConstants.MAIN_WORLD_MAX_X);
        // Biên phải phủ điểm spawn xa nhất (V109: đàn chủ Huyết Sa @296) và bằng nền đất client (DemoPlatforms
        // GROUND_MAX_X) — V57 từng nới nền mà quên biên, chặn người chơi ở 210 trước trận @216.
        assertThat(WorldConstants.MAIN_WORLD_MAX_X).isGreaterThan(296f).isEqualTo(305f);
    }

    private static void runTicks(WorldFacade world, int ticks) {
        for (int i = 0; i < ticks; i++) {
            world.runOneTickOnCallerThread();
        }
    }

    private static float x(WorldFacade world) {
        return POSITION.get(world.characterEntitiesView().get(CHARACTER_ID)).x;
    }
}
