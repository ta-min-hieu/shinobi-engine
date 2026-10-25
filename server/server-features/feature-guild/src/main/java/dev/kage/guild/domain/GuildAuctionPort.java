package dev.kage.guild.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Đấu giá bang (ADR-112) — hiện thực ở server-database. Mọi bước ghi là MỘT giao dịch khoá dòng lô: trả giá (trừ vàng
 * người mới + xếp hoàn tiền người cũ), chốt (một lần, xếp thư vật phẩm + phần chia). Thư đi qua hàng đợi bền.
 */
public interface GuildAuctionPort {

    /** Kết quả trả giá. */
    enum BidOutcome {
        OK,
        /** Không có lô này trong bang của người trả. */
        NOT_FOUND,
        CLOSED,
        TOO_LOW,
        ALREADY_LEADING,
        INSUFFICIENT_GOLD
    }

    /** Loại thư trong hàng đợi. */
    enum PayoutKind {
        /** Hoàn vàng cho người vừa bị trả vượt. */
        REFUND,
        /** Vật phẩm cho người thắng (hoặc người ra đòn cuối khi không ai trả). */
        ITEM,
        /** Phần chia tiền đấu giá cho một người tham gia. */
        SHARE
    }

    /** Lô mới: bang, người ra đòn cuối, những người cùng bang có mặt. */
    record NewLot(long guildId, long killerId, List<Long> participantIds, Instant at) {

        public NewLot {
            participantIds = List.copyOf(participantIds);
        }
    }

    /**
     * Một lô như người xem thấy.
     *
     * @param leaderName tên người đang dẫn; rỗng = chưa ai trả
     */
    record Lot(
            long id,
            long itemTemplateId,
            int quantity,
            long currentBid,
            String leaderName,
            boolean leadingIsMe,
            Instant endsAt,
            int participants) {}

    /** Một thư chờ gửi. {@code itemTemplateId == 0} = thư chỉ vàng. */
    record Payout(long id, long characterId, PayoutKind kind, long gold, long itemTemplateId, int quantity) {}

    /** Gieo vật phẩm từ bảng {@code guild_auction_drop} rồi mở lô; rỗng nếu bảng rơi trống. */
    Optional<Lot> createLot(NewLot lot);

    /** Lô còn mở của bang người xem, sắp hết giờ trước. */
    List<Lot> openLotsOf(long characterId, Instant now);

    BidOutcome bid(long characterId, long lotId, long amount, Instant now);

    /** Chốt mọi lô đã hết giờ — mỗi lô một giao dịch, chỉ một lần. Trả số lô vừa chốt. */
    int settleDue(Instant now);

    List<Payout> pendingPayouts();

    void markPaid(long payoutId);
}
