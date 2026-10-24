package dev.kage.server.gateway;

import java.util.List;
import java.util.Optional;
import java.util.function.LongFunction;

import dev.kage.friend.api.DaoLuFacade;

/** ADR-094 — thưởng EXP đánh quái khi đạo lữ cùng nhóm; cùng khuôn {@link GuildSkillEffects}. */
final class DaoLuEffects {

    private DaoLuEffects() {}

    /**
     * EXP sau thưởng đạo lữ: +10% khi đạo lữ đang CÙNG NHÓM. Nhóm chỉ được tra (chạm DB) khi có đạo lữ — đa số cú hạ
     * quái dừng ở bộ nhớ đệm đạo lữ.
     *
     * @param partyMembersOf mọi thành viên nhóm của một nhân vật, rỗng nếu không có nhóm
     */
    static long boostedExp(DaoLuFacade daoLu, long characterId, long amount, LongFunction<List<Long>> partyMembersOf) {
        Optional<Long> partner = daoLu.partnerOf(characterId);
        if (partner.isEmpty()) {
            return amount;
        }
        return daoLu.boostedExp(amount, partyMembersOf.apply(characterId).contains(partner.get()));
    }
}
