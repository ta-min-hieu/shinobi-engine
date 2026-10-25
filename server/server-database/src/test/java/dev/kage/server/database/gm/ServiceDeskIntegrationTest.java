package dev.kage.server.database.gm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import dev.kage.checkin.domain.ActivityPort;
import dev.kage.gm.domain.GiftCode;
import dev.kage.gm.domain.GiftCodeOutcome;
import dev.kage.gm.domain.GiftCodePort;
import dev.kage.gm.domain.PlayerReport;
import dev.kage.gm.domain.PlayerReportPort;
import dev.kage.gm.domain.ReportOutcome;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.checkin.PostgresActivityAdapter;

/** Lô 22 phần 1 trên PostgreSQL THẬT (V89) — thông báo, mã quà, báo cáo, điểm năng động. */
@Testcontainers
class ServiceDeskIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant NOW = Instant.parse("2026-10-25T05:00:00Z");
    private static final Instant DAY_START = Instant.parse("2026-10-24T17:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 25);

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
        accountTx.inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, "desktester"));
        accountId = holder[0].id();
    }

    private static long character(String name) {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner tx = new JdbcCharacterTransactionRunner(dataSource);
        Character[] holder = new Character[1];
        tx.inTransaction(t -> holder[0] = adapter.create(
                t,
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString())));
        return holder[0].id();
    }

    @Test
    void shouldKeepOneServerNotice() {
        PostgresServerNoticeAdapter adapter = new PostgresServerNoticeAdapter(dataSource);
        long admin = character("NoticeAdm");
        adapter.set("Bảo trì 22h", admin, NOW);
        adapter.set("Bảo trì 23h", admin, NOW);
        assertThat(adapter.current()).contains("Bảo trì 23h");
        adapter.clear();
        assertThat(adapter.current()).isEmpty();
    }

    @Test
    void shouldRedeemOncePerCharacterAndRespectUsesAndExpiry() {
        PostgresGiftCodeAdapter adapter = new PostgresGiftCodeAdapter(dataSource);
        long admin = character("CodeAdm");
        long first = character("CodeOne");
        long second = character("CodeTwo");
        GiftCode code = new GiftCode("ONEUSE", 500L, 0L, 0, 1, 0, NOW.plusSeconds(3600));
        assertThat(adapter.create(code, admin, NOW)).isTrue();
        assertThat(adapter.create(code, admin, NOW)).isFalse();

        GiftCodePort.Redemption redeemed = adapter.redeem("ONEUSE", first, NOW);
        assertThat(redeemed.outcome()).isEqualTo(GiftCodeOutcome.OK);
        assertThat(redeemed.needsMail()).isTrue();
        // Thư chưa đánh dấu tới ⇒ đổi lại là GỬI LẠI, không tốn thêm lượt.
        assertThat(adapter.redeem("ONEUSE", first, NOW).outcome()).isEqualTo(GiftCodeOutcome.OK);
        adapter.markMailed("ONEUSE", first);
        assertThat(adapter.redeem("ONEUSE", first, NOW).outcome()).isEqualTo(GiftCodeOutcome.ALREADY_REDEEMED);
        assertThat(adapter.redeem("ONEUSE", second, NOW).outcome()).isEqualTo(GiftCodeOutcome.USED_UP);
        assertThat(adapter.redeem("NOSUCH", second, NOW).outcome()).isEqualTo(GiftCodeOutcome.UNKNOWN_CODE);

        adapter.create(new GiftCode("OLDCODE", 100L, 0L, 0, 10, 0, NOW.minusSeconds(1)), admin, NOW);
        assertThat(adapter.redeem("OLDCODE", second, NOW).outcome()).isEqualTo(GiftCodeOutcome.EXPIRED);
    }

    @Test
    void shouldCaptureChatAndEnforceDailyLimits() {
        PostgresPlayerReportAdapter adapter = new PostgresPlayerReportAdapter(dataSource);
        long reporter = character("Reporter");
        long target = character("Rude");
        for (int i = 0; i < 25; i++) {
            JdbcOperations.update(
                    dataSource,
                    "INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, now())",
                    target,
                    "CHAT_MESSAGE",
                    "{\"channel\":\"WORLD\",\"body\":\"tin " + i + "\"}");
        }
        assertThat(adapter.file(filing(reporter, target))).isEqualTo(ReportOutcome.OK);
        assertThat(adapter.file(filing(reporter, target))).isEqualTo(ReportOutcome.ALREADY_REPORTED);
        PlayerReport report = adapter.open(10).getFirst();
        assertThat(report.chatExcerpt()).contains("tin 24").contains("tin 5").doesNotContain("tin 4\"");
        for (int i = 0; i < PlayerReport.MAX_PER_DAY - 1; i++) {
            assertThat(adapter.file(filing(reporter, character("Other" + i)))).isEqualTo(ReportOutcome.OK);
        }
        assertThat(adapter.file(filing(reporter, character("OneMore")))).isEqualTo(ReportOutcome.DAILY_LIMIT);
        assertThat(adapter.resolve(report.id(), reporter, NOW)).isTrue();
        assertThat(adapter.resolve(report.id(), reporter, NOW)).isFalse();
    }

    private static PlayerReportPort.Filing filing(long reporter, long target) {
        return new PlayerReportPort.Filing(reporter, target, PlayerReport.Reason.CHAT, "nói bậy", NOW, DAY_START);
    }

    @Test
    void shouldCountActivitiesAndPayEachChestOnce() {
        PostgresActivityAdapter adapter = new PostgresActivityAdapter(dataSource);
        long player = character("Busy");
        assertThat(adapter.claim(player, DAY, 20, NOW)).isEmpty(); // 0 điểm
        adapter.record(player, DAY, "check_in"); // 10
        adapter.record(player, DAY, "bounty"); // 10
        adapter.record(player, DAY, "no_such_activity"); // bỏ qua, không ném
        assertThat(adapter.progress(player, DAY)).containsEntry("check_in", 1).containsEntry("bounty", 1);

        ActivityPort.Chest chest = adapter.claim(player, DAY, 20, NOW).orElseThrow();
        assertThat(chest.gold()).isEqualTo(500L);
        assertThat(adapter.claim(player, DAY, 20, NOW)).isEmpty(); // đã nhận
        assertThat(adapter.claim(player, DAY, 40, NOW)).isEmpty(); // chưa đủ
        assertThat(adapter.claim(player, DAY, 33, NOW)).isEmpty(); // mốc lạ
        assertThat(adapter.claimed(player, DAY)).containsExactly(20);
        assertThat(adapter.progress(player, DAY.plusDays(1))).isEmpty();
    }
}
