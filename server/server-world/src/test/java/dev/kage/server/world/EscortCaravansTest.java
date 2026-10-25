package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** ADR-114 — đoàn hộ tống: đi khi người hộ tống đủ gần, chờ khi xa, chịu đòn quái đang giao chiến, tới nơi/gục/bỏ. */
class EscortCaravansTest {

    private static final long GUARD = 7L;
    private static final float DT = 0.1f;
    private static final EscortRoute ROUTE = new EscortRoute("daily_caravan_escort", 0f, 10f, 30, 2f);

    @Test
    void shouldMoveOnlyWhileTheGuardIsNearAndArriveOnce() {
        EscortCaravans caravans = new EscortCaravans();
        RecordingSink sink = new RecordingSink();
        caravans.start(GUARD, ROUTE, sink);

        float[] guardX = {20f};
        tick(new Scene(caravans, sink), guardX, 0, 20);
        assertThat(sink.last().x()).isZero();
        assertThat(sink.last().state()).isEqualTo(WorldOutboundSink.EscortState.WAITING);

        guardX[0] = 0f;
        tick(
                new Scene(caravans, sink),
                guardX,
                0,
                21); // ~2 giây × 2 m/s ≈ 4 m; báo vị trí mỗi 1 giây nên đọc ở nhịp kế
        assertThat(sink.last().state()).isEqualTo(WorldOutboundSink.EscortState.MOVING);
        assertThat(sink.last().x()).isBetween(3.5f, 4.5f);

        guardX[0] = 6f;
        tick(new Scene(caravans, sink), guardX, 0, 60);
        assertThat(sink.last().state()).isEqualTo(WorldOutboundSink.EscortState.ARRIVED);
        assertThat(sink.last().x()).isEqualTo(10f);
        assertThat(caravans.size()).isZero();
        long arrivals = sink.escorts.stream()
                .filter(e -> e.state() == WorldOutboundSink.EscortState.ARRIVED)
                .count();
        assertThat(arrivals).isEqualTo(1L);
    }

    @Test
    void shouldFailWhenEngagedMonstersWearItDown() {
        EscortCaravans caravans = new EscortCaravans();
        RecordingSink sink = new RecordingSink();
        caravans.start(GUARD, ROUTE, sink);

        tick(new Scene(caravans, sink), new float[] {30f}, 12, 40); // 12 sát thương/giây ⇒ gục ở giây thứ 3

        assertThat(sink.last().state()).isEqualTo(WorldOutboundSink.EscortState.FAILED);
        assertThat(sink.last().hp()).isZero();
        assertThat(caravans.size()).isZero();
    }

    @Test
    void shouldAbandonWhenTheGuardLeavesOrCancels() {
        EscortCaravans caravans = new EscortCaravans();
        RecordingSink sink = new RecordingSink();
        caravans.start(GUARD, ROUTE, sink);
        tick(new Scene(caravans, sink), new float[] {Float.NaN}, 0, 1);
        assertThat(sink.last().state()).isEqualTo(WorldOutboundSink.EscortState.ABANDONED);

        caravans.start(GUARD, ROUTE, sink);
        caravans.cancel(GUARD, sink);
        assertThat(sink.last().state()).isEqualTo(WorldOutboundSink.EscortState.ABANDONED);
        assertThat(caravans.size()).isZero();
    }

    /** Một cảnh: đoàn + người nghe. */
    private record Scene(EscortCaravans caravans, RecordingSink sink) {}

    private static void tick(Scene scene, float[] guardX, int threat, int ticks) {
        for (int i = 0; i < ticks; i++) {
            scene.caravans().tick(DT, id -> guardX[0], (x, reach) -> threat, scene.sink());
        }
    }

    private static final class RecordingSink implements EscortListener {
        final List<WorldOutboundSink.EscortSnapshot> escorts = new ArrayList<>();

        @Override
        public void onEscortChanged(WorldOutboundSink.EscortSnapshot snapshot) {
            escorts.add(snapshot);
        }

        WorldOutboundSink.EscortSnapshot last() {
            return escorts.getLast();
        }
    }
}
