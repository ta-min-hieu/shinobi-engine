package dev.kage.server.database.pvp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.pvp.domain.EloRating;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Postgres THẬT qua Testcontainers — {@code PostgresPvpRatingAdapter} chưa từng có test nào trước
 * 2026-09-20 (rà bằng máy: không {@code PostgresPvpRatingAdapterIntegrationTest.java} nào tồn tại
 * trong repo cho tới file này).
 *
 * <p><b>Race THẬT đã tìm được và vá cùng ngày</b> (xem javadoc
 * {@code dev.kage.pvp.domain.PvpRatingPort#recordDuelResult} cho mô tả đầy đủ): bản cũ của
 * {@code RecordDuelResultUseCase} đọc rating 2 người bằng 2 lời gọi {@code findByCharacterId} RỜI,
 * không khoá, rồi mới gọi Port ghi — một nhân vật thắng 2 trận Arena gần như liên tiếp (trận sau
 * chốt trước khi trận trước ghi xong DB, cả hai chạy trên {@code Virtual Thread} khác nhau qua
 * {@code ArenaInstanceRegistry.settlementExecutor}) có thể mất nguyên 1 trận Elo: cả 2 lần đọc thấy
 * CÙNG rating cũ, ai ghi SAU thắng. {@code wins}/{@code losses} không bị ảnh hưởng (cộng dồn qua
 * {@code UPSERT ... SET wins = pvp_rating.wins + EXCLUDED.wins}), chỉ {@code rating} bị ghi đè sai.
 *
 * <p>Test dưới đây KHÔNG gọi {@code recordDuelResult} hai lần đồng thời và hy vọng may mắn bắt được
 * race (không tất định) — nó tự tay khoá {@code FOR UPDATE} dòng A bằng một kết nối JDBC riêng, THẬT
 * SỰ ghi một rating mới rồi mới commit (mô phỏng "một trận khác của A vừa chốt xong nơi khác"), rồi
 * verify lời gọi {@code recordDuelResult} thật cho trận 2 của A đọc TRÚNG giá trị vừa commit đó —
 * xem javadoc riêng của {@link #shouldReadThePostCommitRating_whenASecondDuelRacesAConcurrentlyCommittingOne}
 * cho lý do phải ghi một giá trị mới THẬT (không chỉ giữ khoá suông) mới phân biệt được bug.
 */
@Testcontainers
class PostgresPvpRatingAdapterIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "pvpratingtester"));
        accountId = accountHolder[0].id();
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

    @Test
    void shouldApplyBothDuelsInSequence_whenRunOneAfterAnother() {
        long a = createFreshCharacter("PvpSeqA");
        long b = createFreshCharacter("PvpSeqB");
        long c = createFreshCharacter("PvpSeqC");
        PostgresPvpRatingAdapter adapter = new PostgresPvpRatingAdapter(dataSource);

        adapter.recordDuelResult(a, b, EloRating::computeNewRatings); // A thắng B.
        int ratingAfterFirstDuel = adapter.findByCharacterId(a).orElseThrow().rating();
        EloRating.Result second = adapter.recordDuelResult(a, c, EloRating::computeNewRatings); // A thắng C.

        // Trận 2 PHẢI tính từ rating THẬT SỰ sau trận 1, không phải DEFAULT_RATING — đây chính là
        // bất biến mà race đã phá vỡ khi 2 trận chồng lấn thời gian.
        EloRating.Result expected = EloRating.computeNewRatings(ratingAfterFirstDuel, EloRating.DEFAULT_RATING);
        assertThat(second.newWinnerRating()).isEqualTo(expected.newWinnerRating());
        assertThat(adapter.findByCharacterId(a).orElseThrow().wins()).isEqualTo(2);
    }

    /**
     * ⚠️ Chỉ "có bị chặn hay không" là phép đo KHÔNG ĐỦ: ngay cả khi {@code recordDuelResult} đọc
     * rời không khoá (bug cũ), câu {@code INSERT ... ON CONFLICT DO UPDATE} cuối cùng vẫn phải chờ
     * khoá {@code FOR UPDATE} của luồng kia mới GHI được — tức lời gọi vẫn "bị chặn" một đoạn, dù
     * đọc đã xong từ trước và đọc TRÚNG giá trị CŨ. Muốn phân biệt đúng bug, luồng cạnh tranh ở đây
     * phải THẬT SỰ ghi một giá trị MỚI rồi commit (không chỉ giữ khoá suông): bản ĐỌC RỜI (bug) sẽ
     * đọc trúng giá trị CŨ trước khi luồng kia ghi, rồi ghi ĐÈ lên giá trị mới đó bằng kết quả tính
     * từ dữ liệu cũ — mất đúng phần thay đổi của "trận 1"; bản ĐÃ VÁ ({@code SELECT ... FOR UPDATE}
     * trước khi đọc) sẽ chờ tới khi luồng kia commit rồi mới đọc, thấy giá trị MỚI, tính đúng.
     */
    @Test
    @Timeout(15)
    void shouldReadThePostCommitRating_whenASecondDuelRacesAConcurrentlyCommittingOne() throws Exception {
        long a = createFreshCharacter("PvpRaceA");
        long b = createFreshCharacter("PvpRaceB");
        long c = createFreshCharacter("PvpRaceC");
        PostgresPvpRatingAdapter adapter = new PostgresPvpRatingAdapter(dataSource);

        // Trận 1 đã CHỐT XONG THẬT (không phải đang treo) — rating của A giờ khác DEFAULT_RATING.
        adapter.recordDuelResult(a, b, EloRating::computeNewRatings);
        int ratingAfterFirstDuel = adapter.findByCharacterId(a).orElseThrow().rating();
        // Giá trị GIẢ LẬP "trận khác của A vừa chốt xong ở nơi khác" — cố tình KHÁC hẳn công thức
        // Elo thật để không thể trùng ngẫu nhiên với kết quả của nhánh bug.
        int concurrentlyCommittedRating = ratingAfterFirstDuel + 777;

        // Tự tay: khoá dòng A, GIỮ một lúc, rồi GHI giá trị mới ở trên và commit — mô phỏng chính
        // xác việc "một trận khác của A đang trong transaction, sắp ghi xong".
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch proceedToCommit = new CountDownLatch(1);
        ExecutorService lockHolder = Executors.newSingleThreadExecutor();
        Future<?> lockHolderTask = lockHolder.submit(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT rating FROM pvp_rating WHERE character_id = ? FOR UPDATE")) {
                    lock.setLong(1, a);
                    // ResultSet chỉ cần THỰC THI để khoá dòng — nội dung không dùng tới, nhưng vẫn
                    // phải đóng qua try-with-resources (SpotBugs RV: bỏ qua giá trị trả về của
                    // executeQuery() coi như rò tài nguyên).
                    try (ResultSet ignored = lock.executeQuery()) {
                        // không đọc gì — chỉ cần dòng đã bị khoá lúc executeQuery() trả về.
                    }
                }
                lockAcquired.countDown();
                if (!proceedToCommit.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test không cho phép commit kịp — xem lỗi ở luồng chính");
                }
                try (PreparedStatement update =
                        connection.prepareStatement("UPDATE pvp_rating SET rating = ? WHERE character_id = ?")) {
                    update.setInt(1, concurrentlyCommittedRating);
                    update.setLong(2, a);
                    update.executeUpdate();
                }
                connection.commit();
            }
            return null;
        });
        assertThat(lockAcquired.await(5, TimeUnit.SECONDS))
                .as("luồng giữ khoá phải chiếm được FOR UPDATE trước khi test tiếp tục")
                .isTrue();

        // Trận 2 của A — gọi khi khoá trên CÒN GIỮ và CHƯA ghi gì. Bản đã vá phải CHỜ ở đây (chờ
        // đúng chỗ ĐỌC, không chỉ chỗ ghi) cho tới khi luồng kia commit xong.
        ExecutorService caller = Executors.newSingleThreadExecutor();
        Future<EloRating.Result> secondDuel =
                caller.submit(() -> adapter.recordDuelResult(a, c, EloRating::computeNewRatings));

        assertThatThrownBy(() -> secondDuel.get(1, TimeUnit.SECONDS))
                .as("trận 2 của A phải CHỜ, không được đọc/ghi ngay trong khi khoá còn bị giữ")
                .isInstanceOf(TimeoutException.class);

        proceedToCommit.countDown();
        lockHolderTask.get(5, TimeUnit.SECONDS);
        EloRating.Result result = secondDuel.get(5, TimeUnit.SECONDS);

        // Phải tính từ concurrentlyCommittedRating (giá trị luồng KIA vừa commit), KHÔNG phải
        // ratingAfterFirstDuel (giá trị CŨ trước khi luồng kia ghi) — đây là phép đo trực tiếp cho
        // chính bug "đọc rời rồi ghi đè" đã tìm được.
        EloRating.Result expected = EloRating.computeNewRatings(concurrentlyCommittedRating, EloRating.DEFAULT_RATING);
        assertThat(result.newWinnerRating()).isEqualTo(expected.newWinnerRating());
        assertThat(adapter.findByCharacterId(a).orElseThrow().rating()).isEqualTo(expected.newWinnerRating());
    }

    /**
     * Cùng race, nhưng ở trận ĐẦU TIÊN của A — chưa có dòng {@code pvp_rating} nào (bug tìm được
     * 2026-10-03). {@code SELECT ... FOR UPDATE} không khoá được một dòng CHƯA TỒN TẠI, và ở READ
     * COMMITTED nó cũng không thấy (nên không chờ) một INSERT chưa commit của giao dịch khác. Trận 2
     * vì thế đọc {@link EloRating#DEFAULT_RATING}, rồi câu UPSERT của nó chờ INSERT kia commit và
     * GHI ĐÈ rating vừa commit bằng kết quả tính từ 1000 — mất trọn trận đầu (wins vẫn đúng 2 vì
     * cộng dồn, chỉ rating sai). Hai trận đầu đời chốt cùng lúc là thật: A thắng B, được thả về world
     * chính, thắng C ngay trong khi Virtual Thread chốt trận 1 còn đang ghi.
     *
     * <p>Mô phỏng "trận 1 đang ghi dở": một kết nối riêng INSERT dòng của A (rating mới) và GIỮ chưa
     * commit. Đo GIÁ TRỊ CUỐI — rating của A sau khi cả hai commit.
     */
    @Test
    @Timeout(15)
    void shouldReadThePostCommitRating_whenASecondDuelRacesTheFirstDuelEverOfThatCharacter() throws Exception {
        long a = createFreshCharacter("PvpFirstA");
        long c = createFreshCharacter("PvpFirstC");
        PostgresPvpRatingAdapter adapter = new PostgresPvpRatingAdapter(dataSource);
        int firstDuelRating = EloRating.DEFAULT_RATING + 777;

        CountDownLatch rowInserted = new CountDownLatch(1);
        CountDownLatch proceedToCommit = new CountDownLatch(1);
        ExecutorService firstDuel = Executors.newSingleThreadExecutor();
        Future<?> firstDuelTask = firstDuel.submit(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO pvp_rating (character_id, rating, wins, losses, updated_at)"
                                + " VALUES (?, ?, 1, 0, now())")) {
                    insert.setLong(1, a);
                    insert.setInt(2, firstDuelRating);
                    insert.executeUpdate();
                }
                rowInserted.countDown();
                if (!proceedToCommit.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test không cho phép commit kịp — xem lỗi ở luồng chính");
                }
                connection.commit();
            }
            return null;
        });
        assertThat(rowInserted.await(5, TimeUnit.SECONDS)).isTrue();

        ExecutorService caller = Executors.newSingleThreadExecutor();
        Future<EloRating.Result> secondDuel =
                caller.submit(() -> adapter.recordDuelResult(a, c, EloRating::computeNewRatings));
        // Cho trận 2 thời gian chạy tới chỗ nó bị chặn (bản lỗi: đã đọc 1000 và đang chờ ở UPSERT).
        assertThatThrownBy(() -> secondDuel.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);

        proceedToCommit.countDown();
        firstDuelTask.get(5, TimeUnit.SECONDS);
        secondDuel.get(5, TimeUnit.SECONDS);

        EloRating.Result expected = EloRating.computeNewRatings(firstDuelRating, EloRating.DEFAULT_RATING);
        assertThat(adapter.findByCharacterId(a).orElseThrow().rating())
                .as("trận 2 phải tính từ rating trận 1 vừa commit, không phải từ 1000")
                .isEqualTo(expected.newWinnerRating());
        assertThat(adapter.findByCharacterId(a).orElseThrow().wins()).isEqualTo(2);
        firstDuel.shutdown();
        caller.shutdown();
    }
}
