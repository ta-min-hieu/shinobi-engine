package dev.kage.server.gateway;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.api.DeathLogFacade;
import dev.kage.character.api.PvpStatsFacade;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.DeathLogPort;

/**
 * Lịch sử tử vong (ADR-337). Tick thread báo một nhân vật bị hạ ⇒ ghi audit trên Virtual Thread riêng (tên người chơi
 * và DB không chạm tick thread). Quái chỉ có id thực thể, nên lớp này nhớ mã quái theo id từ các gói spawn đã phát
 * (LRU {@value #MAX_MONSTERS} con). Chưa nối ⇒ không làm gì (composition test).
 */
final class DeathLogHooks {

    /** Số quái nhớ mã — đủ cho mọi quái đang sống quanh người chơi, không phình theo thời gian. */
    static final int MAX_MONSTERS = 4_096;

    private static final Logger LOG = LoggerFactory.getLogger(DeathLogHooks.class);

    /** Nguồn dữ liệu. */
    record Deps(DeathLogFacade log, CharacterFacade characters, PvpStatsFacade pvp) {}

    private final Map<Long, String> monsterCodes = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
            return size() > MAX_MONSTERS;
        }
    };
    private final ExecutorService writers = Executors.newVirtualThreadPerTaskExecutor();
    private volatile Optional<Deps> deps = Optional.empty();

    /** Nối ở composition root. */
    void bind(Deps bound) {
        this.deps = Optional.of(bound);
    }

    /** Tick thread — một quái vừa được phát cho ai đó; nhớ mã của nó. */
    void rememberMonster(long monsterId, String monsterCode) {
        synchronized (monsterCodes) {
            monsterCodes.put(monsterId, monsterCode);
        }
    }

    /** Mã quái theo id thực thể ("" nếu không nhớ). */
    String monsterCode(long monsterId) {
        synchronized (monsterCodes) {
            return monsterCodes.getOrDefault(monsterId, "");
        }
    }

    /** Tick thread — nhân vật bị hạ ở hoành độ {@code x}; {@code killerEntityId} dương = người chơi, âm = quái. */
    void onDefeated(long victimId, long killerEntityId, float x) {
        Optional<Deps> bound = deps;
        if (bound.isEmpty()) {
            return;
        }
        String monster = killerEntityId < 0L ? monsterCode(killerEntityId) : "";
        DeathLogPort.Entry draft = new DeathLogPort.Entry("", monster, Math.round(x), java.time.Instant.now());
        writers.execute(() -> write(bound.get(), victimId, killerEntityId, draft));
    }

    // PMD AvoidCatchingGenericException: việc phụ chạy trên Virtual Thread riêng — lỗi dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static void write(Deps bound, long victimId, long killerEntityId, DeathLogPort.Entry draft) {
        try {
            String killer = killerEntityId > 0L && killerEntityId != victimId
                    ? bound.characters()
                            .findById(killerEntityId)
                            .map(Character::name)
                            .orElse("")
                    : "";
            bound.log().record(victimId, new DeathLogPort.Entry(killer, draft.monsterCode(), draft.x(), draft.at()));
            if (!killer.isEmpty()) {
                bound.pvp().recordKill(killerEntityId, victimId); // ADR-378
            }
        } catch (RuntimeException e) {
            LOG.warn("Ghi lịch sử tử vong lỗi: victim={}", victimId, e);
        }
    }
}
