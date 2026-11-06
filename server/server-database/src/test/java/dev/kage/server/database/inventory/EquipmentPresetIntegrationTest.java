package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
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
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/** ADR-175 — bộ trang bị trên Postgres thật: lưu, ghi đè trọn bộ, các bộ độc lập nhau. */
@Testcontainers
final class EquipmentPresetIntegrationTest {

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
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "presettester"));
        accountId = accountHolder[0].id();
    }

    @Test
    void saveReplacesTheWholePreset_andPresetsAreIndependent() {
        long owner = createFreshCharacter("PresetA");
        PostgresEquipmentPresetAdapter adapter = new PostgresEquipmentPresetAdapter(dataSource);

        assertThat(adapter.presetsOf(owner)).isEmpty();
        adapter.replace(owner, 1, Map.of(EquipmentSlotType.WEAPON, 11L, EquipmentSlotType.ARMOR, 12L));
        adapter.replace(owner, 2, Map.of(EquipmentSlotType.WEAPON, 21L));
        adapter.replace(owner, 1, Map.of(EquipmentSlotType.HELMET, 13L));

        assertThat(adapter.presetsOf(owner))
                .containsOnlyKeys(1, 2)
                .containsEntry(1, Map.of(EquipmentSlotType.HELMET, 13L))
                .containsEntry(2, Map.of(EquipmentSlotType.WEAPON, 21L));
        adapter.replace(owner, 2, Map.of());
        assertThat(adapter.presetsOf(owner)).containsOnlyKeys(1);
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
}
