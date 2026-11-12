package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.kage.checkin.api.WeeklyTaskFacade;
import dev.kage.checkin.domain.WeeklyTaskPort;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;

/**
 * Nhắc nhiệm vụ tuần (ADR-282): còn ≤ {@link #WARN_AHEAD} tới mốc reset tuần thì nhắc MỘT lần mỗi người đang online còn
 * nhiệm vụ tuần chưa lĩnh (ADR-154). Cùng khuôn {@link DailyReminder}.
 */
final class WeeklyReminder {

    /** Nhắc trước bao lâu. */
    static final Duration WARN_AHEAD = Duration.ofHours(3);

    private static final long PERIOD_SECONDS = 60L;

    /** Nguồn dữ liệu. */
    record Deps(WeeklyTaskFacade tasks, ConnectedCharacterRegistry registry, Clock clock) {}

    /** Số việc tuần chưa lĩnh, trong đó bao nhiêu đã đủ tiến độ. */
    record Missing(int remaining, int readyToClaim) {}

    private final Deps deps;
    private volatile Instant remindedForReset = Instant.EPOCH;

    WeeklyReminder(Deps deps) {
        this.deps = deps;
    }

    /** Việc định kỳ cho {@link ExpansionJobs}. */
    static ExpansionJobs.Job job(Deps deps) {
        WeeklyReminder reminder = new WeeklyReminder(deps);
        return new ExpansionJobs.Job("nhắc nhiệm vụ tuần", PERIOD_SECONDS, reminder::tick);
    }

    /** Đếm việc chưa lĩnh. Tĩnh để test. */
    static Missing missing(List<WeeklyTaskPort.Task> tasks) {
        int remaining = 0;
        int ready = 0;
        for (WeeklyTaskPort.Task task : tasks) {
            if (!task.claimed()) {
                remaining++;
                if (task.progress() >= task.target()) {
                    ready++;
                }
            }
        }
        return new Missing(remaining, ready);
    }

    void tick() {
        Instant now = deps.clock().instant();
        Instant reset = ResetSchedule.nextWeekly(now);
        if (reset.equals(remindedForReset) || Duration.between(now, reset).compareTo(WARN_AHEAD) > 0) {
            return;
        }
        remindedForReset = reset;
        for (long characterId : deps.registry().onlineCharacterIds()) {
            Missing missing = missing(deps.tasks().tasks(characterId, now));
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
                .setBody("Sắp qua tuần mới: còn " + missing.remaining() + " nhiệm vụ tuần chưa lĩnh ("
                        + missing.readyToClaim() + " đã đủ, chỉ cần lĩnh — phím E). / The weekly reset is near: "
                        + missing.remaining() + " weekly tasks unclaimed (" + missing.readyToClaim()
                        + " complete, just claim them — key E).")
                .build()
                .toByteArray();
    }
}
