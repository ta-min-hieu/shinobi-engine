package dev.kage.client.core.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * NETWORK.md §10: "Phát hiện kết nối chết là <b>bắt buộc</b> — TCP có thể treo mà không báo lỗi."
 *
 * <p>Server đã làm phần của nó từ 2026-10-02 (đóng sau 45 giây không nhận khung nào). Phía client
 * thì {@code readLoop} chặn trên {@code readInt()} với {@code soTimeout = 0}: một đường truyền
 * "đen" (Wi-Fi đổi mạng, NAT quên ánh xạ, server treo mà không đóng socket) giữ client ở trạng thái
 * {@code isConnected() == true} MÃI MÃI — không thử lại, không tự đăng nhập lại, thế giới đứng im.
 *
 * <p>Khác {@link GameConnectionReconnectTest}, ô này cần bắt tay TLS XONG (đường dẫn hỏng nằm sau
 * bắt tay), nên dựng một {@link SSLServerSocket} thật bằng đúng cặp cert/key DEV mà server-gateway
 * dùng trong test (cert đã được client pin sẵn — ADR-027). Key đọc THẲNG từ test resource của
 * server-gateway ({@value #SERVER_KEY_PATH}) chứ không chép sang đây: gitleaks (pre-commit) chặn mọi
 * bản sao private key mới, và một nguồn duy nhất thì không thể lệch khỏi cert.
 */
class GameConnectionDeadLinkTest {

    /** Hạn đọc rút ngắn cho test — bản thật là {@code GameConnection.READ_TIMEOUT_MS}. */
    private static final int TEST_READ_TIMEOUT_MS = 1_000;

    /** Key DEV của server-gateway, tính từ gốc repo. */
    private static final String SERVER_KEY_PATH = "server/server-gateway/src/test/resources/tls/dev-key.pem";

    @Test
    @Timeout(20)
    void shouldReconnect_whenTheServerGoesSilentWithoutClosingTheSocket() throws Exception {
        try (SSLServerSocket server = openTlsServer()) {
            server.setSoTimeout(10_000);
            GameConnection connection = new GameConnection(
                    "127.0.0.1", server.getLocalPort(), GameConnection.HEARTBEAT_INTERVAL_MS, TEST_READ_TIMEOUT_MS);
            List<Socket> accepted = new ArrayList<>();
            try {
                connection.start();
                // Lần 1: bắt tay xong rồi IM HẲN — không đọc, không ghi, không đóng. Đúng hình dạng
                // của một đường truyền đen nhìn từ phía client.
                SSLSocket first = (SSLSocket) server.accept();
                accepted.add(first);
                first.startHandshake();
                int generationAfterFirst = waitForGeneration(connection, 1);
                assertThat(generationAfterFirst).isEqualTo(1);

                // Client phải tự nhận ra đường truyền đã chết và kết nối lại (backoff 1 giây).
                Socket second;
                try {
                    second = server.accept();
                } catch (SocketTimeoutException e) {
                    throw new AssertionError(
                            "client không bao giờ kết nối lại — vẫn chờ mãi trên một socket im lặng", e);
                }
                accepted.add(second);
                assertThat(second).isNotNull();
            } finally {
                connection.stop();
                for (Socket socket : accepted) {
                    socket.close();
                }
            }
        }
    }

    private static int waitForGeneration(GameConnection connection, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (connection.connectionGeneration() < expected && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        return connection.connectionGeneration();
    }

    private static SSLServerSocket openTlsServer() throws IOException, GeneralSecurityException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Certificate certificate;
        try (InputStream in = resource("tls/dev-cert.pem")) {
            certificate = certificateFactory.generateCertificate(in);
        }
        String pem = Files.readString(serverKeyFile(), StandardCharsets.US_ASCII)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        PrivateKey key = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        // Mật khẩu keystore chỉ sống trong bộ nhớ của test — sinh ngẫu nhiên, không có gì để giữ bí mật.
        char[] password = UUID.randomUUID().toString().toCharArray();
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("kage-server", key, password, new Certificate[] {certificate});
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, password);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, null);
        return (SSLServerSocket)
                context.getServerSocketFactory().createServerSocket(0, 4, InetAddress.getLoopbackAddress());
    }

    /** Gradle chạy test ở thư mục module ({@code client/client-core}) — đi ngược lên tới gốc repo. */
    private static Path serverKeyFile() throws IOException {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(SERVER_KEY_PATH);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IOException(
                "Không tìm thấy " + SERVER_KEY_PATH + " từ " + Path.of("").toAbsolutePath());
    }

    private static InputStream resource(String path) throws IOException {
        InputStream in = GameConnectionDeadLinkTest.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IOException("Thiếu test resource " + path);
        }
        return in;
    }
}
