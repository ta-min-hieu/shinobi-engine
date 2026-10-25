package dev.kage.server.gateway;

import java.util.List;
import java.util.function.LongFunction;

import dev.kage.friend.api.SwornFacade;

/** ADR-110 — thưởng EXP đánh quái khi có huynh đệ kết nghĩa cùng nhóm; cùng khuôn {@link DaoLuEffects}. */
final class SwornEffects {

    private SwornEffects() {}

    /**
     * EXP sau thưởng kết nghĩa: +5% khi ít nhất một huynh đệ CÙNG NHÓM. Nhóm chỉ được tra (chạm DB) khi có kết nghĩa.
     *
     * @param partyMembersOf mọi thành viên nhóm của một nhân vật, rỗng nếu không có nhóm
     */
    static long boostedExp(SwornFacade sworn, long characterId, long amount, LongFunction<List<Long>> partyMembersOf) {
        if (sworn.membersOf(characterId).isEmpty()) {
            return amount;
        }
        return sworn.boostedExp(amount, sworn.hasBrotherIn(characterId, partyMembersOf.apply(characterId)));
    }
}
