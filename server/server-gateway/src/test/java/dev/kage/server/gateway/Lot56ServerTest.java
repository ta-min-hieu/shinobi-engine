package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/** Lô 56 — thư nhóm (ADR-375), lời nhắn kết bạn (ADR-376), đỉnh online trong ngày (ADR-377). */
class Lot56ServerTest {

    @Test
    void validatesPartyMail() {
        assertThat(Lot56Commands.validPartyMail("Hẹn đi Bí Cảnh", "20h tối nay"))
                .isTrue();
        assertThat(Lot56Commands.validPartyMail("", "x")).isFalse();
        assertThat(Lot56Commands.validPartyMail("x".repeat(65), "")).isFalse();
        assertThat(Lot56Commands.validPartyMail("ok", "x".repeat(1025))).isFalse();
    }

    @Test
    void cleansRequestNote() {
        assertThat(Lot56Commands.cleanRequestNote("  chào đạo hữu \n kết bạn nhé "))
                .isEqualTo("chào đạo hữu   kết bạn nhé");
        assertThat(Lot56Commands.cleanRequestNote("y".repeat(80))).hasSize(60);
        assertThat(Lot56Commands.cleanRequestNote("")).isEmpty();
        assertThat(Lot56Commands.cleanRequestNote("a\u0000b")).doesNotContain("\u0000");
    }

    @Test
    void peakTracksDayAndResetsAtVietnamMidnight() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2031-01-01T10:00:00Z"));
        Clock clock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        PeakOnlineTracker tracker = new PeakOnlineTracker(clock);
        tracker.observe(3);
        tracker.observe(7);
        tracker.observe(2);
        assertThat(tracker.today(1).peak()).isEqualTo(7);
        // 17:00 UTC = 00:00 giờ Việt Nam hôm sau.
        now.set(Instant.parse("2031-01-01T17:00:01Z"));
        assertThat(tracker.today(2).peak()).as("sang ngày mới").isEqualTo(2);
    }
}
