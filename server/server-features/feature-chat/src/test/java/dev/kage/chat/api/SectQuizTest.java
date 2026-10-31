package dev.kage.chat.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.kage.chat.domain.QuizQuestion;
import dev.kage.chat.domain.QuizRules;

/** ADR-122 — phiên hỏi đáp: mở câu đúng giờ, người đúng ĐẦU TIÊN thắng, hết giờ thì lộ đáp án. */
class SectQuizTest {

    /** 20:00 giờ VN ngày 31/10 = 13:00 UTC. */
    private static final Instant START = Instant.parse("2026-10-31T13:00:00Z");

    private static final QuizQuestion SECT =
            new QuizQuestion("sect", "quiz.sect.q", "quiz.sect.a", Set.of("thanh hu tong", "thanh hu"));

    private static SectQuiz quiz() {
        return new SectQuiz(() -> List.of(SECT), List.of(LocalTime.of(20, 0)), 1L);
    }

    @Test
    void normalizeShouldStripVietnameseMarksCaseAndPunctuation() {
        assertThat(QuizRules.normalize("  Thanh Hư Tông! ")).isEqualTo("thanh hu tong");
        assertThat(QuizRules.normalize("Đại   Kiếp")).isEqualTo("dai kiep");
        assertThat(SECT.matches("THANH HƯ")).isTrue();
        assertThat(SECT.matches("thanh")).isFalse();
    }

    @Test
    void shouldStayQuietOutsideASession() {
        SectQuiz quiz = quiz();

        assertThat(quiz.tick(START.minusSeconds(1))).isEmpty();
        assertThat(quiz.onWorldChat(1L, "A", "thanh hu tong")).isEmpty();
    }

    @Test
    void firstCorrectAnswerShouldWinOnceAndLaterAnswersShouldNot() {
        SectQuiz quiz = quiz();

        List<SectQuiz.Signal> opened = quiz.tick(START.plusSeconds(1));
        assertThat(opened).singleElement().isInstanceOf(SectQuiz.Signal.Ask.class);
        SectQuiz.Signal.Ask ask = (SectQuiz.Signal.Ask) opened.getFirst();
        assertThat(ask.index()).isZero();
        assertThat(ask.endsAt()).isEqualTo(START.plus(QuizRules.WINDOW));
        assertThat(quiz.tick(START.plusSeconds(3)))
                .as("đã mở rồi thì không mở lại")
                .isEmpty();

        assertThat(quiz.onWorldChat(1L, "A", "sai")).isEmpty();
        Optional<SectQuiz.Signal.Reveal> won = quiz.onWorldChat(2L, "B", "Thanh Hư Tông");
        assertThat(won).isPresent();
        SectQuiz.Winner winner = won.get().winner().orElseThrow();
        assertThat(winner.characterId()).isEqualTo(2L);
        assertThat(winner.rewardKey()).isEqualTo("quiz:2026-10-31T20:00:0");
        assertThat(quiz.onWorldChat(3L, "C", "thanh hu tong"))
                .as("chỉ người đầu tiên")
                .isEmpty();
        assertThat(quiz.tick(START.plusSeconds(45)))
                .as("đã chốt — hết giờ không lộ lần hai")
                .isEmpty();
    }

    @Test
    void unansweredQuestionShouldRevealWhenTimeRunsOut() {
        SectQuiz quiz = quiz();
        quiz.tick(START.plusSeconds(1));

        List<SectQuiz.Signal> closed = quiz.tick(START.plus(QuizRules.WINDOW).plusSeconds(1));

        assertThat(closed).singleElement().isInstanceOfSatisfying(SectQuiz.Signal.Reveal.class, reveal -> {
            assertThat(reveal.answerKey()).isEqualTo("quiz.sect.a");
            assertThat(reveal.winner()).isEmpty();
        });
        assertThat(quiz.onWorldChat(1L, "A", "thanh hu")).isEmpty();
        // Ngân hàng chỉ một câu ⇒ câu thứ hai của phiên không có gì để hỏi.
        assertThat(quiz.tick(START.plus(QuizRules.PERIOD).plusSeconds(1))).isEmpty();
    }

    @Test
    void startsShouldParseOrFallBack() {
        List<LocalTime> fallback = List.of(LocalTime.of(20, 0));
        assertThat(QuizRules.parseStarts("12:30, 21:05", fallback))
                .containsExactly(LocalTime.of(12, 30), LocalTime.of(21, 5));
        assertThat(QuizRules.parseStarts("", fallback)).isEqualTo(fallback);
        assertThat(QuizRules.parseStarts("25:99", fallback)).isEqualTo(fallback);
    }
}
