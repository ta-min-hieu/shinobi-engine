package dev.kage.friend.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.friend.domain.DaoLuOutcome;
import dev.kage.friend.domain.DaoLuPolicy;
import dev.kage.friend.domain.DaoLuPort;

/** ADR-094 — cầu, nhận, giải; thân mật tối thiểu; bộ nhớ đệm theo kịp kết/giải. */
class DaoLuUseCaseTest {

    private static final long A = 1L;
    private static final long B = 2L;
    private static final long C = 3L;

    private static final class FakePort implements DaoLuPort {
        final Map<Long, Long> partners = new HashMap<>();
        int intimacy = DaoLuPolicy.MIN_INTIMACY;
        int partnerReads;

        @Override
        public Optional<Long> partnerOf(long characterId) {
            partnerReads++;
            return Optional.ofNullable(partners.get(characterId));
        }

        @Override
        public DaoLuOutcome bind(long proposerId, long accepterId, int minIntimacy, long cost) {
            if (partners.containsKey(proposerId) || partners.containsKey(accepterId)) {
                return DaoLuOutcome.ALREADY_BOUND;
            }
            partners.put(proposerId, accepterId);
            partners.put(accepterId, proposerId);
            return DaoLuOutcome.OK;
        }

        @Override
        public Optional<Long> dissolve(long characterId) {
            Long partner = partners.remove(characterId);
            if (partner != null) {
                partners.remove(partner);
            }
            return Optional.ofNullable(partner);
        }

        @Override
        public int intimacyBetween(long firstId, long secondId) {
            return intimacy;
        }
    }

    private final FakePort port = new FakePort();
    private final DaoLuUseCase useCase = new DaoLuUseCase(
            port,
            new PendingOffers(
                    Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC), Duration.ofMinutes(5)));

    @Test
    void shouldBindAfterAcceptAndServePartnerFromCache() {
        assertThat(useCase.propose(A, B)).isEqualTo(DaoLuOutcome.OK);
        assertThat(useCase.accept(B, A)).isEqualTo(DaoLuOutcome.OK);
        int readsAfterBind = port.partnerReads;
        assertThat(useCase.partnerOf(A)).contains(B);
        assertThat(useCase.partnerOf(B)).contains(A);
        assertThat(port.partnerReads).isEqualTo(readsAfterBind);
    }

    @Test
    void shouldRefuseLowIntimacySelfAndMissingProposal() {
        port.intimacy = DaoLuPolicy.MIN_INTIMACY - 1;
        assertThat(useCase.propose(A, B)).isEqualTo(DaoLuOutcome.INTIMACY_TOO_LOW);
        assertThat(useCase.propose(A, A)).isEqualTo(DaoLuOutcome.CANNOT_TARGET_SELF);
        assertThat(useCase.accept(B, A)).isEqualTo(DaoLuOutcome.NO_PENDING_PROPOSAL);
    }

    @Test
    void shouldRefuseProposingWhileBoundAndFreeBothOnDissolve() {
        useCase.propose(A, B);
        useCase.accept(B, A);
        assertThat(useCase.propose(C, A)).isEqualTo(DaoLuOutcome.ALREADY_BOUND);

        assertThat(useCase.dissolve(B)).contains(A);
        assertThat(useCase.partnerOf(A)).isEmpty();
        assertThat(useCase.propose(C, A)).isEqualTo(DaoLuOutcome.OK);
    }

    @Test
    void shouldAddTenPercentExp() {
        assertThat(DaoLuPolicy.boostedExp(100L)).isEqualTo(110L);
        assertThat(DaoLuPolicy.boostedExp(5L)).isEqualTo(5L);
    }
}
