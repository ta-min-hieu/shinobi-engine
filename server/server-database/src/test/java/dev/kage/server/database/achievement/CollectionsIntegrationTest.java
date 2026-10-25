package dev.kage.server.database.achievement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.achievement.domain.BestiaryPort;
import dev.kage.achievement.domain.ReputationPort;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.friend.domain.SwornOutcome;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.friend.PostgresSwornAdapter;
import dev.kage.server.database.world.PostgresHerbNodeAdapter;

/** Lô 22 phần 2 trên PostgreSQL THẬT (V90) — điểm thu thập, sổ sưu tầm, danh vọng, kết nghĩa. */
@Testcontainers
class CollectionsIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant NOW = Instant.parse("2026-10-25T05:00:00Z");

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
        accountTx.inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, "collectiontester"));
        accountId = holder[0].id();
    }

    private static long character(String name, long gold) {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner tx = new JdbcCharacterTransactionRunner(dataSource);
        Character[] holder = new Character[1];
        tx.inTransaction(t -> holder[0] = adapter.create(
                t,
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString())));
        JdbcOperations.update(
                dataSource, "UPDATE character_stat SET gold = ? WHERE character_id = ?", gold, holder[0].id());
        return holder[0].id();
    }

    private static void befriend(long first, long second, int intimacy) {
        JdbcOperations.update(dataSource, """
                INSERT INTO friendship (requester_id, addressee_id, status, created_at, responded_at, intimacy)
                VALUES (?, ?, 'ACCEPTED', now(), now(), ?)
                """, first, second, intimacy);
    }

    private static long gold(long characterId) {
        return JdbcOperations.queryOne(
                        dataSource,
                        "SELECT gold FROM character_stat WHERE character_id = ?",
                        rs -> rs.getLong(1),
                        characterId)
                .orElseThrow();
    }

    @Test
    void shouldSeedFishingSpotsAndOreVeins() {
        assertThat(new PostgresHerbNodeAdapter(dataSource).listHerbNodes())
                .anySatisfy(node -> assertThat(node.kind()).isEqualTo("FISH"))
                .anySatisfy(node -> assertThat(node.kind()).isEqualTo("ORE"))
                .allSatisfy(node -> assertThat(node.gatherSeconds()).isPositive());
    }

    @Test
    void shouldUnlockABestiaryPageExactlyOnceAndSumItsBonus() {
        PostgresBestiaryAdapter adapter = new PostgresBestiaryAdapter(dataSource);
        long hunter = character("Hunter", 0L);
        for (int i = 0; i < 49; i++) {
            assertThat(adapter.recordKill(hunter, "training_dummy", NOW)).isEmpty();
        }
        assertThat(adapter.recordKill(hunter, "training_dummy", NOW))
                .map(BestiaryPort.Entry::monsterCode)
                .contains("training_dummy");
        assertThat(adapter.recordKill(hunter, "training_dummy", NOW)).isEmpty(); // đã mở
        assertThat(adapter.recordKill(hunter, "sample_boss", NOW)).isEmpty(); // boss không có trang
        assertThat(adapter.bonusOf(hunter)).isEqualTo(new BestiaryPort.Bonus(0, 0, 0, 10));
        assertThat(adapter.progressOf(hunter))
                .filteredOn(p -> p.entry().monsterCode().equals("training_dummy"))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.unlocked()).isTrue();
                    assertThat(p.kills()).isEqualTo(51);
                });
    }

    @Test
    void shouldGateTheReputationShopByTierAndSpendPoints() {
        PostgresReputationAdapter adapter = new PostgresReputationAdapter(dataSource);
        long player = character("Renown", 0L);
        long pill = adapter.shop().getFirst().itemTemplateId();
        long talisman = adapter.shop().get(1).itemTemplateId();
        assertThat(adapter.buy(player, pill)).isEqualTo(ReputationPort.BuyOutcome.NOT_ENOUGH_REPUTATION);
        adapter.add(player, 50);
        assertThat(adapter.buy(player, talisman)).isEqualTo(ReputationPort.BuyOutcome.TIER_TOO_LOW);
        assertThat(adapter.buy(player, pill)).isEqualTo(ReputationPort.BuyOutcome.OK);
        assertThat(adapter.standingOf(player)).isEqualTo(new ReputationPort.Standing(50, 20));
        adapter.add(player, 60); // earned 110 ⇒ bậc 1 dù đã tiêu
        assertThat(adapter.buy(player, talisman)).isEqualTo(ReputationPort.BuyOutcome.OK);
        assertThat(adapter.standingOf(player).available()).isEqualTo(30);
        assertThat(adapter.buy(player, -1L)).isEqualTo(ReputationPort.BuyOutcome.UNKNOWN_ITEM);
    }

    @Test
    void shouldFoundJoinAndDissolveABrotherhood() {
        PostgresSwornAdapter adapter = new PostgresSwornAdapter(dataSource);
        long elder = character("ElderBro", 10_000L);
        long second = character("SecondBro", 0L);
        long third = character("ThirdBro", 0L);
        long stranger = character("Stranger", 0L);
        befriend(elder, second, 300);
        befriend(elder, third, 500);
        befriend(elder, stranger, 100);

        assertThat(adapter.join(elder, stranger)).isEqualTo(SwornOutcome.INTIMACY_TOO_LOW);
        assertThat(adapter.join(elder, second)).isEqualTo(SwornOutcome.OK);
        assertThat(gold(elder)).isEqualTo(7_000L);
        assertThat(adapter.join(elder, third)).isEqualTo(SwornOutcome.OK);
        assertThat(gold(elder)).isEqualTo(6_000L);
        assertThat(adapter.membersOf(third)).containsExactlyInAnyOrder(elder, second, third);
        assertThat(adapter.join(elder, second)).isEqualTo(SwornOutcome.ALREADY_SWORN);
        assertThat(gold(elder)).isEqualTo(6_000L); // rollback, không trừ

        assertThat(adapter.leave(third)).containsExactlyInAnyOrder(elder, second);
        assertThat(adapter.leave(second)).containsExactly(elder); // còn một người ⇒ giải tán
        assertThat(adapter.membersOf(elder)).isEmpty();
        assertThat(adapter.leave(elder)).isEmpty();
    }
}
