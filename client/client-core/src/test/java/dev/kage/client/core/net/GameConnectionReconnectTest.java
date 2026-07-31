package dev.kage.client.core.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.net.Socket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verify {@link GameConnection} tự kết nối lại sau khi mất kết nối (NETWORK.md §10, ROADMAP
 * Phase 2 Exit Criteria "Mất kết nối → reconnect có backoff").
 *
 * <p>Dùng {@link ServerSocket} THUẦN (không {@code GatewayServer} thật của server-gateway, không
 * TLS) — tránh client-core test phụ thuộc ngược sang module server. Assertion chỉ cần {@code
 * accept()} tầng TCP thành công (ADR-027: TLS handshake là hành động ỨNG DỤNG xảy ra SAU khi TCP
 * đã kết nối — {@code accept()} trả về độc lập với việc handshake TLS có thành công hay không sau
 * đó). {@link GameConnection} sẽ cố bắt tay TLS trên kết nối này và thất bại (phía server giả
 * không phải TLS) — vô hại, {@code connectionLoop()} bắt IOException/SSLException đó, coi là mất
 * kết nối bình thường, không ảnh hưởng assertion đã pass trước khi {@code finally} dọn dẹp.
 */
class GameConnectionReconnectTest {

    @Test
    @Timeout(15)
    void shouldReconnect_afterServerAppearsOnSamePort() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        GameConnection connection = new GameConnection("127.0.0.1", port);
        try {
            // Lần đầu: CHƯA có server nào lắng nghe -> connection phải TỰ THỬ LẠI, không throw/chết
            // (attempt đầu fail gần như tức thì, rồi vào backoff 1s — NETWORK.md §10).
            connection.start();
            Thread.sleep(500);
            assertThat(connection.isConnected())
                    .as("chưa có server nào lắng nghe, không được coi là đã kết nối")
                    .isFalse();

            // Mở server thật trên ĐÚNG port đó -> connection (đang trong backoff loop) phải tự bắt được.
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                serverSocket.setSoTimeout(8_000);
                Socket accepted = serverSocket.accept();
                assertThat(accepted)
                        .as("GameConnection phải tự kết nối lại khi server xuất hiện")
                        .isNotNull();
                accepted.close();
            }
        } finally {
            connection.stop();
        }
    }
}
