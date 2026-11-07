package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.time.ZoneOffset;
import java.util.Locale;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.I18NBundle;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.GuildWarHistoryEntry;
import dev.kage.protocol.GuildWarResult;

/** ADR-208 — một dòng lịch sử bang chiến: ngày, kết quả, đối thủ, tỷ số của mình trước. */
class GuildWarHistoryCoordinatorTest {

    @Test
    void shouldDescribeAWarFromOurSide() {
        I18NBundle vi = I18NBundle.createBundle(
                new FileHandle(new File("src/main/resources/i18n/strings").getAbsoluteFile()), Locale.of("vi"));
        GuildWarHistoryEntry entry = GuildWarHistoryEntry.newBuilder()
                .setOpponentGuildName("Thanh Vân")
                .setDeclaredByUs(false)
                .setOurKills(7)
                .setTheirKills(3)
                .setResult(GuildWarResult.GUILD_WAR_RESULT_WIN)
                .setEndedEpochSeconds(1_762_516_800L) // 2025-11-07 12:00 UTC
                .build();

        assertThat(GuildWarHistoryCoordinator.describe(vi, entry, ZoneOffset.UTC))
                .isEqualTo("07/11 12:00 - THẮNG khi bị Thanh Vân tuyên chiến: 7 - 3");
    }
}
