package dev.kage.quest.domain;

/**
 * Cộng vàng thưởng quest ({@code ctx.give_gold}, ADR-055 D4) trong CÙNG transaction lĩnh thưởng — Port
 * riêng của feature-quest, cùng khuôn {@link ExpPort}. Adapter thật đi qua {@code GoldLedger} (điểm
 * duy nhất ghi {@code character_stat.gold}, cũng là chỗ phát {@code GoldChangedEvent}).
 *
 * <p>Chạm trần vàng thì adapter NÉM (rollback cả lượt lĩnh) — gateway trả {@code error.gold_cap_reached}
 * như mọi đường cộng vàng khác.
 */
public interface QuestGoldPort {

    /** Port cho composition KHÔNG trao vàng (fake ở test cũ) — gọi tới là lỗi lập trình, ồn ào. */
    QuestGoldPort UNSUPPORTED = (tx, characterId, amount) -> {
        throw new UnsupportedOperationException("QuestGoldPort chưa được nối — composition này không trao vàng");
    };

    void creditGold(Transaction tx, long characterId, long amount);
}
