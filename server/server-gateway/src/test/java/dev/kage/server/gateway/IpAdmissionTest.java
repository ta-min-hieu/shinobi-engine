package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

/** Dòng 71(a) (2026-10-17) — trần kết nối/đăng nhập theo IP, xem {@link IpAdmission}. */
class IpAdmissionTest {

    private static final InetSocketAddress PLAYER = address("203.0.113.7", 50_001);
    private static final InetSocketAddress SAME_IP_OTHER_PORT = address("203.0.113.7", 50_002);
    private static final InetSocketAddress OTHER_PLAYER = address("198.51.100.9", 50_001);
    private static final InetSocketAddress LOOPBACK = address("127.0.0.1", 50_001);

    @Test
    void shouldRefuseTheConnectionAboveTheCap_andAdmitAgainOnceOneCloses() {
        IpAdmission admission = new IpAdmission(2, 0);

        assertThat(admission.tryOpen(PLAYER)).isTrue();
        assertThat(admission.tryOpen(SAME_IP_OTHER_PORT))
                .as("trần tính theo IP, không theo cổng")
                .isTrue();
        assertThat(admission.tryOpen(PLAYER)).isFalse();
        assertThat(admission.tryOpen(OTHER_PLAYER)).as("IP khác có trần riêng").isTrue();

        admission.close(PLAYER);

        assertThat(admission.tryOpen(PLAYER)).isTrue();
    }

    @Test
    void shouldForgetAnAddressOnceItsLastConnectionCloses() {
        IpAdmission admission = new IpAdmission(4, 0);
        admission.tryOpen(PLAYER);
        admission.tryOpen(PLAYER);

        admission.close(PLAYER);
        admission.close(PLAYER);
        admission.close(PLAYER); // đóng thừa không được đẩy bộ đếm xuống âm

        assertThat(admission.openConnectionCount(PLAYER.getAddress())).isZero();
        assertThat(admission.tryOpen(PLAYER)).isTrue();
        assertThat(admission.openConnectionCount(PLAYER.getAddress())).isEqualTo(1);
    }

    @Test
    void shouldRefuseLoginsAboveThePerMinuteCap_andAllowThemInTheNextWindow() {
        AtomicLong now = new AtomicLong();
        IpAdmission admission = new IpAdmission(0, 3, now::get);

        assertThat(admission.tryLogin(PLAYER)).isTrue();
        assertThat(admission.tryLogin(PLAYER)).isTrue();
        assertThat(admission.tryLogin(SAME_IP_OTHER_PORT)).isTrue();
        assertThat(admission.tryLogin(PLAYER)).isFalse();
        assertThat(admission.tryLogin(OTHER_PLAYER)).isTrue();

        now.addAndGet(Duration.ofMinutes(1).toNanos());

        assertThat(admission.tryLogin(PLAYER)).isTrue();
    }

    @Test
    void shouldForgetIdleLoginBooks() {
        AtomicLong now = new AtomicLong();
        IpAdmission admission = new IpAdmission(0, 3, now::get);
        admission.tryLogin(PLAYER);

        now.addAndGet(Duration.ofMinutes(5).toNanos());
        admission.tryLogin(OTHER_PLAYER);

        assertThat(admission.trackedLoginAddressCount())
                .as("chỉ còn sổ của IP vừa đăng nhập")
                .isEqualTo(1);
    }

    @Test
    void shouldNeverLimitLoopbackOrAddresslessChannels() {
        IpAdmission admission = new IpAdmission(1, 1);

        for (int i = 0; i < 50; i++) {
            assertThat(admission.tryOpen(LOOPBACK)).isTrue();
            assertThat(admission.tryLogin(LOOPBACK)).isTrue();
            assertThat(admission.tryOpen(new EmbeddedChannel().remoteAddress())).isTrue();
        }
    }

    @Test
    void zeroShouldTurnALimitOff() {
        IpAdmission admission = IpAdmission.unlimited();

        for (int i = 0; i < 100; i++) {
            assertThat(admission.tryOpen(PLAYER)).isTrue();
            assertThat(admission.tryLogin(PLAYER)).isTrue();
        }
    }

    @Test
    void shouldRejectNegativeLimits() {
        assertThatThrownBy(() -> new IpAdmission(-1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IpAdmission(10, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static InetSocketAddress address(String ip, int port) {
        try {
            return new InetSocketAddress(InetAddress.getByName(ip), port);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}
