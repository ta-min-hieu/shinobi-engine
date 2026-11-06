package dev.kage.guild.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-179 — chuẩn hoá thông báo bang. */
final class GuildNoticeFacadeTest {

    @Test
    void normalizeShouldMakeOneLineAndRefuseOverTheLimit() {
        assertThat(GuildNoticeFacade.normalize("  Tối nay\n\tđi   Bí Cảnh ")).contains("Tối nay đi Bí Cảnh");
        assertThat(GuildNoticeFacade.normalize("")).contains("");
        assertThat(GuildNoticeFacade.normalize("a".repeat(GuildNoticeFacade.MAX_LENGTH)))
                .isPresent();
        assertThat(GuildNoticeFacade.normalize("a".repeat(GuildNoticeFacade.MAX_LENGTH + 1)))
                .isEmpty();
    }
}
