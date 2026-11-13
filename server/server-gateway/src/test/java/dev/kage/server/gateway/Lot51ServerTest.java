package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** Lô 51 — kiểm chữ (ADR-335/336), biểu quyết đuổi khỏi nhóm (ADR-338). */
class Lot51ServerTest {

    @Test
    void validatesText() {
        assertThat(Lot51Commands.validText("Bế quan tới tối", 60)).isTrue();
        assertThat(Lot51Commands.validText("a".repeat(61), 60)).isFalse();
        assertThat(Lot51Commands.validText("x\u0007y", 60)).isFalse();
    }

    @Test
    void neededIsMoreThanHalf() {
        assertThat(PartyVoteKicks.needed(1)).isEqualTo(1);
        assertThat(PartyVoteKicks.needed(2)).isEqualTo(2);
        assertThat(PartyVoteKicks.needed(3)).isEqualTo(2);
        assertThat(PartyVoteKicks.needed(4)).isEqualTo(3);
    }

    @Test
    void votePassesWithMajorityOnce() {
        PartyVoteKicks kicks = new PartyVoteKicks();
        PartyVoteKicks.Vote vote = new PartyVoteKicks.Vote(9L, 1L, Set.of(1L, 2L, 3L, 4L), 0L);
        assertThat(kicks.start(7L, vote, 0L)).isTrue();
        assertThat(kicks.start(7L, new PartyVoteKicks.Vote(9L, 2L, Set.of(1L, 2L), 0L), 0L))
                .isFalse();
        assertThat(kicks.cast(7L, 1L, true, 1_000L)).isEqualTo(PartyVoteKicks.Cast.ALREADY_VOTED);
        assertThat(kicks.cast(7L, 9L, false, 1_000L)).isEqualTo(PartyVoteKicks.Cast.NO_VOTE);
        assertThat(kicks.cast(7L, 2L, false, 1_000L)).isEqualTo(PartyVoteKicks.Cast.COUNTED);
        assertThat(kicks.cast(7L, 3L, true, 1_000L)).isEqualTo(PartyVoteKicks.Cast.COUNTED);
        assertThat(kicks.cast(7L, 4L, true, 1_000L)).isEqualTo(PartyVoteKicks.Cast.PASSED);
        assertThat(kicks.active(7L, 1_000L)).isEmpty();
    }

    @Test
    void voteExpires() {
        PartyVoteKicks kicks = new PartyVoteKicks();
        kicks.start(7L, new PartyVoteKicks.Vote(9L, 1L, Set.of(1L, 2L, 3L), 0L), 0L);
        assertThat(kicks.cast(7L, 2L, true, PartyVoteKicks.WINDOW_MILLIS)).isEqualTo(PartyVoteKicks.Cast.NO_VOTE);
    }

    @Test
    void deathLogRemembersMonsterCodes() {
        DeathLogHooks hooks = new DeathLogHooks();
        hooks.rememberMonster(-5L, "wild_wolf");
        assertThat(hooks.monsterCode(-5L)).isEqualTo("wild_wolf");
        assertThat(hooks.monsterCode(-6L)).isEmpty();
        hooks.onDefeated(1L, -5L, 3f); // chưa nối ⇒ không làm gì, không ném
    }
}
