package dev.kage.tools.loadtest;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Percentile packet latency (BENCHMARK.md §5 "Packet latency p50/p95/p99") — {@link #snapshot}
 * DRAIN toàn bộ mẫu rồi tính (không giữ mẫu vô hạn qua soak test 2h, tránh phình bộ nhớ tool). Đây
 * là công cụ đo NGOÀI game loop (chạy trong process load-test riêng) nên allocation không bị cấm
 * như /CLAUDE.md §6 (chỉ áp dụng cho code CHẠY TRONG game loop server thật).
 */
final class LatencyRecorder {

    private final ConcurrentLinkedQueue<Long> samplesNanos = new ConcurrentLinkedQueue<>();

    void record(long latencyNanos) {
        samplesNanos.add(latencyNanos);
    }

    /** {@code count=0} nghĩa là không có traffic nào hoàn tất trong khoảng báo cáo vừa qua. */
    record Snapshot(int count, double p50Millis, double p95Millis, double p99Millis) {
        static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0);
    }

    Snapshot snapshot() {
        long[] values = drainToSortedArray();
        if (values.length == 0) {
            return Snapshot.EMPTY;
        }
        return new Snapshot(
                values.length,
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
