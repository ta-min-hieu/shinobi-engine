package dev.kage.tools.loadtest;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * {@link FrameChannel} giả với ĐỒNG HỒ ẢO: mỗi frame dựng sẵn mang thời điểm nó tới socket; đọc một
 * frame chưa tới thì đồng hồ nhảy tới lúc nó tới, hết frame thì "chờ" đúng {@code readTimeout} rồi
 * nổ {@link SocketTimeoutException} — y như socket thật đặt {@code SO_TIMEOUT}.
 */
final class ScriptedChannel implements FrameChannel {

    private static final long READ_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);

    private record Arrival(long atNanos, PacketCodec.Frame frame) {}

    private final Deque<Arrival> inbound = new ArrayDeque<>();
    private int nextSequenceId;
    private long nowNanos;

    ScriptedChannel(int firstSequenceId, long startNanos) {
        this.nextSequenceId = firstSequenceId;
        this.nowNanos = startNanos;
    }

    /** Frame server gửi xuống, tới socket ở thời điểm {@code atMillis} (đồng hồ ảo). */
    ScriptedChannel arrives(long atMillis, int opcode, int sequenceId) {
        PacketCodec.Frame frame =
                new PacketCodec.Frame(new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId), new byte[0]);
        inbound.add(new Arrival(TimeUnit.MILLISECONDS.toNanos(atMillis), frame));
        return this;
    }

    long nowNanos() {
        return nowNanos;
    }

    int pendingFrames() {
        return inbound.size();
    }

    @Override
    public int send(int opcode, byte[] payload) {
        return nextSequenceId++;
    }

    @Override
    public PacketCodec.Frame readFrame() throws IOException {
        Arrival next = inbound.poll();
        if (next == null) {
            nowNanos += READ_TIMEOUT_NANOS;
            throw new SocketTimeoutException("Read timed out");
        }
        nowNanos = Math.max(nowNanos, next.atNanos());
        return next.frame();
    }
}
