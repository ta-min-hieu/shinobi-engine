package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.ItemCatalogEntry;
import dev.kage.protocol.ItemUseEffect;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lọc túi theo loại (ADR-324) — {@code /loctui trangbi|dan|khac} (hoặc {@code /bagfilter equip|use|other}): in các
 * món trong túi thuộc loại đó (theo catalog: mặc được / dùng được / còn lại), dùng lại mô tả của {@code /timdo}.
 */
final class BagFilterCoordinator implements WindowFlow {

    /** Ba loại. */
    enum Kind {
        EQUIPMENT("trangbi", "equip"),
        USABLE("dan", "use"),
        OTHER("khac", "other");

        private final String vietnamese;
        private final String english;

        Kind(String vietnamese, String english) {
            this.vietnamese = vietnamese;
            this.english = english;
        }

        /** Từ người chơi gõ (không dấu) ⇒ loại. */
        static Optional<Kind> parse(String word) {
            String folded = MentionWatch.fold(word).replace(" ", "");
            for (Kind kind : values()) {
                if (kind.vietnamese.equals(folded) || kind.english.equals(folded)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    private final ChatCoordinator chat;
    private final ItemCatalogCoordinator catalog;
    private final I18NBundle i18n = KageI18n.load();
    private Supplier<List<BagSearchCoordinator.Described>> items = List::of;

    BagFilterCoordinator(ChatCoordinator chat, ItemCatalogCoordinator catalog) {
        this.chat = chat;
        this.catalog = catalog;
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối túi đồ ({@code InventoryCoordinator#describedItems}). */
    void useItems(Supplier<List<BagSearchCoordinator.Described>> source) {
        this.items = source;
    }

    /** Loại của một mẫu vật phẩm theo catalog (không có trong catalog ⇒ khác). Tĩnh để test. */
    static Kind kindOf(Optional<ItemCatalogEntry> entry) {
        if (entry.isEmpty()) {
            return Kind.OTHER;
        }
        if (entry.get().getEquipSlot() != EquipmentSlot.EQUIPMENT_SLOT_UNSPECIFIED) {
            return Kind.EQUIPMENT;
        }
        return entry.get().getUseEffect() != ItemUseEffect.ITEM_USE_EFFECT_UNSPECIFIED ? Kind.USABLE : Kind.OTHER;
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/loctui".equals(command) && !"/bagfilter".equals(command)) {
            return false;
        }
        Optional<Kind> kind = Kind.parse(words.length > 1 ? words[1] : "");
        if (kind.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "bagfilter.usage"));
            return true;
        }
        List<BagSearchCoordinator.Described> found = new ArrayList<>();
        for (BagSearchCoordinator.Described item : items.get()) {
            if (kindOf(catalog.byId(item.itemTemplateId())) == kind.get()) {
                found.add(item);
            }
        }
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "bagfilter.header", found.size()));
        for (BagSearchCoordinator.Described item : found) {
            chat.showSystemNotice(
                    KageI18n.formatOrKey(i18n, "bagsearch.line", item.slotIndex() + 1, item.text(), item.quantity()));
        }
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
