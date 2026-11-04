package dev.kage.inventory.api;

import java.time.Instant;
import java.util.List;

import dev.kage.inventory.domain.JunkSalePort;

/** Bán nhanh đồ rác (ADR-170). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class JunkSaleFacade {

    /** Composition không có bán rác (test). */
    public static final JunkSaleFacade DISABLED = new JunkSaleFacade(new JunkSalePort() {
        @Override
        public List<Line> preview(long characterId) {
            return List.of();
        }

        @Override
        public Sold sellAll(long characterId, Instant at) {
            return new Sold(List.of(), 0L, false);
        }
    });

    private final JunkSalePort port;

    /** Dựng ở composition root. */
    public JunkSaleFacade(JunkSalePort port) {
        this.port = port;
    }

    /** Những gì sẽ bán. */
    public List<JunkSalePort.Line> preview(long characterId) {
        return port.preview(characterId);
    }

    /** Bán hết. */
    public JunkSalePort.Sold sellAll(long characterId, Instant at) {
        return port.sellAll(characterId, at);
    }
}
