package dev.kage.world.api;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import dev.kage.world.domain.DungeonSweepPort;

/** Quét nhanh Bí Cảnh (ADR-162). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class DungeonSweepFacade {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Mã Bí Cảnh hợp lệ — mã đến thẳng từ packet; mã lạ (vd chứa {@code U+0000}, Postgres từ chối cả câu SELECT ⇒ 500)
     * bị trả {@code UNKNOWN_DUNGEON} trước khi chạm DB. Tìm ra bởi {@code GatewayOpcodeFuzzDatabaseTest} 2026-11-06.
     */
    private static final java.util.regex.Pattern DUNGEON_CODE = java.util.regex.Pattern.compile("[a-z0-9_]{1,64}");

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
        if (!DUNGEON_CODE.matcher(dungeonCode).matches()) {
            return DungeonSweepPort.Result.refused(DungeonSweepPort.Outcome.UNKNOWN_DUNGEON, 0);
        }
        LocalDate today = LocalDate.now(clock.withZone(VIETNAM));
        return port.sweep(characterId, dungeonCode, today, DungeonQuotaFacade.MAX_DAILY_ENTRIES);
    }
}
