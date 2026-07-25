package dev.kage.server.database.friend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.friend.domain.DuplicateFriendRequestException;
import dev.kage.friend.domain.Friendship;
import dev.kage.friend.domain.FriendshipStatus;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V12 ({@code friendship}) +
 * UNIQUE constraint chống gửi trùng lời mời cùng hướng (feature-friend/CLAUDE.md).
 */
@Testcontainers
class FriendIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "friendtester"));
        accountId = accountHolder[0].id();
    }

    private static long createFreshCharacter(String name) {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner characterTx = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter = new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        characterTx.inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        return holder[0].id();
    }

    private static PostgresFriendAdapter newAdapter() {
        return new PostgresFriendAdapter(dataSource);
    }

    @Test
    void shouldCreatePendingRequest_andFindItFromEitherSide() {
        long a = createFreshCharacter("FriendA1");
        long b = createFreshCharacter("FriendB1");
        PostgresFriendAdapter adapter = newAdapter();

        Friendship created = adapter.sendRequest(a, b);

        assertThat(created.status()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(adapter.findBetween(a, b)).isPresent();
        assertThat(adapter.findBetween(b, a)).isPresent();
    }

    @Test
    void shouldRejectDuplicateRequest_sameDirection() {
        long a = createFreshCharacter("FriendA2");
        long b = createFreshCharacter("FriendB2");
        PostgresFriendAdapter adapter = newAdapter();
        adapter.sendRequest(a, b);

        assertThatThrownBy(() -> adapter.sendRequest(a, b))
                .isInstanceOf(DuplicateFriendRequestException.class);
    }

    @Test
    void shouldAcceptRequest_andAppearInListAccepted() {
        long a = createFreshCharacter("FriendA3");
        long b = createFreshCharacter("FriendB3");
        PostgresFriendAdapter adapter = newAdapter();
        Friendship created = adapter.sendRequest(a, b);

        boolean accepted = adapter.accept(created.id(), b);

        assertThat(accepted).isTrue();
        assertThat(adapter.listAccepted(a)).extracting(f -> f.otherParty(a)).containsExactly(b);
        assertThat(adapter.listAccepted(b)).extracting(f -> f.otherParty(b)).containsExactly(a);
    }

    @Test
    void shouldRejectAccept_whenCallerIsNotTheAddressee() {
        long a = createFreshCharacter("FriendA4");
        long b = createFreshCharacter("FriendB4");
        PostgresFriendAdapter adapter = newAdapter();
        Friendship created = adapter.sendRequest(a, b);

        boolean accepted = adapter.accept(created.id(), a); // a là requester, không phải addressee

        assertThat(accepted).isFalse();
        assertThat(adapter.findBetween(a, b).orElseThrow().status()).isEqualTo(FriendshipStatus.PENDING);
    }

    @Test
    void shouldDeleteRow_whenRejected() {
        long a = createFreshCharacter("FriendA5");
        long b = createFreshCharacter("FriendB5");
        PostgresFriendAdapter adapter = newAdapter();
        Friendship created = adapter.sendRequest(a, b);

        boolean rejected = adapter.reject(created.id(), b);

        assertThat(rejected).isTrue();
        assertThat(adapter.findBetween(a, b)).isEmpty();
    }

    @Test
    void shouldDeleteRow_whenRemoved() {
        long a = createFreshCharacter("FriendA6");
        long b = createFreshCharacter("FriendB6");
        PostgresFriendAdapter adapter = newAdapter();
        Friendship created = adapter.sendRequest(a, b);
        adapter.accept(created.id(), b);

        boolean removed = adapter.remove(a, b);

        assertThat(removed).isTrue();
        assertThat(adapter.findBetween(a, b)).isEmpty();
    }

    @Test
    void shouldListOnlyPendingIncomingRequests() {
        long a = createFreshCharacter("FriendA7");
        long b = createFreshCharacter("FriendB7");
        long c = createFreshCharacter("FriendC7");
        PostgresFriendAdapter adapter = newAdapter();
        adapter.sendRequest(a, b); // b nhận
        adapter.sendRequest(c, b); // b nhận thêm 1 cái nữa
        adapter.sendRequest(b, c); // c nhận (không liên quan tới b's incoming)

        List<Friendship> pending = adapter.listPendingIncoming(b);

        assertThat(pending).hasSize(2);
        assertThat(pending).extracting(f -> f.requesterId()).containsExactlyInAnyOrder(a, c);
    }
}
