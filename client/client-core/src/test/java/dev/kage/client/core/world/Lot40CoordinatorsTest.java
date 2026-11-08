package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Lô 40 — đọc lệnh bình chọn bang (ADR-248). */
class Lot40CoordinatorsTest {

    @Test
    void shouldSplitPollQuestionAndOptions() {
        assertThat(GuildPollCoordinator.parseCreate("Đi bí cảnh? | Có | Không"))
                .contains(new GuildPollCoordinator.Draft("Đi bí cảnh?", List.of("Có", "Không")));
        assertThat(GuildPollCoordinator.parseCreate("Chỉ câu hỏi | một")).isEmpty();
        assertThat(GuildPollCoordinator.parseCreate(" | a | b")).isEmpty();
    }

    @Test
    void shouldReadVoteNumberOneToFour() {
        assertThat(GuildPollCoordinator.parseVote("1")).contains(0);
        assertThat(GuildPollCoordinator.parseVote(" 4 ")).contains(3);
        assertThat(GuildPollCoordinator.parseVote("5")).isEmpty();
        assertThat(GuildPollCoordinator.parseVote("tao")).isEmpty();
    }
}
