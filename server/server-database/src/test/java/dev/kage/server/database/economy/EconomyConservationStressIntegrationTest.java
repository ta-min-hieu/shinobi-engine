package dev.kage.server.database.economy;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.LongAdder;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.GoldChangeListener;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.trade.api.TradeFacade;

/**
 * Kiểm BẢO TOÀN kinh tế dưới tải đồng thời NGẪU NHIÊN, trên Postgres thật, qua đúng Facade mà
 * gateway gọi (2026-10-04). Khác {@code EconomyConcurrencyIntegrationTest} (mỗi test đặt MỘT cuộc
 * đua đã biết trước): ở đây {@link #THREAD_COUNT} luồng × {@link #CHARACTER_COUNT} nhân vật trộn mua/bán, rơi
 * đồ, dùng đan, cường hoá, mặc/gỡ, gửi/nhận/xoá thư, toàn bộ vòng đời giao dịch và quest — hợp lệ
 * lẫn không hợp lệ — rồi khẳng định các bất biến ở {@link EconomyInvariants} sau MỖI vòng (các luồng
 * đã dừng hẳn) và lần cuối sau khi dọn phiên giao dịch.
 *
 * <p><b>Tái lập.</b> Ngẫu nhiên có seed cố định (in ra khi đỏ). Thứ tự xen kẽ giữa các luồng thì
 * không tái lập được — seed chỉ cố định CHUỖI thao tác mỗi luồng định làm. Thấy đỏ thì rút gọn
 * thành một ca tất định (như các test ở {@code EconomyConcurrencyIntegrationTest}) trước khi sửa.
 *
 * <p><b>Ngân sách.</b> Mặc định {@value #DEFAULT_SECONDS}s để nằm trong {@code check}. Chạy dài bằng
 * system property (Gradle chuyển tiếp mọi {@code -Dkage.econ.*}):
 * {@code ./gradlew :server:server-database:test --tests '*EconomyConservationStress*'
 * -Dkage.econ.seconds=600 -Dkage.econ.seed=42}. Các khoá: {@code seed}, {@code seconds},
 * {@code roundSeconds}, {@code threads}, {@code characters}.
 */
