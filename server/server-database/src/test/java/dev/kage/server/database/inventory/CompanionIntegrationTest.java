package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
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
import dev.kage.inventory.application.ComputeEquipmentBonusUseCase;
import dev.kage.inventory.application.EquipItemUseCase;
import dev.kage.inventory.application.StorageUseCase;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.MountPolicy;
import dev.kage.inventory.domain.PetProgress;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/** ADR-070/072 trên Postgres thật — tốc độ phi kiếm, EXP + cấp linh thú, chỉ số linh thú theo cấp, chặn kho. */
@Testcontainers
class CompanionIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int ANY_LEVEL = 100;
    private static final String TEMPLATE_ID = "SELECT id FROM item_template WHERE code = ?";
    private static final String INSERT_ITEM = """
            INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
            VALUES (?, ?, 1, 0, 0) RETURNING id
            """;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;

    // volatile: @BeforeAll ghi ở luồng JUnit, test đọc từ luồng khác (SpotBugs AT).
    static volatile long accountId;

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
                .inTransaction(tx -> holder[0] = new PostgresAccountAdapter().findOrCreate(tx, "companiontester"));
        accountId = holder[0].id();
    }

    @Test
    void wornFlyingSwordShouldGiveItsSpeed_andNothingElse() {
        long characterId = createFreshCharacter("MountA");
        long sword = query(INSERT_ITEM, characterId, query(TEMPLATE_ID, "jade_flying_sword"));

        assertThat(bonuses().moveSpeedPercent(characterId)).isZero();
        equip(characterId, sword, EquipmentSlotType.MOUNT);

        assertThat(bonuses().moveSpeedPercent(characterId)).isEqualTo(35);
        assertThat(bonuses().execute(characterId).bonusAtk()).isZero();
        assertThat(bonuses().execute(characterId).bonusHp()).isZero();
    }

    @Test
    void wornPetShouldLevelFromExp_andScaleItsStatsByPetLevel() {
        long characterId = createFreshCharacter("PetA");
        long fox = query(INSERT_ITEM, characterId, query(TEMPLATE_ID, "spirit_fox"));
        CompanionPortUnderTest companions = new CompanionPortUnderTest();

        assertThat(companions.adapter.grantPetExp(characterId, 500L))
                .as("chưa mặc")
                .isEmpty();
        equip(characterId, fox, EquipmentSlotType.PET);
        int atkAtLevelOne = bonuses().execute(characterId).bonusAtk();
        PetProgress progress = companions.adapter.grantPetExp(characterId, 350L).orElseThrow();

        assertThat(progress.level()).isEqualTo(3);
        assertThat(progress.leveledUp()).isTrue();
        assertThat(companions.adapter.equippedPet(characterId).orElseThrow().level())
                .isEqualTo(3);
        assertThat(atkAtLevelOne).isEqualTo(3);
        assertThat(bonuses().execute(characterId).bonusHp())
                .as("10 máu gốc × (1 + 10% × 2)")
                .isEqualTo(12);
        assertThat(storage().deposit(characterId, fox, 1).outcome())
                .as("linh thú đã có cấp không vào kho (sẽ mất cấp)")
                .isIn(StorageUseCase.Outcome.SOCKETED, StorageUseCase.Outcome.EQUIPPED);
    }

    /** ADR-203 — Phi Kiếm Tinh Thạch nâng phi kiếm đang mặc, +5% tốc độ mỗi cấp, dừng ở cấp tối đa không trừ món. */
    @Test
    void essenceShouldUpgradeTheWornSword_upToTheCap() {
        long characterId = createFreshCharacter("MountUp");
        long sword = query(INSERT_ITEM, characterId, query(TEMPLATE_ID, "wooden_flying_sword"));
        long essence = query(INSERT_ITEM, characterId, query(TEMPLATE_ID, "flying_sword_essence"));
        dev.kage.inventory.application.UseItemUseCase use = new dev.kage.inventory.application.UseItemUseCase(
                new dev.kage.inventory.application.UseItemUseCase.Ports(
                        new PostgresInventoryAdapter(dataSource),
                        new PostgresItemTemplateAdapter(dataSource),
                        new PostgresAuditAdapter(),
                        new PostgresInventoryTradeOfferAdapter(),
                        new PostgresCompanionAdapter(dataSource)),
                new JdbcInventoryTransactionRunner(dataSource));

        assertThat(use.execute(characterId, essence).outcome())
                .as("chưa cưỡi phi kiếm")
                .isEqualTo(dev.kage.inventory.application.UseItemUseCase.Outcome.NO_MOUNT_EQUIPPED);
        equip(characterId, sword, EquipmentSlotType.MOUNT);
        int base = bonuses().moveSpeedPercent(characterId);
        assertThat(use.execute(characterId, essence).outcome())
                .isEqualTo(dev.kage.inventory.application.UseItemUseCase.Outcome.OK);
        assertThat(bonuses().moveSpeedPercent(characterId)).isEqualTo(base + MountPolicy.SPEED_PER_LEVEL);
        JdbcOperations.update(
                dataSource, "UPDATE inventory_item SET enhance_level = ? WHERE id = ?", MountPolicy.MAX_LEVEL, sword);
        long more = query(INSERT_ITEM, characterId, query(TEMPLATE_ID, "flying_sword_essence"));
        assertThat(use.execute(characterId, more).outcome())
                .isEqualTo(dev.kage.inventory.application.UseItemUseCase.Outcome.MOUNT_AT_MAX_LEVEL);
        assertThat(bonuses().moveSpeedPercent(characterId)).isEqualTo(MountPolicy.speed(base, MountPolicy.MAX_LEVEL));
    }

    /** ADR-193 — tên riêng theo linh thú đang mặc; chưa mặc thì không ghi; rỗng thì xoá. */
    @Test
    void petNameShouldFollowTheWornPet() {
        long characterId = createFreshCharacter("PetName");
        long turtle = query(INSERT_ITEM, characterId, query(TEMPLATE_ID, "spirit_turtle"));
        PostgresPetNameAdapter names = new PostgresPetNameAdapter(dataSource);

        assertThat(names.renameEquipped(characterId, "Tiểu Quy")).as("chưa mặc").isFalse();
        equip(characterId, turtle, EquipmentSlotType.PET);
        assertThat(names.renameEquipped(characterId, "Tiểu Quy")).isTrue();
        assertThat(names.equippedName(characterId)).contains("Tiểu Quy");
        assertThat(new PostgresCompanionAdapter(dataSource)
                        .equippedPet(characterId)
                        .orElseThrow()
                        .level())
                .as("đặt tên tạo dòng pet_state cấp 1 — không đổi cấp")
                .isEqualTo(1);
        assertThat(names.renameEquipped(characterId, "")).isTrue();
        assertThat(names.equippedName(characterId)).isEmpty();
    }

    /** Gói adapter cho dễ đọc. */
    private static final class CompanionPortUnderTest {
        final PostgresCompanionAdapter adapter = new PostgresCompanionAdapter(dataSource);
    }

    private static ComputeEquipmentBonusUseCase bonuses() {
        return new ComputeEquipmentBonusUseCase(
                        new PostgresEquipmentAdapter(dataSource),
                        new PostgresInventoryAdapter(dataSource),
                        new PostgresItemTemplateAdapter(dataSource),
                        new PostgresGemAdapter(dataSource))
                .withCompanions(new PostgresCompanionAdapter(dataSource));
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

    private static void equip(long characterId, long itemId, EquipmentSlotType slot) {
        assertThat(new EquipItemUseCase(
                                new PostgresInventoryAdapter(dataSource),
                                new PostgresItemTemplateAdapter(dataSource),
                                new PostgresEquipmentAdapter(dataSource),
                                new JdbcInventoryTransactionRunner(dataSource))
                        .execute(characterId, itemId, slot, ANY_LEVEL))
                .isEqualTo(EquipItemUseCase.EquipItemResult.OK);
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
