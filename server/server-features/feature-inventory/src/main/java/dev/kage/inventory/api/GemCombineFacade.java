package dev.kage.inventory.api;

import java.util.List;

import dev.kage.inventory.domain.GemCombinePort;

/** Ghép Linh Ngọc (ADR-161). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class GemCombineFacade {

    /** Composition không có ghép ngọc (test). */
    public static final GemCombineFacade DISABLED = new GemCombineFacade(new GemCombinePort() {
        @Override
        public List<Recipe> recipes(long characterId) {
            return List.of();
        }

        @Override
        public Outcome combine(long characterId, long sourceItemTemplateId) {
            return Outcome.UNKNOWN_RECIPE;
        }
    });

    private final GemCombinePort port;

    /** Dựng ở composition root. */
    public GemCombineFacade(GemCombinePort port) {
        this.port = port;
    }

    /** Bảng công thức kèm số viên người đó có. */
    public List<GemCombinePort.Recipe> recipes(long characterId) {
        return port.recipes(characterId);
    }

    /** Ghép một lần. */
    public GemCombinePort.Outcome combine(long characterId, long sourceItemTemplateId) {
        return port.combine(characterId, sourceItemTemplateId);
    }
}
