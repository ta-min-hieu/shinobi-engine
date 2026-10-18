package dev.kage.checkin.domain;

/**
 * Quà của MỘT ngày trong chu kỳ (bảng {@code daily_check_in_reward}, ADR-060).
 *
 * @param day 1..{@link CheckInCycle#DAYS}
 * @param gold linh thạch, {@code >= 0}
 * @param itemCode mã vật phẩm, rỗng nếu ngày này chỉ có vàng
 * @param quantity số vật phẩm, 0 khi {@code itemCode} rỗng
 */
public record CheckInReward(int day, long gold, String itemCode, int quantity) {

    public CheckInReward {
        if (day < 1 || day > CheckInCycle.DAYS) {
            throw new IllegalArgumentException("day ngoài chu kỳ: " + day);
        }
        if (gold < 0 || quantity < 0 || itemCode.isEmpty() != (quantity == 0)) {
            throw new IllegalArgumentException("Quà điểm danh sai dạng: ngày " + day);
        }
    }

    public boolean hasItem() {
        return !itemCode.isEmpty();
    }
}
