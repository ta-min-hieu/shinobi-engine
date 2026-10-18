package dev.kage.server.database.quest;

import java.sql.Connection;

import dev.kage.quest.domain.QuestGoldPort;
import dev.kage.quest.domain.Transaction;
import dev.kage.server.database.GoldLedger;

/**
 * Hiện thực {@link QuestGoldPort} (ADR-055 D4) — không tự viết SQL: đi qua {@link GoldLedger}, điểm DUY
 * NHẤT ghi {@code character_stat.gold} và phát {@code GoldChangedEvent} sau commit. Trần vàng ⇒
 * {@code GoldLedger} ném, cả lượt lĩnh rollback.
 */
public final class PostgresQuestGoldAdapter implements QuestGoldPort {

    private static final String REASON_QUEST_REWARD = "gold.reason.quest_reward";

    @Override
    public void creditGold(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        GoldLedger.credit(connection, characterId, amount, REASON_QUEST_REWARD);
    }
}
