package dev.kage.server.database.guild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildDisbandedException;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Xin gia nhập một bang vừa bị giải tán, đo bằng PostgreSQL THẬT. Lớp riêng để giữ
 * {@link GuildIntegrationTest} dưới trần độ phức tạp PMD.
 */
@Testcontainers
class GuildDisbandRaceIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "guilddisbander"));
        accountId = accountHolder[0].id();
    }

    /**
     * {@code SendGuildJoinRequestUseCase} tra bang theo tên rồi mới ghi đơn — leader giải tán đúng
     * giữa hai bước đó thì câu INSERT vi phạm FOREIGN KEY {@code guild_id}. Bản cũ để lỗi đó thành
     * {@code DatabaseAccessException} chung ⇒ người xin nhận 500 {@code error.server_internal} thay vì
     * "không tìm thấy bang".
     */
    @Test
    void shouldThrowGuildDisbanded_whenApplyingToAGuildThatWasJustDisbanded() {
        long leader = createFreshCharacter("GDisL1");
        long applicant = createFreshCharacter("GDisA1");
        PostgresGuildAdapter adapter = new PostgresGuildAdapter(dataSource);
        Guild guild = adapter.createGuild("GDisGuild1", leader);
        assertThat(adapter.disbandGuild(leader)).isTrue();

        assertThatThrownBy(() -> adapter.sendJoinRequest(guild.id(), applicant))
                .isInstanceOf(GuildDisbandedException.class);
        assertThat(adapter.findPendingRequestOfCharacter(applicant)).isEmpty();
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
