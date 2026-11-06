package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.SetGuildNoticeOutcome;
import dev.kage.protocol.SetGuildNoticeResponse;

/** ADR-179 — câu báo kết quả đăng thông báo bang. */
final class GuildNoticeCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void describeShouldCoverPostedClearedAndRefused() {
        SetGuildNoticeResponse ok = SetGuildNoticeResponse.newBuilder()
                .setOutcome(SetGuildNoticeOutcome.SET_GUILD_NOTICE_OUTCOME_OK)
                .setNotice("Tối nay đi Bí Cảnh")
                .build();
        assertThat(GuildNoticeCoordinator.describe(KageI18n.load(), ok)).contains("Tối nay đi Bí Cảnh");
        assertThat(GuildNoticeCoordinator.describe(
                        KageI18n.load(), ok.toBuilder().setNotice("").build()))
                .doesNotStartWith("guild_notice.");
        assertThat(GuildNoticeCoordinator.describe(
                        KageI18n.load(),
                        ok.toBuilder()
                                .setOutcome(SetGuildNoticeOutcome.SET_GUILD_NOTICE_OUTCOME_NOT_LEADER)
                                .build()))
                .doesNotStartWith("guild_notice.");
    }
}
