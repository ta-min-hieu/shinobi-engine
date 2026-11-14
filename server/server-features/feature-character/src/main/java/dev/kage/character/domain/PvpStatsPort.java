package dev.kage.character.domain;

/** Thống kê PvP ngoài đấu trường (ADR-378) — hai cột trong {@code character_lifetime_stats}. */
public interface PvpStatsPort {

    /** Số lần hạ người chơi và số lần bị người chơi hạ. */
    record Stats(int playerKills, int deathsByPlayer) {}

    /** Ghi một lần {@code killerId} hạ {@code victimId}. */
    void recordKill(long killerId, long victimId);

    /** Số liệu của nhân vật (0/0 nếu chưa có dòng). */
    Stats stats(long characterId);
}
