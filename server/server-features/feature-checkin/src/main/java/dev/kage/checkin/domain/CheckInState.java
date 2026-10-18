package dev.kage.checkin.domain;

import java.time.LocalDate;

/**
 * Lần điểm danh gần nhất của một nhân vật.
 *
 * @param lastDay ngày trong chu kỳ đã nhận gần nhất; 0 = chưa từng điểm danh ({@link #NEVER})
 * @param lastClaimDate ngày lịch (giờ Việt Nam) của lần đó; {@link LocalDate#EPOCH} khi chưa từng
 */
public record CheckInState(int lastDay, LocalDate lastClaimDate) {

    /** Chưa từng điểm danh — không có dòng nào trong {@code daily_check_in}. */
    public static final CheckInState NEVER = new CheckInState(0, LocalDate.EPOCH);

    public boolean isNever() {
        return lastDay == 0;
    }
}
