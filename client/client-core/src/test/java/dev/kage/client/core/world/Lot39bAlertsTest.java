package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Lô 39 — ngưỡng máu thấp (ADR-243), ô trống túi (ADR-246), nhận diện nhắc tên (ADR-245). */
class Lot39bAlertsTest {

    @Test
    void lowHealthBelowThirtyPercentWhileAlive() {
        assertThat(LowHealthWarning.isLow(29, 100)).isTrue();
        assertThat(LowHealthWarning.isLow(30, 100)).isFalse();
        assertThat(LowHealthWarning.isLow(0, 100)).isFalse();
        assertThat(LowHealthWarning.isLow(5, 0)).isFalse();
    }

    @Test
    void freeSlotsIgnoreUnknownCapacity() {
        assertThat(BagSpaceWarningCoordinator.freeSlots(28, 30)).isEqualTo(2);
        assertThat(BagSpaceWarningCoordinator.freeSlots(31, 30)).isZero();
        assertThat(BagSpaceWarningCoordinator.freeSlots(3, 0)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void mentionMatchesWholeNameWithoutAccentsFromOthersOnly() {
        MentionWatch watch = new MentionWatch();
        assertThat(watch.mentions("BaoDo01", "ĐạoHữu01 ơi")).isFalse();
        watch.useOwnName("ĐạoHữu01");
        assertThat(watch.mentions("BaoDo01", "dao huu01")).isFalse();
        assertThat(watch.mentions("BaoDo01", "đi bí cảnh không daohuu01?")).isTrue();
        assertThat(watch.mentions("BaoDo01", "ĐẠOHỮU01!")).isTrue();
        assertThat(watch.mentions("BaoDo01", "xdaohuu01")).isFalse();
        assertThat(watch.mentions("ĐạoHữu01", "ĐạoHữu01 đây")).isFalse();
        assertThat(watch.mentions("", "ĐạoHữu01")).isFalse();
        assertThat(MentionWatch.fold("Đạo Hữu")).isEqualTo("dao huu");
    }
}
