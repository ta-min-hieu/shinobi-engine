package dev.kage.monster.api;

import java.util.List;
import java.util.Map;

import dev.kage.monster.domain.WorldBossPort;
import dev.kage.monster.domain.WorldBossReward;
import dev.kage.monster.domain.WorldBossSchedule;

/** API boss thế giới (ADR-081). Chạm DB — chỉ gọi trên luồng nền/Virtual Thread. */
public final class WorldBossFacade {

    /** Composition không có boss thế giới (test). */
    public static final WorldBossFacade DISABLED = new WorldBossFacade(new WorldBossPort() {
        @Override
        public List<WorldBossSchedule> schedules() {
            return List.of();
        }

        @Override
        public List<WorldBossReward> rewards(String monsterCode) {
            return List.of();
        }

        @Override
        public void addDamage(Map<Long, Long> damageByCharacter) {
            // không có DB — không ghi gì
        }

        @Override
        public List<DamageRank> topDamage(int limit) {
            return List.of();
        }
    });

    private final WorldBossPort port;

    /** Dựng ở composition root. */
    public WorldBossFacade(WorldBossPort port) {
        this.port = port;
    }

    /** Mọi lịch boss thế giới. */
    public List<WorldBossSchedule> schedules() {
        return port.schedules();
    }

    /** Bậc thưởng theo hạng của một boss. */
    public List<WorldBossReward> rewards(String monsterCode) {
        return port.rewards(monsterCode);
    }

    /** Cộng sổ của một trận vào tổng tích luỹ. */
    public void addDamage(Map<Long, Long> damageByCharacter) {
        port.addDamage(damageByCharacter);
    }

    /** Bia Anh Danh "sát thương boss". */
    public List<WorldBossPort.DamageRank> topDamage(int limit) {
        return port.topDamage(limit);
    }
}
