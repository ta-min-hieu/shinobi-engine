package dev.kage.trade.application;

import dev.kage.trade.domain.Transaction;
import dev.kage.trade.domain.TradeGoldPort;
import java.util.HashMap;
import java.util.Map;

/** Fake in-memory THẬT — mô phỏng {@code character_stat.gold}, dùng chung cho test {@code application/}. */
final class FakeTradeGoldPort implements TradeGoldPort {

    private final Map<Long, Long> balances = new HashMap<>();

    void setGold(long characterId, long amount) {
        balances.put(characterId, amount);
    }

    long getGold(long characterId) {
        return balances.getOrDefault(characterId, 0L);
    }

    @Override
    public boolean deductGold(Transaction tx, long characterId, long amount) {
        long current = balances.getOrDefault(characterId, 0L);
        if (current < amount) {
            return false;
        }
        balances.put(characterId, current - amount);
        return true;
    }

    @Override
    public void creditGold(Transaction tx, long characterId, long amount) {
        balances.merge(characterId, amount, Long::sum);
    }
}
