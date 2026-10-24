package dev.kage.world.api;

import java.util.List;

import dev.kage.world.domain.HerbNode;
import dev.kage.world.domain.HerbNodePort;

/** Bụi linh thảo (ADR-098) — đọc-thuần, nạp một lần lúc khởi động như {@link WorldGeometryFacade}. */
public final class HerbNodeFacade {

    /** Composition không có bụi nào (test). */
    public static final HerbNodeFacade DISABLED = new HerbNodeFacade(List::of);

    private final HerbNodePort port;

    /** Dựng ở composition root. */
    public HerbNodeFacade(HerbNodePort port) {
        this.port = port;
    }

    /** Mọi bụi của world chính. */
    public List<HerbNode> listHerbNodes() {
        return port.listHerbNodes();
    }
}
