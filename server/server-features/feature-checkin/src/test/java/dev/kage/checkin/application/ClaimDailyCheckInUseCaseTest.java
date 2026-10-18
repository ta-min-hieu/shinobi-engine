package dev.kage.checkin.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.kage.checkin.domain.CheckInPort;
import dev.kage.checkin.domain.CheckInReward;
import dev.kage.checkin.domain.CheckInRewardSender;
import dev.kage.checkin.domain.CheckInState;

/** Luật điểm danh (ADR-060): một lần/ngày theo 0h giờ VN, chu kỳ 7, bỏ ngày không phạt, gửi lại không trùng. */
class ClaimDailyCheckInUseCaseTest {

    private static final long CHARACTER = 9L;

    /** 2026-10-18 16:59:59Z = 23:59:59 giờ VN. */
    private static final Instant JUST_BEFORE_VN_MIDNIGHT = Instant.parse("2026-10-18T16:59:59Z");

    private final FakePort port = new FakePort();
    private final FakeSender sender = new FakeSender();

    @Test
    void shouldGiveDayOneOnce_thenDayTwoAfterVietnamMidnight() {
        ClaimDailyCheckInUseCase.Result first =
                useCaseAt(JUST_BEFORE_VN_MIDNIGHT).execute(CHARACTER);
        ClaimDailyCheckInUseCase.Result again =
                useCaseAt(JUST_BEFORE_VN_MIDNIGHT).execute(CHARACTER);
        ClaimDailyCheckInUseCase.Result next =
                useCaseAt(JUST_BEFORE_VN_MIDNIGHT.plusSeconds(1)).execute(CHARACTER);

        assertThat(first).isEqualTo(new ClaimDailyCheckInUseCase.Result.Claimed(1, 1L));
        assertThat(again).isInstanceOf(ClaimDailyCheckInUseCase.Result.AlreadyClaimed.class);
        assertThat(next).isEqualTo(new ClaimDailyCheckInUseCase.Result.Claimed(2, 2L));
    }

    @Test
    void shouldWrapAfterDaySeven_andNotResetOnAMissedDay() {
        port.states.put(CHARACTER, new CheckInState(7, LocalDate.of(2026, 10, 1)));

        ClaimDailyCheckInUseCase.Result result =
                useCaseAt(JUST_BEFORE_VN_MIDNIGHT).execute(CHARACTER);

        assertThat(result).isEqualTo(new ClaimDailyCheckInUseCase.Result.Claimed(1, 1L));
        port.states.put(CHARACTER, new CheckInState(3, LocalDate.of(2026, 10, 1)));
        assertThat(useCaseAt(JUST_BEFORE_VN_MIDNIGHT.plusSeconds(1)).execute(CHARACTER))
                .as("bỏ hai tuần vẫn sang ngày 4")
                .isEqualTo(new ClaimDailyCheckInUseCase.Result.Claimed(4, 2L));
    }

    /** Chết giữa "gửi thư" và "ghi trạng thái": lần sau cùng ngày dùng lại đúng khoá ⇒ không thư thứ hai. */
    @Test
    void shouldNotSendASecondMail_whenTheStateWriteWasLost() {
        port.failNextAdvance = true;
        useCaseAt(JUST_BEFORE_VN_MIDNIGHT).execute(CHARACTER);

        ClaimDailyCheckInUseCase.Result retried =
                useCaseAt(JUST_BEFORE_VN_MIDNIGHT).execute(CHARACTER);

        assertThat(sender.mailsCreated).isEqualTo(1);
        assertThat(retried).isEqualTo(new ClaimDailyCheckInUseCase.Result.Claimed(1, 0L));
    }

    @Test
    void shouldKeepTheStateUntouched_whenTheMailCannotBeSent() {
        sender.down = true;

        ClaimDailyCheckInUseCase.Result result =
                useCaseAt(JUST_BEFORE_VN_MIDNIGHT).execute(CHARACTER);

        assertThat(result).isInstanceOf(ClaimDailyCheckInUseCase.Result.NotDelivered.class);
        assertThat(port.states).isEmpty();
    }

    private ClaimDailyCheckInUseCase useCaseAt(Instant now) {
        return new ClaimDailyCheckInUseCase(port, sender, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static final class FakePort implements CheckInPort {
        final Map<Long, CheckInState> states = new HashMap<>();
        boolean failNextAdvance;

        @Override
        public Optional<CheckInState> find(long characterId) {
            return Optional.ofNullable(states.get(characterId));
        }

        @Override
        public boolean advance(long characterId, CheckInState expected, CheckInState next) {
            if (failNextAdvance) {
                failNextAdvance = false;
                return false;
            }
            if (!expected.equals(states.getOrDefault(characterId, CheckInState.NEVER))) {
                return false;
            }
            states.put(characterId, next);
            return true;
        }

        @Override
        public List<CheckInReward> rewards() {
            List<CheckInReward> rewards = new ArrayList<>();
            for (int day = 1; day <= 7; day++) {
                rewards.add(new CheckInReward(day, 100L * day, "", 0));
            }
            return rewards;
        }
    }

    private static final class FakeSender implements CheckInRewardSender {
        final Set<UUID> usedKeys = new HashSet<>();
        int mailsCreated;
        boolean down;

        @Override
        public Delivery send(long characterId, CheckInReward reward, UUID idempotencyKey) {
            if (down) {
                return Delivery.FAILED;
            }
            if (!usedKeys.add(idempotencyKey)) {
                return new Delivery(true, 0L);
            }
            mailsCreated++;
            return new Delivery(true, mailsCreated);
        }
    }
}
