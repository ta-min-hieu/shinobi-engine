package dev.kage.world.domain;

import java.util.List;

/** Đọc {@code herb_node} (ADR-098) — adapter thật ở {@code server-database}. */
public interface HerbNodePort {

    /** Mọi bụi linh thảo của world chính, theo id. */
    List<HerbNode> listHerbNodes();
}
