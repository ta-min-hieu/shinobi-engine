package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.EnemyView;
import dev.kage.protocol.ListEnemiesResponse;

/** ADR-194 — danh sách cừu nhân thành các dòng chat có dịch. */
final class EnemyListCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void emptyAndFilledLists() {
        assertThat(EnemyListCoordinator.describe(KageI18n.load(), ListEnemiesResponse.getDefaultInstance(), 0L))
                .singleElement()
                .asString()
                .doesNotStartWith("enemy.");
        ListEnemiesResponse response = ListEnemiesResponse.newBuilder()
                .addEnemies(EnemyView.newBuilder()
                        .setName("Benkei")
                        .setKillCount(3)
                        .setLastKilledEpochSeconds(1_000L)
                        .setOnline(true))
                .build();
        assertThat(EnemyListCoordinator.describe(KageI18n.load(), response, 1_000L + 7_200L))
                .hasSize(2)
                .allSatisfy(line -> assertThat(line).doesNotStartWith("enemy."))
                .last()
                .asString()
                .contains("Benkei")
                .contains("3");
    }
}
