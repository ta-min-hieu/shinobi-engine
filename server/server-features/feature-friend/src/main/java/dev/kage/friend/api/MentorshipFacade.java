package dev.kage.friend.api;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import dev.kage.friend.application.MentorshipUseCase;
import dev.kage.friend.application.PendingOffers;
import dev.kage.friend.domain.MentorshipOutcome;
import dev.kage.friend.domain.MentorshipPort;

/** API sư đồ (ADR-093). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class MentorshipFacade {

    /** Lời bái sư chờ sư phụ trả lời trong ngần này. */
    public static final Duration REQUEST_TTL = Duration.ofMinutes(5);

    /** Composition không có sư đồ (test) — không ai có sư phụ, không nhận được ai. */
    public static final MentorshipFacade DISABLED = new MentorshipFacade(
            new MentorshipPort() {
                @Override
                public Optional<Long> mentorOf(long discipleId) {
                    return Optional.empty();
                }

                @Override
                public List<Long> disciplesOf(long mentorId) {
                    return List.of();
                }

                @Override
                public MentorshipOutcome bind(long mentorId, long discipleId, int maxDisciples) {
                    return MentorshipOutcome.MENTOR_FULL;
                }

                @Override
                public boolean unbind(long firstId, long secondId) {
                    return false;
                }

                @Override
                public boolean claimMilestone(long discipleId, long mentorId, int milestone) {
                    return false;
                }

                @Override
                public void markMilestonesPassed(long discipleId, long mentorId, List<Integer> milestones) {
                    // không có sư đồ — không có mốc nào để ghi
                }

                @Override
                public void graduate(long discipleId) {
                    // không có sư đồ — không có quan hệ nào để khép
                }
            },
            Clock.systemUTC());

    private final MentorshipUseCase useCase;

    /** Dựng ở composition root. */
    public MentorshipFacade(MentorshipPort port, Clock clock) {
        this.useCase = new MentorshipUseCase(port, new PendingOffers(clock, REQUEST_TTL));
    }

    /** Đồ đệ xin bái sư — lời chờ sư phụ nhận trong {@link #REQUEST_TTL}. */
    public MentorshipOutcome request(long discipleId, int discipleLevel, long mentorId, int mentorLevel) {
        return useCase.request(discipleId, discipleLevel, mentorId, mentorLevel);
    }

    /** Sư phụ nhận lời bái sư của {@code discipleId}. */
    public MentorshipOutcome accept(long mentorId, int mentorLevel, long discipleId, int discipleLevel) {
        return useCase.accept(mentorId, mentorLevel, discipleId, discipleLevel);
    }

    /** Một trong hai cắt quan hệ sư đồ. */
    public MentorshipOutcome leave(long characterId, long otherId) {
        return useCase.leave(characterId, otherId);
    }

    /** Sư phụ hiện tại. */
    public Optional<Long> mentorOf(long discipleId) {
        return useCase.mentorOf(discipleId);
    }

    /** Đồ đệ hiện tại. */
    public List<Long> disciplesOf(long mentorId) {
        return useCase.disciplesOf(mentorId);
    }

    /** Gọi mỗi lần nhân vật lên cấp — xem {@link MentorshipUseCase#onLevelReached}. */
    public List<MentorshipUseCase.MilestoneReward> onLevelReached(long characterId, int level) {
        return useCase.onLevelReached(characterId, level);
    }
}
