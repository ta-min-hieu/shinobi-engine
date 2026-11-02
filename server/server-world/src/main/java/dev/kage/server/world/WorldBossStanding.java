package dev.kage.server.world;

import java.util.Map;

/**
 * Ảnh chụp sổ sát thương của MỘT boss thế giới đang sống (ADR-157) — bản sao bất biến, dựng trên tick thread rồi
 * giao ra ngoài; không ai đọc lại sổ thật ngoài tick thread.
 *
 * @param damageByCharacter characterId → sát thương đã gây
 */
public record WorldBossStanding(String bossCode, Map<Long, Long> damageByCharacter) {

    public WorldBossStanding {
        damageByCharacter = Map.copyOf(damageByCharacter);
    }
}
