package dev.kage.inventory.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sắp xếp túi đồ (ADR-160) — luật thuần, không chạm DB. Hai bước:
 *
 * <ol>
 *   <li><b>Gom chồng</b>: các dòng {@link Row#mergeable() gộp được} cùng loại và cùng trạng thái khoá dồn vào những
 *       dòng id nhỏ nhất, mỗi dòng tới {@code maxStack}; dòng thừa bị xoá.
 *   <li><b>Xếp thứ tự</b>: trang bị (theo {@link #SLOT_ORDER}, cấp yêu cầu cao trước, cường hoá cao
 *       trước) → vật phẩm dùng được → nguyên liệu/khác → món đang mặc cuối cùng; cùng nhóm thì theo mã, chồng nhiều
 *       trước, id cuối cùng cho tất định.
 * </ol>
 */
public final class BagSortPolicy {

    /** Thứ hạng nhóm — số nhỏ đứng trước. */
    private static final int GROUP_EQUIPMENT = 0;

    private static final int GROUP_USABLE = 1;
    private static final int GROUP_OTHER = 2;
    private static final int GROUP_EQUIPPED = 3;

    /**
     * Thứ tự ô trang bị trong túi — vũ khí trước, rồi giáp từ thân ra ngoài, trang sức, pháp bảo, phi kiếm, linh thú.
     * Ô không có trong danh sách (dữ liệu lạ) xếp sau mọi ô đã biết. Tên khớp {@link EquipmentSlotType}.
     */
    private static final List<String> SLOT_ORDER = List.of(
            "WEAPON", "ARMOR", "HELMET", "GLOVE", "BOOT", "ACCESSORY_1", "ACCESSORY_2", "ARTIFACT", "MOUNT", "PET");

    private static final Comparator<Row> ORDER = Comparator.comparingInt(BagSortPolicy::group)
            .thenComparingInt(BagSortPolicy::slotRank)
            .thenComparing(Comparator.comparingInt(Row::requiredLevel).reversed())
            .thenComparing(Comparator.comparingInt(Row::enhanceLevel).reversed())
            .thenComparing(Row::templateCode)
            .thenComparing(Comparator.comparingInt(Row::quantity).reversed())
            .thenComparingLong(Row::id);

    /**
     * Một dòng túi nhìn từ luật sắp xếp.
     *
     * @param equipSlot {@code item_template.equip_slot} — rỗng nếu không mặc được
     * @param usable có {@code use_effect} (đan, phù…)
     * @param mergeable được gom: chưa cường hoá, không khảm/tẩy luyện/linh thú, không đang mặc, không đang chào giao
     *     dịch
     */
    public record Row(
            long id,
            long itemTemplateId,
            String templateCode,
            int quantity,
            int maxStack,
            Optional<String> equipSlot,
            boolean usable,
            int requiredLevel,
            int enhanceLevel,
            boolean equipped,
            boolean mergeable,
            boolean locked) {}

    /** Một dòng sống sót: số lượng mới và vị trí mới. */
    public record Placement(long id, int quantity, int slotIndex) {}

    /** Kết quả: dòng giữ lại (đã đánh số 0..n-1) và id dòng phải xoá vì đã gom vào dòng khác. */
    public record Plan(List<Placement> placements, List<Long> deletedIds) {
        /** Bản sao bất biến — kế hoạch không đổi sau khi lập. */
        public Plan {
            placements = List.copyOf(placements);
            deletedIds = List.copyOf(deletedIds);
        }
    }

    private BagSortPolicy() {}

    /** Lập kế hoạch cho toàn bộ túi của một nhân vật. */
    public static Plan plan(List<Row> rows) {
        List<Long> deleted = new ArrayList<>();
        List<Row> survivors = merge(rows, deleted);
        survivors.sort(ORDER);
        List<Placement> placements = new ArrayList<>(survivors.size());
        for (int i = 0; i < survivors.size(); i++) {
            Row row = survivors.get(i);
            placements.add(new Placement(row.id(), row.quantity(), i));
        }
        return new Plan(placements, deleted);
    }

    private static List<Row> merge(List<Row> rows, List<Long> deleted) {
        List<Row> survivors = new ArrayList<>();
        Map<String, List<Row>> groups = new LinkedHashMap<>();
        rows.stream().sorted(Comparator.comparingLong(Row::id)).forEach(row -> {
            if (row.mergeable() && row.maxStack() > 1) {
                groups.computeIfAbsent(row.itemTemplateId() + "/" + row.locked(), k -> new ArrayList<>())
                        .add(row);
            } else {
                survivors.add(row);
            }
        });
        for (List<Row> group : groups.values()) {
            long remaining = group.stream().mapToLong(Row::quantity).sum();
            for (Row row : group) {
                if (remaining <= 0) {
                    deleted.add(row.id());
                    continue;
                }
                int quantity = (int) Math.min(row.maxStack(), remaining);
                remaining -= quantity;
                survivors.add(withQuantity(row, quantity));
            }
        }
        return survivors;
    }

    private static Row withQuantity(Row row, int quantity) {
        return new Row(
                row.id(),
                row.itemTemplateId(),
                row.templateCode(),
                quantity,
                row.maxStack(),
                row.equipSlot(),
                row.usable(),
                row.requiredLevel(),
                row.enhanceLevel(),
                row.equipped(),
                row.mergeable(),
                row.locked());
    }

    private static int group(Row row) {
        if (row.equipped()) {
            return GROUP_EQUIPPED;
        }
        if (row.equipSlot().isPresent()) {
            return GROUP_EQUIPMENT;
        }
        return row.usable() ? GROUP_USABLE : GROUP_OTHER;
    }

    private static int slotRank(Row row) {
        int rank = row.equipSlot().map(SLOT_ORDER::indexOf).orElse(-1);
        return rank < 0 ? SLOT_ORDER.size() : rank;
    }
}
