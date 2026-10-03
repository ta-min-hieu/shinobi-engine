package dev.kage.tools.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

final class LatencyRecorderTest {

    @Test
    void shouldUseNearestRankPercentiles() {
        LatencyRecorder recorder = new LatencyRecorder();
        for (int millis = 100; millis >= 1; millis--) { // thứ tự ngược — snapshot phải tự sắp xếp.
            recorder.record(TimeUnit.MILLISECONDS.toNanos(millis));
        }

        LatencyRecorder.Snapshot snapshot = recorder.snapshot();

        assertThat(snapshot.count()).isEqualTo(100);
        assertThat(snapshot.p50Millis()).isEqualTo(50.0);
        assertThat(snapshot.p95Millis()).isEqualTo(95.0);
        assertThat(snapshot.p99Millis()).isEqualTo(99.0);
    }

    @Test
    void shouldDrainSamplesAndCensoredCountOnSnapshot() {
        LatencyRecorder recorder = new LatencyRecorder();
        recorder.record(TimeUnit.MILLISECONDS.toNanos(3));
        recorder.recordCensored(TimeUnit.SECONDS.toNanos(5));

        assertThat(recorder.snapshot().count()).isEqualTo(2);
        assertThat(recorder.snapshot()).isEqualTo(LatencyRecorder.Snapshot.EMPTY);
    }

    /**
     * Coordinated omission: client vòng kín (gửi → chờ → ngủ → gửi) đứng chờ suốt một lần server
     * khựng 5s, nên chỉ ghi ĐÚNG MỘT mẫu xấu — trong khi người chơi thật (gửi theo nhịp, không chờ
     * phản hồi) sẽ có ~10 lệnh kẹt trong khoảng đó. 99 mẫu 1ms + 1 lần khựng 5s: bản cũ cho p99 =
     * 1ms, tức lần khựng VÔ HÌNH.
     */
    @Test
    void shouldBackfillSamplesThatAClosedLoopClientNeverSentDuringAStall() {
        LatencyRecorder recorder = new LatencyRecorder();
        long expectedInterval = TimeUnit.MILLISECONDS.toNanos(500);
        for (int i = 0; i < 99; i++) {
            recorder.record(TimeUnit.MILLISECONDS.toNanos(1), expectedInterval);
        }
        recorder.record(TimeUnit.SECONDS.toNanos(5), expectedInterval);

        LatencyRecorder.Snapshot snapshot = recorder.snapshot();

        // 5000ms + 9 mẫu bù (4500, 4000, ..., 500).
        assertThat(snapshot.count()).isEqualTo(109);
        assertThat(snapshot.p99Millis()).isEqualTo(4500.0);
    }

    @Test
    void shouldNotBackfill_whenLatencyIsWithinExpectedInterval() {
        LatencyRecorder recorder = new LatencyRecorder();

        recorder.record(TimeUnit.MILLISECONDS.toNanos(499), TimeUnit.MILLISECONDS.toNanos(500));

        assertThat(recorder.snapshot().count()).isEqualTo(1);
    }
}
