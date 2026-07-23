package dev.kage.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProfanityFilterTest {

    @Test
    void shouldDetectBannedWord_caseInsensitive() {
        assertThat(ProfanityFilter.containsBannedWord("mày NGU vừa thôi")).isTrue();
    }

    @Test
    void shouldAllowCleanMessage() {
        assertThat(ProfanityFilter.containsBannedWord("chào mọi người")).isFalse();
    }
}
