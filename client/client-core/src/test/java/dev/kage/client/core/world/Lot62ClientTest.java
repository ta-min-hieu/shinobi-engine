package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** Lô 62 phía client — từ khoá theo dõi (ADR-429), lệnh xoá câu chào (ADR-425). */
class Lot62ClientTest {

    @Test
    void matchesWatchWordsIgnoringCase() {
        assertThat(ChatLensCoordinator.contains(Set.of("boss"), "BOSS thế giới xuất hiện"))
                .isTrue();
        assertThat(ChatLensCoordinator.contains(Set.of("boss"), "mua đan")).isFalse();
        assertThat(ChatLensCoordinator.contains(Set.of(), "boss")).isFalse();
    }

    @Test
    void recognisesClearGreeting() {
        assertThat(SocialExtrasCoordinator.isClear("xoa")).isTrue();
        assertThat(SocialExtrasCoordinator.isClear("CLEAR")).isTrue();
        assertThat(SocialExtrasCoordinator.isClear("xin chào")).isFalse();
    }
}
