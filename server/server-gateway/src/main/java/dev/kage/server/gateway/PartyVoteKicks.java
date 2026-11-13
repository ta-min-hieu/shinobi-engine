package dev.kage.server.gateway;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Trạng thái biểu quyết đuổi khỏi nhóm (ADR-338) — chỉ trong RAM, mỗi nhóm tối đa một cuộc bầu, hết hạn sau
 * {@value #WINDOW_MILLIS} ms (dọn lười lúc đọc). Mọi thao tác {@code synchronized}: lệnh của nhiều thành viên đến trên
 * nhiều Virtual Thread.
 */
final class PartyVoteKicks {

    /** Một cuộc bầu kéo dài bao lâu. */
    static final long WINDOW_MILLIS = 60_000L;

    /** Kết quả bỏ phiếu. */
    enum Cast {
        COUNTED,
        PASSED,
        NO_VOTE,
        ALREADY_VOTED
    }

    /** Một cuộc bầu đang chạy. {@code voters} = mọi thành viên trừ người bị đuổi lúc khởi xướng. */
    static final class Vote {
        final long targetId;
        final long initiatorId;
        final Set<Long> voters;
        final long startedAtMillis;
        private final Set<Long> voted = new HashSet<>();
        private int yes;

        Vote(long targetId, long initiatorId, Set<Long> voters, long startedAtMillis) {
            this.targetId = targetId;
            this.initiatorId = initiatorId;
            this.voters = Set.copyOf(voters);
            this.startedAtMillis = startedAtMillis;
            voted.add(initiatorId);
            yes = 1;
        }

        int yes() {
            return yes;
        }

        int needed() {
            return PartyVoteKicks.needed(voters.size());
        }

        boolean passed() {
            return yes >= needed();
        }

        int secondsLeft(long nowMillis) {
            return (int) Math.max(0L, (startedAtMillis + WINDOW_MILLIS - nowMillis) / 1_000L);
        }
    }

    private final Map<Long, Vote> byParty = new HashMap<>();

    /** Số phiếu đồng ý cần: quá nửa số người được bầu. Tĩnh để test. */
    static int needed(int voters) {
        return voters / 2 + 1;
    }

    /** Cuộc bầu đang chạy của nhóm (đã hết hạn ⇒ dọn và trả rỗng). */
    synchronized Optional<Vote> active(long partyId, long nowMillis) {
        Vote vote = byParty.get(partyId);
        if (vote != null && nowMillis - vote.startedAtMillis >= WINDOW_MILLIS) {
            byParty.remove(partyId);
            return Optional.empty();
        }
        return Optional.ofNullable(vote);
    }

    /** Mở cuộc bầu; {@code false} nếu nhóm đang có cuộc bầu khác. Thông qua ngay ⇒ không giữ lại. */
    synchronized boolean start(long partyId, Vote vote, long nowMillis) {
        if (active(partyId, nowMillis).isPresent()) {
            return false;
        }
        if (!vote.passed()) {
            byParty.put(partyId, vote);
        }
        return true;
    }

    /** Bỏ phiếu; thông qua ⇒ cuộc bầu bị gỡ. */
    synchronized Cast cast(long partyId, long voterId, boolean agree, long nowMillis) {
        Optional<Vote> found = active(partyId, nowMillis);
        if (found.isEmpty() || !found.get().voters.contains(voterId)) {
            return Cast.NO_VOTE;
        }
        Vote vote = found.get();
        if (!vote.voted.add(voterId)) {
            return Cast.ALREADY_VOTED;
        }
        if (agree) {
            vote.yes++;
        }
        if (vote.passed()) {
            byParty.remove(partyId);
            return Cast.PASSED;
        }
        return Cast.COUNTED;
    }
}
