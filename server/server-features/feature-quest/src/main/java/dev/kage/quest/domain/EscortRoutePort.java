package dev.kage.quest.domain;

import java.util.Optional;

/** Tuyến hộ tống của một quest (ADR-114 D1, bảng {@code escort_route}) — hiện thực ở server-database. */
public interface EscortRoutePort {

    /** Không quest nào có tuyến hộ tống (composition test). */
    EscortRoutePort NONE = questCode -> Optional.empty();

    /**
     * Một tuyến: đoàn đi từ {@code startX} tới {@code endX}.
     *
     * @param speedMps tốc độ khi người hộ tống đứng đủ gần
     */
    record Route(String questCode, float startX, float endX, int maxHp, float speedMps) {}

    Optional<Route> routeOf(String questCode);
}
