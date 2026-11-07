package dev.kage.inventory.api;

import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

import dev.kage.inventory.domain.AutoSellJunkPort;
import dev.kage.inventory.domain.ItemStack;

/** Lọc nhặt đồ (ADR-204). Chạm DB — Virtual Thread. */
public final class AutoSellJunkFacade {

    /** Composition không có tính năng này (test): luôn tắt, không bán gì. */
    public static final AutoSellJunkFacade DISABLED = new AutoSellJunkFacade(new AutoSellJunkPort() {
        @Override
        public boolean enabled(long characterId) {
            return false;
        }

        @Override
        public void setEnabled(long characterId, boolean enabled) {
            // không lưu
        }

        @Override
        public Set<Long> junkTemplates() {
            return Set.of();
        }

        @Override
        public OptionalLong sell(long characterId, List<ItemStack> stacks) {
            return OptionalLong.empty();
        }
    });

    private final AutoSellJunkPort port;

    /** Dựng ở composition root. */
    public AutoSellJunkFacade(AutoSellJunkPort port) {
        this.port = port;
    }

    /** Công tắc của {@code characterId}. */
    public boolean enabled(long characterId) {
        return port.enabled(characterId);
    }

    /** Bật / tắt. */
    public void setEnabled(long characterId, boolean enabled) {
        port.setEnabled(characterId, enabled);
    }

    /** Mẫu vật phẩm rác — đọc một lần lúc khởi động. */
    public Set<Long> junkTemplates() {
        return port.junkTemplates();
    }

    /** Bán các chồng rác vừa rơi; rỗng = không bán được (vàng chạm trần), người gọi cấp món như thường. */
    public OptionalLong sell(long characterId, List<ItemStack> stacks) {
        return stacks.isEmpty() ? OptionalLong.of(0L) : port.sell(characterId, stacks);
    }
}
