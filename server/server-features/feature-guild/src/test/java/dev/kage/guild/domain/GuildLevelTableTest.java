package dev.kage.guild.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class GuildLevelTableTest {

    @Test
    void levelShouldFollowTheCumulativeThresholds() {
        assertThat(GuildLevelTable.levelFor(0)).isEqualTo(1);
        assertThat(GuildLevelTable.levelFor(9_999)).isEqualTo(1);
        assertThat(GuildLevelTable.levelFor(10_000)).isEqualTo(2);
        assertThat(GuildLevelTable.levelFor(149_999)).isEqualTo(4);
        assertThat(GuildLevelTable.levelFor(10_000_000)).isEqualTo(GuildLevelTable.MAX_LEVEL);
    }

    @Test
    void levelOneKeepsTheOwnersFiftyMemberCap_andEachLevelAddsTen() {
        assertThat(GuildLevelTable.maxMembers(1)).isEqualTo(50);
        assertThat(GuildLevelTable.maxMembers(5)).isEqualTo(90);
        assertThat(GuildLevelTable.expForNextLevel(1)).isEqualTo(10_000);
        assertThat(GuildLevelTable.expForNextLevel(GuildLevelTable.MAX_LEVEL)).isZero();
    }

    @Test
    void weekShouldStartOnMondayInVietnam() {
        // Chủ nhật 2026-10-25 23:30 UTC = thứ Hai 2026-10-26 06:30 giờ Việt Nam.
        assertThat(GuildWeeklyQuest.weekStart(Instant.parse("2026-10-25T23:30:00Z")))
                .isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(GuildWeeklyQuest.weekStart(Instant.parse("2026-10-21T10:00:00Z")))
                .isEqualTo(LocalDate.of(2026, 10, 19));
    }
}
