package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.guild.domain.GuildWar;
import dev.kage.guild.domain.GuildWarPort;

/** ADR-102 D2 — chỉ cặp người thuộc hai bang đang chiến VỚI NHAU mới được đánh. */
class GuildWarRosterTest {

    private static final long GUILD_A = 10L;
    private static final long GUILD_B = 20L;
    private static final long GUILD_C = 30L;
    private static final long GUILD_D = 40L;
    private static final Instant START = Instant.parse("2026-10-25T12:00:00Z");

    private static GuildWar war(long first, long second) {
        return new GuildWar(
                first * 100 + second,
                new GuildWar.Side(first, "g" + first, 0),
                new GuildWar.Side(second, "g" + second, 0),
                START,
                START.plusSeconds(3600));
    }

    @Test
    void shouldAllowOnlyMembersOfTwoGuildsAtWarWithEachOther() {
        GuildWarRoster roster = new GuildWarRoster();
        roster.replace(
                List.of(
                        new GuildWarPort.Combatant(7L, GUILD_A),
                        new GuildWarPort.Combatant(3L, GUILD_B),
                        new GuildWarPort.Combatant(5L, GUILD_A),
                        new GuildWarPort.Combatant(9L, GUILD_C),
                        new GuildWarPort.Combatant(11L, GUILD_D)),
                List.of(war(GUILD_A, GUILD_B), war(GUILD_C, GUILD_D)));

        assertThat(roster.allows(7L, 3L)).isTrue();
        assertThat(roster.allows(3L, 5L)).isTrue(); // hai chiều
        assertThat(roster.allows(7L, 5L)).isFalse(); // cùng bang
        assertThat(roster.allows(7L, 9L)).isFalse(); // hai bang đều đang chiến nhưng KHÔNG với nhau
        assertThat(roster.allows(9L, 11L)).isTrue();
        assertThat(roster.allows(7L, 99L)).isFalse(); // người ngoài
        assertThat(roster.isAtWar(5L)).isTrue();
        assertThat(roster.isAtWar(99L)).isFalse();
    }

    @Test
    void shouldAllowNobodyOnceTheWarsAreGone() {
        GuildWarRoster roster = new GuildWarRoster();
        assertThat(roster.allows(7L, 3L)).isFalse();
        roster.replace(
                List.of(new GuildWarPort.Combatant(7L, GUILD_A), new GuildWarPort.Combatant(3L, GUILD_B)),
                List.of(war(GUILD_A, GUILD_B)));
        assertThat(roster.allows(7L, 3L)).isTrue();

        roster.replace(List.of(), List.of());
        assertThat(roster.allows(7L, 3L)).isFalse();
        assertThat(roster.isAtWar(7L)).isFalse();
    }
}
