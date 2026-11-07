package dev.kage.trade.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.kage.trade.domain.TradeHistoryPort;

/** Lịch sử giao dịch (ADR-189) — {@link #PAGE} giao dịch gần nhất trong {@link #WINDOW}. Chạm DB — Virtual Thread. */
public final class TradeHistoryFacade {

    /** Số giao dịch tối đa một lần hỏi. */
    public static final int PAGE = 30;

    /** Chỉ nhìn lại chừng này. */
    public static final Duration WINDOW = Duration.ofDays(30);

    /** Composition không có lịch sử (test): rỗng. */
    public static final TradeHistoryFacade DISABLED = new TradeHistoryFacade((characterId, since, limit) -> List.of());

    private final TradeHistoryPort port;

    /** Dựng ở composition root. */
    public TradeHistoryFacade(TradeHistoryPort port) {
        this.port = port;
    }

    /** Giao dịch gần nhất của {@code characterId} tính tới {@code now}. */
    public List<TradeHistoryPort.Entry> recent(long characterId, Instant now) {
        return port.recent(characterId, now.minus(WINDOW), PAGE);
    }
}
