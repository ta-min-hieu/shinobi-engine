package dev.kage.world.api;

import dev.kage.world.domain.ConsumablePort;

/** Tiêu / hoàn một vật phẩm theo mã (lô 23). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class ConsumableFacade {

    /** Hồi Hồn Đan — đứng dậy tại chỗ (ADR-117). */
    public static final String REVIVE_PILL = "revive_pill";

    /** Composition không có DB (test) — không có gì để tiêu. */
    public static final ConsumableFacade DISABLED = new ConsumableFacade(new ConsumablePort() {
        @Override
        public boolean consume(long characterId, String itemCode) {
            return false;
        }

        @Override
        public void refund(long characterId, String itemCode) {
            // tắt — không có gì để hoàn
        }
    });

    private final ConsumablePort port;

    /** Dựng ở composition root. */
    public ConsumableFacade(ConsumablePort port) {
        this.port = port;
    }

    /** Trừ đúng một cái; {@code false} nếu không có. */
    public boolean consume(long characterId, String itemCode) {
        return port.consume(characterId, itemCode);
    }

    /** Trả lại một cái. */
    public void refund(long characterId, String itemCode) {
        port.refund(characterId, itemCode);
    }
}
