package dev.kage.inventory.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Roll bảng rơi đồ — random CHỈ sinh ở server (ENGINE.md §10), {@link ThreadLocalRandom}, KHÔNG
 * bao giờ nhận tỉ lệ/kết quả rơi đồ từ client. Mỗi {@link DropTableEntry} roll ĐỘC LẬP — cùng tinh
 * thần {@code CombatConstants.resolveAttack} (server-world): hàm thuần, không mock random khi test
 * (xác minh bằng hành vi thống kê/tích hợp, không unit test tất định).
 */
public final class LootRoller {

    private LootRoller() {
    }

    public static List<ItemStack> roll(List<DropTableEntry> entries) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        for (DropTableEntry entry : entries) {
            if (ThreadLocalRandom.current().nextInt(100) < entry.dropChancePercent()) {
                int quantity = entry.minQuantity() == entry.maxQuantity()
                        ? entry.minQuantity()
                        : ThreadLocalRandom.current().nextInt(entry.minQuantity(), entry.maxQuantity() + 1);
                drops.add(ItemStack.newItem(entry.itemTemplateId(), quantity, 0));
            }
        }
        return drops;
    }
}
