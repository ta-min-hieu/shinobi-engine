package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.FriendView;
import dev.kage.protocol.SetFriendNoteOutcome;
import dev.kage.protocol.SetFriendNoteResponse;

/** ADR-173 — ghi chú hiện kèm tên trong danh sách bạn, và câu báo kết quả lệnh /ghichu. */
final class FriendNoteCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void displayNameShouldCarryTheNoteOnlyWhenPresent() {
        FriendView plain = FriendView.newBuilder().setName("Lan").build();
        assertThat(FriendController.displayNameOf(plain)).isEqualTo("Lan");
        assertThat(FriendController.displayNameOf(
                        plain.toBuilder().setNote("sư tỷ").build()))
                .isEqualTo("Lan (sư tỷ)");
    }

    @Test
    void describeShouldReportSavedClearedAndRefused() {
        SetFriendNoteResponse ok = SetFriendNoteResponse.newBuilder()
                .setOutcome(SetFriendNoteOutcome.SET_FRIEND_NOTE_OUTCOME_OK)
                .setTargetName("Lan")
                .setNote("sư tỷ")
                .build();
        assertThat(FriendNoteCoordinator.describe(KageI18n.load(), ok))
                .contains("Lan")
                .contains("sư tỷ");
        assertThat(FriendNoteCoordinator.describe(
                        KageI18n.load(), ok.toBuilder().setNote("").build()))
                .doesNotStartWith("friend_note.")
                .contains("Lan");
        SetFriendNoteResponse refused = ok.toBuilder()
                .setOutcome(SetFriendNoteOutcome.SET_FRIEND_NOTE_OUTCOME_NOT_FRIENDS)
                .build();
        assertThat(FriendNoteCoordinator.describe(KageI18n.load(), refused))
                .doesNotStartWith("friend_note.")
                .contains("Lan");
    }
}
