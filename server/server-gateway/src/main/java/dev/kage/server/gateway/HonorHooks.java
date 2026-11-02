package dev.kage.server.gateway;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.pvp.api.HonorFacade;

/**
 * Điểm vinh dự (ADR-155) — bước phụ sau khi một trận đấu trường chốt xong (đã ở Virtual Thread của việc chốt). Lỗi
 * dừng ở đây: kết quả trận và Elo không suy suyển.
 */
final class HonorHooks {

    private static final Logger LOG = LoggerFactory.getLogger(HonorHooks.class);

    // volatile, không final: registry dựng TRƯỚC DB — bind() sau.
    private volatile HonorFacade facade = HonorFacade.DISABLED;

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(HonorFacade realFacade) {
        this.facade = realFacade;
    }

    // PMD AvoidCatchingGenericException: việc phụ — lỗi dừng ở đây, việc chính không suy suyển.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onDuelSettled(long winnerId, long loserId) {
        try {
            facade.onDuelSettled(winnerId, loserId, Instant.now());
        } catch (RuntimeException e) {
            LOG.warn("Cộng điểm vinh dự lỗi: winner={}, loser={}", winnerId, loserId, e);
        }
    }
}
