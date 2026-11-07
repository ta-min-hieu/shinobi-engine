package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;

/** ADR-192 — câu "lần cuối online" theo khoảng cách thời gian. */
final class LastOnlineTextTest {

    private static final long NOW = 1_800_000_000L;

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void describesEachRange() {
        assertThat(LastOnlineText.describe(KageI18n.load(), 0L, NOW)).isEmpty();
        assertThat(LastOnlineText.describe(KageI18n.load(), NOW - 10, NOW)).doesNotStartWith("last_online.");
        assertThat(LastOnlineText.describe(KageI18n.load(), NOW - 5 * 60, NOW)).contains("5");
        assertThat(LastOnlineText.describe(KageI18n.load(), NOW - 3 * 3600, NOW))
                .contains("3");
        assertThat(LastOnlineText.describe(KageI18n.load(), NOW - 2 * 86_400, NOW))
                .contains("2");
    }
}