@Testcontainers
class EconomyConservationStressIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(EconomyConservationStressIntegrationTest.class);

    private static final int POSTGRES_PORT = 5432;
    private static final long DEFAULT_SEED = 20_261_004L;
    private static final int DEFAULT_SECONDS = 20;
    private static final int DEFAULT_ROUND_SECONDS = 5;
    private static final int DEFAULT_THREADS = 8;
    private static final int DEFAULT_CHARACTERS = 6;
    private static final long JOIN_GRACE_SECONDS = 60L;
    private static final long START_GOLD = 5_000L;
    private static final String INSERT_ITEM =
            "INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)"
                    + " VALUES (?, ?, ?, 0, ?)";
    private static final List<String> BUYABLE_CODES = List.of(
            "kunai", "minor_healing_pill", "wooden_sword", "iron_scrap", "novice_wraps", "enhance_protection_charm");
    /** Túi ban đầu mỗi nhân vật: {mã, số lượng} — mỗi cặp một dòng. */
    private static final List<Map.Entry<String, Integer>> STARTING_BAG = List.of(
            Map.entry("kunai", 10),
            Map.entry("minor_healing_pill", 3),
            Map.entry("wooden_sword", 1),
            Map.entry("iron_scrap", 5));

    private static final long RUN_SEED = Long.getLong("kage.econ.seed", DEFAULT_SEED);
    private static final int RUN_SECONDS = Integer.getInteger("kage.econ.seconds", DEFAULT_SECONDS);
    private static final int ROUND_SECONDS = Integer.getInteger("kage.econ.roundSeconds", DEFAULT_ROUND_SECONDS);
    private static final int THREAD_COUNT = Integer.getInteger("kage.econ.threads", DEFAULT_THREADS);
    private static final int CHARACTER_COUNT = Integer.getInteger("kage.econ.characters", DEFAULT_CHARACTERS);

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    /** Mọi lần vàng đổi ĐÃ COMMIT mà {@code GoldLedger} phát ra — sổ vàng độc lập thứ hai. */
    private static final Map<Long, LongAdder> COMMITTED_GOLD_DELTAS = new ConcurrentHashMap<>();

    static DataSource dataSource;

    @BeforeAll
    static void migrate() {
        dataSource = PostgresDataSourceFactory.create(new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()));
        FlywayMigrator.migrate(dataSource);
        GoldLedger.configureListener(change -> COMMITTED_GOLD_DELTAS
                .computeIfAbsent(change.characterId(), id -> new LongAdder())
                .add(change.delta()));
    }

    @AfterAll
    static void shutdown() {
        GoldLedger.configureListener(GoldChangeListener.NO_OP);
        PostgresDataSourceFactory.close(dataSource);
    }

    @Test
    void shouldConserveGoldAndItems_underRandomConcurrentEconomyTraffic() throws InterruptedException {
        EconomyStressWorld world = buildWorld();
        EconomyInvariants invariants = new EconomyInvariants(world, COMMITTED_GOLD_DELTAS);
        assertThat(invariants.checkAll()).as("bất biến trước khi chạy").isEmpty();

        int rounds = Math.max(1, RUN_SECONDS / Math.max(1, ROUND_SECONDS));
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        try {
            for (int round = 0; round < rounds; round++) {
                runRound(world, executor, round);
                assertClean(world, invariants.checkAll(), "sau vòng " + round);
            }
        } finally {
            executor.shutdownNow();
        }

        cancelEveryOpenTrade(world);
        List<String> finalViolations = new ArrayList<>(invariants.checkAll());
        finalViolations.addAll(nothingLeftHanging(invariants));
        assertClean(world, finalViolations, "sau khi dọn phiên giao dịch");
        LOG.info(
                "Bảo toàn kinh tế ĐẠT — seed={}, {} vòng × {}s, {} luồng, {} nhân vật. Kết quả: {}",
                RUN_SEED,
                rounds,
                ROUND_SECONDS,
                THREAD_COUNT,
                CHARACTER_COUNT,
                world.tally().outcomeCounts());
    }

    private static void runRound(EconomyStressWorld world, ExecutorService executor, int round)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ROUND_SECONDS);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < THREAD_COUNT; t++) {
            Random random = new Random(RUN_SEED * 1_000_003L + (long) round * THREAD_COUNT + t);
            futures.add(executor.submit(new EconomyStressActor(world, random, deadline)));
        }
        for (Future<?> future : futures) {
            try {
                future.get(ROUND_SECONDS + JOIN_GRACE_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throw new AssertionError(
                        "Một thao tác ném lỗi (vòng %d, seed=%d): %s".formatted(round, RUN_SEED, e.getCause()), e);
            } catch (TimeoutException e) {
                throw new AssertionError(
                        "Luồng treo quá hạn (vòng %d, seed=%d) — nghi deadlock".formatted(round, RUN_SEED), e);
            }
        }
    }

    private static void assertClean(EconomyStressWorld world, List<String> invariantViolations, String when) {
        List<String> violations = new ArrayList<>(world.tally().violations());
        violations.addAll(invariantViolations);
        assertThat(violations)
                .as(
                        "Vi phạm bảo toàn %s — seed=%d (chạy lại: -Dkage.econ.seed=%d). Kết quả thao tác: %s",
                        when, RUN_SEED, RUN_SEED, world.tally().outcomeCounts())
                .isEmpty();
    }

    /** Dọn qua đúng use case huỷ — phiên còn mở sau khi luồng dừng là trạng thái hợp lệ, chưa phải lỗi. */
    private static void cancelEveryOpenTrade(EconomyStressWorld world) {
        for (long characterId : world.characters()) {
            Optional<TradeFacade.TradeStatusView> status =
                    world.facades().trade().getStatus(characterId);
            status.ifPresent(view -> world.facades().trade().cancel(view.sessionId(), characterId));
        }
    }

    /** Không phiên/lời chào/khoá tư vấn/transaction dở dang nào sống sót khi mọi luồng đã dừng. */
    private static List<String> nothingLeftHanging(EconomyInvariants invariants) {
        List<String> violations = new ArrayList<>();
        long sessions = invariants.single("SELECT COUNT(*) FROM trade_session");
        long offerItems = invariants.single("SELECT COUNT(*) FROM trade_offer_item");
        long advisoryLocks = invariants.single("SELECT COUNT(*) FROM pg_locks WHERE locktype = 'advisory'");
        long idleInTransaction = invariants.single("SELECT COUNT(*) FROM pg_stat_activity"
                + " WHERE datname = current_database() AND state LIKE 'idle in transaction%'");
        if (sessions + offerItems > 0) {
            violations.add("còn %d phiên / %d dòng lời chào sau khi huỷ hết".formatted(sessions, offerItems));
        }
        if (advisoryLocks > 0) {
            violations.add("còn %d khoá tư vấn bị giữ khi không ai chạy".formatted(advisoryLocks));
        }
        if (idleInTransaction > 0) {
            violations.add("còn %d kết nối 'idle in transaction'".formatted(idleInTransaction));
        }
        return violations;
    }

    // ---------------------------------------------------------------- dựng trạng thái ban đầu

    private static EconomyStressWorld buildWorld() {
        LOG.info(
                "Bảo toàn kinh tế: seed={}, {}s (vòng {}s), {} luồng, {} nhân vật",
                RUN_SEED,
                RUN_SECONDS,
                ROUND_SECONDS,
                THREAD_COUNT,
                CHARACTER_COUNT);
        EconomyStressCatalog catalog = loadCatalog();
        long accountId = createAccount();
        long[] characterIds = new long[CHARACTER_COUNT];
        Map<Long, Long> initialQuantities = new HashMap<>();
        for (int i = 0; i < CHARACTER_COUNT; i++) {
            characterIds[i] = createCharacter(accountId, i);
            setGold(characterIds[i]);
            int slot = 0;
            for (Map.Entry<String, Integer> entry : STARTING_BAG) {
                long templateId = catalog.idOf(entry.getKey());
                execute(INSERT_ITEM, characterIds[i], templateId, entry.getValue(), slot++);
                initialQuantities.merge(templateId, (long) entry.getValue(), Long::sum);
            }
        }
        return new EconomyStressWorld(
                dataSource,
                accountId,
                EconomyStressFacades.wire(dataSource),
                catalog,
                new EconomyStressTally(),
                characterIds,
                START_GOLD,
                initialQuantities);
    }

    private static EconomyStressCatalog loadCatalog() {
        Map<Long, EconomyStressCatalog.Template> templates = new HashMap<>();
        Map<String, Long> idByCode = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT id, code, max_stack, equip_slot FROM item_template");
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String slot = rs.getString("equip_slot");
                EconomyStressCatalog.Template template = new EconomyStressCatalog.Template(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getInt("max_stack"),
                        Optional.ofNullable(slot).map(EquipmentSlotType::valueOf));
                templates.put(template.id(), template);
                idByCode.put(template.code(), template.id());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc item_template", e);
        }
        List<Long> buyable = BUYABLE_CODES.stream().map(idByCode::get).toList();
        return new EconomyStressCatalog(templates, buyable, idByCode);
    }

    private static long createAccount() {
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        Account[] holder = new Account[1];
        new JdbcAccountTransactionRunner(dataSource)
                .inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, "econstress"));
        return holder[0].id();
    }

    private static long createCharacter(long accountId, int index) {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId, "EconStress" + index, Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        new JdbcCharacterTransactionRunner(dataSource)
                .inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        return holder[0].id();
    }

    private static void setGold(long characterId) {
        execute("UPDATE character_stat SET gold = ? WHERE character_id = ?", START_GOLD, characterId);
    }

    private static void execute(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only: " + sql, e);
        }
    }
}
