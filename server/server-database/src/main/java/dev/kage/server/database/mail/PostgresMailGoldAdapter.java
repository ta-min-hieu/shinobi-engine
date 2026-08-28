package dev.kage.server.database.mail;

import java.sql.Connection;

import dev.kage.mail.domain.GoldPort;
import dev.kage.mail.domain.Transaction;
import dev.kage.server.database.GoldLedger;

/**
 * Hiện thực {@link GoldPort} trên PostgreSQL — gold vật lý ở {@code character_stat.gold} (V7).
 *
 * <p>Chỉ có chiều CỘNG (nhận vàng đính kèm thư). Không còn tự viết SQL: đi qua {@link GoldLedger},
 * điểm DUY NHẤT đổi cột đó và cũng là chỗ phát {@code GoldChangedEvent} (ADR-035 Decision-3) — đây
 * chính là đường "vàng đổi mà client không hề bấm gì" mà kiểu hỏi-đáp không bắt kịp.
 */
public final class PostgresMailGoldAdapter implements GoldPort {

    private static final String REASON_CLAIM_MAIL = "gold.reason.claim_mail";

    @Override
    public void creditGold(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        GoldLedger.credit(connection, characterId, amount, REASON_CLAIM_MAIL);
    }
}
