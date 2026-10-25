package dev.kage.inventory.api;

import java.util.List;

import dev.kage.inventory.domain.EquipmentBonus;
import dev.kage.inventory.domain.RefinementPort;

/** API tẩy luyện pháp khí (ADR-111). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class RefinementFacade {

    /** Composition không có tẩy luyện (test) — không món nào, không chỉ số nào. */
    public static final RefinementFacade DISABLED = new RefinementFacade(new RefinementPort() {
        @Override
        public List<WornItem> wornItems(long characterId) {
            return List.of();
        }

        @Override
        public EquipmentBonus wornBonusOf(long characterId) {
            return EquipmentBonus.NONE;
        }

        @Override
        public Result refine(long characterId, long itemId, int lockedLine) {
            return Result.refused(Outcome.NOT_FOUND);
        }
    });

    private final RefinementPort port;

    /** Dựng ở composition root. */
    public RefinementFacade(RefinementPort port) {
        this.port = port;
    }

    /** Các món đang mặc ở ô có chỉ số kèm dòng tẩy. */
    public List<RefinementPort.WornItem> wornItems(long characterId) {
        return port.wornItems(characterId);
    }

    /** Cộng phẳng vào ống chỉ số trang bị (D3). */
    public EquipmentBonus wornBonusOf(long characterId) {
        return port.wornBonusOf(characterId);
    }

    /** Tẩy món của chính mình — {@code characterId} từ session. */
    public RefinementPort.Result refine(long characterId, long itemId, int lockedLine) {
        return port.refine(characterId, itemId, lockedLine);
    }
}
