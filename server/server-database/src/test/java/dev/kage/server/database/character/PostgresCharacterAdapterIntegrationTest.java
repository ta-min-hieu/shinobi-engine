package dev.kage.server.database.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterNameTakenException;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.IdempotencyKeyReusedException;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.server.database.DatabaseAccessException;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V2 + adapter + idempotency
 * (ADR-025) hoạt động đúng trên schema thật (TESTING.md §2).
 */
@Testcontainers
class PostgresCharacterAdapterIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;

    @BeforeAll
    static void migrateAndCreateAccount() {
        DatabaseConfig config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        dataSource = PostgresDataSourceFactory.create(config);
        FlywayMigrator.migrate(dataSource);

        // character.account_id có FK tới account — cần 1 account thật trước khi test character.
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] holder = new Account[1];
        accountTx.inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, "charactertester"));
        accountId = holder[0].id();
    }

    @Test
    void shouldCreateCharacter_withStatRow() {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId, "KageHero1", Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];

        transactionRunner.inTransaction(tx -> holder[0] = adapter.create(tx, newCharacter));

        assertThat(holder[0].name()).isEqualTo("KageHero1");
        assertThat(holder[0].faction()).isEqualTo(Faction.HOA);
        assertThat(holder[0].level()).isEqualTo(1);
        assertThat(adapter.existsByName("KageHero1")).isTrue();
    }

    @Test
    void shouldRejectSecondCreate_whenIdempotencyKeyReused() {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        String idempotencyKey = UUID.randomUUID().toString();
        NewCharacter firstCharacter = new NewCharacter(accountId, "IdemFirst", Faction.HOA, idempotencyKey);
        transactionRunner.inTransaction(tx -> adapter.create(tx, firstCharacter));

        NewCharacter secondCharacter = new NewCharacter(accountId, "IdemSecond", Faction.HOA, idempotencyKey);
        assertThatThrownBy(() -> transactionRunner.inTransaction(tx -> adapter.create(tx, secondCharacter)))
                .isInstanceOf(IdempotencyKeyReusedException.class);

        // Nhân vật thứ 2 KHÔNG được tạo — đúng thiết kế ADR-025 (chống trùng, không phải replay).
        assertThat(adapter.existsByName("IdemSecond")).isFalse();
    }

    /**
     * Sự thật về tầng DB mà hai lớp chặn phía trên dựa vào (2026-10-02): Postgres từ chối
     * {@code U+0000} trong tham số chuỗi — kể cả ở một câu SELECT chỉ để TRA — nên
     * {@code CharacterFacade#findByName} phải lọc tên không hợp lệ trước khi tới đây; và adapter nổ
     * {@link IllegalArgumentException} với {@code idempotencyKey} không phải UUID, nên gateway phải
     * kiểm định dạng khoá trước khi gọi feature.
     */
    @Test
    void shouldFailLoudly_onInputsTheUpperLayersMustFilterOut() {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner transactionRunner = new JdbcCharacterTransactionRunner(dataSource);

        assertThatThrownBy(() -> adapter.findByName("Kage\u0000Hero")).isInstanceOf(DatabaseAccessException.class);

        NewCharacter badKey = new NewCharacter(accountId, "BadKeyHero", Faction.HOA, "not-a-uuid");
        assertThatThrownBy(() -> transactionRunner.inTransaction(tx -> adapter.create(tx, badKey)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(adapter.existsByName("BadKeyHero")).isFalse();
    }

    @Test
    void shouldListAndCountCharactersOfAccount() {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        PostgresAccountAdapter freshAccountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] accountHolder = new Account[1];
        accountTx.inTransaction(tx -> accountHolder[0] = freshAccountAdapter.findOrCreate(tx, "listtester"));
        long freshAccountId = accountHolder[0].id();

        NewCharacter newCharacter = new NewCharacter(
                freshAccountId, "ListHero", Faction.HOA, UUID.randomUUID().toString());
        transactionRunner.inTransaction(tx -> adapter.create(tx, newCharacter));

        assertThat(adapter.countByAccount(freshAccountId)).isEqualTo(1);
        assertThat(adapter.listByAccount(freshAccountId))
                .extracting(Character::name)
                .containsExactly("ListHero");
    }

    @Test
    void shouldDefaultToOrigin_whenPositionNeverSaved() {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId, "PosDefault", Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        transactionRunner.inTransaction(tx -> holder[0] = adapter.create(tx, newCharacter));

        PositionSnapshot position = adapter.loadPosition(holder[0].id());

        assertThat(position.x()).isEqualTo(0f);
        assertThat(position.y()).isEqualTo(0f);
    }

    @Test
    void shouldPersistPosition_acrossSaveAndLoad() {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId, "PosSaved", Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        transactionRunner.inTransaction(tx -> holder[0] = adapter.create(tx, newCharacter));

        adapter.savePosition(holder[0].id(), 12.5f, -3.25f);
        PositionSnapshot position = adapter.loadPosition(holder[0].id());

        assertThat(position.x()).isEqualTo(12.5f);
        assertThat(position.y()).isEqualTo(-3.25f);
    }

    @Test
    void shouldLoadStartingStats_afterCreate() {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId, "StatHero", Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        transactionRunner.inTransaction(tx -> holder[0] = adapter.create(tx, newCharacter));

        CharacterStats stats = adapter.loadStats(holder[0].id());

        // Số liệu khởi điểm PLACEHOLDER (V2 migration comment + PostgresCharacterAdapter.STARTING_STAT).
        assertThat(stats.str()).isEqualTo(10);
        assertThat(stats.agi()).isEqualTo(10);
        assertThat(stats.intelligence()).isEqualTo(10);
        assertThat(stats.vit()).isEqualTo(10);
    }

    @Test
    void shouldSaveLevel_onlyWhenExpIsStillTheValueItWasDerivedFrom() {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId, "LevelCas", Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        transactionRunner.inTransaction(tx -> holder[0] = adapter.create(tx, newCharacter));
        long id = holder[0].id();
        long expAfterFirstGrant = adapter.addExperience(id, 100L);
        long expAfterSecondGrant = adapter.addExperience(id, 200L);

        boolean staleWritten = adapter.saveLevel(id, 2, expAfterFirstGrant);
        boolean freshWritten = adapter.saveLevel(id, 3, expAfterSecondGrant);

        assertThat(staleWritten).isFalse();
        assertThat(freshWritten).isTrue();
        assertThat(adapter.findById(id).orElseThrow().level()).isEqualTo(3);
    }

    /**
     * Hai người tạo CÙNG một tên đúng cùng lúc: cả hai đều qua phép kiểm {@code existsByName} của
     * use case, chỉ UNIQUE {@code character.name} chặn được người thứ hai. Trước bản vá 2026-09-24
     * adapter để lọt nguyên {@code DatabaseAccessException} — người thua nhận
     * {@code error.create_character_failed} ("máy chủ lỗi") thay vì "tên đã có người dùng".
     * Đo GIÁ TRỊ CUỐI: đúng một nhân vật mang tên đó, bên thua thấy tín hiệu domain.
     */
    @Test
    void shouldReportNameTaken_whenTwoCreatesRaceForTheSameName() throws Exception {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        String name = "RaceName";
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<Throwable>> first =
                    executor.submit(() -> createAfterBarrier(adapter, transactionRunner, barrier, name));
            Future<Optional<Throwable>> second =
                    executor.submit(() -> createAfterBarrier(adapter, transactionRunner, barrier, name));
            Optional<Throwable> firstFailure = first.get(1, TimeUnit.MINUTES);
            Optional<Throwable> secondFailure = second.get(1, TimeUnit.MINUTES);

            assertThat(adapter.existsByName(name)).isTrue();
            assertThat(adapter.listByAccount(accountId).stream()
                            .filter(c -> c.name().equals(name))
                            .count())
                    .isEqualTo(1L);
            assertThat(firstFailure.isPresent() ^ secondFailure.isPresent()).isTrue();
            assertThat(firstFailure.or(() -> secondFailure)).containsInstanceOf(CharacterNameTakenException.class);
        } finally {
            executor.shutdownNow();
        }
    }

    // Trả về exception của lượt tạo (rỗng = tạo thành công) để luồng test so sánh hai kết quả.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static Optional<Throwable> createAfterBarrier(
            PostgresCharacterAdapter adapter,
            JdbcCharacterTransactionRunner transactionRunner,
            CyclicBarrier barrier,
            String name)
            throws Exception {
        NewCharacter newCharacter =
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
        barrier.await(1, TimeUnit.MINUTES);
        try {
            transactionRunner.inTransaction(tx -> adapter.create(tx, newCharacter));
            return Optional.empty();
        } catch (RuntimeException e) {
            return Optional.of(e);
        }
    }
}
