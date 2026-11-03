package dev.kage.server.database.quest;

import dev.kage.quest.domain.QuestItemTakePort;
import dev.kage.quest.domain.Transaction;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryItemTake;

/**
 * Hiện thực {@link QuestItemTakePort} trên PostgreSQL — chiều ngược của
 * {@link PostgresQuestInventoryGrantAdapter}. Luật lấy đồ ở {@link InventoryItemTake} (dùng chung).
 */
public final class PostgresQuestItemTakeAdapter implements QuestItemTakePort {

    @Override
    public void lockInventory(Transaction tx, long characterId) {
        CharacterInventoryLock.acquire(((JdbcTransaction) tx).connection(), characterId);
    }

    @Override
    public boolean takeItemByCode(Transaction tx, long characterId, String itemTemplateCode, int quantity) {
        return InventoryItemTake.takeUnlockedByCode( // ADR-159
                ((JdbcTransaction) tx).connection(), characterId, itemTemplateCode, quantity);
    }
}
