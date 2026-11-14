package dev.kage.pvp.domain;

import java.util.List;

/** Báo cừu nhân online (ADR-426) — đọc ngược {@code pvp_enemy} + công tắc {@code enemy_alert_off}. */
public interface EnemyAlertPort {

    /** Những người đang ghi {@code enemyId} là cừu nhân và KHÔNG tắt báo. */
    List<Long> watchersOf(long enemyId);

    /** Đã tắt báo chưa. */
    boolean muted(long characterId);

    /** Tắt ({@code true}) / bật lại báo. */
    void setMuted(long characterId, boolean muted);
}
