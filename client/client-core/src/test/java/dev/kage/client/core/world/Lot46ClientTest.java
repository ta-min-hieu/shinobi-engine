package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Lô 46 phía client — chuyển tiếp thư, thư hẹn giờ, khoảng cách, chụp màn hình, bấm giờ, máy tính. */
class Lot46ClientTest {

    @Test
    void calculatorEvaluatesArithmetic() {
        assertThat(ArithmeticExpression.evaluate("1200*3+50")).contains(new BigDecimal("3650"));
        assertThat(ArithmeticExpression.evaluate("(1+2)*-3")).contains(new BigDecimal("-9"));
        assertThat(ArithmeticExpression.evaluate("10 / 4")).contains(new BigDecimal("2.5"));
        assertThat(ArithmeticExpression.evaluate("2x3")).contains(new BigDecimal("6"));
        assertThat(ArithmeticExpression.evaluate("1/0")).isEmpty();
        assertThat(ArithmeticExpression.evaluate("1+")).isEmpty();
        assertThat(ArithmeticExpression.evaluate("(1")).isEmpty();
        assertThat(ArithmeticExpression.evaluate("1..2")).isEmpty();
        assertThat(ArithmeticExpression.evaluate("abc")).isEmpty();
        assertThat(ArithmeticExpression.evaluate("(".repeat(40) + "1" + ")".repeat(40)))
                .isEmpty();
    }

    @Test
    void stopwatchFormatsElapsedTime() {
        assertThat(StopwatchCoordinator.format(0)).isEqualTo("0:00.0");
        assertThat(StopwatchCoordinator.format(65_400)).isEqualTo("1:05.4");
        assertThat(StopwatchCoordinator.format(3_725_000)).isEqualTo("1:02:05.0");
    }

    @Test
    void parsesScheduledMail() {
        assertThat(ScheduledMailCoordinator.parse("30 BaoDo01 Hẹn gặp | ở tông môn"))
                .contains(new ScheduledMailCoordinator.Parsed(30, "BaoDo01", "Hẹn gặp", "ở tông môn"));
        assertThat(ScheduledMailCoordinator.parse("5 BaoDo01 Chỉ tiêu đề"))
                .contains(new ScheduledMailCoordinator.Parsed(5, "BaoDo01", "Chỉ tiêu đề", ""));
        assertThat(ScheduledMailCoordinator.parse("abc BaoDo01 x")).isEmpty();
        assertThat(ScheduledMailCoordinator.parse("5 BaoDo01")).isEmpty();
        assertThat(ScheduledMailCoordinator.parse("5 BaoDo01 | thân")).isEmpty();
    }

    @Test
    void forwardDraftPrefixesAndTrims() {
        MailController.Mail mail =
                new MailController.Mail(1L, true, 2L, "x".repeat(64), "y".repeat(2000), 50L, List.of(), false);
        MailController.Draft draft = MailForwardCoordinator.draft("BaoDo01", mail);
        assertThat(draft.subject()).startsWith("Fwd: ").hasSize(MailController.MAX_SUBJECT_LENGTH);
        assertThat(draft.body()).hasSize(MailController.MAX_BODY_LENGTH);
        assertThat(draft.goldAmount()).isZero();
        MailController.Mail again = new MailController.Mail(1L, true, 2L, "Fwd: a", "b", 0L, List.of(), false);
        assertThat(MailForwardCoordinator.draft("X", again).subject()).isEqualTo("Fwd: a");
    }

    @Test
    void distanceRoundsToMeters() {
        assertThat(TargetHudSync.meters(3f, 4f)).isEqualTo(5);
        assertThat(TargetHudSync.meters(0.4f, 0f)).isZero();
    }

    @Test
    void screenshotNameCarriesTimestamp() {
        assertThat(ScreenshotCoordinator.fileName(LocalDateTime.of(2026, 11, 12, 9, 5, 7)))
                .isEqualTo("KageScreenshots/kage-20261112-090507.png");
    }
}
