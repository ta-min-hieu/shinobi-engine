package dev.kage.friend.application;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.kage.friend.domain.DaoLuOutcome;
import dev.kage.friend.domain.DaoLuPolicy;
import dev.kage.friend.domain.DaoLuPort;

/**
 * Đạo lữ (ADR-094). Đạo lữ được hỏi MỖI cú hạ quái (thưởng EXP cùng nhóm), nên giữ một bản nhớ đệm theo nhân vật —
 * chỉ đường kết/giải của chính use case này đổi được nó (server một instance, ADR-010).
 */
public final class DaoLuUseCase {

    private final DaoLuPort port;
    private final PendingOffers proposals;
    private final Map<Long, Optional<Long>> partners = new ConcurrentHashMap<>();

    public DaoLuUseCase(DaoLuPort port, PendingOffers proposals) {
        this.port = port;
        this.proposals = proposals;
    }

    /** Cầu đạo lữ — kiểm sớm thân mật và đạo lữ hiện có, lời chờ người kia nhận. */
    public DaoLuOutcome propose(long proposerId, long targetId) {
        if (proposerId == targetId) {
            return DaoLuOutcome.CANNOT_TARGET_SELF;
        }
        if (partnerOf(proposerId).isPresent() || partnerOf(targetId).isPresent()) {
            return DaoLuOutcome.ALREADY_BOUND;
        }
        if (port.intimacyBetween(proposerId, targetId) < DaoLuPolicy.MIN_INTIMACY) {
            return DaoLuOutcome.INTIMACY_TOO_LOW;
        }
        proposals.put(targetId, proposerId);
        return DaoLuOutcome.OK;
    }

    /** Nhận lời — người cầu trả phí trong cùng transaction ghi quan hệ. */
    public DaoLuOutcome accept(long accepterId, long proposerId) {
        if (!proposals.take(accepterId, proposerId)) {
            return DaoLuOutcome.NO_PENDING_PROPOSAL;
        }
        DaoLuOutcome outcome = port.bind(proposerId, accepterId, DaoLuPolicy.MIN_INTIMACY, DaoLuPolicy.BIND_COST);
        if (outcome == DaoLuOutcome.OK) {
            partners.put(proposerId, Optional.of(accepterId));
            partners.put(accepterId, Optional.of(proposerId));
        }
        return outcome;
    }

    /** Giải đạo lữ; trả người kia (để báo) nếu có. */
    public Optional<Long> dissolve(long characterId) {
        Optional<Long> partner = port.dissolve(characterId);
        partners.put(characterId, Optional.empty());
        partner.ifPresent(id -> partners.put(id, Optional.empty()));
        return partner;
    }

    /** Đạo lữ hiện tại — đọc DB lần đầu, sau đó từ bộ nhớ đệm. */
    public Optional<Long> partnerOf(long characterId) {
        return partners.computeIfAbsent(characterId, port::partnerOf);
    }

    /** Quan hệ vừa bị xoá từ đường khác (chặn — ADR-091) — quên bộ nhớ đệm của hai người. */
    public void forget(long firstId, long secondId) {
        partners.remove(firstId);
        partners.remove(secondId);
    }
}
