package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GuildLogEntry;
import dev.kage.protocol.GuildLogKind;

/** ADR-180 — một dòng nhật ký bang theo từng loại. */
final class GuildLogCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void rowsNameTheActorsAndCarryTheDetail() {
        GuildLogEntry kicked = GuildLogEntry.newBuilder()
                .setKind(GuildLogKind.GUILD_LOG_KIND_KICKED)
                .setActorName("Lan")
                .setSubjectName("Minh")
                .setAtEpochSeconds(0)
                .build();
        assertThat(GuildLogCoordinator.describe(KageI18n.load(), kicked, ZoneOffset.UTC))
                .startsWith("01/01 00:00")
                .contains("Lan")
                .contains("Minh");
        GuildLogEntry renamed = kicked.toBuilder()
                .setKind(GuildLogKind.GUILD_LOG_KIND_RENAMED)
                .setDetail("LinhKiemMon")
                .build();
        assertThat(GuildLogCoordinator.describe(KageI18n.load(), renamed, ZoneOffset.UTC))
                .contains("LinhKiemMon");
        GuildLogEntry gone = kicked.toBuilder().setActorName("").build();
        assertThat(GuildLogCoordinator.describe(KageI18n.load(), gone, ZoneOffset.UTC))
                .doesNotContain("guild_log.");
    }
}
