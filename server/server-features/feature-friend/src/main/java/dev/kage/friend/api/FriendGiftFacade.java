package dev.kage.friend.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

import dev.kage.friend.application.FriendGiftUseCase;
import dev.kage.friend.domain.FriendGiftOutcome;
import dev.kage.friend.domain.FriendGiftPort;

/** API tặng quà bạn bè (ADR-084). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class FriendGiftFacade {

    /** Composition không có tặng quà (test) — không ai là bạn để tặng. */
    public static final FriendGiftFacade DISABLED = new FriendGiftFacade(new FriendGiftUseCase(
            new FriendGiftPort() {
                @Override
                public GiftResult gift(long senderId, long recipientId, LocalDate today) {
                    return GiftResult.refused(FriendGiftOutcome.NOT_FRIENDS);
                }

                @Override
                public Map<Long, Integer> intimacyOf(long characterId) {
                    return Map.of();
                }
            },
            Clock.systemUTC()));

    private final FriendGiftUseCase useCase;

    /** Dựng ở composition root. */
    public FriendGiftFacade(FriendGiftUseCase useCase) {
        this.useCase = useCase;
    }

    /** Tặng một món quà — {@code senderId} từ session. */
    public FriendGiftUseCase.Result gift(long senderId, long recipientId) {
        return useCase.gift(senderId, recipientId);
    }

    /** Thân mật với từng bạn. */
    public Map<Long, Integer> intimacyOf(long characterId) {
        return useCase.intimacyOf(characterId);
    }
}
