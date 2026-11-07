package dev.kage.server.database.economy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
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
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.shop.PostgresBuybackAdapter;
import dev.kage.shop.api.BuybackFacade;
import dev.kage.shop.api.ShopFacade;
import dev.kage.shop.domain.BuybackPort;

/**
 * ADR-202 — mua lại trên Postgres thật: bán ghi dòng cùng transaction (giữ cấp cường hoá), mua lại trừ đúng giá đã nhận
 * và trả đúng món; thiếu vàng thì lùi cả phần cấp món; quá 1 giờ thì không còn.
 */
@Testcontainers
class BuybackIntegrationTest {

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
    void soldEnhancedItemComesBackForTheSameGold_andShortGoldRollsBack() {
        long sword = fixture.templateId("wooden_sword");
        long seller = fixture.newCharacter(0L);
        long row = fixture.addRow(seller, sword, 1);
        JdbcOperations.update(dataSource, "UPDATE inventory_item SET enhance_level = 3 WHERE id = ?", row);
        BuybackFacade buyback = new BuybackFacade(new PostgresBuybackAdapter(dataSource));

        assertThat(facades.shop().sellItem(seller, row, 1, EconomyBoundaryFixture.newKey()))
                .isEqualTo(ShopFacade.SellResult.OK);
        long earned = fixture.gold(seller);
        List<BuybackPort.Entry> entries = buyback.list(seller, Instant.now());
        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.itemTemplateId()).isEqualTo(sword);
            assertThat(entry.enhanceLevel()).isEqualTo(3);
            assertThat(entry.price()).isEqualTo(earned);
        });
        long entryId = entries.get(0).id();

        fixture.setGold(seller, earned - 1);
        assertThat(buyback.buyBack(seller, entryId, Instant.now())).isEqualTo(BuybackPort.Outcome.INSUFFICIENT_GOLD);
        assertThat(fixture.rowCount(seller)).as("thiếu vàng — món chưa về").isZero();

        fixture.setGold(seller, earned);
        assertThat(buyback.buyBack(seller, entryId, Instant.now())).isEqualTo(BuybackPort.Outcome.OK);
        assertThat(fixture.gold(seller)).isZero();
        assertThat(fixture.rowCount(seller)).isEqualTo(1);
        assertThat(fixture.totalQuantity(seller, sword)).isEqualTo(1L);
        assertThat(buyback.list(seller, Instant.now())).isEmpty();
        assertThat(buyback.buyBack(seller, entryId, Instant.now())).isEqualTo(BuybackPort.Outcome.NOT_FOUND);
    }

    @Test
    void expiredAndOtherPeoplesEntriesAreNotFound() {
        long kunai = fixture.templateId("kunai");
        long seller = fixture.newCharacter(0L);
        long other = fixture.newCharacter(1_000_000L);
        long row = fixture.addRow(seller, kunai, 5);
        facades.shop().sellItem(seller, row, 5, EconomyBoundaryFixture.newKey());
        BuybackFacade buyback = new BuybackFacade(new PostgresBuybackAdapter(dataSource));
        long entryId = buyback.list(seller, Instant.now()).get(0).id();

        assertThat(buyback.buyBack(other, entryId, Instant.now())).isEqualTo(BuybackPort.Outcome.NOT_FOUND);
        Instant later = Instant.now().plus(BuybackFacade.WINDOW).plusSeconds(60);
        assertThat(buyback.list(seller, later)).as("quá 1 giờ").isEmpty();
        assertThat(buyback.buyBack(seller, entryId, later)).isEqualTo(BuybackPort.Outcome.NOT_FOUND);
    }
}
