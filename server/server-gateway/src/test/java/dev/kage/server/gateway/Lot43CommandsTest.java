package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-274 (cộng bão hoà của giá trị túi) và ADR-278 (dòng báo người nhận). */
class Lot43CommandsTest {

    @Test
    void bagValueArithmeticSaturates() {
        assertThat(Lot43Commands.saturatingAdd(2L, 3L)).isEqualTo(5L);
        assertThat(Lot43Commands.saturatingAdd(Long.MAX_VALUE - 1, 5L)).isEqualTo(Long.MAX_VALUE);
        assertThat(Lot43Commands.saturatingMultiply(7L, 6L)).isEqualTo(42L);
        assertThat(Lot43Commands.saturatingMultiply(Long.MAX_VALUE / 2, 3L)).isEqualTo(Long.MAX_VALUE);
        assertThat(Lot43Commands.saturatingMultiply(5L, 0L)).isZero();
    }

    @Test
    void receivedTextNamesGiverAndAmount() {
        assertThat(Lot43Commands.receivedText("Kage", 25L)).contains("Kage", "25");
    }
}
