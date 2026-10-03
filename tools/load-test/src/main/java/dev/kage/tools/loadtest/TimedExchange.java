package dev.kage.tools.loadtest;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.function.LongSupplier;

/**
 * Một lượt hỏi-đáp CÓ ĐO: gửi, chờ đúng phản hồi của chính lệnh đó ({@link RoundTrip}), ghi độ trễ.
 * Tách khỏi {@link SimulatedPlayer} (2026-10-03) để mọi quy tắc ghi mẫu kiểm được bằng test với
 * đồng hồ ảo:
 *
 * <ul>
 *   <li>Lượt HẾT GIỜ vẫn vào histogram, dưới dạng cận dưới ({@link LatencyRecorder#recordCensored}).
 *       Bản cũ bỏ mẫu đó ⇒ lúc quá tải p99 tự đẹp lên đúng khi nó xấu nhất.
 *   <li>Lượt server từ chối ({@code ErrorResponse}) vẫn là một phản hồi có độ trễ thật, nhưng được
 *       đếm riêng {@link FailureCounters.Kind#REJECTED}, không lẫn với quá tải.
 * </ul>
 */
final class TimedExchange {

    // 2000 (không phải 200) — nhiều client tụ 1 điểm sinh AoI event fan-out O(n) thật, ngân sách quá
    // nhỏ biến "chậm nhưng vẫn tới" thành "lỗi" giả (soak test 2026-07-26).
    static final int MAX_FRAMES_TO_SKIP = 2000;

    private final FrameChannel channel;
    private final LatencyRecorder recorder;
    private final FailureCounters failures;
    private final LongSupplier nanoClock;

    TimedExchange(FrameChannel channel, LatencyRecorder recorder, FailureCounters failures, LongSupplier nanoClock) {
        this.channel = channel;
        this.recorder = recorder;
        this.failures = failures;
        this.nanoClock = nanoClock;
    }

    /**
     * @param expectedIntervalNanos nhịp gửi DỰ KIẾN của loại lệnh này — dùng bù coordinated omission
     *     ({@link LatencyRecorder#record(long, long)}); {@link Long#MAX_VALUE} = không bù
     * @throws SocketTimeoutException nếu hết giờ chờ (mẫu cận dưới đã được ghi trước khi ném)
     * @throws RoundTrip.ResponseMissingException nếu hết ngân sách frame (mẫu cận dưới đã được ghi)
     * @throws IOException nếu socket chết
     */
    RoundTrip.Outcome run(int commandOpcode, int responseOpcode, byte[] payload, long expectedIntervalNanos)
            throws IOException {
        long startNanos = nanoClock.getAsLong();
        int sequenceId = channel.send(commandOpcode, payload);
        RoundTrip.Outcome outcome;
        try {
            outcome = RoundTrip.await(channel, responseOpcode, sequenceId, MAX_FRAMES_TO_SKIP);
        } catch (SocketTimeoutException | RoundTrip.ResponseMissingException e) {
            recorder.recordCensored(nanoClock.getAsLong() - startNanos);
            throw e;
        }
        recorder.record(nanoClock.getAsLong() - startNanos, expectedIntervalNanos);
        if (outcome == RoundTrip.Outcome.REJECTED) {
            failures.increment(FailureCounters.Kind.REJECTED);
        }
        return outcome;
    }
}
