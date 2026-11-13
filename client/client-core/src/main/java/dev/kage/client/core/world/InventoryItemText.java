package dev.kage.client.core.world;

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

    /** ADR-326 — đầu tên món vừa nhặt (cùng chữ với dấu thư chưa đọc). */
    static final String NEW_MARK = "(mới) ";

    /** ADR-199 — khoá i18n của bốn chỉ số so sánh, cùng thứ tự với {@link #effectiveStats}. */
    private static final String[] STAT_KEYS = {
        "compare.stat.atk", "compare.stat.def", "compare.stat.res", "compare.stat.hp"
    };

    private final I18NBundle i18n = KageI18n.load();
    private final LongFunction<ItemCatalogEntry> catalogLookup;
    private final LongFunction<String> nameOrFallback;
    private java.util.function.LongPredicate isNew = id -> false; // ADR-326
    private Comparison comparison = Comparison.NONE;

    /**
     * ADR-199 — so với món đang mặc: tra món đang mặc cùng ô và hệ số cường hoá server gửi. {@link #NONE} = không so.
     *
     * @param counterpart món đang mặc ở ô mà món đang xem mặc vào; rỗng nếu ô trống hoặc món không mặc được
     * @param stepPercent phần trăm cường hoá mỗi cấp; 0 = server chưa gửi ⇒ không so (không tự đoán)
     */
    record Comparison(
            java.util.function.Function<InventoryController.Item, java.util.Optional<InventoryController.Item>>
                    counterpart,
            java.util.function.IntSupplier stepPercent) {

        static final Comparison NONE = new Comparison(item -> java.util.Optional.empty(), () -> 0);
    }

    /**
     * @param catalogLookup tra {@code item_template_id} ra dòng catalog; {@code null} khi chưa có
     *     catalog hoặc id lạ — lúc đó KHÔNG hiện chỉ số nào thay vì hiện "ATK +0" (Điều 24)
     * @param nameOrFallback tên đã dịch, hoặc dạng số khi chưa có catalog
     */
    InventoryItemText(LongFunction<ItemCatalogEntry> catalogLookup, LongFunction<String> nameOrFallback) {
        this.catalogLookup = catalogLookup;
        this.nameOrFallback = nameOrFallback;
    }

    /** ADR-326 — cách biết một mẫu vừa nhặt (nhãn "mới"). */
    void useNewMark(java.util.function.LongPredicate newItem) {
        this.isNew = newItem;
    }

    /** ADR-199 — bật so sánh với món đang mặc. */
    void useComparison(Comparison newComparison) {
        this.comparison = newComparison;
    }

    String describe(InventoryController.Item item) {
        StringBuilder builder = new StringBuilder(isNew.test(item.itemTemplateId()) ? NEW_MARK : ""); // ADR-326
        builder.append(nameOrFallback.apply(item.itemTemplateId()));
        if (item.quantity() > 1) {
            builder.append(' ').append(format("inventory.label.quantity", item.quantity()));
        }
        if (item.enhanceLevel() > 0) {
            builder.append(' ').append(format("inventory.label.enhance", item.enhanceLevel()));
        }
        appendStats(builder, item);
        appendComparison(builder, item);
        return builder.toString();
    }

    /**
     * ADR-199 — "So với đang mặc: Công +3, Thủ -1" (chỉ số HIỆU DỤNG sau cường hoá, theo hệ số server gửi). Món không
     * mặc được, đang là chính món đang mặc, hoặc chưa biết hệ số ⇒ không thêm gì.
     */
    private void appendComparison(StringBuilder builder, InventoryController.Item item) {
        int step = comparison.stepPercent().getAsInt();
        ItemCatalogEntry entry = catalogLookup.apply(item.itemTemplateId());
        if (step <= 0
                || entry == null
                || entry.getEquipSlot() == dev.kage.protocol.EquipmentSlot.EQUIPMENT_SLOT_UNSPECIFIED) {
            return;
        }
        java.util.Optional<InventoryController.Item> worn =
                comparison.counterpart().apply(item);
        if (worn.isPresent() && worn.get().itemId() == item.itemId()) {
            return;
        }
        int[] mine = effectiveStats(entry, item.enhanceLevel(), step);
        int[] theirs = worn.map(w -> effectiveStats(catalogLookup.apply(w.itemTemplateId()), w.enhanceLevel(), step))
                .orElse(new int[STAT_KEYS.length]);
        String key = worn.isEmpty() ? "compare.vs_empty" : "compare.vs_worn";
        builder.append('\n').append(format(key, diffText(mine, theirs)));
    }

    /** "Công +3, Thủ -1" — chỉ các chỉ số có đổi; "không đổi chỉ số" nếu bằng nhau. */
    private String diffText(int[] mine, int[] theirs) {
        StringJoiner diff = new StringJoiner(", ");
        for (int i = 0; i < STAT_KEYS.length; i++) {
            int delta = mine[i] - theirs[i];
            if (delta != 0) {
                diff.add(format(STAT_KEYS[i], String.format(java.util.Locale.ROOT, "%+d", delta)));
            }
        }
        return diff.length() == 0 ? text("compare.same") : diff.toString();
    }

    /** Công, thủ, kháng, máu sau cường hoá; catalog chưa có món đó ⇒ toàn 0. */
    private static int[] effectiveStats(ItemCatalogEntry entry, int level, int step) {
        if (entry == null) {
            return new int[STAT_KEYS.length];
        }
        return new int[] {
            effective(entry.getBonusAtk(), level, step),
            effective(entry.getBonusDef(), level, step),
            effective(entry.getBonusRes(), level, step),
            effective(entry.getBonusHp(), level, step)
        };
    }

    /**
     * Chỉ số hiệu dụng — CÙNG công thức số nguyên với {@code EnhancementTable.enhancedBonus} ở server (làm tròn nửa
     * lên), với hệ số do server gửi. Hiển thị thôi; server vẫn là bên tính chỉ số thật.
     */
    static int effective(int base, int level, int stepPercent) {
        if (base == 0 || level <= 0) {
            return base;
        }
        long scaled = (long) base * (100L + (long) stepPercent * level);
        return (int) Math.floorDiv(scaled + 50L, 100L);
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
        return KageI18n.textOrKey(i18n, key);
    }

    private String format(String key, Object argument) {
        return KageI18n.formatOrKey(i18n, key, argument);
    }

    private String format2(String key, Object first, Object second) {
        return KageI18n.formatOrKey(i18n, key, first, second);
    }
}
