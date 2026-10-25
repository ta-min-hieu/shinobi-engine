package dev.kage.checkin.api;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.kage.checkin.domain.ActivityPort;
import dev.kage.checkin.domain.ActivityRules;

/** API lịch hoạt động + điểm năng động (ADR-106). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class ActivityFacade {

    /** Ngày năng động đổi lúc 0h giờ Việt Nam, cùng nhiệm vụ ngày. */
    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Mã các việc — khớp {@code activity_definition.code} (V89). */
    public enum Activity {
        CHECK_IN("check_in"),
        ONLINE_REWARD("online_reward"),
        BOUNTY("bounty"),
        DUNGEON_RUN("dungeon_run"),
        DAILY_QUEST("daily_quest"),
        HERB_GATHER("herb_gather");

        private final String code;

        Activity(String code) {
            this.code = code;
        }

        /** Mã trong DB. */
        public String code() {
            return code;
        }
    }

    /** Một việc để hiển thị. */
    public record ActivityView(ActivityPort.Definition definition, int count, int points) {}

    /** Một rương để hiển thị. */
    public record ChestView(ActivityPort.Chest chest, boolean reached, boolean claimed) {}

    /** Trạng thái hôm nay. */
    public record Status(List<ActivityView> activities, int points, List<ChestView> chests) {

        /** Danh sách bất biến — record không tự copy. */
        public Status {
            activities = List.copyOf(activities);
            chests = List.copyOf(chests);
        }
    }

    /** Composition không có lịch hoạt động (test). */
    public static final ActivityFacade DISABLED = new ActivityFacade(
            new ActivityPort() {
                @Override
                public List<Definition> definitions() {
                    return List.of();
                }

                @Override
                public List<Chest> chests() {
                    return List.of();
                }

                @Override
                public Map<String, Integer> progress(long characterId, LocalDate day) {
                    return Map.of();
                }

                @Override
                public void record(long characterId, LocalDate day, String activityCode) {
                    // không lưu gì
                }

                @Override
                public Set<Integer> claimed(long characterId, LocalDate day) {
                    return Set.of();
                }

                @Override
                public Optional<Chest> claim(long characterId, LocalDate day, int milestone, Instant at) {
                    return Optional.empty();
                }
            },
            Clock.systemUTC());

    private final ActivityPort port;
    private final Clock clock;

    /** Dựng ở composition root. */
    public ActivityFacade(ActivityPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    /** Ngày năng động hiện tại. */
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), VIETNAM);
    }

    /** Cộng một lần cho một việc vừa thành công ở server. */
    public void record(long characterId, Activity activity) {
        port.record(characterId, today(), activity.code());
    }

    /** Trạng thái hôm nay. */
    public Status status(long characterId) {
        LocalDate day = today();
        Map<String, Integer> progress = port.progress(characterId, day);
        List<ActivityPort.Definition> definitions = port.definitions();
        List<ActivityView> activities = definitions.stream()
                .map(definition -> {
                    int count = progress.getOrDefault(definition.code(), 0);
                    return new ActivityView(definition, count, ActivityRules.pointsOf(definition, count));
                })
                .toList();
        int points = ActivityRules.totalPoints(definitions, progress);
        Set<Integer> claimed = port.claimed(characterId, day);
        List<ChestView> chests = port.chests().stream()
                .map(chest -> new ChestView(chest, points >= chest.milestone(), claimed.contains(chest.milestone())))
                .toList();
        return new Status(activities, points, chests);
    }

    /** Nhận rương mốc — xem {@link ActivityPort#claim}. */
    public Optional<ActivityPort.Chest> claim(long characterId, int milestone) {
        return port.claim(characterId, today(), milestone, clock.instant());
    }
}
