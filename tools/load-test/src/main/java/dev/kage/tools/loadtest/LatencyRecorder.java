package dev.kage.tools.loadtest;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Percentile packet latency (BENCHMARK.md §5 "Packet latency p50/p95/p99") — {@link #snapshot}
 * DRAIN toàn bộ mẫu rồi tính (không giữ mẫu vô hạn qua soak test 2h, tránh phình bộ nhớ tool). Đây
 * là công cụ đo NGOÀI game loop (chạy trong process load-test riêng) nên allocation không bị cấm
 * như /CLAUDE.md §6 (chỉ áp dụng cho code CHẠY TRONG game loop server thật).
 */
final class LatencyRecorder {

    private final ConcurrentLinkedQueue<Long> samplesNanos = new ConcurrentLinkedQueue<>();
    private final AtomicLong censoredSamples = new AtomicLong();

    void record(long latencyNanos) {
        samplesNanos.add(latencyNanos);
    }

    /**
     * Ghi một mẫu VÀ bù coordinated omission — cùng ngữ nghĩa
     * {@code HdrHistogram.recordValueWithExpectedInterval}.
     *
     * <p>{@link SimulatedPlayer} là client VÒNG KÍN: gửi rồi đứng chờ phản hồi rồi mới ngủ và gửi
     * tiếp. Khi server khựng {@code L} giây, nó chỉ ghi ĐÚNG MỘT mẫu xấu, còn những lệnh lẽ ra đã
     * gửi trong khoảng đó (người chơi thật gửi theo phím bấm, không chờ phản hồi) không bao giờ tồn
     * tại để mà chậm. Bù bằng các mẫu {@code L - k·interval} cho tới khi xuống dưới {@code interval}.
     *
     * @param expectedIntervalNanos nhịp gửi dự kiến; mẫu ≤ nhịp này không sinh mẫu bù
     */
    void record(long latencyNanos, long expectedIntervalNanos) {
        record(latencyNanos);
        if (expectedIntervalNanos <= 0) {
            return;
        }
        for (long missed = latencyNanos - expectedIntervalNanos;
                missed >= expectedIntervalNanos;
                missed -= expectedIntervalNanos) {
            record(missed);
        }
    }

    /**
     * Lượt hết giờ chờ — vẫn vào histogram với giá trị đã chờ (CẬN DƯỚI của độ trễ thật), đếm riêng
     * để report nói rõ p99 có thể còn tệ hơn. Bỏ mẫu này đi (bản cũ) làm p99 đẹp lên đúng lúc quá tải.
     */
    void recordCensored(long elapsedNanos) {
        censoredSamples.incrementAndGet();
        record(elapsedNanos);
    }

    /**
     * {@code count=0} nghĩa là không có traffic nào hoàn tất trong khoảng báo cáo vừa qua.
     * {@code censoredCount} là số mẫu trong {@code count} chỉ là CẬN DƯỚI (hết giờ chờ).
     */
    record Snapshot(int count, long censoredCount, double p50Millis, double p95Millis, double p99Millis) {
        static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0);
    }

    Snapshot snapshot() {
        long censored = censoredSamples.getAndSet(0);
        long[] values = drainToSortedArray();
        if (values.length == 0) {
            return Snapshot.EMPTY;
        }
        return new Snapshot(
                values.length,
                censored,
                percentileMillis(values, 0.50),
                percentileMillis(values, 0.95),
                percentileMillis(values, 0.99));
    }

    private long[] drainToSortedArray() {
        long[] values = new long[samplesNanos.size()];
        int i = 0;
        Long sample;
        while (i < values.length && (sample = samplesNanos.poll()) != null) {
            values[i++] = sample;
        }
        long[] trimmed = i == values.length ? values : Arrays.copyOf(values, i);
        Arrays.sort(trimmed);
        return trimmed;
    }

    private static double percentileMillis(long[] sortedNanos, double percentile) {
        int index = (int) Math.ceil(percentile * sortedNanos.length) - 1;
        int clampedIndex = Math.max(0, Math.min(index, sortedNanos.length - 1));
        return sortedNanos[clampedIndex] / 1_000_000.0;
    }
}
