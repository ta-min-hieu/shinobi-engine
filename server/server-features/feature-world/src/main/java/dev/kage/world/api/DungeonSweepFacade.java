package dev.kage.world.api;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import dev.kage.world.domain.DungeonSweepPort;

/** Quét nhanh Bí Cảnh (ADR-162). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class DungeonSweepFacade {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Composition không có quét nhanh (test). */
    public static final DungeonSweepFacade DISABLED = new DungeonSweepFacade(
            (characterId, dungeonCode, day, maxEntries) ->
                    DungeonSweepPort.Result.refused(DungeonSweepPort.Outcome.UNKNOWN_DUNGEON, 0),
            Clock.systemUTC());

    private final DungeonSweepPort port;
    private final Clock clock;

    /** Dựng ở composition root. */
    public DungeonSweepFacade(DungeonSweepPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    /** Quét một lần; lượt ngày cùng trần với lượt vào thật ({@link DungeonQuotaFacade#MAX_DAILY_ENTRIES}). */
    public DungeonSweepPort.Result sweep(long characterId, String dungeonCode) {
        LocalDate today = LocalDate.now(clock.withZone(VIETNAM));
        return port.sweep(characterId, dungeonCode, today, DungeonQuotaFacade.MAX_DAILY_ENTRIES);
    }
}
