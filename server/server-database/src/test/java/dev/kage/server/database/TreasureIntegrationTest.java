package dev.kage.server.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
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
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.world.PostgresTreasureAdapter;
import dev.kage.world.domain.TreasurePort;

/**
 * ADR-147 — Tàng Bảo Đồ trên PostgreSQL THẬT (V119): tiêu đúng một tấm bản đồ, một cuộc tìm mỗi nhân vật, túi đầy giữ
 * nguyên cuộc tìm, đào xong không đào lại được, phần thưởng có quái canh kho trả đúng loài của điểm chôn.
 */
@Testcontainers
class TreasureIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    /** Thứ tự id của V119 — điểm chôn thứ ba (khe đá Linh Mạch, X = 95). */
    private static final int SPIRIT_VEIN_ROCKS = 2;

    /** Trọng số V119 của các phần thưởng test chọn — mỗi số chỉ thuộc về MỘT phần thưởng. */
    private static final int SMALL_CACHE = 40;

    private static final int PILL_CACHE = 20;
    private static final int GUARDED_CACHE = 8;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;

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
    }

    @Test
    void readingShouldConsumeOneMapAndOpenOneHuntAtATime() {
        long hunter = character("tbd_reader");
        PostgresTreasureAdapter treasure = new PostgresTreasureAdapter(dataSource);

        assertThat(treasure.read(hunter, NOW, weights -> 0).outcome()).isEqualTo(TreasurePort.Outcome.NO_MAP);

        give(hunter, "treasure_map", 2, 0);
        TreasurePort.ReadResult read = treasure.read(hunter, NOW, weights -> {
            assertThat(weights).as("chọn đều trên sáu điểm").containsOnly(1).hasSize(6);
            return SPIRIT_VEIN_ROCKS;
        });
        assertThat(read.outcome()).isEqualTo(TreasurePort.Outcome.OK);
        assertThat(read.spot().orElseThrow().x()).isEqualTo(95f);
        assertThat(maps(hunter)).isEqualTo(1L);

        TreasurePort.ReadResult again = treasure.read(hunter, NOW, weights -> 0);
        assertThat(again.outcome()).isEqualTo(TreasurePort.Outcome.ALREADY_HUNTING);
        assertThat(again.spot()).isEqualTo(read.spot());
        assertThat(maps(hunter)).as("đang tìm thì không tiêu thêm").isEqualTo(1L);
        assertThat(treasure.activeHunt(hunter)).isEqualTo(read.spot());
    }

    @Test
    void digShouldKeepTheHuntOnFullBag_payOnce_andRefuseAStaleSpot() {
        long hunter = character("tbd_digger");
        PostgresTreasureAdapter treasure = new PostgresTreasureAdapter(dataSource);
        setGold(hunter, 0L);
        give(hunter, "treasure_map", 1, 0);
        TreasurePort.Spot spot =
                treasure.read(hunter, NOW, weights -> SPIRIT_VEIN_ROCKS).spot().orElseThrow();

        assertThat(treasure.dig(hunter, spot.id() + 1, NOW, weights -> indexOfWeight(weights, SMALL_CACHE))
                        .outcome())
                .as("điểm phía gọi kiểm không còn là cuộc tìm đang mở")
                .isEqualTo(TreasurePort.Outcome.NO_HUNT);

        for (int slot = 0; slot < 20; slot++) {
            give(hunter, "kunai", 1, slot);
        }
        TreasurePort.DigResult full =
                treasure.dig(hunter, spot.id(), NOW, weights -> indexOfWeight(weights, PILL_CACHE));
        assertThat(full.outcome()).isEqualTo(TreasurePort.Outcome.BAG_FULL);
        assertThat(full.spot()).contains(spot);
        assertThat(treasure.activeHunt(hunter)).as("túi đầy — kho vẫn còn đó").contains(spot);

        JdbcOperations.update(
                dataSource,
                "DELETE FROM inventory_item WHERE character_id = ? AND item_template_id = "
                        + "(SELECT id FROM item_template WHERE code = 'kunai')",
                hunter);
        TreasurePort.DigResult paid =
                treasure.dig(hunter, spot.id(), NOW, weights -> indexOfWeight(weights, SMALL_CACHE));
        assertThat(paid.outcome()).isEqualTo(TreasurePort.Outcome.OK);
        assertThat(paid.resultKey()).isEqualTo("treasure.result.small_cache");
        assertThat(paid.spawnGuardian()).isFalse();
        assertThat(scalar("SELECT gold FROM character_stat WHERE character_id = ?", hunter))
                .isEqualTo(paid.gold())
                .isPositive();
        assertThat(treasure.activeHunt(hunter)).isEmpty();
        assertThat(treasure.dig(hunter, spot.id(), NOW, weights -> indexOfWeight(weights, SMALL_CACHE))
                        .outcome())
                .as("đào lần hai không được thưởng lại")
                .isEqualTo(TreasurePort.Outcome.NO_HUNT);
        assertThat(scalar("SELECT COUNT(*) FROM audit_log WHERE character_id = ? AND action = 'TREASURE_DIG'", hunter))
                .isEqualTo(1L);
    }

    @Test
    void guardedCacheShouldAskForTheSpotsGuardian() {
        long hunter = character("tbd_guard");
        PostgresTreasureAdapter treasure = new PostgresTreasureAdapter(dataSource);
        give(hunter, "treasure_map", 1, 0);
        TreasurePort.Spot spot =
                treasure.read(hunter, NOW, weights -> SPIRIT_VEIN_ROCKS).spot().orElseThrow();

        TreasurePort.DigResult result =
                treasure.dig(hunter, spot.id(), NOW, weights -> indexOfWeight(weights, GUARDED_CACHE));

        assertThat(result.outcome()).isEqualTo(TreasurePort.Outcome.OK);
        assertThat(result.spawnGuardian()).isTrue();
        assertThat(result.spot().orElseThrow().guardianMonsterCode()).isEqualTo("stone_ogre");
    }

    @Test
    void mapsShouldDropFromEarlyMainWorldMonsters() {
        assertThat(scalar("""
                        SELECT COUNT(*) FROM drop_table_entry e
                          JOIN drop_table dt ON dt.id = e.drop_table_id
                          JOIN item_template it ON it.id = e.item_template_id
                         WHERE it.code = 'treasure_map'
                           AND dt.code IN ('wandering_bandit', 'archive_warden', 'stone_ogre')
                        """)).isEqualTo(3L);
        assertThat(scalar("""
                        SELECT COUNT(*) FROM treasure_spot s
                         WHERE EXISTS (SELECT 1 FROM pvp_zone z
                                        WHERE z.world_code = 'main' AND s.x BETWEEN z.min_x AND z.max_x)
                        """)).as("không điểm chôn nào trong dải PvP").isZero();
        assertThat(scalar("""
                        SELECT COUNT(*) FROM treasure_spot s
                         WHERE NOT EXISTS (SELECT 1 FROM monster_template m WHERE m.code = s.guardian_monster_code)
                        """)).as("mọi quái canh kho là loài có thật").isZero();
    }

    /** Chọn phần thưởng có trọng số {@code weight} — không dựa vào thứ tự id. */
    private static int indexOfWeight(int[] weights, int weight) {
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] == weight) {
                return i;
            }
        }
        throw new AssertionError("không có phần thưởng trọng số " + weight);
    }

    private static long maps(long characterId) {
        return scalar(
                "SELECT COALESCE(SUM(i.quantity), 0) FROM inventory_item i JOIN item_template t"
                        + " ON t.id = i.item_template_id WHERE i.character_id = ? AND t.code = 'treasure_map'",
                characterId);
    }

    private static long scalar(String sql, Object... params) {
        return JdbcOperations.queryOne(dataSource, sql, rs -> rs.getLong(1), params)
                .orElseThrow();
    }

    private static void setGold(long characterId, long gold) {
        JdbcOperations.update(
                dataSource, "UPDATE character_stat SET gold = ? WHERE character_id = ?", gold, characterId);
    }

    private static void give(long characterId, String code, int quantity, int slot) {
        JdbcOperations.update(dataSource, """
                INSERT INTO inventory_item (character_id, item_template_id, quantity, slot_index, enhance_level)
                SELECT ?, id, ?, ?, 0 FROM item_template WHERE code = ?
                """, characterId, quantity, slot, code);
    }

    private static long character(String name) {
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] account = new Account[1];
        accountTx.inTransaction(tx -> account[0] = accountAdapter.findOrCreate(tx, name));
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner tx = new JdbcCharacterTransactionRunner(dataSource);
        Character[] holder = new Character[1];
        tx.inTransaction(t -> holder[0] = adapter.create(
                t,
                new NewCharacter(
                        account[0].id(), name, Faction.HOA, UUID.randomUUID().toString())));
        return holder[0].id();
    }
}
