package dev.kage.market.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.kage.market.domain.MarketHistoryPort;

/** Lịch sử phường thị (ADR-177) — {@link #PAGE} giao dịch gần nhất trong {@link #WINDOW}. Chạm DB — Virtual Thread. */
public final class MarketHistoryFacade {

    /** Số dòng tối đa một lần hỏi. */
    public static final int PAGE = 50;

    /** Chỉ nhìn lại chừng này. */
    public static final Duration WINDOW = Duration.ofDays(30);

    /** Composition không có lịch sử (test): rỗng. */
    public static final MarketHistoryFacade DISABLED =
            new MarketHistoryFacade((characterId, since, limit) -> List.of());

    private final MarketHistoryPort port;

    /** Dựng ở composition root. */
    public MarketHistoryFacade(MarketHistoryPort port) {
        this.port = port;
    }

    /** Giao dịch gần nhất của {@code characterId} tính tới {@code now}. */
    public List<MarketHistoryPort.Entry> recent(long characterId, Instant now) {
        return port.recent(characterId, now.minus(WINDOW), PAGE);
    }
}
