package dev.kage.server.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.inventory.application.AddItemUseCase;
import dev.kage.inventory.application.EnhanceItemUseCase;
import dev.kage.inventory.application.EnhancePorts;
import dev.kage.inventory.application.EquipItemUseCase;
import dev.kage.inventory.application.GrantLootUseCase;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ProcessMonsterKilledUseCase;
import dev.kage.quest.application.QuestRewardPorts;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.inventory.JdbcInventoryTransactionRunner;
import dev.kage.server.database.inventory.PostgresAuditAdapter;
import dev.kage.server.database.inventory.PostgresDropTableAdapter;
import dev.kage.server.database.inventory.PostgresEquipmentAdapter;
import dev.kage.server.database.inventory.PostgresGoldAdapter;
import dev.kage.server.database.inventory.PostgresInventoryAdapter;
import dev.kage.server.database.inventory.PostgresItemTemplateAdapter;
import dev.kage.server.database.quest.JdbcQuestTransactionRunner;
import dev.kage.server.database.quest.PostgresQuestAuditAdapter;
import dev.kage.server.database.quest.PostgresQuestExpAdapter;
import dev.kage.server.database.quest.PostgresQuestInventoryGrantAdapter;
import dev.kage.server.database.quest.PostgresQuestProgressAdapter;
import dev.kage.server.database.quest.PostgresQuestTemplateAdapter;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify Phase 6 Exit Criteria (ROADMAP.md):
 * "Chơi được: tạo nhân vật → nhận quest → đánh quái → nhặt đồ → cường hoá → hoàn thành quest",
 * XÂU CHUỖI các UseCase đã được test riêng lẻ ở {@code QuestIntegrationTest}/
 * {@code EnhanceItemUseCaseIntegrationTest}/{@code PostgresInventoryAdapterIntegrationTest} thành
 * MỘT hành trình người chơi thật, trên CÙNG 1 nhân vật, để bắt lỗi tích hợp giữa các hệ thống mà
 * test riêng lẻ từng feature không thấy được (VD ID item rơi ra có mặc/cường hoá được thật không).
 *
 * <p>KHÔNG đi qua {@code WorldFacade}/AI/Netty (đã verify riêng ở {@code WorldFacadeTest}/
 * {@code GatewayServerIntegrationTest}) — gọi thẳng UseCase như production code thật sự gọi
 * (VD {@code ConnectedCharacterRegistry.onMonsterKilled} gọi CẢ {@code GrantLootUseCase} lẫn
 * {@code ProcessMonsterKilledUseCase} cạnh nhau mỗi lần quái chết, test này lặp lại đúng thứ tự đó).
 *
 * <p>{@code training_dummy} chỉ rơi {@code kunai} (không mặc được) — dùng {@code wooden_sword}
 * (grant thẳng qua {@code AddItemUseCase}, không phải loot) để verify mặc đồ + cường hoá, đúng
 * cách {@code EnhanceItemUseCaseIntegrationTest}/{@code PostgresEquipmentAdapterIntegrationTest}
 * đã làm — bảng rơi đồ MVP chưa có vũ khí rơi được (GAMEPLAY.md §5 chưa mở rộng).
 */
