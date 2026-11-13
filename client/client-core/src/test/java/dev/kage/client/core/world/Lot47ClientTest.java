package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

/** Lô 47 phía client — thu phóng giao diện (ADR-308). */
class Lot47ClientTest {

    @Test
    void parsesUiScalePercent() {
        assertThat(UiScaleCoordinator.parse("120")).isEqualTo(OptionalInt.of(120));
        assertThat(UiScaleCoordinator.parse("90%")).isEqualTo(OptionalInt.of(90));
        assertThat(UiScaleCoordinator.parse("79")).isEmpty();
        assertThat(UiScaleCoordinator.parse("151")).isEmpty();
        assertThat(UiScaleCoordinator.parse("abc")).isEmpty();
        assertThat(UiScaleCoordinator.parse("")).isEmpty();
    }
}
