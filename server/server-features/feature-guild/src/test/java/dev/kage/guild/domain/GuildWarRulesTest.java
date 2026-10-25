package dev.kage.guild.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** ADR-102 — bang nhiều lần hạ hơn thắng, hoà không ai; giờ đánh kín đầu, hở cuối. */
class GuildWarRulesTest {

    private static final Instant START = Instant.parse("2026-10-25T12:00:00Z");
    private static final Instant END = START.plus(GuildWarRules.DURATION);

    private static GuildWar war(int attackerKills, int defenderKills) {
        return new GuildWar(
                1L,
                new GuildWar.Side(10L, "Thanh_Van", attackerKills),
                new GuildWar.Side(20L, "Huyet_Sat", defenderKills),
                START,
                END);
    }

    @Test
    void shouldPickTheSideWithMoreKills() {
        assertThat(GuildWarRules.winner(war(3, 1))).map(GuildWar.Side::guildId).contains(10L);
        assertThat(GuildWarRules.winner(war(0, 2))).map(GuildWar.Side::guildId).contains(20L);
    }

    @Test
    void shouldHaveNoWinnerOnATie_includingNoKillsAtAll() {
        assertThat(GuildWarRules.winner(war(2, 2))).isEmpty();
        assertThat(GuildWarRules.winner(war(0, 0))).isEmpty();
    }

    @Test
    void shouldBeActiveFromStartUntilJustBeforeEnd() {
        GuildWar war = war(0, 0);
        assertThat(war.isActive(START.minusSeconds(1))).isFalse();
        assertThat(war.isActive(START)).isTrue();
        assertThat(war.isActive(END.minusSeconds(1))).isTrue();
        assertThat(war.isActive(END)).isFalse();
        assertThat(war.involves(20L)).isTrue();
        assertThat(war.involves(30L)).isFalse();
    }
}
