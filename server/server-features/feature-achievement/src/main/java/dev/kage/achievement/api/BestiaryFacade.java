package dev.kage.achievement.api;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import dev.kage.achievement.domain.BestiaryPort;

/** API sổ sưu tầm yêu thú (ADR-108). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class BestiaryFacade {

    /** Không có sổ nào (test). */
    public static final BestiaryFacade DISABLED = new BestiaryFacade(BestiaryPort.NONE, Clock.systemUTC());

    private final BestiaryPort port;
    private final Clock clock;

    /** Dựng ở composition root. */
    public BestiaryFacade(BestiaryPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    /** Một lần hạ quái của người ra đòn cuối — xem {@link BestiaryPort#recordKill}. */
    public Optional<BestiaryPort.Entry> recordKill(long characterId, String monsterCode) {
        return port.recordKill(characterId, monsterCode, clock.instant());
    }

    /** Sổ của một nhân vật. */
    public List<BestiaryPort.Progress> progressOf(long characterId) {
        return port.progressOf(characterId);
    }

    /** Thưởng chỉ số của các trang đã mở. */
    public BestiaryPort.Bonus bonusOf(long characterId) {
        return port.bonusOf(characterId);
    }
}
