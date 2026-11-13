package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.pvp.api.TournamentFacade;

/**
 * Báo trước sự kiện (ADR-306): còn ≤ {@link #WARN_AHEAD} tới một mốc trong lịch sự kiện tuần (ADR-224) thì báo toàn
 * server MỘT dòng chat, mỗi mốc một lần. Boss thế giới không báo ở đây — {@link WorldBossScheduler} đã tự báo trước.
 */
final class EventReminder {

    /** Báo trước bao lâu. */
    static final Duration WARN_AHEAD = Duration.ofMinutes(5);

    private static final long PERIOD_SECONDS = 60L;

    /** Các mốc được báo. */
    enum Kind {
        TOURNAMENT("Luận võ đại hội", "The sect tournament"),
        DAILY_RESET("Làm mới ngày", "The daily reset"),
        WEEKLY_RESET("Làm mới tuần", "The weekly reset");

        private final String vietnamese;
        private final String english;

        Kind(String vietnamese, String english) {
            this.vietnamese = vietnamese;
            this.english = english;
        }
    }

    /** Nguồn dữ liệu. */
    record Deps(TournamentFacade tournament, ConnectedCharacterRegistry registry, Clock clock) {}

    private final Deps deps;
    private final Map<Kind, Instant> announced = new EnumMap<>(Kind.class);

    EventReminder(Deps deps) {
        this.deps = deps;
    }

    /** Việc định kỳ cho {@link ExpansionJobs}. */
    static ExpansionJobs.Job job(Deps deps) {
        return new ExpansionJobs.Job("báo trước sự kiện", PERIOD_SECONDS, new EventReminder(deps)::tick);
    }

    void tick() {
        Instant now = deps.clock().instant();
        check(Kind.TOURNAMENT, deps.tournament().current(now).startAt(), now);
        check(Kind.DAILY_RESET, ResetSchedule.nextDaily(now), now);
        check(Kind.WEEKLY_RESET, ResetSchedule.nextWeekly(now), now);
    }

    private synchronized void check(Kind kind, Instant at, Instant now) {
        if (!due(at, now) || at.equals(announced.get(kind))) {
            return;
        }
        announced.put(kind, at);
        deps.registry().broadcastToAll(Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, notice(kind, minutesLeft(at, now)));
    }

    /** Mốc nằm trong ({@code now}, {@code now + WARN_AHEAD}]. Tĩnh để test. */
    static boolean due(Instant at, Instant now) {
        return at.isAfter(now) && !at.isAfter(now.plus(WARN_AHEAD));
    }

    /** Số phút còn lại, làm tròn lên. Tĩnh để test. */
    static long minutesLeft(Instant at, Instant now) {
        long seconds = Duration.between(now, at).toSeconds();
        return Math.max(1L, (seconds + 59L) / 60L);
    }

    /** Dòng chat song ngữ. Tĩnh để test. */
    static byte[] notice(Kind kind, long minutes) {
        return ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody(kind.vietnamese + " bắt đầu sau " + minutes + " phút! / " + kind.english + " starts in "
                        + minutes + " minutes!")
                .build()
                .toByteArray();
    }
}
