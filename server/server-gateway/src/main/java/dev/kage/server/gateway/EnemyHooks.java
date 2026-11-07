package dev.kage.server.gateway;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.pvp.api.EnemyListFacade;

/**
 * Danh sách cừu nhân (ADR-194). {@link #onCharacterDefeated} đến từ tick thread ⇒ chỉ lọc (người chơi hạ người chơi,
 * người bị hạ đứng trong dải PvP — phán quyết sẵn của {@link KillingIntentHooks#inPvpZone}) rồi đẩy lần ghi DB sang
 * Virtual Thread.
 */
final class EnemyHooks {

    private static final Logger LOG = LoggerFactory.getLogger(EnemyHooks.class);

    /** Thứ việc này cần. */
    record Deps(EnemyListFacade facade, Clock clock) {}

    private final ExecutorService writers = Executors.newVirtualThreadPerTaskExecutor();
    // volatile, không final: registry dựng TRƯỚC DB — bind() sau, cùng khuôn KillingIntentHooks.
    private volatile Optional<Deps> deps = Optional.empty();

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(Deps realDeps) {
        this.deps = Optional.of(realDeps);
    }

    /**
     * Gọi trên tick thread — không chạm DB ở đây.
     *
     * @param killerEntityId DƯƠNG = người chơi, ÂM = quái, 0 = không ai
     * @param inPvpZone người bị hạ đứng trong dải PvP tự do của world chính
     */
    void onCharacterDefeated(long victimId, long killerEntityId, boolean inPvpZone) {
        if (killerEntityId <= 0L || killerEntityId == victimId || !inPvpZone || deps.isEmpty()) {
            return;
        }
        Deps d = deps.get();
        writers.execute(() -> record(d, victimId, killerEntityId));
    }

    // PMD AvoidCatchingGenericException: việc phụ chạy trên Virtual Thread riêng — lỗi dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static void record(Deps d, long victimId, long killerId) {
        try {
            d.facade().recordKill(victimId, killerId, d.clock().instant());
        } catch (RuntimeException e) {
            LOG.warn("Ghi cừu nhân lỗi: victim={}, killer={}", victimId, killerId, e);
        }
    }
}
