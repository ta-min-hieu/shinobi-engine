package dev.kage.server.database.quest.fuzz;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;

/**
 * Bộ kiểm THUỘC TÍNH cho máy trạng thái quest + hội thoại (2026-10-06), trên Postgres thật, qua
 * đúng Facade gateway gọi — cùng tinh thần {@code WorldSimulationPropertyTest} và
 * {@code EconomyConservationStressIntegrationTest}.
 *
 * <p>Mỗi seed: một nhân vật mới đặt ở điểm ngẫu nhiên của chuỗi 20 quest (+ một quest giao đồ mẫu
 * không tiên quyết, để có lúc HAI quest cùng nghe một sự kiện), rồi chuỗi lệnh ngẫu nhiên: hạ quái,
 * mở lời, đi cây hội thoại tới lời mời nhận quest, nhận thẳng (kể cả mã bịa), lĩnh thưởng (khoá
 * idempotency mới hoặc đã đốt), đưa đồ giao vào túi (có lúc phân mảnh, có lúc thiếu), nhồi túi gần
 * đầy, bán sạch một loại, đăng nhập lại, và LOẠT SONG SONG của cùng một lệnh hoặc của nhiều lệnh
 * khác nhau. Sau MỖI bước, trạng thái đã lưu phải khớp mô hình tham chiếu ({@link QuestFuzzModel})
 * và các bất biến ở {@link QuestFuzzChecker}; cuối seed mọi quest dở phải đi tới lĩnh thưởng được
 * ({@link QuestFuzzDrain}).
 *
 * <p>⚠️ Không có lệnh BỎ quest để fuzz: dự án chưa có tính năng đó (không opcode, không use case).
 * Hệ quả là bất biến "không kẹt" phải đứng một mình — xem {@link QuestFuzzDrain}.
 *
 * <p>Ngân sách mặc định vừa cổng {@code check}. Bản dài: {@code ./gradlew :server:server-database:test
 * --tests '*QuestStateMachineProperty*' -Dkage.questFuzz.seeds=200 -Dkage.questFuzz.steps=400}. Đỏ thì
 * thông báo in seed + vết lệnh; chạy lại đúng seed đó bằng
 * {@code -Dkage.questFuzz.seed=<seed> -Dkage.questFuzz.seeds=1}.
 */
@Testcontainers
class QuestStateMachinePropertyIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(QuestStateMachinePropertyIntegrationTest.class);

    private static final int POSTGRES_PORT = 5432;
    private static final long BASE_SEED = Long.getLong("kage.questFuzz.seed", 20_261_006L);
    private static final int SEEDS = Integer.getInteger("kage.questFuzz.seeds", 8);
    private static final int STEPS = Integer.getInteger("kage.questFuzz.steps", 120);
    private static final int BURST_THREADS = 4;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

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
        // Quest mẫu ở test resources của module này (cùng script QuestDeliveryIntegrationTest dùng).
        new QuestFuzzDb(dataSource).execute("""
                INSERT INTO quest_template (code, type, script_ref)
                VALUES (?, 'MAIN', '/scripts/quest/it_fixture_delivery.js')
                """, QuestFuzzSpec.FIXTURE_DELIVERY.code());
    }

    @AfterAll
    static void shutdown() {
        PostgresDataSourceFactory.close(dataSource);
    }

    @Test
    void shouldHoldEveryQuestInvariant_underRandomisedCommandsRelogsAndConcurrentDuplicates()
            throws InterruptedException {
        QuestFuzzDb db = new QuestFuzzDb(dataSource);
        QuestFuzzContent content =
                QuestFuzzContent.load(db, QuestFuzzSystem.wire(dataSource).dialogue());
        assertThat(content.questsOfferedInDialogue())
                .as("bộ fuzz phải tìm được lời mời nhận quest trong cây hội thoại")
                .isNotEmpty();
        long accountId = db.createAccount("questfuzz");
        Map<String, Integer> coverage = new TreeMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(BURST_THREADS);
        try {
            for (int i = 0; i < SEEDS; i++) {
                long seed = BASE_SEED + i;
                QuestFuzzRun run = new QuestFuzzRun(seed, db, content);
                List<String> violations = run.run(accountId, STEPS, executor);
                assertThat(violations).as(() -> run.failureReport(violations)).isEmpty();
                run.coverage().forEach((key, count) -> coverage.merge(key, count, Integer::sum));
            }
        } finally {
            executor.shutdownNow();
        }
        LOG.info("Fuzz quest ĐẠT — {} seed từ {}, {} bước mỗi seed. Độ phủ: {}", SEEDS, BASE_SEED, STEPS, coverage);
        // Một lượt "xanh" mà không lĩnh được gì, không ai giao đồ, không loạt song song nào là lượt
        // không kiểm gì cả — khẳng định nó thật sự đi qua các nhánh đắt giá.
        assertThat(coverage)
                .as("độ phủ của lượt fuzz: %s", coverage)
                .containsKeys("Claim:OK", "Dialogue:OK", "Accept:PREREQUISITE_NOT_MET", "completed", "burst2");
    }
}
