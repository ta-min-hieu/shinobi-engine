package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-164 — dòng HUD "x2 EXP": giờ hết hạn tính từ lúc nhận, phút làm tròn LÊN; hết hạn thì rỗng (HUD gỡ dòng). */
final class ExpBuffCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    private final ExpBuffCoordinator coordinator = new ExpBuffCoordinator();

    @Test
    void shouldShowEndTimeAndMinutesLeft() {
        String text = coordinator.describe(1_790L, LocalTime.of(10, 0));

        assertThat(text).contains("10:29").contains("30");
    }

    @Test
    void expiredBuffShouldHideTheLine() {
        assertThat(coordinator.describe(0L, LocalTime.of(10, 0))).isEmpty();
    }
}
