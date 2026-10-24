package dev.kage.checkin.api;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import dev.kage.checkin.domain.OnlineRewardPolicy;
import dev.kage.checkin.domain.OnlineTimePort;

/**
 * API thưởng thời gian online (ADR-096). Giờ vào world của phiên đang chạy do gateway giữ (RAM) và đưa vào; chạm DB —
 * chỉ gọi trên Virtual Thread.
 */
public final class OnlineRewardFacade {

    /** Kết quả nhận một mốc. */
    public enum Outcome {
        OK,
        NOT_REACHED,
        ALREADY_CLAIMED,
        UNKNOWN_MILESTONE
    }

    /** Một mốc để hiển thị. */
    public record MilestoneView(OnlineRewardPolicy.Milestone milestone, boolean reached, boolean claimed) {}

    /** Trạng thái hôm nay. */
    public record Status(int secondsToday, List<MilestoneView> milestones) {

        /** Danh sách bất biến — record không tự copy. */
        public Status {
            milestones = List.copyOf(milestones);
        }
    }

    /** Kết quả kèm mốc vừa nhận (để gateway gửi thư quà). */
    public record ClaimResult(Outcome outcome, Optional<OnlineRewardPolicy.Milestone> milestone) {}

    /** Composition không có thưởng online (test). */
    public static final OnlineRewardFacade DISABLED = new OnlineRewardFacade(
            new OnlineTimePort() {
                @Override
                public void addSeconds(long characterId, LocalDate day, int seconds) {
                    // không lưu gì
                }

                @Override
                public Day read(long characterId, LocalDate day) {
                    return new Day(0, 0);
                }

                @Override
                public boolean claim(
                        long characterId, LocalDate day, OnlineRewardPolicy.Milestone milestone, int liveSeconds) {
                    return false;
                }
            },
            Clock.systemUTC());

    private final OnlineTimePort port;
    private final Clock clock;

    /** Dựng ở composition root. */
    public OnlineRewardFacade(OnlineTimePort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    /** Rời world — lưu phần giây của phiên thuộc về hôm nay. */
    public void onLeftWorld(long characterId, Instant enteredAt) {
        Instant now = clock.instant();
        int seconds = OnlineRewardPolicy.secondsOnDayOf(enteredAt, now);
        if (seconds > 0) {
            port.addSeconds(characterId, OnlineRewardPolicy.dayOf(now), seconds);
        }
    }

    /** Trạng thái hôm nay; {@code enteredAt} là giờ vào world của phiên đang chạy. */
    public Status status(long characterId, Instant enteredAt) {
        Instant now = clock.instant();
        OnlineTimePort.Day day = port.read(characterId, OnlineRewardPolicy.dayOf(now));
        int seconds = day.seconds() + OnlineRewardPolicy.secondsOnDayOf(enteredAt, now);
        List<MilestoneView> views = OnlineRewardPolicy.MILESTONES.stream()
                .map(m -> new MilestoneView(m, seconds >= m.requiredSeconds(), (day.claimedMask() & m.mask()) != 0))
                .toList();
        return new Status(seconds, views);
    }

    /** Nhận mốc {@code minutes}. */
    public ClaimResult claim(long characterId, int minutes, Instant enteredAt) {
        Optional<OnlineRewardPolicy.Milestone> milestone = OnlineRewardPolicy.milestone(minutes);
        if (milestone.isEmpty()) {
            return new ClaimResult(Outcome.UNKNOWN_MILESTONE, Optional.empty());
        }
        Instant now = clock.instant();
        LocalDate today = OnlineRewardPolicy.dayOf(now);
        int live = OnlineRewardPolicy.secondsOnDayOf(enteredAt, now);
        OnlineRewardPolicy.Milestone m = milestone.get();
        if (port.claim(characterId, today, m, live)) {
            return new ClaimResult(Outcome.OK, milestone);
        }
        boolean claimed = (port.read(characterId, today).claimedMask() & m.mask()) != 0;
        return new ClaimResult(claimed ? Outcome.ALREADY_CLAIMED : Outcome.NOT_REACHED, Optional.empty());
    }
}
