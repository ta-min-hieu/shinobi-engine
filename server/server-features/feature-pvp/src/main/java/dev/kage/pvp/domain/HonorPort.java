package dev.kage.pvp.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Điểm vinh dự + cửa hàng (ADR-155, V126) — hiện thực ở server-database. Cộng điểm và đổi đồ đều là MỘT giao dịch; đổi
 * đồ mở đầu bằng khoá túi của nhân vật.
 */
public interface HonorPort {

    /** Kết quả đổi đồ. */
    enum Outcome {
        OK,
        UNKNOWN_ITEM,
        NOT_ENOUGH_HONOR,
        WEEKLY_LIMIT,
        BAG_FULL
    }

    /** Một món trong cửa hàng + số lần đã đổi tuần này. */
    record ShopItem(long itemTemplateId, int price, int quantity, int weeklyLimit, int boughtThisWeek) {}

    /** Điểm còn tiêu được + cửa hàng tuần {@code weekStart}. */
    record Shop(long available, List<ShopItem> items) {

        public Shop {
            items = List.copyOf(items);
        }
    }

    /** Cộng {@code points} điểm trận này, theo trần ngày {@link HonorRules#DAILY_CAP}; trả điểm thực cộng. */
    int award(long characterId, int points, LocalDate day);

    Shop shop(long characterId, LocalDate weekStart);

    /** Đổi một lần món {@code itemTemplateId}; từ chối thì rollback trọn. */
    Outcome buy(long characterId, long itemTemplateId, LocalDate weekStart, Instant now);
}
