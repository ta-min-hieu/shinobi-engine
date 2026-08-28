package dev.kage.server.database.inventory;

import java.sql.Connection;

import dev.kage.inventory.domain.GoldPort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.server.database.GoldLedger;

/**
 * Hiện thực {@link GoldPort} trên PostgreSQL — gold vật lý ở {@code character_stat.gold} (V7).
 *
 * <p>Không còn tự viết SQL: mọi thay đổi vàng đi qua {@link GoldLedger}, điểm DUY NHẤT đổi cột đó
 * và cũng là chỗ duy nhất phát {@code GoldChangedEvent} (ADR-035 Decision-3 — xem javadoc lớp đó
 * để biết vì sao không rắc lời gọi phát sự kiện ở từng use-case).
 *
 * <p>Port này chỉ phục vụ CƯỜNG HOÁ nên {@code reasonKey} là hằng số ở đây.
 */
public final class PostgresGoldAdapter implements GoldPort {

    private static final String REASON_ENHANCE = "gold.reason.enhance_item";

    @Override
    public boolean deductGold(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        return GoldLedger.deduct(connection, characterId, amount, REASON_ENHANCE);
    }
}
