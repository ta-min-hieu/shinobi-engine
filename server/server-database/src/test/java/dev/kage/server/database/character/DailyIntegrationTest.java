package dev.kage.server.database.character;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.SeclusionPort;
import dev.kage.checkin.domain.OnlineRewardPolicy;
import dev.kage.inventory.domain.DismantlePort;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.checkin.PostgresOnlineTimeAdapter;
import dev.kage.server.database.inventory.PostgresDismantleAdapter;
import dev.kage.server.database.world.PostgresHerbNodeAdapter;

/** Lô 21 phần 2 trên PostgreSQL THẬT (V86) — bế quan, thưởng online, phân giải, bụi linh thảo. */
@Testcontainers
class DailyIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;

    @BeforeAll
    static void migrate() {
        DatabaseConfig config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        dataSource = PostgresDataSourceFactory.create(config);
        FlywayMigrator.migrate(dataSource);
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] holder = new Account[1];
        accountTx.inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, "dailytester"));
        accountId = holder[0].id();
    }

    private static long character(String name) {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner tx = new JdbcCharacterTransactionRunner(dataSource);
        Character[] holder = new Character[1];
        tx.inTransaction(t -> holder[0] = adapter.create(
                t,
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString())));
        return holder[0].id();
    }

    private static long scalar(String sql, Object... params) {
        return JdbcOperations.queryOne(dataSource, sql, rs -> rs.getLong(1), params)
                .orElseThrow();
    }

    @Test
    void seclusionShouldBankOnceCapAtEightHoursAndChargeForDouble() {
        long id = character("SeclusA1");
        PostgresSeclusionAdapter seclusion = new PostgresSeclusionAdapter(dataSource);
        assertThat(seclusion.bankOfflineTime(id, NOW)).isZero(); // chưa từng thoát

        seclusion.markLoggedOut(id, NOW.minus(Duration.ofHours(10)));
        assertThat(seclusion.bankOfflineTime(id, NOW)).isEqualTo(480);
        assertThat(seclusion.bankOfflineTime(id, NOW)).isEqualTo(480); // vào lại chưa thoát: không tích thêm

        JdbcOperations.update(dataSource, "UPDATE character_stat SET gold = 10 WHERE character_id = ?", id);
        assertThat(seclusion.claim(id, true).outcome()).isEqualTo(SeclusionPort.Outcome.INSUFFICIENT_GOLD);
        assertThat(seclusion.bankedMinutes(id)).isEqualTo(480);

        long expBefore = scalar("SELECT exp FROM character WHERE id = ?", id);
        SeclusionPort.Claim claim = seclusion.claim(id, false);
        assertThat(claim.outcome()).isEqualTo(SeclusionPort.Outcome.OK);
        assertThat(scalar("SELECT exp FROM character WHERE id = ?", id)).isEqualTo(expBefore + claim.expGained());
        assertThat(seclusion.claim(id, false).outcome()).isEqualTo(SeclusionPort.Outcome.NOTHING_BANKED);
    }

    @Test
    void onlineClaimShouldNeedEnoughSecondsAndWinOnce() {
        long id = character("OnlineA1");
        PostgresOnlineTimeAdapter online = new PostgresOnlineTimeAdapter(dataSource);
        LocalDate day = LocalDate.of(2026, 10, 24);
        OnlineRewardPolicy.Milestone ten = OnlineRewardPolicy.milestone(10).orElseThrow();

        assertThat(online.claim(id, day, ten, 500)).isFalse();
        online.addSeconds(id, day, 300);
        assertThat(online.claim(id, day, ten, 300)).isTrue();
        assertThat(online.claim(id, day, ten, 9_999)).isFalse();
        assertThat(online.read(id, day).claimedMask()).isEqualTo(ten.mask());
        assertThat(online.read(id, day).seconds()).isEqualTo(300);
    }

    @Test
    void dismantleShouldPayGoldAndHerbsAndRefuseEquippedOrSocketed() {
        long id = character("DismantA1");
        long vest = JdbcOperations.queryOne(dataSource, """
                        INSERT INTO inventory_item (character_id, item_template_id, quantity, slot_index, enhance_level)
                        SELECT ?, id, 1, 50, 3 FROM item_template WHERE code = 'novice_vest' RETURNING id
                        """, rs -> rs.getLong(1), id)
                .orElseThrow();
        long goldBefore = scalar("SELECT gold FROM character_stat WHERE character_id = ?", id);
        PostgresDismantleAdapter dismantle = new PostgresDismantleAdapter(dataSource);

        DismantlePort.Result result = dismantle.dismantle(id, vest);
        assertThat(result.outcome()).isEqualTo(DismantlePort.Outcome.OK);
        assertThat(result.gold()).isEqualTo(20L + 300L);
        assertThat(result.herbs()).isEqualTo(2);
        assertThat(scalar("SELECT gold FROM character_stat WHERE character_id = ?", id))
                .isEqualTo(goldBefore + 320L);
        assertThat(scalar("""
                        SELECT COALESCE(SUM(ii.quantity), 0) FROM inventory_item ii
                          JOIN item_template it ON it.id = ii.item_template_id
                         WHERE ii.character_id = ? AND it.code = 'spirit_herb'
                        """, id)).isEqualTo(2L);
        assertThat(dismantle.dismantle(id, vest).outcome()).isEqualTo(DismantlePort.Outcome.NOT_FOUND);
    }

    @Test
    void herbNodesShouldBeSeeded() {
        // V86 bốn bụi linh thảo; V90 (ADR-107) thêm hai bờ câu + hai mạch khoáng cùng bảng.
        assertThat(new PostgresHerbNodeAdapter(dataSource).listHerbNodes())
                .filteredOn(node -> node.kind().equals("HERB"))
                .hasSize(4)
                .allSatisfy(node -> assertThat(node.itemCode()).isEqualTo("spirit_herb"));
    }
}
