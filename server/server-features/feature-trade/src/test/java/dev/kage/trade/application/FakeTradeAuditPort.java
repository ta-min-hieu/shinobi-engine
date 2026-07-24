package dev.kage.trade.application;

import dev.kage.trade.domain.Transaction;
import dev.kage.trade.domain.TradeAuditPort;
import java.util.ArrayList;
import java.util.List;

/** Fake in-memory THẬT — dùng chung cho test {@code application/}. */
final class FakeTradeAuditPort implements TradeAuditPort {

    final List<String> loggedActions = new ArrayList<>();

    @Override
    public void log(Transaction tx, long characterId, String action, String detailJson) {
        loggedActions.add(action);
    }
}
