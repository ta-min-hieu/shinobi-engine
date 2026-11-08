package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.quest.domain.QuestCatalogPort;
import dev.kage.quest.domain.QuestLogEntry;
import dev.kage.quest.domain.QuestStatus;

/**
 * Nhắc việc ngày chưa xong (ADR-252): còn ≤ {@link #WARN_AHEAD} tới mốc reset ngày thì nhắc MỘT lần mỗi người đang
 * online còn nhiệm vụ ngày chưa lĩnh hôm nay. Điểm danh không cần nhắc — đã tự nhận lúc vào world (ADR-060).
 */
final class DailyReminder {

    /** Nhắc trước bao lâu. */
    static final Duration WARN_AHEAD = Duration.ofHours(1);

    private static final long PERIOD_SECONDS = 60L;

    /** Nguồn dữ liệu. */
    record Deps(GatewayFacades gateway, ConnectedCharacterRegistry registry, Clock clock) {}

    /** Đếm cho một người: số nhiệm vụ ngày còn thiếu, trong đó bao nhiêu đã xong chưa lĩnh. */
    record Missing(int remaining, int readyToClaim) {}

    private final Deps deps;
    private volatile Instant remindedForReset = Instant.EPOCH;

    DailyReminder(Deps deps) {
        this.deps = deps;
    }

    /** Việc định kỳ cho {@link ExpansionJobs}. */
    static ExpansionJobs.Job job(Deps deps) {
        DailyReminder reminder = new DailyReminder(deps);
        return new ExpansionJobs.Job("nhắc việc ngày", PERIOD_SECONDS, reminder::tick);
    }

    /**
     * Nhiệm vụ ngày đã mở khoá (không tiên quyết, hoặc tiên quyết đã lĩnh) mà hôm nay chưa lĩnh. Tĩnh để test.
     *
     * @param log nhật ký hôm nay (nhiệm vụ ngày lĩnh trước 0h không có mặt — ADR-055)
     */
    static Missing missing(List<QuestCatalogPort.Entry> catalog, List<QuestLogEntry> log) {
        Map<String, QuestStatus> status = new HashMap<>();
        log.forEach(entry -> status.put(entry.questCode(), entry.status()));
        int remaining = 0;
        int ready = 0;
        for (QuestCatalogPort.Entry quest : catalog) {
            boolean unlocked = quest.prerequisiteCode()
                    .map(code -> status.get(code) == QuestStatus.CLAIMED)
                    .orElse(true);
            QuestStatus mine = status.get(quest.code());
            if (quest.daily() && unlocked && mine != QuestStatus.CLAIMED) {
                remaining++;
                if (mine == QuestStatus.COMPLETED) {
                    ready++;
                }
            }
        }
        return new Missing(remaining, ready);
    }

    void tick() {
        Instant now = deps.clock().instant();
        Instant reset = ResetSchedule.nextDaily(now);
        if (reset.equals(remindedForReset) || Duration.between(now, reset).compareTo(WARN_AHEAD) > 0) {
            return;
        }
        remindedForReset = reset;
        List<QuestCatalogPort.Entry> catalog = deps.gateway()
                .progression()
                .expansion()
                .lot34()
                .lot35()
                .lot36()
                .lot37()
                .questCatalog()
                .all();
        for (long characterId : deps.registry().onlineCharacterIds()) {
            Missing missing = missing(
                    catalog, deps.gateway().contentFacades().questFacade().listQuests(characterId));
            if (missing.remaining() > 0) {
                deps.registry()
                        .sendToCharacterIfOnline(
                                characterId, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, notice(missing));
            }
        }
    }

    /** Dòng chat hệ thống song ngữ. Tĩnh để test. */
    static byte[] notice(Missing missing) {
        return ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody("Còn chưa tới 1 giờ là qua ngày mới: còn " + missing.remaining() + " nhiệm vụ ngày chưa lĩnh ("
                        + missing.readyToClaim() + " đã xong, chỉ cần lĩnh thưởng). / Less than an hour to the daily"
                        + " reset: " + missing.remaining() + " daily quests left (" + missing.readyToClaim()
                        + " done, just claim them).")
                .build()
                .toByteArray();
    }
}
