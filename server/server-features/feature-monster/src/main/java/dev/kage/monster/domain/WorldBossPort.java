package dev.kage.monster.domain;

import java.util.List;
import java.util.Map;

/** Lịch, thưởng và sổ sát thương tích luỹ của boss thế giới (ADR-081) — hiện thực ở server-database. */
public interface WorldBossPort {

    /** Mọi lịch boss thế giới (tham số — đọc một lần). */
    List<WorldBossSchedule> schedules();

    /** Bậc thưởng của boss {@code monsterCode}, theo {@code rank_from}. */
    List<WorldBossReward> rewards(String monsterCode);

    /** Cộng dồn sát thương vào {@code world_boss_damage} (một transaction). */
    void addDamage(Map<Long, Long> damageByCharacter);

    /**
     * Một dòng Bia Anh Danh "sát thương boss" (ADR-081 D5).
     *
     * @param characterId nhân vật
     * @param totalDamage tổng sát thương lên mọi boss thế giới
     */
    record DamageRank(long characterId, long totalDamage) {}

    /** {@code limit} người gây nhiều sát thương nhất, giảm dần rồi id tăng dần. */
    List<DamageRank> topDamage(int limit);
}
