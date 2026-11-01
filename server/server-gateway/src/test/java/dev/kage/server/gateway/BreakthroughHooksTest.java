package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-136 — đột phá = cấp MỞ ĐẦU một cảnh giới mới (19, 37, 55, 73, 91); mọi cấp khác không phải đột phá. */
class BreakthroughHooksTest {

    @Test
    void shouldRecogniseTheFirstLevelOfEachNewRealm() {
        assertThat(BreakthroughHooks.realmOpenedAt(19)).hasValue(1);
        assertThat(BreakthroughHooks.realmOpenedAt(37)).hasValue(2);
        assertThat(BreakthroughHooks.realmOpenedAt(55)).hasValue(3);
        assertThat(BreakthroughHooks.realmOpenedAt(73)).hasValue(4);
        assertThat(BreakthroughHooks.realmOpenedAt(91)).hasValue(5);
    }

    @Test
    void shouldIgnoreLevelsInsideARealm() {
        assertThat(BreakthroughHooks.realmOpenedAt(1)).isEmpty();
        assertThat(BreakthroughHooks.realmOpenedAt(18)).isEmpty();
        assertThat(BreakthroughHooks.realmOpenedAt(20)).isEmpty();
        assertThat(BreakthroughHooks.realmOpenedAt(100)).isEmpty();
    }
}
