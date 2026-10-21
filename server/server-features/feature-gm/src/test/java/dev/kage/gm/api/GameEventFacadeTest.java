package dev.kage.gm.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.gm.domain.GameEvent;
import dev.kage.gm.domain.GameEventKind;
import dev.kage.gm.domain.GameEventPort;

class GameEventFacadeTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    /** Đồng hồ chỉnh tay được. */
    private static final class MutableClock extends Clock {
        private Instant now = NOW;

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class FakePort implements GameEventPort {
        private final List<GameEvent> rows = new ArrayList<>();

        @Override
        public List<GameEvent> loadAll() {
            return List.copyOf(rows);
        }

        @Override
        public void save(GameEvent event, long gmAuditLogId) {
            rows.removeIf(row -> row.kind() == event.kind());
            rows.add(event);
        }

        @Override
        public void clear(GameEventKind kind) {
            rows.removeIf(row -> row.kind() == kind);
        }
    }

    @Test
    void multiplierIsOneWithoutAnEvent() {
        assertThat(GameEventFacade.DISABLED.multiplier(GameEventKind.EXP)).isEqualTo(1);
    }

    @Test
    void startedEventAppliesUntilItExpires() {
        MutableClock clock = new MutableClock();
        GameEventFacade events = new GameEventFacade(new FakePort(), clock);

        events.start(GameEventKind.EXP, 2, Duration.ofMinutes(60), 7L);

        assertThat(events.multiplier(GameEventKind.EXP)).isEqualTo(2);
        assertThat(events.multiplier(GameEventKind.DROP)).isEqualTo(1);
        clock.now = NOW.plus(Duration.ofMinutes(60));
        assertThat(events.multiplier(GameEventKind.EXP)).isEqualTo(1);
        assertThat(events.active()).isEmpty();
    }

    @Test
    void eventSurvivesARestart_throughThePort() {
        FakePort port = new FakePort();
        MutableClock clock = new MutableClock();
        new GameEventFacade(port, clock).start(GameEventKind.DROP, 3, Duration.ofMinutes(30), 1L);

        GameEventFacade restarted = new GameEventFacade(port, clock);

        assertThat(restarted.multiplier(GameEventKind.DROP)).isEqualTo(3);
    }

    @Test
    void stopReportsWhetherSomethingWasRunning() {
        GameEventFacade events = new GameEventFacade(new FakePort(), new MutableClock());

        assertThat(events.stop(GameEventKind.EXP)).isFalse();
        events.start(GameEventKind.EXP, 2, Duration.ofMinutes(5), 1L);
        assertThat(events.stop(GameEventKind.EXP)).isTrue();
        assertThat(events.multiplier(GameEventKind.EXP)).isEqualTo(1);
    }
}
