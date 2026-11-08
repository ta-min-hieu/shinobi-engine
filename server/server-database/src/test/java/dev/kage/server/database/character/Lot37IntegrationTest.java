package dev.kage.server.database.character;

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
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;

/** Lô 37 trên Postgres thật: cờ quà tân thủ một lần (ADR-227), danh mục nhiệm vụ (ADR-228), công tắc khoe (ADR-230). */
@Testcontainers
final class Lot37IntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lot37tester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void marksTheStarterGiftOnlyOnce_andSharesAchievementsByDefault() {
        long owner = createFreshCharacter("L37Owner");
        PostgresStarterGiftAdapter gifts = new PostgresStarterGiftAdapter(dataSource);
        dev.kage.server.database.achievement.PostgresAchievementShareAdapter share =
                new dev.kage.server.database.achievement.PostgresAchievementShareAdapter(dataSource);

        assertThat(gifts.markSent(owner)).isTrue();
        assertThat(gifts.markSent(owner)).isFalse();
        assertThat(share.enabled(owner)).isTrue();
        share.setEnabled(owner, false);
        assertThat(share.enabled(owner)).isFalse();
    }

    @Test
    void listsTheQuestCatalogInStoryOrderWithPrerequisites() {
        java.util.List<dev.kage.quest.domain.QuestCatalogPort.Entry> catalog =
                new dev.kage.server.database.quest.PostgresQuestCatalogAdapter(dataSource).all();

        assertThat(catalog.getFirst().code()).isEqualTo("kill_training_dummies");
        assertThat(catalog.getFirst().prerequisiteCode()).isEmpty();
        assertThat(catalog).anySatisfy(entry -> assertThat(entry.daily()).isTrue());
        assertThat(catalog)
                .anySatisfy(entry -> assertThat(entry.prerequisiteCode()).isPresent());
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
