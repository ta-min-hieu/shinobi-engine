package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class MonsterCatalogSnapshotTest {

    private static MonsterCatalogSnapshot.MonsterSpecies species(String code) {
        return new MonsterCatalogSnapshot.MonsterSpecies(
                code,
                "mob_" + code,
                50,
                20L,
                new MonsterCatalogSnapshot.MonsterCombatNumbers(8, 0, 5, 3, 90, 0, 0, "NONE"));
    }

    @Test
    void shouldDropSpawnPointsPointingAtUnknownSpecies() {
        // Dữ liệu config lệch không đáng làm cả world không khởi động được — FK trong DB là hàng
        // rào thứ nhất, đây là hàng rào thứ hai.
        MonsterCatalogSnapshot catalog = MonsterCatalogSnapshot.of(
                List.of(species("training_dummy")),
                List.of(
                        new MonsterCatalogSnapshot.MonsterSpawnDefinition("training_dummy", 8f, 0f, 10f),
                        new MonsterCatalogSnapshot.MonsterSpawnDefinition("khong_ton_tai", 14f, 0f, 10f)));

        assertThat(catalog.spawnPoints()).hasSize(1);
        assertThat(catalog.spawnPoints().get(0).monsterCode()).isEqualTo("training_dummy");
    }

    @Test
    void shouldReportEmptyWhenNoSpawnPoints() {
        assertThat(MonsterCatalogSnapshot.EMPTY.isEmpty()).isTrue();
        assertThat(MonsterCatalogSnapshot.of(List.of(species("a")), List.of()).isEmpty())
                .isTrue();
    }

    @Test
    void shouldFindSpeciesByCode() {
        MonsterCatalogSnapshot catalog = MonsterCatalogSnapshot.of(List.of(species("stone_ogre")), List.of());

        assertThat(catalog.find("stone_ogre")).isPresent();
        assertThat(catalog.find("khong_co")).isEmpty();
    }
}
