package dev.kage.server.database.mail;

import java.sql.Connection;

import dev.kage.mail.domain.GoldPort;
import dev.kage.mail.domain.Transaction;
import dev.kage.server.database.GoldLedger;

/**
 * Hiện thực {@link GoldPort} trên PostgreSQL — gold vật lý ở {@code character_stat.gold} (V7).
 *
 * <p>Không tự viết SQL: cả hai chiều đi qua {@link GoldLedger}, điểm DUY NHẤT đổi cột đó và cũng
 * là chỗ phát {@code GoldChangedEvent} (ADR-035 Decision-3). Nhờ vậy người GỬI thư nhìn thấy số
 * vàng của mình tụt xuống ngay khi transaction commit, không phải relog mới thấy — đúng đường
 * "vàng đổi mà client không hề bấm gì" mà kiểu hỏi-đáp không bắt kịp.
 *
 * <p>Hai chiều tiền ở đây tương ứng đúng hai use-case: trừ = ký quỹ lúc GỬI, cộng = nhận thư.
 */
public final class PostgresMailGoldAdapter implements GoldPort {

    private static final String REASON_SEND_MAIL = "gold.reason.send_mail";

    private static final String REASON_CLAIM_MAIL = "gold.reason.claim_mail";

    @Override
    public boolean deductGold(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        return GoldLedger.deduct(connection, characterId, amount, REASON_SEND_MAIL);
    }

    @Override
    public void creditGold(Transaction tx, long characterId, long amount) {
        Connection connection = ((JdbcTransaction) tx).connection();
        GoldLedger.credit(connection, characterId, amount, REASON_CLAIM_MAIL);
    }
}