@Testcontainers
class MvpLoopIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final String QUEST_CODE = "kill_training_dummies";
    // 50% tỉ lệ rơi kunai (V4 seed) — lặp đủ nhiều để xác suất KHÔNG rơi lần nào gần như bằng 0
    // (0.5^10 ≈ 0.1%), tránh test bị flaky vì random thật (không mock, cùng tinh thần LootRollerTest).
    private static final int SIMULATED_KILLS_FOR_LOOT = 10;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;
    static long woodenSwordTemplateId;

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "mvplooptester"));
        accountId = accountHolder[0].id();
        woodenSwordTemplateId = findItemTemplateIdByCode("wooden_sword");
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

    private static long findItemTemplateIdByCode(String code) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT id FROM item_template WHERE code = ?")) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only lookup item_template: code=" + code, e);
        }
    }

    private static void grantGold(long characterId, long amount) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE character_stat SET gold = ? WHERE character_id = ?")) {
            statement.setLong(1, amount);
            statement.setLong(2, characterId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only cấp gold: characterId=" + characterId, e);
        }
    }

    private static long readExp(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT exp FROM character WHERE id = ?")) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc exp: characterId=" + characterId, e);
        }
    }

    private static AcceptQuestUseCase newAcceptQuestUseCase() {
        return new AcceptQuestUseCase(
                new PostgresQuestProgressAdapter(dataSource),
                new PostgresQuestTemplateAdapter(dataSource),
                new JdbcQuestTransactionRunner(dataSource));
    }

    private static ProcessMonsterKilledUseCase newProcessMonsterKilledUseCase() {
        return new ProcessMonsterKilledUseCase(
                new PostgresQuestProgressAdapter(dataSource),
                new PostgresQuestTemplateAdapter(dataSource),
                new JdbcQuestTransactionRunner(dataSource));
    }

    private static ClaimQuestRewardUseCase newClaimQuestRewardUseCase() {
        QuestRewardPorts rewardPorts = new QuestRewardPorts(
                new PostgresQuestExpAdapter(),
                new PostgresQuestInventoryGrantAdapter(),
                new PostgresQuestAuditAdapter());
        return new ClaimQuestRewardUseCase(
                new PostgresQuestProgressAdapter(dataSource),
                new PostgresQuestTemplateAdapter(dataSource),
                rewardPorts,
                new JdbcQuestTransactionRunner(dataSource));
    }

    private static GrantLootUseCase newGrantLootUseCase() {
        return new GrantLootUseCase(
                new PostgresDropTableAdapter(dataSource),
                new PostgresInventoryAdapter(dataSource),
                new JdbcInventoryTransactionRunner(dataSource));
    }

    private static AddItemUseCase newAddItemUseCase() {
        return new AddItemUseCase(
                new PostgresInventoryAdapter(dataSource), new JdbcInventoryTransactionRunner(dataSource));
    }

    private static EquipItemUseCase newEquipItemUseCase() {
        return new EquipItemUseCase(
                new PostgresInventoryAdapter(dataSource), new PostgresItemTemplateAdapter(dataSource),
                new PostgresEquipmentAdapter(dataSource), new JdbcInventoryTransactionRunner(dataSource));
    }

    private static EnhanceItemUseCase newEnhanceItemUseCase() {
        EnhancePorts ports = new EnhancePorts(
                new PostgresInventoryAdapter(dataSource),
                new PostgresGoldAdapter(),
                new PostgresAuditAdapter(),
                new PostgresItemTemplateAdapter(dataSource));
        return new EnhanceItemUseCase(ports, new JdbcInventoryTransactionRunner(dataSource));
    }

    @Test
    void shouldPlayFullMvpLoop_createCharacterQuestCombatLootEquipEnhanceComplete() {
        // 1. Tạo nhân vật.
        long characterId = createFreshCharacter("MvpLoopHero");

        // 2. Nhận quest.
        AcceptQuestUseCase.AcceptResult acceptResult = newAcceptQuestUseCase().execute(characterId, QUEST_CODE);
        assertThat(acceptResult).isEqualTo(AcceptQuestUseCase.AcceptResult.OK);

        // 3. Đánh quái nhiều lần — mỗi lần giết, production code gọi CẢ GrantLootUseCase lẫn
        // ProcessMonsterKilledUseCase cạnh nhau (xem ConnectedCharacterRegistry.onMonsterKilled).
        // kill_training_dummies.js cần đúng 3 lần giết mới hoàn thành (QuestIntegrationTest).
        List<ItemStack> allLoot = new ArrayList<>();
        Optional<ProcessMonsterKilledUseCase.QuestStatusChange> statusChange = Optional.empty();
        for (int i = 0; i < SIMULATED_KILLS_FOR_LOOT; i++) {
            allLoot.addAll(newGrantLootUseCase().execute(characterId, "training_dummy"));
            Optional<ProcessMonsterKilledUseCase.QuestStatusChange> change =
                    newProcessMonsterKilledUseCase().execute(characterId, "training_dummy");
            if (change.isPresent()) {
                statusChange = change;
            }
        }

        // 4. Hoàn thành quest sau đủ số lần giết.
        assertThat(statusChange).isPresent();
        assertThat(statusChange.get().status()).isEqualTo(QuestStatus.COMPLETED);

        // 5. Nhặt đồ — training_dummy chỉ rơi kunai (50% mỗi lần, 10 lần thử), gần như chắc chắn
        // rơi được ít nhất 1 lần thật (không mock random).
        assertThat(allLoot).isNotEmpty();

        // 6. Mặc đồ — training_dummy không rơi vũ khí (chỉ kunai, không equip_slot), grant thẳng
        // wooden_sword để verify mặc đồ + cường hoá (đúng cách EnhanceItemUseCaseIntegrationTest
        // đã làm, KHÔNG phải hạn chế của test này).
        AddItemUseCase.AddItemResult addResult =
                newAddItemUseCase().execute(characterId, ItemStack.newItem(woodenSwordTemplateId, 1, 0));
        assertThat(addResult).isEqualTo(AddItemUseCase.AddItemResult.OK);
        long swordItemId = new PostgresInventoryAdapter(dataSource)
                .findByCharacter(characterId)
                .orElseThrow()
                .findByTemplate(woodenSwordTemplateId)
                .orElseThrow()
                .id();

        EquipItemUseCase.EquipItemResult equipResult =
                newEquipItemUseCase().execute(characterId, swordItemId, EquipmentSlotType.WEAPON);
        assertThat(equipResult).isEqualTo(EquipItemUseCase.EquipItemResult.OK);

        // 7. Cường hoá vũ khí vừa mặc.
        grantGold(characterId, 100); // đúng giá cường hoá +0->+1
        EnhanceItemUseCase.EnhanceResult enhanceResult =
                newEnhanceItemUseCase().execute(characterId, swordItemId, false);
        assertThat(enhanceResult)
                .isIn(EnhanceItemUseCase.EnhanceResult.SUCCESS, EnhanceItemUseCase.EnhanceResult.FAILED_NO_DROP);

        // 8. Nhận thưởng hoàn thành quest.
        long expBefore = readExp(characterId);
        ClaimQuestRewardUseCase.ClaimResult claimResult =
                newClaimQuestRewardUseCase().execute(characterId, QUEST_CODE);
        assertThat(claimResult.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        assertThat(readExp(characterId)).isEqualTo(expBefore + claimResult.expGranted());
    }
}
