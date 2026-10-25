package dev.kage.quest.api;

import java.util.Optional;

import dev.kage.quest.domain.EscortRoutePort;

/** Tra tuyến hộ tống theo mã quest (ADR-114). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class EscortRouteFacade {

    /** Composition không có hộ tống (test). */
    public static final EscortRouteFacade DISABLED = new EscortRouteFacade(EscortRoutePort.NONE);

    private final EscortRoutePort port;

    /** Dựng ở composition root. */
    public EscortRouteFacade(EscortRoutePort port) {
        this.port = port;
    }

    /** Tuyến của quest {@code questCode}; rỗng nếu đó không phải quest hộ tống. */
    public Optional<EscortRoutePort.Route> routeOf(String questCode) {
        return port.routeOf(questCode);
    }
}
