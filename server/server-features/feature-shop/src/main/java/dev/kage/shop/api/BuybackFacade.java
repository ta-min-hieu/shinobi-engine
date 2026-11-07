package dev.kage.shop.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.kage.shop.domain.BuybackPort;

/** Mua lại đồ đã bán (ADR-202) — {@link #MAX_ENTRIES} món gần nhất trong {@link #WINDOW}. Chạm DB — Virtual Thread. */
public final class BuybackFacade {

    /** Giữ chừng này món gần nhất mỗi nhân vật (cắt lúc ghi). */
    public static final int MAX_ENTRIES = 10;

    /** Mua lại được trong chừng này thời gian sau khi bán. */
    public static final Duration WINDOW = Duration.ofHours(1);

    /** Composition không có tính năng này (test). */
    public static final BuybackFacade DISABLED = new BuybackFacade(new BuybackPort() {
        @Override
        public List<Entry> list(long characterId, Instant since) {
            return List.of();
        }

        @Override
        public Outcome buyBack(long characterId, long entryId, Instant since) {
            return Outcome.NOT_FOUND;
        }
    });

    private final BuybackPort port;

    /** Dựng ở composition root. */
    public BuybackFacade(BuybackPort port) {
        this.port = port;
    }

    /** Món còn mua lại được, mới nhất trước. */
    public List<BuybackPort.Entry> list(long characterId, Instant now) {
        return port.list(characterId, now.minus(WINDOW));
    }

    /** Mua lại một món. */
    public BuybackPort.Outcome buyBack(long characterId, long entryId, Instant now) {
        return port.buyBack(characterId, entryId, now.minus(WINDOW));
    }
}
