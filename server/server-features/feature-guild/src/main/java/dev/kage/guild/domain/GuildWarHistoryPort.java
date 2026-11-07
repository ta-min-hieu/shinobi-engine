package dev.kage.guild.domain;

import java.time.Instant;
import java.util.List;

/** Lịch sử bang chiến (ADR-208) — các trận ĐÃ CHỐT của bang một nhân vật đang ở, nhìn từ phía bang đó. */
public interface GuildWarHistoryPort {

    /** Kết quả một trận nhìn từ phía bang mình. */
    enum Result {
        WIN,
        LOSS,
        DRAW
    }

    /**
     * Một trận đã chốt.
     *
     * @param opponentName tên bang đối địch lúc hỏi (bang đã đổi tên thì là tên mới)
     * @param declaredByUs bang mình là bên tuyên chiến
     */
    record PastWar(
            String opponentName, boolean declaredByUs, int ourKills, int theirKills, Result result, Instant endedAt) {}

    /** Tối đa {@code limit} trận đã chốt gần nhất của bang {@code characterId}; rỗng nếu không ở bang nào. */
    List<PastWar> recentOf(long characterId, int limit);
}
