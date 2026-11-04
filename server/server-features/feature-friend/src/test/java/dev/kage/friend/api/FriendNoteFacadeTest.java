package dev.kage.friend.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-173 — chuẩn hoá ghi chú bạn bè. */
final class FriendNoteFacadeTest {

    @Test
    void normalizeShouldCollapseWhitespaceAndDropControlCharacters() {
        assertThat(FriendNoteFacade.normalize("  sư\t tỷ \n")).contains("sư tỷ");
        assertThat(FriendNoteFacade.normalize("   ")).contains("");
    }

    @Test
    void normalizeShouldRefuseNotesOverTheLimit() {
        assertThat(FriendNoteFacade.normalize("a".repeat(FriendNoteFacade.MAX_LENGTH)))
                .isPresent();
        assertThat(FriendNoteFacade.normalize("a".repeat(FriendNoteFacade.MAX_LENGTH + 1)))
                .isEmpty();
    }
}
