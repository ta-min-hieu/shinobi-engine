package dev.kage.tools.loadtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.Opcode;

/**
 * Ghép phản hồi theo {@code sequenceId} và đo độ trễ của MỘT lượt hỏi-đáp.
 *
 * <p>Bug đo thật (2026-10-03): mỗi vòng của {@code SimulatedPlayer} kết thúc bằng một
 * {@code MoveCommand} dừng bắn-rồi-quên, và server trả {@code MoveResponse} cho CẢ lệnh đó. Phản
 * hồi ấy nằm sẵn trong socket suốt 300-800ms ngủ, nên vòng sau gửi Move mới rồi "chờ"
 * {@code MOVE_RESPONSE} thì nhặt ngay phản hồi CŨ — đo được ~0ms bất kể server chậm cỡ nào.
 */
final class TimedExchangeTest {

    private static final int MOVE = Opcode.OPCODE_MOVE_COMMAND_VALUE;
    private static final int MOVE_RESPONSE = Opcode.OPCODE_MOVE_RESPONSE_VALUE;
    private static final int AOI_EVENT = Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE;
    private static final long NO_COORDINATED_OMISSION_CORRECTION = Long.MAX_VALUE;

    @Test
    void shouldMeasureUntilOwnResponse_notStaleResponseOfPreviousFireAndForgetMove() throws Exception {
        // Lệnh dừng của vòng trước mang seq 4, phản hồi của nó đã tới từ lâu (t=0). Lệnh mới seq 5
        // gửi ở t=10ms, server trả lời ở t=40ms ⇒ độ trễ thật 30ms.
        ScriptedChannel channel = new ScriptedChannel(5, millis(10))
                .arrives(0, MOVE_RESPONSE, 4)
                .arrives(0, AOI_EVENT, 0)
                .arrives(40, MOVE_RESPONSE, 5);
        LatencyRecorder recorder = new LatencyRecorder();
        FailureCounters failures = new FailureCounters();

        RoundTrip.Outcome outcome = new TimedExchange(channel, recorder, failures, channel::nowNanos)
                .run(MOVE, MOVE_RESPONSE, new byte[0], NO_COORDINATED_OMISSION_CORRECTION);

        assertThat(outcome).isEqualTo(RoundTrip.Outcome.RESPONDED);
        assertThat(recorder.snapshot().p50Millis()).isEqualTo(30.0);
        assertThat(channel.pendingFrames()).isZero();
    }

    @Test
    void shouldReportRejected_whenServerAnswersOwnSequenceWithErrorResponse() throws Exception {
        ScriptedChannel channel = new ScriptedChannel(7, 0)
                .arrives(0, Opcode.OPCODE_ERROR_VALUE, 3) // lỗi của lệnh KHÁC — bỏ qua.
                .arrives(12, Opcode.OPCODE_ERROR_VALUE, 7);
        LatencyRecorder recorder = new LatencyRecorder();
        FailureCounters failures = new FailureCounters();

        RoundTrip.Outcome outcome = new TimedExchange(channel, recorder, failures, channel::nowNanos)
                .run(
                        Opcode.OPCODE_SELL_ITEM_COMMAND_VALUE,
                        Opcode.OPCODE_SELL_ITEM_RESPONSE_VALUE,
                        new byte[0],
                        NO_COORDINATED_OMISSION_CORRECTION);

        assertThat(outcome).isEqualTo(RoundTrip.Outcome.REJECTED);
        assertThat(failures.get(FailureCounters.Kind.REJECTED)).isEqualTo(1);
        assertThat(failures.get(FailureCounters.Kind.TIMEOUT)).isZero();
        assertThat(recorder.snapshot().p50Millis()).isEqualTo(12.0);
    }

    /**
     * Bản cũ chỉ ghi mẫu khi phản hồi TỚI — mọi lượt hết giờ biến mất khỏi histogram, nên lúc quá
     * tải p99 tự đẹp lên đúng khi nó xấu nhất (thiên lệch kẻ sống sót).
     */
    @Test
    void shouldKeepTimedOutExchangeInHistogramAsCensoredLowerBound() {
        ScriptedChannel channel = new ScriptedChannel(1, 0);
        LatencyRecorder recorder = new LatencyRecorder();
        TimedExchange exchange = new TimedExchange(channel, recorder, new FailureCounters(), channel::nowNanos);

        assertThatThrownBy(() -> exchange.run(MOVE, MOVE_RESPONSE, new byte[0], NO_COORDINATED_OMISSION_CORRECTION))
                .isInstanceOf(SocketTimeoutException.class);

        LatencyRecorder.Snapshot snapshot = recorder.snapshot();
        assertThat(snapshot.count()).isEqualTo(1);
        assertThat(snapshot.censoredCount()).isEqualTo(1);
        assertThat(snapshot.p99Millis()).isGreaterThanOrEqualTo(5000.0);
    }

    private static long millis(long value) {
        return value * 1_000_000L;
    }
}
