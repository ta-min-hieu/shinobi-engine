package dev.kage.friend.api;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.kage.friend.application.PendingOffers;
import dev.kage.friend.domain.SwornOutcome;
import dev.kage.friend.domain.SwornPolicy;
import dev.kage.friend.domain.SwornPort;

/**
 * API kết nghĩa huynh đệ (ADR-110). Nhóm được hỏi MỖI cú hạ quái (thưởng EXP) và mỗi tin kênh kết nghĩa ⇒ giữ bản nhớ
 * đệm theo nhân vật, chỉ mời/nhận/rời của chính lớp này đổi được nó (server một instance, ADR-010). Chạm DB — gọi trên
 * Virtual Thread.
 */
public final class SwornFacade {

    /** Lời mời chờ người kia nhận trong ngần này. */
    public static final Duration INVITE_TTL = Duration.ofMinutes(5);

    /** Composition không có kết nghĩa (test). */
    public static final SwornFacade DISABLED = new SwornFacade(
            new SwornPort() {
                @Override
                public List<Long> membersOf(long characterId) {
                    return List.of();
                }

                @Override
                public SwornOutcome join(long inviterId, long inviteeId) {
                    return SwornOutcome.INTIMACY_TOO_LOW;
                }

                @Override
                public List<Long> leave(long characterId) {
                    return List.of();
                }

                @Override
                public int intimacyBetween(long firstId, long secondId) {
                    return 0;
                }
            },
            Clock.systemUTC());

    private final SwornPort port;
    private final PendingOffers invites;
    private final Map<Long, List<Long>> groups = new ConcurrentHashMap<>();

    /** Dựng ở composition root. */
    public SwornFacade(SwornPort port, Clock clock) {
        this.port = port;
        this.invites = new PendingOffers(clock, INVITE_TTL);
    }

    /** Mời — kiểm sớm, lời chờ người kia nhận. */
    public SwornOutcome invite(long inviterId, long inviteeId) {
        if (inviterId == inviteeId) {
            return SwornOutcome.CANNOT_TARGET_SELF;
        }
        if (!membersOf(inviteeId).isEmpty()) {
            return SwornOutcome.ALREADY_SWORN;
        }
        if (membersOf(inviterId).size() >= SwornPolicy.MAX_MEMBERS) {
            return SwornOutcome.GROUP_FULL;
        }
        if (port.intimacyBetween(inviterId, inviteeId) < SwornPolicy.MIN_INTIMACY) {
            return SwornOutcome.INTIMACY_TOO_LOW;
        }
        invites.put(inviteeId, inviterId);
        return SwornOutcome.OK;
    }

    /** Nhận lời mời của {@code inviterId}. */
    public SwornOutcome accept(long inviteeId, long inviterId) {
        if (!invites.take(inviteeId, inviterId)) {
            return SwornOutcome.NO_PENDING_INVITE;
        }
        SwornOutcome outcome = port.join(inviterId, inviteeId);
        if (outcome == SwornOutcome.OK) {
            forgetGroupOf(inviterId);
        }
        return outcome;
    }

    /**
     * Rời nhóm.
     *
     * @return những người còn lại (để báo); rỗng nếu chưa kết nghĩa
     */
    public List<Long> leave(long characterId) {
        List<Long> before = membersOf(characterId);
        List<Long> remaining = port.leave(characterId);
        before.forEach(groups::remove);
        groups.remove(characterId);
        return remaining;
    }

    /** Nhóm của một người (gồm người đó) — đọc DB lần đầu, sau đó từ bộ nhớ đệm. */
    public List<Long> membersOf(long characterId) {
        return groups.computeIfAbsent(characterId, port::membersOf);
    }

    /** Có ít nhất một huynh đệ nằm trong {@code partyMembers}. */
    public boolean hasBrotherIn(long characterId, List<Long> partyMembers) {
        for (long member : membersOf(characterId)) {
            if (member != characterId && partyMembers.contains(member)) {
                return true;
            }
        }
        return false;
    }

    /** EXP sau thưởng kết nghĩa. */
    public long boostedExp(long amount, boolean brotherInParty) {
        return brotherInParty ? SwornPolicy.boostedExp(amount) : amount;
    }

    private void forgetGroupOf(long characterId) {
        port.membersOf(characterId).forEach(groups::remove);
        groups.remove(characterId);
    }
}
