package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Lô 46 — kiểm chữ thư hẹn giờ (ADR-297) và khoá chống trùng của job gửi. */
class Lot46ServerTest {

    @Test
    void validatesScheduledMailText() {
        assertThat(Lot46Commands.validText("Chào", "nội dung")).isTrue();
        assertThat(Lot46Commands.validText("", "x")).isFalse();
        assertThat(Lot46Commands.validText("a".repeat(65), "x")).isFalse();
        assertThat(Lot46Commands.validText("a", "b".repeat(1025))).isFalse();
        assertThat(Lot46Commands.validText("a\u0000", "x")).isFalse();
    }

    @Test
    void scheduledMailKeyIsStablePerRow() {
        assertThat(ScheduledMailJob.key(7)).isEqualTo(ScheduledMailJob.key(7));
        assertThat(ScheduledMailJob.key(7)).isNotEqualTo(ScheduledMailJob.key(8));
    }
}
