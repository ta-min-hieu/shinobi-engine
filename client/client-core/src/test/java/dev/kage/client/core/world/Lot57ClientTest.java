package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Lô 57 phía client — rút gọn số (ADR-387), cú pháp hẹn giờ thoát (ADR-388). */
class Lot57ClientTest {

    @Test
    void compactsNumbers() {
        assertThat(CompactNumber.format(999)).isEqualTo("999");
        assertThat(CompactNumber.format(1_000)).isEqualTo("1k");
        assertThat(CompactNumber.format(1_290)).isEqualTo("1.2k");
        assertThat(CompactNumber.format(45_600)).isEqualTo("45.6k");
        assertThat(CompactNumber.format(3_400_000)).isEqualTo("3.4M");
        assertThat(CompactNumber.format(-2_500)).isEqualTo("-2.5k");
    }

    @Test
    void parsesAutoExitMinutes() {
        assertThat(AutoExitCoordinator.parseMinutes("30")).contains(30);
        assertThat(AutoExitCoordinator.parseMinutes("0")).isEmpty();
        assertThat(AutoExitCoordinator.parseMinutes("601")).isEmpty();
        assertThat(AutoExitCoordinator.parseMinutes("abc")).isEmpty();
    }
}
