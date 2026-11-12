package dev.kage.server.database.pvp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
import dev.kage.inventory.domain.ItemLookupPort;
import dev.kage.pvp.domain.DuelHistoryPort;
import dev.kage.pvp.domain.EloRating;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Lô 44 trên Postgres thật: tra cứu vật phẩm (ADR-279), lịch sử đấu tay đôi (ADR-280), tự nhận lời mời nhóm (ADR-286).
 */
@Testcontainers
final class Lot44IntegrationTest {

    private static final int POSTGRES_PORT = 5432;

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
        Account[] accountHolder = new Account[1];
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot44tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void looksUpPricesAndDropSources() {
        ItemLookupPort.Lookup kunai =
                new dev.kage.server.database.inventory.PostgresItemLookupAdapter(dataSource).lookup(1L);
        assertThat(kunai.drops())
                .extracting(ItemLookupPort.DropSource::monsterCode)
                .contains("training_dummy");
        ItemLookupPort.Lookup missing =
                new dev.kage.server.database.inventory.PostgresItemLookupAdapter(dataSource).lookup(999_999L);
        assertThat(missing.buyPrice()).isZero();
        assertThat(missing.drops()).isEmpty();
    }

    @Test
    void duelResultsLeaveHistoryOnBothSides() {
        long winner = createFreshCharacter("DuWinner");
        long loser = createFreshCharacter("DuLoser");
        new PostgresPvpRatingAdapter(dataSource).recordDuelResult(winner, loser, EloRating::computeNewRatings);
        PostgresDuelHistoryAdapter history = new PostgresDuelHistoryAdapter(dataSource);

        DuelHistoryPort.Duel won = history.recent(winner, 10).getFirst();
        assertThat(won.won()).isTrue();
        assertThat(won.opponentName()).isEqualTo("DuLoser");
        assertThat(won.ratingAfter()).isGreaterThan(EloRating.DEFAULT_RATING);
        DuelHistoryPort.Duel lost = history.recent(loser, 10).getFirst();
        assertThat(lost.won()).isFalse();
        assertThat(lost.opponentName()).isEqualTo("DuWinner");
    }

    @Test
    void storesPartyAutoAcceptSwitch() {
        long owner = createFreshCharacter("ApOwner");
        dev.kage.server.database.party.PostgresPartyAutoAcceptAdapter adapter =
                new dev.kage.server.database.party.PostgresPartyAutoAcceptAdapter(dataSource);
        assertThat(adapter.enabled(owner)).isFalse();
        adapter.setEnabled(owner, true);
        assertThat(adapter.enabled(owner)).isTrue();
        assertThat(adapter.enabled(owner + 999)).isFalse();
    }

    private static long createFreshCharacter(String name) {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner characterTx = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter =
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        characterTx.inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        return holder[0].id();
    }
}
