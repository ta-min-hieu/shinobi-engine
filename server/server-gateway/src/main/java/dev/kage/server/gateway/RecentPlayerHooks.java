package dev.kage.server.gateway;

import java.time.Instant;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.party.api.RecentPlayerFacade;

/**
 * Người vừa chơi cùng (ADR-329): ai vào nhóm thì người đó và mọi thành viên khác ghi tên nhau. Gọi từ
 * {@link MembershipNotices} (Virtual Thread). Việc phụ: lỗi dừng ở đây. Chưa nối ⇒ không làm gì (composition test).
 */
final class RecentPlayerHooks {

    private static final Logger LOG = LoggerFactory.getLogger(RecentPlayerHooks.class);

    private volatile RecentPlayerFacade facade;

    /** Nối ở composition root. */
    void bind(RecentPlayerFacade bound) {
        this.facade = bound;
    }

    /** {@code joined} vừa vào nhóm, {@code members} là thành viên sau khi vào. */
    // PMD AvoidCatchingGenericException: việc phụ — lỗi không được làm hỏng lệnh vào nhóm.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onJoined(long joined, Collection<Long> members) {
        RecentPlayerFacade bound = facade;
        if (bound == null) {
            return;
        }
        try {
            bound.recordJoin(joined, members, Instant.now());
        } catch (RuntimeException e) {
            LOG.warn("Không ghi được người vừa chơi cùng: characterId={}", joined, e);
        }
    }
}
