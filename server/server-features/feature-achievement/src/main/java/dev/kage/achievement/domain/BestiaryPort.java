package dev.kage.achievement.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Sổ sưu tầm yêu thú (ADR-108) — hiện thực ở server-database (V90). */
public interface BestiaryPort {

    /** Chỉ số một trang thưởng. */
    enum BonusStat {
        ATK,
        DEF,
        RES,
        HP
    }

    /** Một trang sổ (tham số, Điều 27). */
    record Entry(String monsterCode, int killsRequired, BonusStat bonusStat, int bonusValue, String loreKey) {}

    /** Tiến độ một trang của một nhân vật. */
    record Progress(Entry entry, int kills, boolean unlocked) {}

    /** Tổng chỉ số cộng phẳng từ mọi trang đã mở. */
    record Bonus(int atk, int def, int res, int hp) {

        /** Không trang nào. */
        public static final Bonus NONE = new Bonus(0, 0, 0, 0);
    }

    /** Không có sổ nào (test). */
    BestiaryPort NONE = new BestiaryPort() {
        @Override
        public Optional<Entry> recordKill(long characterId, String monsterCode, Instant at) {
            return Optional.empty();
        }

        @Override
        public List<Progress> progressOf(long characterId) {
            return List.of();
        }

        @Override
        public Bonus bonusOf(long characterId) {
            return Bonus.NONE;
        }
    };

    /**
     * Cộng một lần hạ loài {@code monsterCode} (loài không có trong sổ: không làm gì).
     *
     * @return trang vừa MỞ bởi đúng lần hạ này; rỗng nếu chưa đủ, đã mở từ trước, hoặc loài không có trang
     */
    Optional<Entry> recordKill(long characterId, String monsterCode, Instant at);

    /** Mọi trang theo thứ tự sổ, kèm tiến độ của nhân vật. */
    List<Progress> progressOf(long characterId);

    /** Tổng thưởng của các trang đã mở. */
    Bonus bonusOf(long characterId);
}
