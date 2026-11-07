package dev.kage.market.api;

import java.time.Instant;
import java.util.List;

import dev.kage.market.domain.MarketWatchPort;

/** Theo dõi phường thị (ADR-196). Chạm DB — Virtual Thread. */
public final class MarketWatchFacade {

    /** Số loại vật phẩm theo dõi tối đa mỗi nhân vật. */
    public static final int MAX_WATCHES = 10;

    /** Composition không có tính năng này (test). */
    public static final MarketWatchFacade DISABLED = new MarketWatchFacade(new MarketWatchPort() {
        @Override
        public Upsert upsert(WatchRequest request, int maxEntries) {
            return Upsert.UNKNOWN_ITEM;
        }

        @Override
        public boolean remove(long characterId, long itemTemplateId) {
            return false;
        }

        @Override
        public List<Watch> list(long characterId) {
            return List.of();
        }

        @Override
        public List<Long> watchersFor(long itemTemplateId, long unitPrice) {
            return List.of();
        }
    });

    private final MarketWatchPort port;

    /** Dựng ở composition root. */
    public MarketWatchFacade(MarketWatchPort port) {
        this.port = port;
    }

    /** Đặt / sửa / bỏ ({@code maxUnitPrice == 0}) một dòng theo dõi. */
    public Outcome set(long characterId, long itemTemplateId, long maxUnitPrice, Instant at) {
        if (itemTemplateId <= 0L || maxUnitPrice < 0L) {
            return Outcome.INVALID;
        }
        if (maxUnitPrice == 0L) {
            return port.remove(characterId, itemTemplateId) ? Outcome.REMOVED : Outcome.NOT_WATCHING;
        }
        return switch (port.upsert(
                new MarketWatchPort.WatchRequest(characterId, itemTemplateId, maxUnitPrice, at), MAX_WATCHES)) {
            case OK -> Outcome.OK;
            case TOO_MANY -> Outcome.TOO_MANY;
            case UNKNOWN_ITEM -> Outcome.INVALID;
        };
    }

    /** Các dòng theo dõi của {@code characterId}. */
    public List<MarketWatchPort.Watch> list(long characterId) {
        return port.list(characterId);
    }

    /** Ai cần được báo khi có món {@code itemTemplateId} được bày giá {@code price} cho {@code quantity} cái. */
    public List<Long> watchersFor(long itemTemplateId, long price, int quantity) {
        return port.watchersFor(itemTemplateId, unitPrice(price, quantity));
    }

    /** Giá mỗi cái, làm tròn lên — người theo dõi không bị báo một giá thấp hơn giá thật. */
    public static long unitPrice(long price, int quantity) {
        int count = Math.max(1, quantity);
        return (price + count - 1) / count;
    }

    /** Kết quả {@link #set}. */
    public enum Outcome {
        OK,
        REMOVED,
        NOT_WATCHING,
        TOO_MANY,
        INVALID
    }
}
