package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Lô 64 phía client — tắt tiếng tạm thời (ADR-446), số ô túi trống (ADR-445). */
class Lot64ClientTest {

    @Test
    void togglesMutedNamesIgnoringCase() {
        Set<String> muted = new HashSet<>();
        assertThat(ChatQuietCoordinator.toggle(muted, ChatQuietCoordinator.fold("BaoDo01")))
                .isTrue();
        assertThat(muted).containsExactly("baodo01");
        assertThat(ChatQuietCoordinator.toggle(muted, ChatQuietCoordinator.fold("baodo01")))
                .isFalse();
        assertThat(muted).isEmpty();
    }

    @Test
    void freeSlotsUnknownWithoutCapacity() {
        assertThat(BagSpaceWarningCoordinator.freeSlots(10, 40)).isEqualTo(30);
        assertThat(BagSpaceWarningCoordinator.freeSlots(10, 0)).isEqualTo(Integer.MAX_VALUE);
    }
}
