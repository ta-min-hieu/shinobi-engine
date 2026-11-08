package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-215 — đọc số cái cần tách của {@code /tach}. */
class StackSplitCoordinatorTest {

    @Test
    void shouldAcceptOnlyPositiveSmallNumbers() {
        assertThat(StackSplitCoordinator.parseAmount(" 5 ")).contains(5);
        assertThat(StackSplitCoordinator.parseAmount("")).isEmpty();
        assertThat(StackSplitCoordinator.parseAmount("0")).isEmpty();
        assertThat(StackSplitCoordinator.parseAmount("12345")).isEmpty();
        assertThat(StackSplitCoordinator.parseAmount("x")).isEmpty();
    }
}
