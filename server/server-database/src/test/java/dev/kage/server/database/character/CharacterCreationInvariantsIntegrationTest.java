package dev.kage.server.database.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.application.CharacterLimitExceededException;
import dev.kage.character.application.CharacterNotFoundException;
import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.DuplicateCharacterNameException;
import dev.kage.character.application.SelectCharacterUseCase;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.IdempotencyKeyReusedException;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;

/**
 * Bất biến của tạo/chọn nhân vật trên PostgreSQL THẬT, qua đúng use case production dùng, đo bằng
 * GIÁ TRỊ CUỐI trong DB (số dòng {@code character}/{@code character_stat}/{@code idempotency_key}).
 *
 * <p>Bổ sung cho {@link CharacterLimitRaceIntegrationTest} (giới hạn/account) và
 * {@code PostgresCharacterAdapterIntegrationTest} (hai lượt cùng tên ở tầng ADAPTER): ở đây là tầng
 * USE CASE với nhiều account, nên kiểm được cả lỗi nghiệp vụ mà gateway dịch thành error_key.
 */
@Testcontainers
class CharacterCreationInvariantsIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int CONTENDERS = 8;
    private static final int ROUNDS = 5;
    private static final int LIMIT = 4;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    private static final AtomicInteger NEXT_ACCOUNT = new AtomicInteger();

    static DataSource dataSource;
    static PostgresCharacterAdapter adapter;
    static CreateCharacterUseCase createUseCase;

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
        adapter = new PostgresCharacterAdapter(dataSource);
        createUseCase = new CreateCharacterUseCase(adapter, new JdbcCharacterTransactionRunner(dataSource));
    }

    /**
     * Nhiều account cùng giành MỘT tên cùng lúc: đúng một người thắng, mọi người thua nhận đúng lỗi
     * "tên trùng" (gateway dịch thành {@code error.duplicate_character_name}, không phải 500), và
     * không lượt thua nào để lại dấu vết: không dòng {@code character_stat} mồ côi, không khoá
     * idempotency bị "đốt" (người thua thử lại cùng khoá với tên khác phải được).
     */
    @Test
    void shouldLetExactlyOneAccountWinAName_andLeaveNoTraceOfTheLosers() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONTENDERS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String name = "Contested" + round;
                CyclicBarrier barrier = new CyclicBarrier(CONTENDERS);
                List<String> keys = new ArrayList<>();
                List<Long> accounts = new ArrayList<>();
                List<Future<Optional<RuntimeException>>> futures = new ArrayList<>();
                for (int i = 0; i < CONTENDERS; i++) {
                    long accountId = createAccount();
                    String key = UUID.randomUUID().toString();
                    accounts.add(accountId);
                    keys.add(key);
                    futures.add(executor.submit(() -> createAfterBarrier(barrier, accountId, name, key)));
                }
                int winners = 0;
                List<Integer> loserIndexes = new ArrayList<>();
                for (int i = 0; i < CONTENDERS; i++) {
                    Optional<RuntimeException> failure = futures.get(i).get(1, TimeUnit.MINUTES);
                    if (failure.isEmpty()) {
                        winners++;
                    } else {
                        assertThat(failure.get())
                                .as("người thua phải nhận lỗi nghiệp vụ 'tên trùng', không phải lỗi DB chung")
                                .isInstanceOf(DuplicateCharacterNameException.class);
                        loserIndexes.add(i);
                    }
                }

                assertThat(winners).as("số người thắng tên '%s'", name).isEqualTo(1);
                assertThat(countCharactersNamed(name)).isEqualTo(1);
                assertThat(countUsedKeys(keys))
                        .as("chỉ khoá của người thắng được ghi")
                        .isEqualTo(1);
                // Người thua thử lại ĐÚNG khoá cũ với tên khác: khoá chưa bị đốt nên phải thành công.
                int loser = loserIndexes.get(0);
                Character retried = createUseCase.execute(accounts.get(loser), "Retry" + round, keys.get(loser));
                assertThat(retried.name()).isEqualTo("Retry" + round);
            }
            assertThat(countCharactersWithoutStatRow())
                    .as("nhân vật thiếu dòng character_stat")
                    .isZero();
            assertThat(countStatRowsWithoutCharacter())
                    .as("dòng character_stat mồ côi")
                    .isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Client gửi CreateCharacter hai lần cùng khoá idempotency (mạng chập chờn, bấm hai lần) — cả hai
     * tới server cùng lúc. Đúng một nhân vật được tạo; lượt kia nhận "yêu cầu trùng".
     */
    @Test
    void shouldCreateOnlyOneCharacter_whenTheSameRequestIsSentTwiceConcurrently() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                long accountId = createAccount();
                String key = UUID.randomUUID().toString();
                String name = "Twice" + round;
                CyclicBarrier barrier = new CyclicBarrier(2);
                Future<Optional<RuntimeException>> first =
                        executor.submit(() -> createAfterBarrier(barrier, accountId, name, key));
                Future<Optional<RuntimeException>> second =
                        executor.submit(() -> createAfterBarrier(barrier, accountId, name, key));
                Optional<RuntimeException> firstFailure = first.get(1, TimeUnit.MINUTES);
                Optional<RuntimeException> secondFailure = second.get(1, TimeUnit.MINUTES);

                assertThat(adapter.countByAccount(accountId))
                        .as("vòng %d", round)
                        .isEqualTo(1);
                assertThat(firstFailure.isPresent() ^ secondFailure.isPresent()).isTrue();
                // Lượt thua thấy khoá đã dùng (cùng account ⇒ xếp hàng sau khoá account) hoặc — nếu kịp
                // kiểm sớm sau khi lượt thắng commit — thấy tên đã có. Cả hai đều là lỗi 409 đúng nghĩa.
                assertThat(firstFailure.or(() -> secondFailure).orElseThrow())
                        .isInstanceOfAny(IdempotencyKeyReusedException.class, DuplicateCharacterNameException.class);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /** Giới hạn 4 nhân vật/account khi hai lượt tạo cùng lúc dùng TÊN TRÙNG NHAU lẫn tên khác nhau. */
    @Test
    void shouldHoldTheLimit_andNameUniqueness_underMixedContention() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONTENDERS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                long accountId = createAccount();
                CyclicBarrier barrier = new CyclicBarrier(CONTENDERS);
                List<Future<Optional<RuntimeException>>> futures = new ArrayList<>();
                for (int i = 0; i < CONTENDERS; i++) {
                    // Từng cặp lượt dùng chung một tên: 4 tên khác nhau, mỗi tên 2 lượt.
                    String name = "Mix" + round + "n" + (i / 2);
                    futures.add(executor.submit(() -> createAfterBarrier(
                            barrier, accountId, name, UUID.randomUUID().toString())));
                }
                for (Future<Optional<RuntimeException>> future : futures) {
                    future.get(1, TimeUnit.MINUTES).ifPresent(failure -> assertThat(failure)
                            .isInstanceOfAny(
                                    DuplicateCharacterNameException.class, CharacterLimitExceededException.class));
                }
                List<Character> characters = adapter.listByAccount(accountId);
                assertThat(characters).hasSizeLessThanOrEqualTo(LIMIT);
                assertThat(characters.stream().map(Character::name).distinct().count())
                        .isEqualTo(characters.size());
                // 4 tên khác nhau, giới hạn 4 ⇒ mỗi tên đúng một lượt thắng.
                assertThat(characters).hasSize(LIMIT);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectHostileNames_withoutWritingAnything() throws SQLException {
        long accountId = createAccount();
        long charactersBefore = countAllCharacters();

        for (String hostile : hostileNames()) {
            assertThatThrownBy(() -> createUseCase.execute(
                            accountId, hostile, UUID.randomUUID().toString()))
                    .as("tên độc phải bị từ chối là đầu vào sai: %s", describe(hostile))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(countAllCharacters()).isEqualTo(charactersBefore);
    }

    /**
     * Tiếng Việt gõ dấu TÁCH RỜI (NFD) — từng nằm trong {@link #hostileNames()}; từ 2026-10-14 được nhận
     * và LƯU dạng NFC (chủ dự án chọn, docs/README.md dòng 42).
     */
    @Test
    void shouldStoreDecomposedVietnameseComposed() {
        long accountId = createAccount();

        createUseCase.execute(accountId, "Hoa\u0300ng", UUID.randomUUID().toString());

        assertThat(adapter.listByAccount(accountId)).extracting(Character::name).containsExactly("Ho\u00E0ng");
        assertThat(adapter.findByName("Ho\u00E0ng")).isPresent();
    }

    /** Tên hợp lệ ở biên (tiếng Việt dựng sẵn, chữ Hán, ký tự ngoài BMP, đúng 16) phải lưu và đọc lại y nguyên. */
    @Test
    void shouldRoundTripValidEdgeNames_exactly() {
        for (String name : validEdgeNames()) {
            long accountId = createAccount();
            Character created =
                    createUseCase.execute(accountId, name, UUID.randomUUID().toString());

            assertThat(adapter.listByAccount(accountId))
                    .extracting(Character::name)
                    .containsExactly(name);
            assertThat(adapter.findByName(name)).map(Character::id).contains(created.id());
        }
    }

    /** Chọn nhân vật của account khác / không tồn tại: cùng một lỗi "không tìm thấy" (SECURITY.md §4). */
    @Test
    void shouldNotSelectCharacterOfAnotherAccount_orAnId_thatDoesNotExist() {
        long owner = createAccount();
        long intruder = createAccount();
        Character owned =
                createUseCase.execute(owner, "OwnedOne", UUID.randomUUID().toString());
        SelectCharacterUseCase select = new SelectCharacterUseCase(adapter);

        for (long characterId : new long[] {owned.id(), 0L, -1L, Long.MAX_VALUE, Long.MIN_VALUE}) {
            assertThatThrownBy(() -> select.execute(intruder, characterId))
                    .isInstanceOf(CharacterNotFoundException.class);
        }
        assertThat(select.execute(owner, owned.id()).id()).isEqualTo(owned.id());
    }

    static List<String> hostileNames() {
        return List.of(
                "",
                " ",
                "  ",
                "A",
                "Kage Hero",
                " Kage",
                "Kage ",
                "Kage\n",
                "Kage\tHero",
                "Kage\u0000",
                "\u0000\u0000",
                "Kage\u200BHero",
                "Kage\u200DHero",
                "\u200B\u200B",
                "Kage\u202EoreH",
                "\u202E\u202E",
                "Kage\u200EHero",
                "Kage\u00A0Hero",
                "Kage\u3000Hero",
                "Kage\uD800",
                "\uD83D\uDE00\uD83D\uDE00",
                "Kage-Hero",
                "Kage.Hero",
                "<b>Kage</b>",
                "Kage'--",
                "%s%n",
                "A".repeat(17),
                "A".repeat(10_000));
    }

    static List<String> validEdgeNames() {
        return List.of("Ho\u00E0ng_Long", "\u6797\u52C7", "\uD840\uDC00".repeat(8), "A".repeat(16), "Kage_2");
    }

    static String describe(String value) {
        StringBuilder out = new StringBuilder();
        value.codePoints().limit(24).forEach(cp -> out.append(String.format("U+%04X ", cp)));
        return out.append("(len=").append(value.length()).append(')').toString();
    }

    // Trả về exception của lượt tạo (rỗng = thành công) để luồng test tổng hợp kết quả.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static Optional<RuntimeException> createAfterBarrier(
            CyclicBarrier barrier, long accountId, String name, String key) throws Exception {
        barrier.await(1, TimeUnit.MINUTES);
        try {
            createUseCase.execute(accountId, name, key);
            return Optional.empty();
        } catch (RuntimeException e) {
            return Optional.of(e);
        }
    }

    private static long createAccount() {
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        String username = "inv" + NEXT_ACCOUNT.incrementAndGet();
        Account[] holder = new Account[1];
        accountTx.inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, username));
        return holder[0].id();
    }

    private static long countCharactersNamed(String name) throws SQLException {
        return scalar("SELECT COUNT(*) FROM character WHERE name = ?", name);
    }

    private static long countAllCharacters() throws SQLException {
        return scalar("SELECT COUNT(*) FROM character");
    }

    private static long countCharactersWithoutStatRow() throws SQLException {
        return scalar("SELECT COUNT(*) FROM character c"
                + " WHERE NOT EXISTS (SELECT 1 FROM character_stat s WHERE s.character_id = c.id)");
    }

    private static long countStatRowsWithoutCharacter() throws SQLException {
        return scalar("SELECT COUNT(*) FROM character_stat s"
                + " WHERE NOT EXISTS (SELECT 1 FROM character c WHERE c.id = s.character_id)");
    }

    private static long countUsedKeys(List<String> keys) throws SQLException {
        long used = 0;
        for (String key : keys) {
            used += scalar("SELECT COUNT(*) FROM idempotency_key WHERE key = ?", UUID.fromString(key));
        }
        return used;
    }

    private static long scalar(String sql, Object... params) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }
}
