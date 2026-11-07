package dev.kage.pvp.domain;

import java.time.Instant;
import java.util.List;

/** Danh sách cừu nhân (ADR-194) — bảng {@code pvp_enemy}. Chạm DB. */
public interface EnemyListPort {

    /**
     * {@code killerId} vừa hạ {@code victimId}: cộng một lần vào dòng (tạo nếu chưa có), rồi chỉ giữ {@code maxEntries}
     * dòng gần nhất của người bị hạ.
     */
    void recordKill(long victimId, long killerId, Instant at, int maxEntries);

    /** Cừu nhân của {@code characterId}, mới nhất trước. */
    List<Enemy> list(long characterId);

    /** Xoá một dòng theo tên cừu nhân; {@code false} nếu không có dòng đó. */
    boolean remove(long characterId, String enemyName);

    /** Một cừu nhân. */
    record Enemy(long characterId, String name, int killCount, Instant lastKilledAt) {}
}
