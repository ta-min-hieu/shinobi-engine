package dev.kage.inventory.api;

import java.util.List;

import dev.kage.inventory.domain.GearRankingPort;
import dev.kage.inventory.domain.GearRankingPort.Ranked;

/**
 * API xếp hạng linh thú (ADR-439) và cường hoá (ADR-440) — đọc {@code pet_state} / {@code inventory_item}. Chạm DB —
 * Virtual Thread.
 */
public final class GearRankingFacade {

    /** Số dòng mỗi bảng. */
    public static final int TOP = 10;

    /** Bảng rỗng (composition test). */
    public static final GearRankingFacade DISABLED = new GearRankingFacade(new GearRankingPort() {
        @Override
        public List<Ranked> topPets(int limit) {
            return List.of();
        }

        @Override
        public List<Ranked> topEnhance(int limit) {
            return List.of();
        }
    });

    private final GearRankingPort port;

    /** Dựng ở composition root. */
    public GearRankingFacade(GearRankingPort port) {
        this.port = port;
    }

    /** Top {@value #TOP}. */
    public List<GearRankingPort.Ranked> topPets() {
        return port.topPets(TOP);
    }

    /** Top {@value #TOP}. */
    public List<GearRankingPort.Ranked> topEnhance() {
        return port.topEnhance(TOP);
    }
}
