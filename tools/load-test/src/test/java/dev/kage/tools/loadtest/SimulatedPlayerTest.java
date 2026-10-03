package dev.kage.tools.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * Vòng đời client và cách report đếm nó. Bản cũ in {@code players.size()} dưới nhãn "client=":
 * client bắt tay hỏng vẫn được tính là đang tạo tải tới hết test.
 */
final class SimulatedPlayerTest {

    @Test
    void shouldEndAndCountHandshakeFailure_whenGatewayUnreachable() {
        // Cổng 0 không bao giờ nhận kết nối ⇒ connect() hỏng ngay, không cần dựng server.
        int closedPort = 0;
        SimulatedPlayer.Metrics metrics = new SimulatedPlayer.Metrics(
                new LatencyRecorder(), new FailureCounters(), new AtomicLong(), new AtomicLong());
        SimulatedPlayer player = new SimulatedPlayer("127.0.0.1", closedPort, "loadtest-unreachable", metrics);

        player.run();

        assertThat(player.phase()).isEqualTo(SimulatedPlayer.Phase.ENDED);
        assertThat(metrics.failures().get(FailureCounters.Kind.HANDSHAKE)).isEqualTo(1);
        assertThat(metrics.failures().total()).isEqualTo(1);

        LoadTestRunner.ClientCounts counts = LoadTestRunner.ClientCounts.of(List.of(player));
        assertThat(counts.started()).isEqualTo(1);
        assertThat(counts.inWorld()).isZero();
        assertThat(counts.ended()).isEqualTo(1);
    }

    @Test
    void shouldComputeBandwidthOverActualElapsedTime_notNominalInterval() {
        // Report cuối: 10s lẻ sau report định kỳ, 10 KiB ⇒ 1 KiB/s (bản cũ chia cho 60s ⇒ 0,17).
        assertThat(LoadTestRunner.kibPerSecond(10 * 1024, 10_000_000_000L)).isEqualTo(1.0);
        assertThat(LoadTestRunner.kibPerSecond(1024, 0)).isZero();
    }
}
