package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.inventory.domain.DropTableEntry;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V4 + adapter hoạt động đúng
 * trên schema thật (TESTING.md §2), bao gồm seed data ('kunai' / 'training_dummy' drop table).
 */
@Testcontainers
class PostgresInventoryAdapterIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;

    @BeforeAll
    static void migrate() {
        var config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        dataSource = PostgresDataSourceFactory.create(config);
        FlywayMigrator.migrate(dataSource);

        var accountAdapter = new PostgresAccountAdapter();
        var accountTx = new JdbcAccountTransactionRunner(dataSource);
        var accountHolder = new Account[1];
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "inventorytester"));
        accountId = accountHolder[0].id();
    }

    /**
     * Mỗi test tạo NHÂN VẬT RIÊNG thay vì dùng chung 1 {@code characterId} — JUnit không đảm bảo
     * thứ tự chạy test trong 1 class, dùng chung ID khiến test "chưa từng lưu gì" flaky tuỳ test
     * nào chạy trước (từng bắt được thật: {@code shouldPersistItems_...} chạy trước làm
     * {@code shouldReturnEmptyInventory_...} thấy túi KHÔNG rỗng).
     */
    private static long createFreshCharacter(String name) {
        var characterAdapter = new PostgresCharacterAdapter(dataSource);
        var characterTx = new JdbcCharacterTransactionRunner(dataSource);
        var newCharacter = new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
        var holder = new Character[1];
        characterTx.inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        return holder[0].id();
    }

    @Test
    void shouldReturnEmptyInventory_whenNeverSaved() {
        long characterId = createFreshCharacter("InvEmpty");
        var adapter = new PostgresInventoryAdapter(dataSource);

        var inventory = adapter.findByCharacter(characterId).orElseThrow();

        assertThat(inventory.items()).isEmpty();
        assertThat(inventory.isFull()).isFalse();
    }

    @Test
    void shouldPersistItems_acrossSaveAndLoad() {
        long characterId = createFreshCharacter("InvPersist");
        var adapter = new PostgresInventoryAdapter(dataSource);
        var transactionRunner = new JdbcInventoryTransactionRunner(dataSource);
        var inventory = new Inventory(characterId, 20, List.of(ItemStack.newItem(1L, 3, 0)));

        transactionRunner.inTransaction(tx -> adapter.save(tx, inventory));
        var reloaded = adapter.findByCharacter(characterId).orElseThrow();

        assertThat(reloaded.items()).hasSize(1);
        var reloadedStack = reloaded.items().get(0);
        assertThat(reloadedStack.itemTemplateId()).isEqualTo(1L);
        assertThat(reloadedStack.quantity()).isEqualTo(3);
        assertThat(reloadedStack.isPersisted()).isTrue(); // id thật đã được DB gán qua RETURNING id
    }

    @Test
    void shouldReplaceItems_onSecondSave() {
        long characterId = createFreshCharacter("InvReplace");
        var adapter = new PostgresInventoryAdapter(dataSource);
        var transactionRunner = new JdbcInventoryTransactionRunner(dataSource);
        var first = new Inventory(
                characterId, 20, List.of(ItemStack.newItem(1L, 1, 0), ItemStack.newItem(1L, 2, 0)));
        transactionRunner.inTransaction(tx -> adapter.save(tx, first));

        var second = new Inventory(characterId, 20, List.of(ItemStack.newItem(1L, 9, 0)));
        transactionRunner.inTransaction(tx -> adapter.save(tx, second));
        var reloaded = adapter.findByCharacter(characterId).orElseThrow();

        // 2 item CŨ (không còn trong "second") bị xoá thật, item mới được thêm — không cộng dồn.
        assertThat(reloaded.items()).hasSize(1);
        assertThat(reloaded.items().get(0).quantity()).isEqualTo(9);
    }

    @Test
    void shouldPreserveItemId_whenSavingAlreadyPersistedItem() {
        // Đúng vấn đề lát cắt "mặc đồ" cần sửa: ID KHÔNG được đổi giữa các lần save khi item vẫn
        // còn đó — equipment_slot.inventory_item_id tham chiếu ID này, đổi ID ngầm sẽ làm gãy
        // tham chiếu mà không ai biết.
        long characterId = createFreshCharacter("InvStableId");
        var adapter = new PostgresInventoryAdapter(dataSource);
        var transactionRunner = new JdbcInventoryTransactionRunner(dataSource);
        transactionRunner.inTransaction(
                tx -> adapter.save(tx, new Inventory(characterId, 20, List.of(ItemStack.newItem(1L, 1, 0)))));
        var afterFirstSave = adapter.findByCharacter(characterId).orElseThrow();
        long realId = afterFirstSave.items().get(0).id();

        // Load lại, đổi quantity (giữ nguyên id thật), save lần 2.
        var mutated = new ItemStack(realId, 1L, 5, 0);
        transactionRunner.inTransaction(
                tx -> adapter.save(tx, new Inventory(characterId, 20, List.of(mutated))));
        var afterSecondSave = adapter.findByCharacter(characterId).orElseThrow();

        assertThat(afterSecondSave.items()).containsExactly(mutated); // id KHÔNG đổi, chỉ quantity đổi
    }

    @Test
    void shouldFindSeededDropTableEntries_forTrainingDummy() {
        var adapter = new PostgresDropTableAdapter(dataSource);

        List<DropTableEntry> entries = adapter.findEntries("training_dummy");

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).dropChancePercent()).isEqualTo(50);
    }

    @Test
    void shouldReturnEmptyList_forUnknownDropTableCode() {
        var adapter = new PostgresDropTableAdapter(dataSource);

        assertThat(adapter.findEntries("does_not_exist")).isEmpty();
    }
}
