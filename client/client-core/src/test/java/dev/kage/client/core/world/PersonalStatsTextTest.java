package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.time.ZoneOffset;
import java.util.Locale;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.I18NBundle;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.CalendarEntry;
import dev.kage.protocol.CalendarEventKind;
import dev.kage.protocol.GetPersonalStatsResponse;

/** ADR-223/224 — dòng thống kê cá nhân và dòng lịch sự kiện. */
class PersonalStatsTextTest {

    private static final I18NBundle VI = I18NBundle.createBundle(
            new FileHandle(new File("src/main/resources/i18n/strings").getAbsoluteFile()), Locale.of("vi"));

    @Test
    void shouldDescribeStatsWithHoursAndMinutes() {
        GetPersonalStatsResponse stats = GetPersonalStatsResponse.newBuilder()
                .setMonstersKilled(12)
                .setDeaths(3)
                .setPvpWins(2)
                .setPvpLosses(1)
                .setQuestsClaimed(4)
                .setAchievementsUnlocked(5)
                .setPlaytimeSeconds(3_725)
                .build();

        assertThat(PersonalStatsCoordinator.describe(VI, stats))
                .contains("hạ 12 yêu thú")
                .contains("bị hạ 3 lần")
                .endsWith("đã tu luyện 1 giờ 2 phút.");
    }

    @Test
    void shouldDescribeCalendarEntries() {
        CalendarEntry boss = CalendarEntry.newBuilder()
                .setKind(CalendarEventKind.CALENDAR_EVENT_KIND_WORLD_BOSS)
                .setAtEpochSeconds(1_000 + 3_660)
                .build();

        assertThat(EventCalendarCoordinator.describe(VI, boss, 1_000, ZoneOffset.UTC))
                .startsWith("Yêu Vương (boss thế giới): ")
                .endsWith("(còn 1 giờ 1 phút)");
        assertThat(EventCalendarCoordinator.describe(
                        VI, boss.toBuilder().setActive(true).build(), 1_000, ZoneOffset.UTC))
                .isEqualTo("Yêu Vương (boss thế giới): ĐANG DIỄN RA");
    }
}
