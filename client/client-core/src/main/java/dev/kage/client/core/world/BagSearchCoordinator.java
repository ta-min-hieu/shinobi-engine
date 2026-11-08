package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tìm trong túi đồ (ADR-254) — {@code /timdo <tên>} (hoặc {@code /find}) so khớp không dấu trên mô tả các món đang có
 * trong túi, in tối đa {@value #MAX_RESULTS} kết quả "ô N: tên ×số lượng".
 */
final class BagSearchCoordinator implements WindowFlow {

    static final int MAX_RESULTS = 10;

    /** Một món trong túi đã có mô tả hiển thị. */
    record Described(int slotIndex, int quantity, String text) {}

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private Supplier<List<Described>> items = List::of;

    BagSearchCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối túi đồ ({@code InventoryCoordinator#describedItems}). */
    void useItems(Supplier<List<Described>> source) {
        this.items = source;
    }

    /** Các món khớp {@code query} (không dấu, chứa-trong), tối đa {@value #MAX_RESULTS}. Tĩnh để test. */
    static List<Described> search(List<Described> all, String query) {
        String folded = MentionWatch.fold(query.strip());
        List<Described> found = new ArrayList<>();
        for (Described item : all) {
            if (!folded.isEmpty() && MentionWatch.fold(item.text()).contains(folded) && found.size() < MAX_RESULTS) {
                found.add(item);
            }
        }
        return found;
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/timdo".equals(command) && !"/find".equals(command)) {
            return false;
        }
        if (words.length < 2 || words[1].isBlank()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "bagsearch.usage"));
            return true;
        }
        List<Described> found = search(items.get(), words[1]);
        if (found.isEmpty()) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "bagsearch.none", words[1].strip()));
            return true;
        }
        for (Described item : found) {
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
