package dev.kage.inventory.domain;

import java.util.ArrayList;
import java.util.List;

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

    public long characterId() {
        return characterId;
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
