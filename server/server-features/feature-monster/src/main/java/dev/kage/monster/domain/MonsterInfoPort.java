package dev.kage.monster.domain;

import java.util.List;
import java.util.Optional;

/** Thông tin công khai của một loại quái (ADR-255): chỉ số cơ bản + bảng rơi đồ. Chỉ đọc. */
public interface MonsterInfoPort {

    /** Một dòng bảng rơi. */
    record Drop(long itemTemplateId, int chancePercent, int minQuantity, int maxQuantity) {}

    /** Chỉ số + rơi đồ. */
    record Profile(
            String code,
            String nameKey,
            int level,
            int maxHp,
            int atk,
            int def,
            long expReward,
            boolean boss,
            List<Drop> drops) {}

    /** Theo mã quái; rỗng nếu không có. */
    Optional<Profile> info(String code);
}
