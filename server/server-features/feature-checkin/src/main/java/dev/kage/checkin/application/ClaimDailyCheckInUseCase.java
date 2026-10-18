package dev.kage.checkin.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.kage.checkin.domain.CheckInCycle;
import dev.kage.checkin.domain.CheckInPort;
import dev.kage.checkin.domain.CheckInReward;
import dev.kage.checkin.domain.CheckInRewardSender;
import dev.kage.checkin.domain.CheckInState;

/**
 * Điểm danh của hôm nay (ADR-060 D2): gửi quà TRƯỚC, rồi compare-and-set trạng thái.
 *
 * <p><b>Vì sao gửi trước.</b> Thư và trạng thái điểm danh thuộc hai feature, không chung transaction.
 * Khoá chống trùng của thư suy ra từ (nhân vật, ngày) — hai lượt cùng đọc một trạng thái thì cùng ra một
 * khoá, nên chỉ MỘT lá thư được tạo dù hai kết nối cùng vào world; tiến trình chết giữa hai bước thì lần
 * vào sau trong cùng ngày gửi lại đúng khoá đó (trùng ⇒ không thư mới) rồi mới ghi trạng thái. Thứ tự
 * ngược lại (ghi trạng thái trước) thì chết giữa hai bước là MẤT quà vĩnh viễn.
 */
public final class ClaimDailyCheckInUseCase {

    private final CheckInPort port;
    private final CheckInRewardSender sender;
    private final Clock clock;

    public ClaimDailyCheckInUseCase(CheckInPort port, CheckInRewardSender sender, Clock clock) {
        this.port = port;
        this.sender = sender;
        this.clock = clock;
    }

    /** Kết quả một lượt. */
    public sealed interface Result {
        /** Hôm nay đã nhận (lượt này không làm gì). */
        record AlreadyClaimed() implements Result {}

        /**
         * Vừa nhận quà ngày {@code day}.
         *
         * @param mailId thư vừa tạo; 0 nếu thư đã có từ một lượt trước (không cần báo thư mới)
         */
        record Claimed(int day, long mailId) implements Result {}

        /** Gửi quà không được — trạng thái giữ nguyên, lần vào sau thử lại. */
        record NotDelivered() implements Result {}
    }

    public Result execute(long characterId) {
        LocalDate today = CheckInCycle.today(clock);
        CheckInState state = port.find(characterId).orElse(CheckInState.NEVER);
        if (CheckInCycle.claimedOn(state, today)) {
            return new Result.AlreadyClaimed();
        }
        int day = CheckInCycle.nextDay(state);
        CheckInReward reward = rewardOf(day);
        CheckInRewardSender.Delivery delivery = sender.send(characterId, reward, keyOf(characterId, today));
        if (!delivery.delivered()) {
            return new Result.NotDelivered();
        }
        if (!port.advance(characterId, state, new CheckInState(day, today))) {
            // Lượt khác (cùng khoá thư) đã ghi trước — quà vẫn chỉ một lá.
            return new Result.AlreadyClaimed();
        }
        return new Result.Claimed(day, delivery.mailId());
    }

    private CheckInReward rewardOf(int day) {
        List<CheckInReward> rewards = port.rewards();
        for (CheckInReward reward : rewards) {
            if (reward.day() == day) {
                return reward;
            }
        }
        throw new IllegalStateException("Bảng daily_check_in_reward thiếu ngày " + day);
    }

    /** Khoá chống trùng của thư quà — tất định theo (nhân vật, ngày). */
    static UUID keyOf(long characterId, LocalDate day) {
        return UUID.nameUUIDFromBytes(("check-in:" + characterId + ":" + day).getBytes(StandardCharsets.UTF_8));
    }
}
