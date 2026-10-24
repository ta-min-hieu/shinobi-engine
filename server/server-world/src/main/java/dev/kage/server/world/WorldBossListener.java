package dev.kage.server.world;

import java.util.Map;

/**
 * Nghe boss thế giới chết (ADR-081 D3) — server-world chỉ báo SỰ KIỆN kèm sổ sát thương; thưởng, xếp hạng, thông báo
 * là việc của gateway. Gọi TRÊN tick thread: hiện thực phải trả về ngay.
 */
@FunctionalInterface
public interface WorldBossListener {

    /** Không ai nghe (mặc định, world hầm ngục/đấu trường). */
    WorldBossListener NONE = (monsterCode, damageByCharacter) -> {};

    /**
     * Một boss thế giới vừa chết.
     *
     * @param monsterCode mã boss
     * @param damageByCharacter tổng sát thương mỗi nhân vật gây ra lên con boss này (bản sao bất biến)
     */
    void onWorldBossDefeated(String monsterCode, Map<Long, Long> damageByCharacter);
}
