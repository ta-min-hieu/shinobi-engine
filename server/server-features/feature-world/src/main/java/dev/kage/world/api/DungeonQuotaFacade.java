package dev.kage.world.api;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import dev.kage.world.domain.DungeonQuotaPort;

/** Lượt Bí Cảnh mỗi ngày + thưởng lần đầu (ADR-099). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class DungeonQuotaFacade {

    /** Lượt mỗi ngày mỗi Bí Cảnh. */
    public static final int MAX_DAILY_ENTRIES = 3;

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Composition không giới hạn lượt (test) — mọi lệnh vào đều được, không ai có thưởng lần đầu. */
    public static final DungeonQuotaFacade UNLIMITED = new DungeonQuotaFacade(
            new DungeonQuotaPort() {
                @Override
                public Optional<Long> tryConsume(
                        List<Long> characterIds, String dungeonCode, LocalDate day, int maxEntries) {
                    return Optional.empty();
                }

                @Override
                public int usedOn(long characterId, String dungeonCode, LocalDate day) {
                    return 0;
                }

                @Override
                public boolean recordFirstClear(long characterId, String dungeonCode) {
                    return false;
                }
            },
            Clock.systemUTC());

    private final DungeonQuotaPort port;
    private final Clock clock;

    /** Dựng ở composition root. */
    public DungeonQuotaFacade(DungeonQuotaPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(VIETNAM));
    }

    /** Trừ lượt cho cả nhóm sắp vào; rỗng = được vào, có giá trị = người hết lượt. */
    public Optional<Long> tryConsume(List<Long> characterIds, String dungeonCode) {
        return characterIds.isEmpty()
                ? Optional.empty()
                : port.tryConsume(characterIds, dungeonCode, today(), MAX_DAILY_ENTRIES);
    }

    /** Lượt còn hôm nay. */
    public int entriesLeft(long characterId, String dungeonCode) {
        return Math.max(0, MAX_DAILY_ENTRIES - port.usedOn(characterId, dungeonCode, today()));
    }

    /** Ghi lần đầu hạ boss. */
    public boolean recordFirstClear(long characterId, String dungeonCode) {
        return port.recordFirstClear(characterId, dungeonCode);
    }
}
