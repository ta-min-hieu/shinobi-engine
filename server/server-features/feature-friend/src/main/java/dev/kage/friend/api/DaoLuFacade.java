package dev.kage.friend.api;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import dev.kage.friend.application.DaoLuUseCase;
import dev.kage.friend.application.PendingOffers;
import dev.kage.friend.domain.DaoLuOutcome;
import dev.kage.friend.domain.DaoLuPolicy;
import dev.kage.friend.domain.DaoLuPort;

/** API đạo lữ (ADR-094). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class DaoLuFacade {

    /** Lời cầu chờ người kia trả lời trong ngần này. */
    public static final Duration PROPOSAL_TTL = Duration.ofMinutes(5);

    /** Composition không có đạo lữ (test). */
    public static final DaoLuFacade DISABLED = new DaoLuFacade(
            new DaoLuPort() {
                @Override
                public Optional<Long> partnerOf(long characterId) {
                    return Optional.empty();
                }

                @Override
                public DaoLuOutcome bind(long proposerId, long accepterId, int minIntimacy, long cost) {
                    return DaoLuOutcome.INTIMACY_TOO_LOW;
                }

                @Override
                public Optional<Long> dissolve(long characterId) {
                    return Optional.empty();
                }

                @Override
                public int intimacyBetween(long firstId, long secondId) {
                    return 0;
                }
            },
            Clock.systemUTC());

    private final DaoLuUseCase useCase;

    /** Dựng ở composition root. */
    public DaoLuFacade(DaoLuPort port, Clock clock) {
        this.useCase = new DaoLuUseCase(port, new PendingOffers(clock, PROPOSAL_TTL));
    }

    /** Cầu đạo lữ — lời chờ người kia nhận trong {@link #PROPOSAL_TTL}. */
    public DaoLuOutcome propose(long proposerId, long targetId) {
        return useCase.propose(proposerId, targetId);
    }

    /** Nhận lời cầu của {@code proposerId}; người cầu trả phí. */
    public DaoLuOutcome accept(long accepterId, long proposerId) {
        return useCase.accept(accepterId, proposerId);
    }

    /** Giải đạo lữ; trả người kia nếu có. */
    public Optional<Long> dissolve(long characterId) {
        return useCase.dissolve(characterId);
    }

    /** Đạo lữ hiện tại (bộ nhớ đệm). */
    public Optional<Long> partnerOf(long characterId) {
        return useCase.partnerOf(characterId);
    }

    /** Quan hệ của hai người vừa bị xoá ở đường khác (chặn) — xem {@link DaoLuUseCase#forget}. */
    public void forget(long firstId, long secondId) {
        useCase.forget(firstId, secondId);
    }

    /** EXP đánh quái sau thưởng đạo lữ — {@code partnerInParty} do gateway tra (feature-friend không biết nhóm). */
    public long boostedExp(long amount, boolean partnerInParty) {
        return partnerInParty ? DaoLuPolicy.boostedExp(amount) : amount;
    }
}
