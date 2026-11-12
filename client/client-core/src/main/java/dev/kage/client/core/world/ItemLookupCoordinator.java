package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ItemCatalogEntry;
import dev.kage.protocol.ItemDropSourceView;
import dev.kage.protocol.LookupItemRequest;
import dev.kage.protocol.LookupItemResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tra cứu vật phẩm (ADR-279) — {@code /vatpham <tên>} (hoặc {@code /item}): tìm mẫu trong catalog vật phẩm theo tên
 * (không dấu; trùng khít ưu tiên, rồi chứa-trong), in chỉ số catalog đã có, rồi hỏi server giá cửa hàng + quái rơi ra.
 */
final class ItemLookupCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/vatpham";
    private static final String COMMAND_EN = "/item";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final ItemCatalogCoordinator catalog;
    private final I18NBundle i18n = KageI18n.load();

    ItemLookupCoordinator(WorldCommandSender sender, ChatCoordinator chat, ItemCatalogCoordinator catalog) {
        this.sender = sender;
        this.chat = chat;
        this.catalog = catalog;
        chat.addSlashCommands(this::onSlash);
    }

    /** Mẫu khớp tên {@code query}: trùng khít (không dấu) trước, rồi chứa-trong; rỗng nếu không có. */
    Optional<ItemCatalogEntry> find(String query) {
        String folded = MentionWatch.fold(query.strip());
        if (folded.isEmpty()) {
            return Optional.empty();
        }
        Optional<ItemCatalogEntry> exact = catalog.entries().stream()
                .filter(entry -> MentionWatch.fold(nameOf(entry)).equals(folded))
                .findFirst();
        return exact.isPresent()
                ? exact
                : catalog.entries().stream()
                        .filter(entry -> MentionWatch.fold(nameOf(entry)).contains(folded))
                        .findFirst();
    }

    private String nameOf(ItemCatalogEntry entry) {
        return KageI18n.textOrKey(i18n, entry.getNameKey());
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        Optional<ItemCatalogEntry> entry = words.length > 1 ? find(words[1]) : Optional.empty();
        if (entry.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "itemlookup.usage"));
            return true;
        }
        ItemCatalogEntry item = entry.get();
        chat.showSystemNotice(KageI18n.formatOrKey(
                i18n,
                "itemlookup.header",
                nameOf(item),
                item.getRequiredLevel(),
                item.getBonusAtk(),
                item.getBonusDef(),
                item.getBonusHp()));
        sender.sendReadRequest(
                Opcode.OPCODE_LOOKUP_ITEM_REQUEST_VALUE,
                LookupItemRequest.newBuilder().setItemTemplateId(item.getId()).build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_LOOKUP_ITEM_RESPONSE_VALUE) {
            return false;
        }
        LookupItemResponse response = LookupItemResponse.parseFrom(frame.payload());
        chat.showSystemNotice(
                KageI18n.formatOrKey(i18n, "itemlookup.prices", response.getBuyPrice(), response.getSellPrice()));
        if (response.getDropsCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "itemlookup.no_drops"));
        }
        for (ItemDropSourceView drop : response.getDropsList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "itemlookup.drop",
                    KageI18n.textOrKey(i18n, drop.getMonsterNameKey()),
                    drop.getChancePercent()));
        }
        return true;
    }
}
