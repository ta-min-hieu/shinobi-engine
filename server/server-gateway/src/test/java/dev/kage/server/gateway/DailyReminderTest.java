package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ChatMessageEvent;
import dev.kage.quest.domain.QuestCatalogPort;
import dev.kage.quest.domain.QuestLogEntry;
import dev.kage.quest.domain.QuestStatus;

/** ADR-252 — chỉ đếm nhiệm vụ ngày đã mở khoá và chưa lĩnh hôm nay. */
class DailyReminderTest {

    private static final List<QuestCatalogPort.Entry> CATALOG = List.of(
            new QuestCatalogPort.Entry("intro", Optional.empty(), false),
            new QuestCatalogPort.Entry("daily_a", Optional.empty(), true),
            new QuestCatalogPort.Entry("daily_b", Optional.of("intro"), true),
            new QuestCatalogPort.Entry("daily_c", Optional.of("locked_story"), true));

    @Test
    void countsUnlockedUnclaimedDailies() {
        assertThat(DailyReminder.missing(CATALOG, List.of())).isEqualTo(new DailyReminder.Missing(1, 0));
        assertThat(DailyReminder.missing(
                        CATALOG,
                        List.of(
                                new QuestLogEntry("intro", QuestStatus.CLAIMED),
                                new QuestLogEntry("daily_a", QuestStatus.COMPLETED))))
                .isEqualTo(new DailyReminder.Missing(2, 1));
        assertThat(DailyReminder.missing(
                        CATALOG,
                        List.of(
                                new QuestLogEntry("intro", QuestStatus.CLAIMED),
                                new QuestLogEntry("daily_a", QuestStatus.CLAIMED),
                                new QuestLogEntry("daily_b", QuestStatus.CLAIMED))))
                .isEqualTo(new DailyReminder.Missing(0, 0));
    }

    @Test
    void noticeIsBilingual() throws InvalidProtocolBufferException {
        String body = ChatMessageEvent.parseFrom(DailyReminder.notice(new DailyReminder.Missing(3, 1)))
                .getBody();
        assertThat(body).contains("còn 3 nhiệm vụ ngày").contains("3 daily quests");
    }
}
