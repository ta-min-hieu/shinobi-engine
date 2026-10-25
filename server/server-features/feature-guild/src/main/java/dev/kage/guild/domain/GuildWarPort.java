package dev.kage.guild.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Bang chiến (ADR-102) — hiện thực ở server-database (V88). */
public interface GuildWarPort {

    /**
     * Một lượt tuyên chiến.
     *
     * @param leaderId bang chủ tuyên (lấy từ session)
     * @param targetGuildName tên bang bị tuyên
     * @param at lúc tuyên
     */
    record Declaration(long leaderId, String targetGuildName, Instant at) {}

    /** Kết quả tuyên; {@code war} có mặt khi và chỉ khi {@code outcome == OK}. */
    record Declared(GuildWarOutcome outcome, Optional<GuildWar> war) {}

    /** Một thành viên của bang đang trong giờ chiến. */
    record Combatant(long characterId, long guildId) {}

    /** Thư thưởng còn chờ gửi. */
    record Payout(long warId, long characterId, String guildName) {}

    /**
     * Tuyên chiến — nguyên tử: khoá hai dòng {@code guild}, kiểm luật, trừ {@link GuildWarRules#DECLARE_COST} vàng của
     * bang chủ, ghi trận bắt đầu sau {@link GuildWarRules#PREPARATION}.
     */
    Declared declare(Declaration declaration);

    /** Mọi trận chưa chốt (đang chờ hoặc đang đánh), bắt đầu sớm nhất trước. */
    List<GuildWar> unsettled();

    /** Thành viên của mọi bang đang TRONG GIỜ chiến tại {@code now}. */
    List<Combatant> combatants(Instant now);

    /**
     * {@code killerId} vừa hạ {@code victimId}: +1 cho bang người hạ nếu hai người thuộc hai bang đang trong giờ chiến
     * với nhau tại {@code at}.
     *
     * @return {@code true} nếu lần hạ được tính
     */
    boolean recordKill(long killerId, long victimId, Instant at);

    /**
     * Chốt trận — nguyên tử và chỉ MỘT lần (khoá dòng trận): bang nhiều điểm hơn thắng, thành viên của nó vào hàng đợi
     * thưởng.
     *
     * @return trận như lúc chốt nếu lần gọi NÀY là lần chốt; rỗng nếu đã chốt rồi hoặc chưa hết giờ
     */
    Optional<GuildWar> settle(long warId, Instant now);

    /** Thư thưởng còn chờ gửi. */
    List<Payout> pendingPayouts();

    /** Xoá một mục khỏi hàng đợi thưởng. */
    void markPaid(Payout payout);
}
