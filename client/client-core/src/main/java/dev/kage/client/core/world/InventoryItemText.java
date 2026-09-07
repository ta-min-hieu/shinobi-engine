package dev.kage.client.core.world;

import java.util.MissingResourceException;
import java.util.StringJoiner;
import java.util.function.LongFunction;

import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.CultivationRealm;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ItemCatalogEntry;

/**
 * Ghép chuỗi hiển thị của MỘT vật phẩm trong ô chi tiết túi đồ: tên · số lượng · mức cường hoá ·
 * chỉ số gốc · cảnh giới yêu cầu.
 *
 * <p><b>Vì sao tách khỏi {@code InventoryCoordinator}.</b> Đây là việc thuần định dạng — không đọc
 * mạng, không giữ trạng thái, không quyết định gì. Nó nằm chung với điều phối cho tới 2026-09-07,
 * khi thêm nút "Dùng" (ADR-046 D5) đẩy {@code InventoryCoordinator} vượt trần
 * {@code CyclomaticComplexity} của PMD — cùng bức tường mà {@code WorldScreen} đã đâm vào và được
 * gỡ bằng {@code PanelHub}. Trần đó không phải phiền toái: nó nói đúng lúc một lớp đang gánh hai
 * việc.
 *
 * <p>Thuần Java + libGDX {@code I18NBundle}, không GL — test được thẳng trong JUnit.
 */
final class InventoryItemText {

    private final I18NBundle i18n = KageI18n.load();
    private final LongFunction<ItemCatalogEntry> catalogLookup;
    private final LongFunction<String> nameOrFallback;

    /**
     * @param catalogLookup tra {@code item_template_id} ra dòng catalog; {@code null} khi chưa có
     *     catalog hoặc id lạ — lúc đó KHÔNG hiện chỉ số nào thay vì hiện "ATK +0" (Điều 24)
     * @param nameOrFallback tên đã dịch, hoặc dạng số khi chưa có catalog
     */
    InventoryItemText(LongFunction<ItemCatalogEntry> catalogLookup, LongFunction<String> nameOrFallback) {
        this.catalogLookup = catalogLookup;
        this.nameOrFallback = nameOrFallback;
    }

    String describe(InventoryController.Item item) {
        StringBuilder builder = new StringBuilder(nameOrFallback.apply(item.itemTemplateId()));
        if (item.quantity() > 1) {
            builder.append(' ').append(format("inventory.label.quantity", item.quantity()));
        }
        if (item.enhanceLevel() > 0) {
            builder.append(' ').append(format("inventory.label.enhance", item.enhanceLevel()));
        }
        appendStats(builder, item);
        return builder.toString();
    }

    /**
     * Bốn chỉ số + cấp yêu cầu + hiệu ứng dùng (ADR-036 D5 field 7..10, ADR-046 D5 field 11..12 của
     * {@code ItemCatalogEntry}). Bỏ hẳn dòng nào không có gì để nói — không hiện "ATK +0" cho một
     * hòn sắt vụn.
     *
     * <p>Đây là chỉ số GỐC của loại vật phẩm, KHÔNG phải chỉ số hiệu dụng sau cường hoá: hệ số
     * cường hoá ({@code EnhancementTable.ENHANCE_STEP}) sống ở server và không có gói nào gửi nó
     * sang đây, nên client tự nhân là bịa (Điều 24 — cùng lý do bảng chi phí cường hoá không được
     * hiện ở client). Nhãn nói rõ "chỉ số gốc" để người chơi không hiểu nhầm.
     */
    private void appendStats(StringBuilder builder, InventoryController.Item item) {
        ItemCatalogEntry entry = catalogLookup.apply(item.itemTemplateId());
        if (entry == null) {
            return;
        }
        StringJoiner stats = new StringJoiner(", ");
        appendStat(stats, "inventory.stat.atk", entry.getBonusAtk());
        appendStat(stats, "inventory.stat.def", entry.getBonusDef());
        appendStat(stats, "inventory.stat.res", entry.getBonusRes());
        appendStat(stats, "inventory.stat.hp", entry.getBonusHp());
        if (stats.length() > 0) {
            builder.append('\n').append(format("inventory.label.base_stats", stats.toString()));
        }
        // ADR-046 D5 — con số DUY NHẤT khiến người chơi quyết định uống hay không. Server vẫn là
        // bên tính máu thật (HealthChangedEvent 408); đây chỉ là lời hứa của catalog.
        if (entry.getUseValue() > 0) {
            builder.append('\n').append(format("inventory.label.heals", entry.getUseValue()));
        }
        // required_level = 0 nghĩa là server CŨ chưa gửi field này (mặc định proto3), không phải
        // "cần Cấp 0" — im lặng bỏ qua thay vì hiện một con số sai.
        if (entry.getRequiredLevel() > 1) {
            builder.append('\n').append(format("inventory.label.required_level", realmLabel(entry.getRequiredLevel())));
        }
    }

    private void appendStat(StringJoiner stats, String key, int value) {
        if (value != 0) {
            stats.add(format(key, value));
        }
    }

    /**
     * Điều kiện cấp độ hiện ra dưới dạng CẢNH GIỚI — ADR-038 D2.
     *
     * <p>Bản ĐẦY ĐỦ ({@code realm.tier} → "Luyện Khí tầng 5") chứ không phải bản ngắn như dòng chọn
     * nhân vật: khung chi tiết vật phẩm xuống dòng tự do, không bị bó trong một nút rộng cố định,
     * và đây là câu người chơi phải đọc để biết mình còn thiếu bao xa.
     */
    private String realmLabel(int level) {
        CultivationRealm realm = CultivationRealm.of(level);
        String realmName = text(realm.nameKey());
        return realm.perfected() ? format("realm.perfect", realmName) : format2("realm.tier", realmName, realm.tier());
    }

    private String text(String key) {
        try {
            return i18n.get(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    private String format(String key, Object argument) {
        try {
            return i18n.format(key, argument);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    private String format2(String key, Object first, Object second) {
        try {
            return i18n.format(key, first, second);
        } catch (MissingResourceException e) {
            return key;
        }
    }
}
