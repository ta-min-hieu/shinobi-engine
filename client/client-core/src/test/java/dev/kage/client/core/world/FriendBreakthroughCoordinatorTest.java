package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.CongratulateFriendOutcome;
import dev.kage.protocol.CongratulateFriendResponse;
import dev.kage.protocol.FriendBreakthroughEvent;

/** ADR-182 — dòng báo bạn đột phá (tên cảnh giới) và câu báo kết quả chúc mừng. */
final class FriendBreakthroughCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void noticeNamesTheFriendAndTheRealm() {
        FriendBreakthroughEvent event = FriendBreakthroughEvent.newBuilder()
                .setFriendName("Lan")
                .setRealm(3)
                .setLevel(55)
                .build();
        assertThat(FriendBreakthroughCoordinator.describe(KageI18n.load(), event))
                .contains("Lan")
                .contains(KageI18n.textOrKey(KageI18n.load(), "realm.kim_dan"));
    }

    @Test
    void outcomesAreTranslated() {
        for (CongratulateFriendOutcome outcome : CongratulateFriendOutcome.values()) {
            if (outcome == CongratulateFriendOutcome.UNRECOGNIZED) {
                continue;
            }
            CongratulateFriendResponse response = CongratulateFriendResponse.newBuilder()
                    .setOutcome(outcome)
                    .setFriendName("Lan")
                    .build();
            assertThat(FriendBreakthroughCoordinator.describe(KageI18n.load(), response))
                    .doesNotStartWith("friend_breakthrough.");
        }
    }
}
