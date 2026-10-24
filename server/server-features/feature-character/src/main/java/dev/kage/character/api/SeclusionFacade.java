package dev.kage.character.api;

import java.time.Clock;

import dev.kage.character.domain.SeclusionPolicy;
import dev.kage.character.domain.SeclusionPort;

/** API bế quan (ADR-095). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class SeclusionFacade {

    /**
     * Trạng thái quỹ để hiển thị.
     *
     * @param expSingle EXP nếu nhận x1 ở cấp hiện tại
     * @param doubleCost linh thạch để nhận x2
     */
    public record Status(int bankedMinutes, long expSingle, long doubleCost) {}

    /** Composition không có bế quan (test) — quỹ luôn rỗng. */
    public static final SeclusionFacade DISABLED = new SeclusionFacade(
            new SeclusionPort() {
                @Override
                public int bankOfflineTime(long characterId, java.time.Instant now) {
                    return 0;
                }

                @Override
                public void markLoggedOut(long characterId, java.time.Instant now) {
                    // không có quỹ để tích
                }

                @Override
                public int bankedMinutes(long characterId) {
                    return 0;
                }

                @Override
                public Claim claim(long characterId, boolean doubled) {
                    return new Claim(Outcome.NOTHING_BANKED, 0L);
                }
            },
            Clock.systemUTC());

    private final SeclusionPort port;
    private final Clock clock;

    /** Dựng ở composition root. */
    public SeclusionFacade(SeclusionPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    /** Vào world — tích phút offline; trả quỹ sau khi tích. */
    public int onEnteredWorld(long characterId) {
        return port.bankOfflineTime(characterId, clock.instant());
    }

    /** Rời world — ghi mốc thoát. */
    public void onLeftWorld(long characterId) {
        port.markLoggedOut(characterId, clock.instant());
    }

    /** Quỹ quy ra EXP ở {@code level} (cấp hiện tại do gateway đọc). */
    public Status status(long characterId, int level) {
        int minutes = port.bankedMinutes(characterId);
        return new Status(minutes, SeclusionPolicy.expFor(minutes, level), SeclusionPolicy.doubleCost(level));
    }

    /** Nhận cả quỹ. */
    public SeclusionPort.Claim claim(long characterId, boolean doubled) {
        return port.claim(characterId, doubled);
    }
}
