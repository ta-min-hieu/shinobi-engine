package dev.kage.guild.domain;

/**
 * Góp vật phẩm cho bang (ADR-207). Một hàm GHI là một transaction trọn trong adapter: lấy món khỏi túi, cộng kinh
 * nghiệm bang + cống hiến bằng giá thu × số lượng, audit, nhật ký bang.
 */
public interface GuildItemDonationPort {

    /** Kết quả một lần góp. */
    enum Outcome {
        OK,
        NOT_IN_GUILD,
        /** Không có trong túi, đang mặc / khoá / cường hoá / khảm / chào giao dịch, hoặc không có giá thu. */
        NOT_DONATABLE,
        INSUFFICIENT_QUANTITY,
        INVALID_QUANTITY
    }

    /**
     * Kết quả kèm số cống hiến nhận được.
     *
     * @param contribution cống hiến nhận được (cũng là kinh nghiệm bang nhận được); 0 nếu không {@code OK}
     * @param guildId bang nhận; 0 nếu không {@code OK}
     */
    record Donation(Outcome outcome, long contribution, boolean leveledUp, long guildId) {

        /** Bị từ chối, không ghi gì. */
        public static Donation refused(Outcome outcome) {
            return new Donation(outcome, 0L, false, 0L);
        }
    }

    /** Góp {@code quantity} cái từ chồng {@code inventoryItemId} của {@code characterId}. */
    Donation donate(long characterId, long inventoryItemId, int quantity);
}
