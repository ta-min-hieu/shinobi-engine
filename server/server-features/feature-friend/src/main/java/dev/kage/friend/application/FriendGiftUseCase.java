package dev.kage.friend.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import dev.kage.friend.domain.FriendGiftOutcome;
import dev.kage.friend.domain.FriendGiftPolicy;
import dev.kage.friend.domain.FriendGiftPort;

/** Tặng quà bạn bè (ADR-084). "Hôm nay" theo giờ Việt Nam — cùng mốc ngày với điểm danh. */
public final class FriendGiftUseCase {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Kết quả kèm mốc thân mật vừa chạm (để gateway gửi thư thưởng).
     *
     * @param intimacy thân mật sau lần tặng
     * @param milestone mốc vừa vượt; rỗng nếu không
     */
    public record Result(FriendGiftOutcome outcome, int intimacy, Optional<Integer> milestone) {}

    private final FriendGiftPort port;
    private final Clock clock;

    public FriendGiftUseCase(FriendGiftPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    public Result gift(long senderId, long recipientId) {
        if (senderId == recipientId) {
            return new Result(FriendGiftOutcome.CANNOT_GIFT_SELF, 0, Optional.empty());
        }
        FriendGiftPort.GiftResult result = port.gift(senderId, recipientId, LocalDate.now(clock.withZone(VIETNAM)));
        Optional<Integer> milestone = result.outcome() == FriendGiftOutcome.OK
                ? FriendGiftPolicy.milestoneCrossed(result.intimacyBefore(), result.intimacyAfter())
                : Optional.empty();
        return new Result(result.outcome(), result.intimacyAfter(), milestone);
    }

    public Map<Long, Integer> intimacyOf(long characterId) {
        return port.intimacyOf(characterId);
    }
}
