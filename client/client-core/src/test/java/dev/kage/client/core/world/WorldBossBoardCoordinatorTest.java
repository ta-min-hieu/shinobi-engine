package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.WorldBossBoardEntry;
import dev.kage.protocol.WorldBossBoardEvent;

/** ADR-157 — bảng là chữ thuần dựng từ gói; ended thì rỗng (HUD gỡ bảng). */
final class WorldBossBoardCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    private final WorldBossBoardCoordinator coordinator = new WorldBossBoardCoordinator();

    @Test
    void shouldListTopAndMyRank() {
        String text = coordinator.describe(WorldBossBoardEvent.newBuilder()
                .setBossCode("demon_king")
                .setParticipants(3)
                .addTop(WorldBossBoardEntry.newBuilder().setName("A").setDamage(900))
                .addTop(WorldBossBoardEntry.newBuilder().setName("B").setDamage(500))
                .setMyRank(2)
                .setMyDamage(500)
                .build());

        assertThat(text.split("\n")).hasSize(4);
        assertThat(text).contains("A").contains("900").contains("B").contains("500");
    }

    @Test
    void endedShouldClearTheBoard() {
        assertThat(coordinator.describe(
                        WorldBossBoardEvent.newBuilder().setEnded(true).build()))
                .isEmpty();
    }
}
