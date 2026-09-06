package dev.kage.tools.loadtest.probe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import dev.kage.protocol.InventoryItemView;

/**
 * Chênh lệch túi đồ giữa hai lần {@code LIST_INVENTORY_RESPONSE} (ADR-035) — trả lời "phần thưởng
 * CÓ THẬT SỰ vào túi không" bằng số, chứ không bằng việc nhìn ô túi trên màn hình.
 *
 * <p>Gộp theo {@code item_template_id} chứ không theo {@code item_id}: cùng một loại vật phẩm có
 * thể nằm ở nhiều ô (xếp chồng tới {@code max_stack}), và khi nhận thêm thì server có quyền cộng
 * vào ô cũ HAY mở ô mới — hai cách đó phải cho ra cùng một con số "+3 cái", nếu không thì bảng
 * chênh lệch lại đẻ ra đúng loại mơ hồ mà nó sinh ra để dẹp.
 *
 * <p>Hàm THUẦN, không I/O — test được không cần server ({@code InventoryDeltaTest}).
 */
final class InventoryDelta {

    private static final String NEWLINE = System.lineSeparator();

    /** Một loại vật phẩm có tổng số lượng khác nhau giữa hai lần chụp. */
    record Line(long itemTemplateId, int quantityBefore, int quantityAfter) {

        int change() {
            return quantityAfter - quantityBefore;
        }
    }

    private InventoryDelta() {}

    /** Chỉ những loại vật phẩm THẬT SỰ đổi số lượng, sắp theo {@code item_template_id}. */
    static List<Line> between(List<InventoryItemView> before, List<InventoryItemView> after) {
        Map<Long, Integer> totalsBefore = totalByTemplate(before);
        Map<Long, Integer> totalsAfter = totalByTemplate(after);
        Set<Long> allTemplateIds = new TreeSet<>(totalsBefore.keySet());
        allTemplateIds.addAll(totalsAfter.keySet());

        List<Line> lines = new ArrayList<>();
        for (Long templateId : allTemplateIds) {
            int quantityBefore = totalsBefore.getOrDefault(templateId, 0);
            int quantityAfter = totalsAfter.getOrDefault(templateId, 0);
            if (quantityBefore != quantityAfter) {
                lines.add(new Line(templateId, quantityBefore, quantityAfter));
            }
        }
        return lines;
    }

    static String renderTable(List<Line> lines) {
        if (lines.isEmpty()) {
            return "  (túi đồ không đổi)";
        }
        StringBuilder table = new StringBuilder(128);
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            table.append(String.format(
                    Locale.ROOT,
                    "  itemTemplateId=%-6d %d -> %d (%+d)",
                    line.itemTemplateId(),
                    line.quantityBefore(),
                    line.quantityAfter(),
                    line.change()));
            if (index < lines.size() - 1) {
                table.append(NEWLINE);
            }
        }
        return table.toString();
    }

    private static Map<Long, Integer> totalByTemplate(List<InventoryItemView> items) {
        Map<Long, Integer> totals = new LinkedHashMap<>();
        for (InventoryItemView item : items) {
            totals.merge(item.getItemTemplateId(), item.getQuantity(), Integer::sum);
        }
        return totals;
    }
}
