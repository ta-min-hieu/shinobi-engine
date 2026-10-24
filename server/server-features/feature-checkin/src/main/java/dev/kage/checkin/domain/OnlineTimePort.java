package dev.kage.checkin.domain;

import java.time.LocalDate;

/** Giây online theo ngày (ADR-096) — hiện thực ở server-database, bảng {@code online_time}. */
public interface OnlineTimePort {

    /** Giây đã lưu + bitmask mốc đã nhận của một ngày (cả hai 0 nếu chưa có dòng). */
    record Day(int seconds, int claimedMask) {}

    /** Cộng {@code seconds} vào ngày {@code day} (tạo dòng nếu chưa có). */
    void addSeconds(long characterId, LocalDate day, int seconds);

    Day read(long characterId, LocalDate day);

    /**
     * Bật bit của {@code milestone} trong ngày nếu (giây đã lưu + {@code liveSeconds}) ≥ số giây của mốc và bit
     * chưa bật — MỘT câu UPDATE có điều kiện, hai lệnh song song không cùng thắng.
     *
     * @return {@code true} nếu lần này bật được
     */
    boolean claim(long characterId, LocalDate day, OnlineRewardPolicy.Milestone milestone, int liveSeconds);
}
