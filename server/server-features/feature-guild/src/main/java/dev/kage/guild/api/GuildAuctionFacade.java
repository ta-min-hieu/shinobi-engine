package dev.kage.guild.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.kage.guild.domain.GuildAuctionPort;

/** API đấu giá bang (ADR-112). Chạm DB — chỉ gọi trên Virtual Thread hoặc luồng nền. */
public final class GuildAuctionFacade {

    /** Composition không có đấu giá (test) — không lô nào. */
    public static final GuildAuctionFacade DISABLED = new GuildAuctionFacade(new GuildAuctionPort() {
        @Override
        public Optional<Lot> createLot(NewLot lot) {
            return Optional.empty();
        }

        @Override
        public List<Lot> openLotsOf(long characterId, Instant now) {
            return List.of();
        }

        @Override
        public BidOutcome bid(long characterId, long lotId, long amount, Instant now) {
            return BidOutcome.NOT_FOUND;
        }

        @Override
        public int settleDue(Instant now) {
            return 0;
        }

        @Override
        public List<Payout> pendingPayouts() {
            return List.of();
        }

        @Override
        public void markPaid(long payoutId) {
            // Hàng đợi luôn rỗng.
        }
    });

    private final GuildAuctionPort port;

    /** Dựng ở composition root. */
    public GuildAuctionFacade(GuildAuctionPort port) {
        this.port = port;
    }

    /** Mở lô sau khi boss Bí Cảnh chết — xem {@link GuildAuctionPort#createLot}. */
    public Optional<GuildAuctionPort.Lot> createLot(GuildAuctionPort.NewLot lot) {
        return port.createLot(lot);
    }

    /** Lô còn mở của bang người xem. */
    public List<GuildAuctionPort.Lot> openLotsOf(long characterId, Instant now) {
        return port.openLotsOf(characterId, now);
    }

    /** Trả giá — {@code characterId} từ session. */
    public GuildAuctionPort.BidOutcome bid(long characterId, long lotId, long amount, Instant now) {
        return port.bid(characterId, lotId, amount, now);
    }

    /** Chốt mọi lô hết giờ — xem {@link GuildAuctionPort#settleDue}. */
    public int settleDue(Instant now) {
        return port.settleDue(now);
    }

    /** Thư còn chờ gửi. */
    public List<GuildAuctionPort.Payout> pendingPayouts() {
        return port.pendingPayouts();
    }

    /** Xoá một thư khỏi hàng đợi khi đã nằm trong hộp. */
    public void markPaid(long payoutId) {
        port.markPaid(payoutId);
    }
}
