package dev.kage.character.api;

import dev.kage.character.domain.TribulationPort;
import dev.kage.character.domain.TribulationRules;

/** Thiên Kiếp (ADR-126). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class TribulationFacade {

    /** Composition không có DB (test) — không ai bị chặn cấp, không kiếp nào. */
    public static final TribulationFacade DISABLED = new TribulationFacade(new TribulationPort() {
        @Override
        public int passed(long characterId) {
            return TribulationRules.TRIBULATIONS;
        }

        @Override
        public boolean advance(long characterId, int expectedPassed) {
            return false;
        }

        @Override
        public void penalize(long characterId, long amount, long floorExp) {
            // tắt
        }
    });

    private final TribulationPort port;

    /** Dựng ở composition root. */
    public TribulationFacade(TribulationPort port) {
        this.port = port;
    }

    /** Số kiếp đã qua. */
    public int passed(long characterId) {
        return port.passed(characterId);
    }

    /** Trần cấp hiện tại của nhân vật. */
    public int levelCap(long characterId) {
        return TribulationRules.levelCap(port.passed(characterId));
    }

    /** Qua kiếp (compare-and-set). */
    public boolean advance(long characterId, int expectedPassed) {
        return port.advance(characterId, expectedPassed);
    }

    /** Thua kiếp ở trần {@code cap}: trừ {@value TribulationRules#FAIL_EXP_PERCENT}% EXP cấp kế, sàn là đầu cấp. */
    public void penalize(long characterId, int cap) {
        port.penalize(characterId, TribulationRules.failPenalty(cap), TribulationRules.expToReach(cap));
    }
}
