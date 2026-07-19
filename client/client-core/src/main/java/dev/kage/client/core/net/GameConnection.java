package dev.kage.client.core.net;

import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kết nối tới server-gateway qua {@link Socket} THUẦN — KHÔNG Netty (Điều 23: chỉ server-gateway
 * được import Netty). Network I/O chạy thread riêng, không block render thread (CLIENT.md §8).
 *
 * <p>Packet nhận về đưa vào queue; caller (KageEngine ở đầu tick) tự {@link #pollInbound()} —
 * không xử lý packet giữa lúc render.
 *
 * <p>Mất kết nối tự động thử lại theo backoff tăng dần (NETWORK.md §10) — chống thundering herd
 * khi nhiều client cùng mất kết nối lúc server phục hồi.
 */
public final class GameConnection {

    private static final Logger LOG = LoggerFactory.getLogger(GameConnection.class);

    private static final int[] RECONNECT_BACKOFF_SECONDS = {1, 2, 4, 8, 16, 30};
    private static final long HEARTBEAT_INTERVAL_MS = 15_000;

    private final String host;
    private final int port;
    private final Queue<PacketCodec.Frame> inbound = new ConcurrentLinkedQueue<>();
    private final AtomicInteger nextSequenceId = new AtomicInteger(1);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile Socket socket;
    private volatile DataOutputStream out;
    private Thread connectionThread;
    private Thread heartbeatThread;

    public GameConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        connectionThread = new Thread(this::connectionLoop, "game-connection");
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    public void stop() {
        running.set(false);
        closeQuietly();
        if (connectionThread != null) {
            connectionThread.interrupt();
        }
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
    }

    public boolean isConnected() {
        var current = socket;
        return current != null && current.isConnected() && !current.isClosed();
    }

    /** Client sinh sequenceId tăng dần THEO KẾT NỐI (NETWORK.md §105). */
    public int nextSequenceId() {
        return nextSequenceId.getAndIncrement();
    }

    public void send(int opcode, int sequenceId, byte[] payload) throws IOException {
        var stream = out;
        if (stream == null) {
            throw new IOException("Chưa kết nối tới server");
        }
        var header = new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId);
        byte[] frame = PacketCodec.encodeFrame(header, payload);
        synchronized (stream) {
            stream.writeInt(frame.length);
            stream.write(frame);
            stream.flush();
        }
    }

    /** Gọi ở ĐẦU TICK — không xử lý packet giữa render (CLIENT.md §8). */
    public PacketCodec.Frame pollInbound() {
        return inbound.poll();
    }

    private void connectionLoop() {
        int attempt = 0;
        while (running.get()) {
            try {
                doConnect();
                attempt = 0; // reset backoff sau khi kết nối thành công
                readLoop(); // block tới khi mất kết nối
            } catch (IOException e) {
                // Mất kết nối (server restart, mạng chập chờn) là chuyện BÌNH THƯỜNG, không phải
                // lỗi nghiêm trọng — nhưng vẫn phải LOG (Điều 42: không nuốt exception im lặng),
                // chỉ ở mức DEBUG vì đây là tình huống dự kiến, không phải bất thường.
                LOG.debug("Mất kết nối tới {}:{}, sẽ thử lại theo backoff", host, port, e);
            }
            closeQuietly();
            if (!running.get()) {
                return;
            }
            int backoffSeconds =
                    RECONNECT_BACKOFF_SECONDS[Math.min(attempt, RECONNECT_BACKOFF_SECONDS.length - 1)];
            attempt++;
            LOG.info("Thử kết nối lại sau {}s (lần {})", backoffSeconds, attempt);
            sleepQuietly(backoffSeconds * 1000L);
        }
    }

    private void doConnect() throws IOException {
        var newSocket = new Socket(host, port);
        socket = newSocket;
        out = new DataOutputStream(newSocket.getOutputStream());
        LOG.info("Đã kết nối tới {}:{}", host, port);
        startHeartbeat();
    }

    private void readLoop() throws IOException {
        var in = new DataInputStream(socket.getInputStream());
        while (running.get()) {
            int length = in.readInt(); // ném IOException khi mất kết nối — vòng lặp tự thoát
            byte[] frameBytes = new byte[length];
            in.readFully(frameBytes);
            inbound.add(PacketCodec.decodeFrame(frameBytes));
        }
    }

    private void startHeartbeat() {
        heartbeatThread = new Thread(() -> {
            while (running.get() && isConnected()) {
                try {
                    var ping = PingCommand.newBuilder()
                            .setClientTimestampMs(System.currentTimeMillis())
                            .build();
                    send(Opcode.OPCODE_PING_VALUE, nextSequenceId(), ping.toByteArray());
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);
                } catch (IOException e) {
                    LOG.debug("Gửi heartbeat thất bại, kết nối có thể đã chết", e);
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "game-connection-heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private void closeQuietly() {
        try {
            var current = socket;
            if (current != null) {
                current.close();
            }
        } catch (IOException ignored) {
            // Đóng kết nối đã chết — không có gì để xử lý thêm, tên biến "ignored" là chủ đích.
        }
        socket = null;
        out = null;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
