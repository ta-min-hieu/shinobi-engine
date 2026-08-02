package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.Opcode;

/**
 * Verify các metric OBSERVABILITY.md §3.2 ghi đúng registry — tập trung vào {@code kage.packet.size}
 * (Distribution, tag {@code direction=received|sent}), phần mới nhất chưa có test trực tiếp.
 */
class GatewayNetworkMetricsTest {

    @Test
    void shouldRecordRealByteSize_whenPacketReceived() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayNetworkMetrics metrics = new GatewayNetworkMetrics(registry);

        metrics.recordPacketReceived(Opcode.OPCODE_MOVE_COMMAND_VALUE, 42);
        metrics.recordPacketReceived(Opcode.OPCODE_MOVE_COMMAND_VALUE, 58);

        DistributionSummary summary =
                registry.get("kage.packet.size").tag("direction", "received").summary();
        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.totalAmount()).isEqualTo(100.0);
        assertThat(summary.max()).isEqualTo(58.0);

        Counter counter = registry.get("kage.packet.received")
                .tag("opcode", "OPCODE_MOVE_COMMAND")
                .counter();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    void shouldRecordRealByteSize_whenPacketSent() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayNetworkMetrics metrics = new GatewayNetworkMetrics(registry);

        metrics.recordPacketSent(Opcode.OPCODE_ERROR_VALUE, 17);

        DistributionSummary summary =
                registry.get("kage.packet.size").tag("direction", "sent").summary();
        assertThat(summary.count()).isEqualTo(1);
        assertThat(summary.totalAmount()).isEqualTo(17.0);

        Counter counter =
                registry.get("kage.packet.sent").tag("opcode", "OPCODE_ERROR").counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldKeepReceivedAndSentDistributionsSeparate() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayNetworkMetrics metrics = new GatewayNetworkMetrics(registry);

        metrics.recordPacketReceived(Opcode.OPCODE_MOVE_COMMAND_VALUE, 10);
        metrics.recordPacketSent(Opcode.OPCODE_ERROR_VALUE, 999);

        DistributionSummary received =
                registry.get("kage.packet.size").tag("direction", "received").summary();
        DistributionSummary sent =
                registry.get("kage.packet.size").tag("direction", "sent").summary();
        assertThat(received.totalAmount()).isEqualTo(10.0);
        assertThat(sent.totalAmount()).isEqualTo(999.0);
    }
}
