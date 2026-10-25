package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.guild.api.GuildWarFacade;

/**
 * Nối "một nhân vật vừa bị nhân vật khác hạ" vào bang chiến (ADR-102 D3). {@code onCharacterDefeated} đến từ tick
 * thread ⇒ ở đây chỉ lọc bằng ảnh chụp RAM rồi đẩy lần ghi DB sang Virtual Thread. DB tự kiểm lại hai người có đúng
 * thuộc hai bang đang trong giờ chiến với nhau không — ảnh chụp chỉ là bộ lọc rẻ.
 */
final class GuildWarHooks {

    private static final Logger LOG = LoggerFactory.getLogger(GuildWarHooks.class);

    /** Ảnh chụp dùng chung: world chính đọc làm luật PvP, hook này đọc làm bộ lọc. */
    final GuildWarRoster roster = new GuildWarRoster();

    private final ExecutorService writers = Executors.newVirtualThreadPerTaskExecutor();
    // volatile, không final: registry dựng TRƯỚC DB — bind() sau, cùng khuôn BountyHooks.
    private volatile GuildWarFacade facade = GuildWarFacade.DISABLED;
    private volatile Clock clock = Clock.systemUTC();

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(GuildWarFacade realFacade, Clock realClock) {
        this.clock = realClock;
        this.facade = realFacade;
    }

    /** Gọi trên tick thread — không chạm DB ở đây. */
    void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {
        if (killerEntityId <= 0 || !roster.isAtWar(defeatedCharacterId) || !roster.isAtWar(killerEntityId)) {
            return;
        }
        GuildWarFacade target = facade;
        Instant at = clock.instant();
        writers.execute(() -> record(target, killerEntityId, defeatedCharacterId, at));
    }

    // PMD AvoidCatchingGenericException: việc phụ chạy trên Virtual Thread riêng — lỗi dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static void record(GuildWarFacade target, long killerId, long victimId, Instant at) {
        try {
            target.recordKill(killerId, victimId, at);
        } catch (RuntimeException e) {
            LOG.warn("Ghi lần hạ bang chiến lỗi: killer={}, victim={}", killerId, victimId, e);
        }
    }

    /** Tắt server có trật tự — gọi TRƯỚC khi đóng pool DB. */
    void stop() {
        writers.shutdown();
    }
}
