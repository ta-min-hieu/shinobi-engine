package dev.kage.achievement.domain;

import java.util.List;

/** Danh vọng tông môn (ADR-109) — hiện thực ở server-database (V90). */
public interface ReputationPort {

    /** Kết quả mua một món. */
    enum BuyOutcome {
        OK,
        UNKNOWN_ITEM,
        TIER_TOO_LOW,
        NOT_ENOUGH_REPUTATION,
        BAG_FULL
    }

    /** Điểm của một nhân vật: tích luỹ (quyết bậc) và đã tiêu. */
    record Standing(long earned, long spent) {

        /** Chưa có dòng nào. */
        public static final Standing NONE = new Standing(0, 0);

        /** Điểm còn tiêu được. */
        public long available() {
            return earned - spent;
        }
    }

    /** Một món của cửa hàng danh vọng (tham số). */
    record ShopItem(long itemTemplateId, int price, int requiredTier) {}

    /** Không có danh vọng (test). */
    ReputationPort NONE = new ReputationPort() {
        @Override
        public void add(long characterId, int points) {
            // không lưu gì
        }

        @Override
        public Standing standingOf(long characterId) {
            return Standing.NONE;
        }

        @Override
        public List<ShopItem> shop() {
            return List.of();
        }

        @Override
        public BuyOutcome buy(long characterId, long itemTemplateId) {
            return BuyOutcome.UNKNOWN_ITEM;
        }
    };

    /** Cộng {@code points} (> 0) vào điểm tích luỹ. */
    void add(long characterId, int points);

    /** Điểm hiện tại. */
    Standing standingOf(long characterId);

    /** Các món, theo thứ tự hiển thị. */
    List<ShopItem> shop();

    /** Mua một món — nguyên tử: đủ bậc, đủ điểm, túi còn chỗ ⇒ trừ điểm + thêm vật phẩm. */
    BuyOutcome buy(long characterId, long itemTemplateId);
}
