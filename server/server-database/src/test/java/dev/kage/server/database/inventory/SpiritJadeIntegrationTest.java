package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

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
import dev.kage.inventory.application.ComputeEquipmentBonusUseCase;
import dev.kage.inventory.application.EquipItemUseCase;
import dev.kage.inventory.application.SocketGemUseCase;
import dev.kage.inventory.application.StorageUseCase;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.GemPolicy;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.trade.PostgresTradeSessionAdapter;
import dev.kage.trade.application.AcceptTradeRequestUseCase;
import dev.kage.trade.application.RequestTradeUseCase;
import dev.kage.trade.application.SetTradeOfferUseCase;
import dev.kage.trade.domain.OfferedItemInput;

/**
 * Linh Ngọc (ADR-064) trên PostgreSQL THẬT: hỏng 3 lần thì lần thứ tư chắc chắn thành (bảo hiểm), mỗi lần
 * tiêu đúng một viên + 200 linh thạch; ngọc cộng PHẲNG vào món đang mặc; kho từ chối món đã khảm.
 */
@Testcontainers
class SpiritJadeIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int ANY_LEVEL = 100;
    private static final String TEMPLATE_ID = "SELECT id FROM item_template WHERE code = ?";
    private static final String INSERT_ITEM = """
            INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
            VALUES (?, ?, ?, 0, 0) RETURNING id
            """;
    private static final String SET_GOLD = "UPDATE character_stat SET gold = ? WHERE character_id = ? RETURNING gold";
    private static final String GOLD = "SELECT gold FROM character_stat WHERE character_id = ?";
    private static final String GEMS_LEFT =
            "SELECT COALESCE(SUM(quantity), 0) FROM inventory_item WHERE character_id = ? AND item_template_id = ?";
    private static final String SOCKET_OF = "SELECT gem_item_template_id FROM item_socket WHERE inventory_item_id = ?";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;

    // volatile: @BeforeAll ghi ở luồng JUnit, test đọc từ luồng khác (SpotBugs AT).
    static volatile long accountId;
    static volatile long swordTemplateId;
    static volatile long fireJadeTemplateId;

    @BeforeAll
    static void migrate() {
        dataSource = PostgresDataSourceFactory.create(new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()));
        FlywayMigrator.migrate(dataSource);
        Account[] holder = new Account[1];
        new JdbcAccountTransactionRunner(dataSource)
                .inTransaction(tx -> holder[0] = new PostgresAccountAdapter().findOrCreate(tx, "jadetester"));
        accountId = holder[0].id();
        swordTemplateId = query(TEMPLATE_ID, "wooden_sword");
        fireJadeTemplateId = query(TEMPLATE_ID, "spirit_jade_fire");
    }

    @Test
    void threeFailuresShouldGuaranteeTheFourth_andTheJadeAddsFlatAttackToWornGear() {
        long characterId = createFreshCharacter("JadeA");
        long sword = query(INSERT_ITEM, characterId, swordTemplateId, 1);
        long jades = query(INSERT_ITEM, characterId, fireJadeTemplateId, 4);
        query(SET_GOLD, 1_000L, characterId);
        equip(characterId, sword);
        int attackBefore = bonuses().execute(characterId).bonusAtk();
        SocketGemUseCase alwaysUnlucky = socketing(fixedRoll(0.99));

        for (int attempt = 1; attempt <= GemPolicy.PITY_AFTER_FAILURES; attempt++) {
            SocketGemUseCase.Result failed = alwaysUnlucky.execute(characterId, sword, jades, key());
            assertThat(failed).isEqualTo(new SocketGemUseCase.Result(SocketGemUseCase.Outcome.FAILED, attempt));
        }
        SocketGemUseCase.Result pity = alwaysUnlucky.execute(characterId, sword, jades, key());

        assertThat(pity).isEqualTo(new SocketGemUseCase.Result(SocketGemUseCase.Outcome.SUCCESS, 0));
        assertThat(query(GOLD, characterId)).isEqualTo(1_000L - 4 * GemPolicy.SOCKET_FEE);
        assertThat(query(GEMS_LEFT, characterId, fireJadeTemplateId)).isZero();
        assertThat(query(SOCKET_OF, sword)).isEqualTo(fireJadeTemplateId);
        assertThat(bonuses().execute(characterId).bonusAtk()).isEqualTo(attackBefore + 3);
    }

    @Test
    void refusalsShouldSpendNothing_andSocketedGearCannotBeStored() {
        long characterId = createFreshCharacter("JadeB");
        long sword = query(INSERT_ITEM, characterId, swordTemplateId, 1);
        long jades = query(INSERT_ITEM, characterId, fireJadeTemplateId, 2);
        query(SET_GOLD, 100L, characterId);
        SocketGemUseCase lucky = socketing(fixedRoll(0.0));

        assertThat(lucky.execute(characterId, sword, jades, key()).outcome())
                .isEqualTo(SocketGemUseCase.Outcome.INSUFFICIENT_GOLD);
        assertThat(lucky.execute(characterId, jades, sword, key()).outcome())
                .as("ngọc không phải trang bị")
                .isEqualTo(SocketGemUseCase.Outcome.NOT_EQUIPMENT);
        assertThat(query(GEMS_LEFT, characterId, fireJadeTemplateId)).isEqualTo(2L);

        query(SET_GOLD, 500L, characterId);
        assertThat(lucky.execute(characterId, sword, jades, key()).outcome())
                .isEqualTo(SocketGemUseCase.Outcome.SUCCESS);
        assertThat(storage().deposit(characterId, sword, 1).outcome()).isEqualTo(StorageUseCase.Outcome.SOCKETED);
    }

    /**
     * docs/README.md dòng 82: món đích đang chào, hoặc mọi viên ngọc đang chào ⇒ từ chối, không tốn phí hay
     * ngọc. Chào 1 trong chồng 2 viên thì viên còn lại vẫn khảm được (cùng luật dùng vật phẩm, dòng 80).
     */
    @Test
    void offeredGearOrEveryOfferedJadeShouldBeRefused_andSpendNothing() {
        long giverId = createFreshCharacter("JadeC");
        long receiverId = createFreshCharacter("JadeD");
        long sword = query(INSERT_ITEM, giverId, swordTemplateId, 1);
        long jades = query(INSERT_ITEM, giverId, fireJadeTemplateId, 2);
        query(SET_GOLD, 1_000L, giverId);
        PostgresTradeSessionAdapter sessions = new PostgresTradeSessionAdapter(dataSource);
        long sessionId = new RequestTradeUseCase(sessions)
                .execute(giverId, receiverId)
                .session()
                .id();
        new AcceptTradeRequestUseCase(sessions).execute(sessionId, receiverId);
        SetTradeOfferUseCase offer = new SetTradeOfferUseCase(sessions);
        SocketGemUseCase lucky = socketing(fixedRoll(0.0));

        offer.execute(sessionId, giverId, 0, List.of(new OfferedItemInput(sword, 1)));
        assertThat(lucky.execute(giverId, sword, jades, key()).outcome())
                .as("món đích đang chào")
                .isEqualTo(SocketGemUseCase.Outcome.ITEM_IN_TRADE);
        offer.execute(sessionId, giverId, 0, List.of(new OfferedItemInput(jades, 2)));
        assertThat(lucky.execute(giverId, sword, jades, key()).outcome())
                .as("cả hai viên ngọc đang chào")
                .isEqualTo(SocketGemUseCase.Outcome.ITEM_IN_TRADE);
        assertThat(query(GOLD, giverId)).isEqualTo(1_000L);
        assertThat(query(GEMS_LEFT, giverId, fireJadeTemplateId)).isEqualTo(2L);

        offer.execute(sessionId, giverId, 0, List.of(new OfferedItemInput(jades, 1)));
        assertThat(lucky.execute(giverId, sword, jades, key()).outcome())
                .as("còn một viên không chào")
                .isEqualTo(SocketGemUseCase.Outcome.SUCCESS);
        assertThat(query(GEMS_LEFT, giverId, fireJadeTemplateId)).isEqualTo(1L);
    }

    private static SocketGemUseCase socketing(RandomGenerator random) {
        PostgresInventoryAdapter inventory = new PostgresInventoryAdapter(dataSource);
        return new SocketGemUseCase(
                new SocketGemUseCase.Ports(
                        inventory,
                        new PostgresItemTemplateAdapter(dataSource),
                        new PostgresGemAdapter(dataSource),
                        new PostgresGoldAdapter(),
                        new PostgresInventoryTradeOfferAdapter()),
                new SocketGemUseCase.Records(new PostgresInventoryIdempotencyAdapter(), new PostgresAuditAdapter()),
                new JdbcInventoryTransactionRunner(dataSource),
                random);
    }

    private static ComputeEquipmentBonusUseCase bonuses() {
        return new ComputeEquipmentBonusUseCase(
                new PostgresEquipmentAdapter(dataSource),
                new PostgresInventoryAdapter(dataSource),
                new PostgresItemTemplateAdapter(dataSource),
                new PostgresGemAdapter(dataSource));
    }

    private static StorageUseCase storage() {
        return new StorageUseCase(
                new StorageUseCase.Ports(
                        new PostgresInventoryAdapter(dataSource),
                        new PostgresEquipmentAdapter(dataSource),
                        new PostgresItemTemplateAdapter(dataSource),
                        new PostgresInventoryTradeOfferAdapter()),
                new PostgresStorageAdapter(dataSource),
                new PostgresAuditAdapter(),
                new JdbcInventoryTransactionRunner(dataSource));
    }

    private static void equip(long characterId, long itemId) {
        assertThat(new EquipItemUseCase(
                                new PostgresInventoryAdapter(dataSource),
                                new PostgresItemTemplateAdapter(dataSource),
                                new PostgresEquipmentAdapter(dataSource),
                                new JdbcInventoryTransactionRunner(dataSource))
                        .execute(characterId, itemId, EquipmentSlotType.WEAPON, ANY_LEVEL))
                .isEqualTo(EquipItemUseCase.EquipItemResult.OK);
    }

    /** {@code RandomGenerator} mà {@code nextDouble()} luôn ra {@code roll} (mặc định dùng 53 bit cao của nextLong). */
    private static RandomGenerator fixedRoll(double roll) {
        long bits = (long) (roll * (1L << 53)) << 11;
        return () -> bits;
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static long createFreshCharacter(String name) {
        Character[] holder = new Character[1];
        new JdbcCharacterTransactionRunner(dataSource)
                .inTransaction(tx -> holder[0] = new PostgresCharacterAdapter(dataSource)
                        .create(
                                tx,
                                new NewCharacter(
                                        accountId,
                                        name,
                                        Faction.HOA,
                                        UUID.randomUUID().toString())));
        return holder[0].id();
    }

    private static long query(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only: " + sql, e);
        }
    }
}
