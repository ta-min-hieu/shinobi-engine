package dev.kage.server.database.market;

import dev.kage.market.domain.MarketGoldPort;
import dev.kage.market.domain.Transaction;
import dev.kage.server.database.GoldLedger;

/** Vàng của phường thị — qua {@link GoldLedger} (điểm DUY NHẤT đổi vàng, phát GoldChangedEvent sau commit). */
public final class PostgresMarketGoldAdapter implements MarketGoldPort {

    private static final String REASON_MARKET = "gold.reason.market";

    @Override
    public boolean deduct(Transaction tx, long characterId, long amount) {
        return GoldLedger.deduct(((JdbcTransaction) tx).connection(), characterId, amount, REASON_MARKET);
    }

    @Override
    public void credit(Transaction tx, long characterId, long amount) {
        GoldLedger.credit(((JdbcTransaction) tx).connection(), characterId, amount, REASON_MARKET);
    }
}
