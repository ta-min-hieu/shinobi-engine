package dev.kage.server.database.economy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;

/**
 * ADR-204 — lọc nhặt đồ trên Postgres thật: công tắc, danh mục rác có giá thu, bán cộng vàng, chạm trần thì không đổi
 * gì.
 */
@Testcontainers
class AutoSellJunkIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static EconomyBoundaryFixture fixture;
    static EconomyStressFacades facades;

    @BeforeAll
    static void migrate() {
        dataSource = PostgresDataSourceFactory.create(new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()));
        FlywayMigrator.migrate(dataSource);
        fixture = new EconomyBoundaryFixture(dataSource);
        facades = EconomyStressFacades.wire(dataSource);
    }

    @AfterAll
    static void shutdown() {
        PostgresDataSourceFactory.close(dataSource);
    }

    @Test
    void togglesListsSellsAndRespectsTheGoldCap() {
        long kunai = fixture.templateId("kunai");
        long sword = fixture.templateId("wooden_sword");
        long owner = fixture.newCharacter(100L);
        dev.kage.server.database.inventory.PostgresAutoSellJunkAdapter adapter =
                new dev.kage.server.database.inventory.PostgresAutoSellJunkAdapter(dataSource);

        assertThat(adapter.enabled(owner)).isFalse();
        adapter.setEnabled(owner, true);
        assertThat(adapter.enabled(owner)).isTrue();
        assertThat(adapter.junkTemplates()).contains(kunai).doesNotContain(sword);

        java.util.OptionalLong gold =
                adapter.sell(owner, List.of(dev.kage.inventory.domain.ItemStack.newItem(kunai, 5, 0)));
        assertThat(gold).hasValue(fixture.sellPrice(kunai) * 5);
        assertThat(fixture.gold(owner)).isEqualTo(100L + fixture.sellPrice(kunai) * 5);

        fixture.setGold(owner, dev.kage.server.database.GoldLedger.MAX_GOLD);
        assertThat(adapter.sell(owner, List.of(dev.kage.inventory.domain.ItemStack.newItem(kunai, 5, 0))))
                .as("chạm trần — không bán")
                .isEmpty();
        assertThat(fixture.gold(owner)).isEqualTo(dev.kage.server.database.GoldLedger.MAX_GOLD);
    }
}
