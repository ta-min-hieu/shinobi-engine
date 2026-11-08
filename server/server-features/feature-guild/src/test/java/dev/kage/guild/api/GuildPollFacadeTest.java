package dev.kage.guild.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** ADR-248 — kiểm câu hỏi / lựa chọn trước khi xuống DB. */
class GuildPollFacadeTest {

    @Test
    void acceptsTwoToFourShortOptions() {
        assertThat(GuildPollFacade.isValid("Đi bí cảnh tối nay?", List.of("Có", "Không")))
                .isTrue();
        assertThat(GuildPollFacade.isValid("Q", List.of("a", "b", "c", "d"))).isTrue();
        assertThat(GuildPollFacade.isValid("Q", List.of("a"))).isFalse();
        assertThat(GuildPollFacade.isValid("Q", List.of("a", "b", "c", "d", "e")))
                .isFalse();
        assertThat(GuildPollFacade.isValid(" ", List.of("a", "b"))).isFalse();
        assertThat(GuildPollFacade.isValid("Q", List.of("a", " "))).isFalse();
        assertThat(GuildPollFacade.isValid("x".repeat(81), List.of("a", "b"))).isFalse();
        assertThat(GuildPollFacade.isValid("Q", List.of("a", "y".repeat(33)))).isFalse();
    }
}
