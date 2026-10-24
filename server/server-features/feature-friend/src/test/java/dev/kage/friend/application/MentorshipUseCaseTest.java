package dev.kage.friend.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.kage.friend.domain.MentorshipOutcome;
import dev.kage.friend.domain.MentorshipPort;

/** ADR-093 — bái sư, nhận đồ đệ, mốc thưởng một lần, xuất sư ở Lv30. */
class MentorshipUseCaseTest {

    private static final Instant T0 = Instant.parse("2026-10-24T00:00:00Z");
    private static final long MENTOR = 1L;
    private static final long DISCIPLE = 2L;

    private static final class MutableClock extends Clock {
        Instant now = T0;

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class FakePort implements MentorshipPort {
        final Map<Long, Long> mentorByDisciple = new HashMap<>();
        final Set<String> claimed = new HashSet<>();

        @Override
        public Optional<Long> mentorOf(long discipleId) {
            return Optional.ofNullable(mentorByDisciple.get(discipleId));
        }

        @Override
        public List<Long> disciplesOf(long mentorId) {
            List<Long> disciples = new ArrayList<>();
            mentorByDisciple.forEach((disciple, mentor) -> {
                if (mentor == mentorId) {
                    disciples.add(disciple);
                }
            });
            return disciples;
        }

        @Override
        public MentorshipOutcome bind(long mentorId, long discipleId, int maxDisciples) {
            if (mentorByDisciple.containsKey(discipleId)) {
                return MentorshipOutcome.ALREADY_HAS_MENTOR;
            }
            if (disciplesOf(mentorId).size() >= maxDisciples) {
                return MentorshipOutcome.MENTOR_FULL;
            }
            mentorByDisciple.put(discipleId, mentorId);
            return MentorshipOutcome.OK;
        }

        @Override
        public boolean unbind(long firstId, long secondId) {
            return mentorByDisciple.remove(firstId, secondId) || mentorByDisciple.remove(secondId, firstId);
        }

        @Override
        public boolean claimMilestone(long discipleId, long mentorId, int milestone) {
            return claimed.add(discipleId + ":" + milestone);
        }

        @Override
        public void markMilestonesPassed(long discipleId, long mentorId, List<Integer> milestones) {
            milestones.forEach(milestone -> claimed.add(discipleId + ":" + milestone));
        }

        @Override
        public void graduate(long discipleId) {
            mentorByDisciple.remove(discipleId);
        }
    }

    private final MutableClock clock = new MutableClock();
    private final FakePort port = new FakePort();
    private final MentorshipUseCase useCase =
            new MentorshipUseCase(port, new PendingOffers(clock, Duration.ofMinutes(5)));

    @Test
    void shouldBindOnlyAfterTheMentorAcceptsAPendingRequest() {
        assertThat(useCase.accept(MENTOR, 45, DISCIPLE, 5)).isEqualTo(MentorshipOutcome.NO_PENDING_REQUEST);
        assertThat(useCase.request(DISCIPLE, 5, MENTOR, 45)).isEqualTo(MentorshipOutcome.OK);
        assertThat(useCase.accept(MENTOR, 45, DISCIPLE, 5)).isEqualTo(MentorshipOutcome.OK);
        assertThat(useCase.mentorOf(DISCIPLE)).contains(MENTOR);
        // Lời đã dùng — nhận lần hai không còn gì để nhận.
        assertThat(useCase.accept(MENTOR, 45, DISCIPLE, 5)).isEqualTo(MentorshipOutcome.NO_PENDING_REQUEST);
    }

    @Test
    void shouldRefuseWrongLevelsSelfAndExpiredRequests() {
        assertThat(useCase.request(DISCIPLE, 30, MENTOR, 45)).isEqualTo(MentorshipOutcome.DISCIPLE_LEVEL_TOO_HIGH);
        assertThat(useCase.request(DISCIPLE, 5, MENTOR, 39)).isEqualTo(MentorshipOutcome.MENTOR_LEVEL_TOO_LOW);
        assertThat(useCase.request(MENTOR, 5, MENTOR, 45)).isEqualTo(MentorshipOutcome.CANNOT_TARGET_SELF);
        useCase.request(DISCIPLE, 5, MENTOR, 45);
        clock.now = T0.plus(Duration.ofMinutes(6));
        assertThat(useCase.accept(MENTOR, 45, DISCIPLE, 5)).isEqualTo(MentorshipOutcome.NO_PENDING_REQUEST);
    }

    @Test
    void shouldRefuseAFourthDisciple() {
        for (long disciple = 10L; disciple < 13L; disciple++) {
            useCase.request(disciple, 5, MENTOR, 45);
            assertThat(useCase.accept(MENTOR, 45, disciple, 5)).isEqualTo(MentorshipOutcome.OK);
        }
        assertThat(useCase.request(DISCIPLE, 5, MENTOR, 45)).isEqualTo(MentorshipOutcome.MENTOR_FULL);
    }

    @Test
    void shouldRewardEachMilestoneOnceAndGraduateAtThirty() {
        useCase.request(DISCIPLE, 5, MENTOR, 45);
        useCase.accept(MENTOR, 45, DISCIPLE, 5);

        assertThat(useCase.onLevelReached(DISCIPLE, 9)).isEmpty();
        List<MentorshipUseCase.MilestoneReward> ten = useCase.onLevelReached(DISCIPLE, 10);
        assertThat(ten).singleElement().satisfies(reward -> {
            assertThat(reward.milestone()).isEqualTo(10);
            assertThat(reward.discipleGold()).isEqualTo(500L);
            assertThat(reward.mentorGold()).isEqualTo(1_000L);
            assertThat(reward.graduated()).isFalse();
        });
        assertThat(useCase.onLevelReached(DISCIPLE, 11)).isEmpty();

        // Nhảy thẳng từ 11 lên 31 (thưởng quest): nhặt cả mốc 20 lẫn 30, rồi xuất sư.
        List<MentorshipUseCase.MilestoneReward> jump = useCase.onLevelReached(DISCIPLE, 31);
        assertThat(jump)
                .extracting(MentorshipUseCase.MilestoneReward::milestone)
                .containsExactly(20, 30);
        assertThat(jump.get(1).graduated()).isTrue();
        assertThat(useCase.mentorOf(DISCIPLE)).isEmpty();
    }

    @Test
    void shouldNotPayMilestonesPassedBeforeBinding() {
        useCase.request(DISCIPLE, 25, MENTOR, 45);
        useCase.accept(MENTOR, 45, DISCIPLE, 25);

        assertThat(useCase.onLevelReached(DISCIPLE, 26)).isEmpty();
        assertThat(useCase.onLevelReached(DISCIPLE, 30))
                .extracting(MentorshipUseCase.MilestoneReward::milestone)
                .containsExactly(30);
    }

    @Test
    void shouldLeaveFromEitherSide() {
        useCase.request(DISCIPLE, 5, MENTOR, 45);
        useCase.accept(MENTOR, 45, DISCIPLE, 5);
        assertThat(useCase.leave(MENTOR, DISCIPLE)).isEqualTo(MentorshipOutcome.OK);
        assertThat(useCase.leave(DISCIPLE, MENTOR)).isEqualTo(MentorshipOutcome.NOT_RELATED);
    }
}
