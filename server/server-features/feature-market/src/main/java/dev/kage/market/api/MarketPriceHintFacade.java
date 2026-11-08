package dev.kage.market.api;

import java.time.Duration;
import java.time.Instant;

import dev.kage.market.domain.MarketPriceHintPort;

/** API giá gợi ý phường thị (ADR-234). */
public final class MarketPriceHintFacade {

    /** Giá bán trung bình tính trên chừng này ngày gần nhất. */
    public static final Duration WINDOW = Duration.ofDays(7);

    /** Không có dữ liệu (composition test). */
    public static final MarketPriceHintFacade DISABLED =
            new MarketPriceHintFacade((itemTemplateId, since) -> MarketPriceHintPort.Hint.NONE);

    private final MarketPriceHintPort port;

    /** Dựng ở composition root. */
    public MarketPriceHintFacade(MarketPriceHintPort port) {
        this.port = port;
    }

    /** Giá gợi ý cho mẫu {@code itemTemplateId}, trung bình bán trong {@link #WINDOW} tính tới {@code now}. */
    public MarketPriceHintPort.Hint hint(long itemTemplateId, Instant now) {
        return port.hint(itemTemplateId, now.minus(WINDOW));
    }
}
