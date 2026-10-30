package dev.kage.server.database.world;

import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;
import dev.kage.server.database.inventory.TemplateQuantityConsumer;
import dev.kage.world.domain.ConsumablePort;

/** Hiện thực {@link ConsumablePort} — cùng cách Truyền Tống Phù (ADR-100): khoá túi chung rồi trừ/cộng một cái. */
public final class PostgresConsumableAdapter implements ConsumablePort {

    private static final String TEMPLATE_ID = "SELECT id FROM item_template WHERE code = ?";

    private final DataSource dataSource;

    public PostgresConsumableAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean consume(long characterId, String itemCode) {
        Optional<Long> templateId = templateId(itemCode);
        if (templateId.isEmpty()) {
            return false;
        }
        return JdbcTransactionRunner.call(dataSource, connection -> {
            CharacterInventoryLock.acquire(connection, characterId);
            return TemplateQuantityConsumer.consume(connection, characterId, templateId.get(), 1);
        });
    }

    @Override
    public void refund(long characterId, String itemCode) {
        templateId(itemCode)
                .ifPresent(id -> JdbcTransactionRunner.run(dataSource, connection -> {
                    CharacterInventoryLock.acquire(connection, characterId);
                    if (!InventoryStackMerge.mergeIntoExistingStack(connection, characterId, id, 1)) {
                        InventoryStackMerge.insertRespectingMaxStack(connection, characterId, id, 1);
                    }
                }));
    }

    private Optional<Long> templateId(String code) {
        return JdbcOperations.queryOne(dataSource, TEMPLATE_ID, rs -> rs.getLong(1), code);
    }
}
