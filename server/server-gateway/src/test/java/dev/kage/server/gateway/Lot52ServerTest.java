package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import dev.kage.guild.api.GuildTagFacade;

/** Lô 52 — ký hiệu bang (ADR-344), cảnh báo trước khi ngắt treo máy (ADR-346). */
class Lot52ServerTest {

    @Test
    void validatesGuildTag() {
        assertThat(GuildTagFacade.valid("KAGE")).isTrue();
        assertThat(GuildTagFacade.valid("ĐẠO")).isTrue();
        assertThat(GuildTagFacade.valid("A")).isFalse();
        assertThat(GuildTagFacade.valid("ABCDE")).isFalse();
        assertThat(GuildTagFacade.valid("A B")).isFalse();
    }

    @Test
    void warnsOnceAMinuteBeforeKick() {
        IdleKick kick = new IdleKick(Duration.ofMinutes(30));
        long start = System.nanoTime();
        assertThat(kick.isWarnDue(start + Duration.ofMinutes(28).toNanos())).isFalse();
        assertThat(kick.isWarnDue(start + Duration.ofMinutes(29).toNanos())).isTrue();
        assertThat(kick.isIdle(start + Duration.ofMinutes(29).toNanos())).isFalse();
    }

    @Test
    void noWarningForVeryShortTimeouts() {
        IdleKick kick = new IdleKick(Duration.ofMinutes(1));
        assertThat(kick.isWarnDue(System.nanoTime() + Duration.ofMinutes(5).toNanos()))
                .isFalse();
    }
}
