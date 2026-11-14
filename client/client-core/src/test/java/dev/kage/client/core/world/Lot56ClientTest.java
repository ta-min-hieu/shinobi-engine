package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Lô 56 phía client — Tab hoàn thành lệnh (ADR-380), dòng lời mời kèm lời nhắn (ADR-376). */
class Lot56ClientTest {

    @Test
    void completesUniqueCommandWithSpace() {
        assertThat(ChatCompletionCoordinator.completion("/thun", ChatCompletionCoordinator.matches("/thun")))
                .isEqualTo("/thunhom ");
    }

    @Test
    void completesCommonPrefixOrKeepsTyped() {
        assertThat(ChatCompletionCoordinator.completion("/a", List.of("/abc", "/abd")))
                .isEqualTo("/ab");
        assertThat(ChatCompletionCoordinator.completion("/ab", List.of("/abc", "/abd")))
                .isEqualTo("/ab");
        assertThat(ChatCompletionCoordinator.completion("/zzz", List.of())).isEqualTo("/zzz");
    }

    @Test
    void onlyCompletesCommandNames() {
        assertThat(ChatCompletionCoordinator.matches("xin chào")).isEmpty();
        assertThat(ChatCompletionCoordinator.matches("/w Ten")).isEmpty();
        assertThat(ChatCompletionCoordinator.matches("/SAO")).containsExactly("/saochep");
    }

    @Test
    void pendingRowShowsNote() {
        assertThat(FriendCoordinator.withNote(new FriendController.PendingRequest(1L, "BaoDo01", "")))
                .isEqualTo("BaoDo01");
        assertThat(FriendCoordinator.withNote(new FriendController.PendingRequest(1L, "BaoDo01", "chào")))
                .isEqualTo("BaoDo01 — \"chào\"");
    }
}
