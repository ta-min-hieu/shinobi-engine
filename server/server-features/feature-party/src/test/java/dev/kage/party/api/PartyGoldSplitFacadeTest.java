package dev.kage.party.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.party.domain.PartyGoldSplitPort;

/** ADR-278 — chia vàng: kiểm số tiền trước khi chạm port. */
class PartyGoldSplitFacadeTest {

    private final List<Long> eaches = new ArrayList<>();
    private final PartyGoldSplitFacade facade = new PartyGoldSplitFacade((giver, recipients, each, at) -> {
        eaches.add(each);
        return new PartyGoldSplitPort.Split(PartyGoldSplitPort.Outcome.OK, 1L);
    });

    @Test
    void splitsEvenlyKeepingRemainder() {
        PartyGoldSplitFacade.Result result = facade.split(1L, List.of(2L, 3L, 4L), 100L, Instant.EPOCH);
        assertThat(result.outcome()).isEqualTo(PartyGoldSplitFacade.Outcome.OK);
        assertThat(result.each()).isEqualTo(33L);
        assertThat(eaches).containsExactly(33L);
    }

    @Test
    void rejectsBadAmountsWithoutTouchingPort() {
        assertThat(facade.split(1L, List.of(), 100L, Instant.EPOCH).outcome())
                .isEqualTo(PartyGoldSplitFacade.Outcome.NO_RECIPIENT);
        assertThat(facade.split(1L, List.of(2L, 3L), 1L, Instant.EPOCH).outcome())
                .isEqualTo(PartyGoldSplitFacade.Outcome.INVALID_AMOUNT);
        assertThat(facade.split(1L, List.of(2L), 0L, Instant.EPOCH).outcome())
                .isEqualTo(PartyGoldSplitFacade.Outcome.INVALID_AMOUNT);
        assertThat(facade.split(1L, List.of(2L), PartyGoldSplitFacade.MAX_AMOUNT + 1, Instant.EPOCH)
                        .outcome())
                .isEqualTo(PartyGoldSplitFacade.Outcome.INVALID_AMOUNT);
        assertThat(eaches).isEmpty();
    }

    @Test
    void reportsNotEnoughGold() {
        PartyGoldSplitFacade poor = new PartyGoldSplitFacade((giver, recipients, each, at) ->
                new PartyGoldSplitPort.Split(PartyGoldSplitPort.Outcome.NOT_ENOUGH_GOLD, 0L));
        assertThat(poor.split(1L, List.of(2L), 10L, Instant.EPOCH).outcome())
                .isEqualTo(PartyGoldSplitFacade.Outcome.NOT_ENOUGH_GOLD);
    }
}
