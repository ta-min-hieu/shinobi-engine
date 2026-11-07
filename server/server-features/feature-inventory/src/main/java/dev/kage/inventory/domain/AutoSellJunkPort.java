package dev.kage.inventory.domain;

import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

/** Lọc nhặt đồ (ADR-204) — công tắc {@code character.auto_sell_junk} + bán đồ rác vừa rơi. Chạm DB. */
public interface AutoSellJunkPort {

    boolean enabled(long characterId);

    void setEnabled(long characterId, boolean enabled);

    /** Mẫu vật phẩm đánh dấu rác ({@code item_template.junk}). */
    Set<Long> junkTemplates();

    /**
     * Cộng giá thu của {@code stacks} (giá bán lại ở Tàng Bảo Các × số lượng) cho {@code characterId}, một transaction,
     * có audit và sổ vàng.
     *
     * @return số vàng đã cộng; rỗng nếu vàng sẽ vượt trần (không đổi gì — người gọi cấp món như thường)
     */
    OptionalLong sell(long characterId, List<ItemStack> stacks);
}
