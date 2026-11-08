package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Lô 38 — đối số và phân loại kết quả của {@code /huy} (ADR-231), {@code /phangiai} (232), lịch sử cường hoá (233). */
class Lot38CoordinatorsTest {

    @Test
    void shouldRecogniseConfirmWordInBothLanguages() {
        assertThat(ItemDestroyCoordinator.isConfirm(" xacnhan ")).isTrue();
        assertThat(ItemDestroyCoordinator.isConfirm("CONFIRM")).isTrue();
        assertThat(ItemDestroyCoordinator.isConfirm("")).isFalse();
        assertThat(ItemDestroyCoordinator.isConfirm("yes")).isFalse();
    }

    @Test
    void shouldAcceptLevelsOneToHundred() {
        assertThat(BulkDismantleCoordinator.parseLevel("20")).contains(20);
        assertThat(BulkDismantleCoordinator.parseLevel("100")).contains(100);
        assertThat(BulkDismantleCoordinator.parseLevel("0")).isEmpty();
        assertThat(BulkDismantleCoordinator.parseLevel("101")).isEmpty();
        assertThat(BulkDismantleCoordinator.parseLevel("abc")).isEmpty();
    }

    @Test
    void shouldClassifyEnhanceResults() {
        assertThat(EnhanceHistoryCoordinator.resultKey("SUCCESS")).isEqualTo("enhancelog.success");
        assertThat(EnhanceHistoryCoordinator.resultKey("FAILED_DROPPED")).isEqualTo("enhancelog.dropped");
        assertThat(EnhanceHistoryCoordinator.resultKey("FAILED_NO_DROP")).isEqualTo("enhancelog.failed");
        assertThat(EnhanceHistoryCoordinator.resultKey("FAILED_PROTECTED")).isEqualTo("enhancelog.failed");
    }
}
