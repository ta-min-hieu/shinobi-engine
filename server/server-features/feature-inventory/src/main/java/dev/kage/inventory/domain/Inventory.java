package dev.kage.inventory.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Túi đồ của một nhân vật. Aggregate root — mọi thay đổi item đi qua đây.
 *
 * <p>Domain thuần: không import infrastructure, test được không cần mock (Điều 18).
 */
public final class Inventory {

    private final long characterId;
    private final int capacity;
    private final List<ItemStack> items;

    public Inventory(long characterId, int capacity, List<ItemStack> items) {
        this.characterId = characterId;
        this.capacity = capacity;
        this.items = new ArrayList<>(items);
    }

    /** Quy tắc "túi đầy" là NGHIỆP VỤ → thuộc domain, không thuộc UseCase. */
    public AddResult add(ItemStack stack) {
        if (items.size() >= capacity) {
            return AddResult.full(); // túi đầy là trạng thái bình thường → không ném exception
        }
        items.add(stack);
        return AddResult.added();
    }

    public boolean isFull() {
        return items.size() >= capacity;
    }

    /** Tìm item theo {@code ItemStack.id} THẬT (không phải vị trí) — dùng khi mặc đồ (Phase 5). */
    public Optional<ItemStack> findItem(long itemId) {
        return items.stream().filter(stack -> stack.id() == itemId).findFirst();
    }

    /**
     * Tìm 1 stack theo {@code itemTemplateId} (dòng ĐẦU TIÊN khớp nếu có nhiều dòng cùng loại) —
     * dùng để tìm "Bảo Vệ" chống rớt cấp trong túi mà không cần biết trước {@code ItemStack.id}
     * thật (Phase 5 §7.1).
     */
    public Optional<ItemStack> findByTemplate(long itemTemplateId) {
        return items.stream().filter(stack -> stack.itemTemplateId() == itemTemplateId).findFirst();
    }

    /**
     * Thay 1 item đã có (cùng {@code ItemStack.id}) bằng phiên bản mới — dùng khi cường hoá đổi
     * {@code enhanceLevel} (Phase 5 §7). {@code updated} phải trỏ ĐÚNG item đã tồn tại (gọi sau
     * {@link #findItem}), không dùng để thêm item mới (dùng {@link #add} cho việc đó).
     */
    public void replaceItem(ItemStack updated) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id() == updated.id()) {
                items.set(i, updated);
                return;
            }
        }
        throw new IllegalStateException("Item không tồn tại trong túi đồ: id=" + updated.id());
    }

    /**
     * Tiêu 1 đơn vị của item {@code itemId} — về 0 thì XOÁ hẳn dòng, còn lại thì giảm
     * {@code quantity}. Dùng khi tiêu "Bảo Vệ" cường hoá (Phase 5 §7.1) — cùng hợp đồng lỗi với
     * {@link #replaceItem}: {@code itemId} phải trỏ ĐÚNG item đã tồn tại (gọi sau
     * {@link #findByTemplate}/{@link #findItem}).
     */
    public void consumeOne(long itemId) {
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.id() == itemId) {
                if (stack.quantity() <= 1) {
                    items.remove(i);
                } else {
                    items.set(i, new ItemStack(stack.id(), stack.itemTemplateId(), stack.quantity() - 1,
                            stack.enhanceLevel()));
                }
                return;
            }
        }
        throw new IllegalStateException("Item không tồn tại trong túi đồ: id=" + itemId);
    }

    public long characterId() {
        return characterId;
    }

    public int capacity() {
        return capacity;
    }

    public List<ItemStack> items() {
        return List.copyOf(items); // không lộ mutable list ra ngoài
    }

    /** Sealed result thay vì trả null / ném exception cho trạng thái bình thường. */
    public sealed interface AddResult permits Added, Full {
        static AddResult added() {
            return new Added();
        }

        static AddResult full() {
            return new Full();
        }
    }

    // public — package khác (application/) cần pattern-match được trên các nhánh này
    public record Added() implements AddResult {
    }

    public record Full() implements AddResult {
    }
}
