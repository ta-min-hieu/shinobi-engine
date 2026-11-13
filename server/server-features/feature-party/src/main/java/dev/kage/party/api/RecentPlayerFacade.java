package dev.kage.party.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import dev.kage.party.domain.RecentPlayerPort;

/**
 * API người vừa chơi cùng (ADR-329): ai vào nhóm thì người đó và mọi thành viên khác ghi tên nhau; mỗi người giữ
 * {@value #KEPT} người gần nhất. Chạm DB — Virtual Thread.
 */
public final class RecentPlayerFacade {

    /** Số người giữ lại mỗi nhân vật. */
    public static final int KEPT = 10;

    /** Không lưu gì (composition test). */
    public static final RecentPlayerFacade DISABLED = new RecentPlayerFacade(new RecentPlayerPort() {
        @Override
        public void record(long characterId, long otherId, Instant at, int keep) {
            // không lưu
        }

        @Override
        public List<Entry> recent(long characterId, int limit) {
            return List.of();
        }
    });

    private final RecentPlayerPort port;

    /** Dựng ở composition root. */
    public RecentPlayerFacade(RecentPlayerPort port) {
        this.port = port;
    }

    /** {@code joined} vừa vào nhóm có {@code members} (có thể gồm chính người đó). */
    public void recordJoin(long joined, Collection<Long> members, Instant at) {
        for (long member : members) {
            if (member != joined) {
                port.record(joined, member, at, KEPT);
                port.record(member, joined, at, KEPT);
            }
        }
    }

    /** Danh sách của mình, mới trước. */
    public List<RecentPlayerPort.Entry> recent(long characterId) {
        return port.recent(characterId, KEPT);
    }
}
