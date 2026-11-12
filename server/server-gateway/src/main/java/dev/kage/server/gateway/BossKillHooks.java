package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.List;

import dev.kage.monster.api.BossKillLogFacade;
import dev.kage.monster.domain.BossKillLogPort;
import dev.kage.monster.domain.WorldBossRanking;

/**
 * Lịch sử boss đã hạ (ADR-296) — chỗ hai đường hạ boss (boss thế giới: {@link WorldBossScheduler}; boss Bí Cảnh:
 * {@link DungeonInstanceRegistry}) ghi một dòng cho mỗi người tham gia. Luôn gọi trên luồng nền (đã rời tick thread).
 * Chưa nối facade ⇒ không ghi gì (composition test).
 */
final class BossKillHooks {

    private volatile BossKillLogFacade log = BossKillLogFacade.DISABLED;

    /** Nối ở composition root. */
    void bind(BossKillLogFacade facade) {
        this.log = facade;
    }

    /** Boss thế giới: mỗi người trong bảng xếp hạng một dòng (hạng + sát thương). */
    void worldBoss(String bossCode, List<WorldBossRanking.Entry> ranked) {
        List<BossKillLogPort.Kill> kills = new ArrayList<>(ranked.size());
        for (WorldBossRanking.Entry entry : ranked) {
            kills.add(new BossKillLogPort.Kill(
                    entry.characterId(), bossCode, BossKillLogPort.Kind.WORLD, entry.rank(), entry.damage()));
        }
        log.record(kills);
    }

    /** Boss Bí Cảnh: mỗi thành viên phòng một dòng (không xếp hạng). */
    void dungeonBoss(String bossCode, List<Long> members) {
        log.record(members.stream()
                .map(id -> new BossKillLogPort.Kill(id, bossCode, BossKillLogPort.Kind.DUNGEON, 0, 0L))
                .toList());
    }

    /** Lịch sử của một người (Virtual Thread). */
    List<BossKillLogPort.Entry> recent(long characterId) {
        return log.recent(characterId);
    }
}
