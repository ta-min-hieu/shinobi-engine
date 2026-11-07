package dev.kage.trade.domain;

import java.time.Instant;
import java.util.List;

/** Lịch sử giao dịch (ADR-189) — đọc dòng audit {@code TRADE_EXECUTED} của chính nhân vật. Chạm DB. */
public interface TradeHistoryPort {

    /** Một món trong một chiều của giao dịch. */
    record Item(long itemTemplateId, int quantity, int enhanceLevel) {}

    /**
     * Một giao dịch nhìn từ phía {@code characterId}: người kia (tên hiện tại, "" nếu không còn), vàng / món đã đưa và
     * đã nhận.
     */
    record Entry(
            String counterpartName,
            long goldGiven,
            long goldReceived,
            List<Item> given,
            List<Item> received,
            Instant at) {

        public Entry {
            given = List.copyOf(given);
            received = List.copyOf(received);
        }
    }

    /** Mới nhất trước, từ {@code since}, tối đa {@code limit} giao dịch. */
    List<Entry> recent(long characterId, Instant since, int limit);
}
