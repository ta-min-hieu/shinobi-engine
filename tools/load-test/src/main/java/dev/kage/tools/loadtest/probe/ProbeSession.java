package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import dev.kage.protocol.wire.PacketCodec;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Nối {@link GameConnection} với hai thứ probe cần: DÒNG đã giải mã in ra stdout, và
 * {@link WorldObservation} cập nhật theo.
 *
 * <p>Cắm ở tầng {@code FrameObserver} chứ không ở từng chỗ gọi, vì đó là chỗ DUY NHẤT thấy được
 * mọi frame: sự kiện AoI xen được vào giữa bắt tay, giữa lúc đi bộ, giữa lúc chờ phản hồi. Bản
 * đầu tiên nếu chỉ ghi ở vòng quan sát sẽ bỏ sót đúng những {@code MonsterSpawnedEvent} rơi ra
 * ngay lúc vào world — tức là đúng dữ liệu cần cho câu hỏi của ROADMAP mục 58.
 */
final class ProbeSession implements GameConnection.FrameObserver {

    private final PrintStream out;
    private final WorldObservation observation;
    private final long startNanos;

    ProbeSession(PrintStream out, WorldObservation observation, long startNanos) {
        this.out = out;
        this.observation = observation;
        this.startNanos = startNanos;
    }

    @Override
    public void onFrameReceived(PacketCodec.Frame frame, int frameBytes) throws IOException {
        long elapsedMillis = elapsedMillis();
        out.println(ProbeEventLine.format(elapsedMillis, frame));
        observation.apply(elapsedMillis, frame);
    }

    long elapsedMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    /** In 1 dòng ghi chú của chính probe (không phải frame) — cùng luồng ra với dòng sự kiện. */
    void note(String message) {
        out.println(elapsedMillis() + "\t-\tPROBE\t" + message);
    }

    void print(String block) {
        out.println(block);
    }
}
