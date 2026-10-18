package dev.kage.market.domain;

/** Linh thạch, trong cùng transaction — adapter đi qua {@code GoldLedger} (phát GoldChangedEvent). */
public interface MarketGoldPort {

    boolean deduct(Transaction tx, long characterId, long amount);

    void credit(Transaction tx, long characterId, long amount);
}
