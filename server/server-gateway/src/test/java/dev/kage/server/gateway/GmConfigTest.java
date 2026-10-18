package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;

/** Hai chốt cấu hình lệnh GM (ADR-052 D3): bật/tắt tường minh, CIDR mặc định chỉ loopback. */
class GmConfigTest {

    @Test
    void shouldBeOffByDefault_andRefuseOddValues() {
        assertThat(GmConfig.parseEnabled(null)).isFalse();
        assertThat(GmConfig.parseEnabled(" TRUE ")).isTrue();
        assertThatThrownBy(() -> GmConfig.parseEnabled("yes")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldAllowOnlyLoopbackByDefault() {
        GmConfig config = new GmConfig(true, GmConfig.parseCidrs(""));

        assertThat(config.allows(new InetSocketAddress(InetAddress.ofLiteral("127.0.0.1"), 1)))
                .isTrue();
        assertThat(config.allows(new InetSocketAddress(InetAddress.ofLiteral("::1"), 1)))
                .isTrue();
        assertThat(config.allows(new InetSocketAddress(InetAddress.ofLiteral("192.168.1.10"), 1)))
                .isFalse();
    }

    @Test
    void shouldMatchPrefixesThatAreNotByteAligned() {
        GmConfig config = new GmConfig(true, GmConfig.parseCidrs("10.0.0.0/12, 192.168.5.7/32"));

        assertThat(config.allows(new InetSocketAddress(InetAddress.ofLiteral("10.15.255.1"), 1)))
                .isTrue();
        assertThat(config.allows(new InetSocketAddress(InetAddress.ofLiteral("10.16.0.1"), 1)))
                .isFalse();
        assertThat(config.allows(new InetSocketAddress(InetAddress.ofLiteral("192.168.5.7"), 1)))
                .isTrue();
        assertThat(config.allows(new InetSocketAddress(InetAddress.ofLiteral("192.168.5.8"), 1)))
                .isFalse();
    }

    @Test
    void shouldRefuseHostnamesAndBadPrefixes() {
        assertThatThrownBy(() -> GmConfig.parseCidrs("example.com/32")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> GmConfig.parseCidrs("10.0.0.0/33")).isInstanceOf(IllegalStateException.class);
    }
}
