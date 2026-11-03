package dev.kage.inventory.api;

import java.util.Set;

import dev.kage.inventory.domain.ItemLockPort;

/** Khoá vật phẩm (ADR-159). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class ItemLockFacade {

    /** Composition không có khoá vật phẩm (test): không món nào khoá, bật khoá thì "không thấy món". */
    public static final ItemLockFacade DISABLED = new ItemLockFacade(new ItemLockPort() {
        @Override
        public Outcome setLocked(long characterId, long itemId, boolean locked) {
            return Outcome.NOT_FOUND;
        }

        @Override
        public Set<Long> lockedItems(long characterId) {
            return Set.of();
        }
    });

    private final ItemLockPort port;

    /** Dựng ở composition root. */
    public ItemLockFacade(ItemLockPort port) {
        this.port = port;
    }

    /** Bật/tắt khoá một món trong túi của chính nhân vật. */
    public ItemLockPort.Outcome setLocked(long characterId, long itemId, boolean locked) {
        return port.setLocked(characterId, itemId, locked);
    }

    /** Các món đang khoá — để danh sách túi đồ gắn cờ. */
    public Set<Long> lockedItems(long characterId) {
        return port.lockedItems(characterId);
    }
}
