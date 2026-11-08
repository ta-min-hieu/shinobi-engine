package dev.kage.character.domain;

/** Thống kê cả đời của một nhân vật (ADR-223): số lần bị hạ, tổng giây trong world. */
public interface LifetimeStatsPort {

    /** Số liệu đã lưu. */
    record Stats(int deaths, long playtimeSeconds) {

        /** Chưa có gì. */
        public static final Stats NONE = new Stats(0, 0L);
    }

    void addDeath(long characterId);

    void addPlaytime(long characterId, long seconds);

    Stats of(long characterId);
}
