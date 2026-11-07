package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.LinkedItemView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Khoe vật phẩm vào chat (ADR-188) — chọn một món trong túi rồi gõ {@code /khoe [lời nhắn]} (hoặc {@code /show}): tin
 * đi ra kênh đang chọn kèm id món; SERVER đọc món thật (cường hoá, ngọc, chỉ số) và gắn vào tin. Người nhận thấy nhãn
 * "[Tên +N | Ngọc]" trước nội dung; {@code /xemmon} (hoặc {@code /iteminfo}) in chỉ số món khoe gần nhất đã thấy.
 */
final class ItemLinkCoordinator implements WindowFlow {

    private static final String SHOW_VI = "/khoe";
    private static final String SHOW_EN = "/show";
    private static final String INFO_VI = "/xemmon";
    private static final String INFO_EN = "/iteminfo";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final ItemCatalogCoordinator itemCatalog;
    private final I18NBundle i18n = KageI18n.load();
    private Supplier<Optional<InventoryController.Item>> selection = Optional::empty;
    /** Món khoe gần nhất thấy trong chat (của bất kỳ ai) — cho {@code /xemmon}. */
    private Optional<LinkedItemView> lastSeen = Optional.empty();

    ItemLinkCoordinator(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.sender = sender;
        this.chat = feeds.chat();
        this.itemCatalog = feeds.itemCatalog();
        chat.addSlashCommands(this::onSlash);
        chat.useLinkLabel(this::label);
    }

    /** Nối món đang chọn trong túi đồ. */
    void useSelection(Supplier<Optional<InventoryController.Item>> selection) {
        this.selection = selection;
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (INFO_VI.equals(command) || INFO_EN.equals(command)) {
            lastSeen.ifPresentOrElse(
                    item -> chat.showSystemNotice(details(item)),
                    () -> chat.showSystemNotice(text("item_link.none_seen")));
            return true;
        }
        if (!SHOW_VI.equals(command) && !SHOW_EN.equals(command)) {
            return false;
        }
        Optional<InventoryController.Item> item = selection.get();
        ChatChannel channel = chat.currentChannel();
        if (item.isEmpty() || channel == ChatChannel.CHAT_CHANNEL_AREA) {
            chat.showSystemNotice(text("item_link.usage"));
            return true;
        }
        String body = words.length > 1 && !words[1].isBlank() ? words[1].strip() : text("item_link.default_body");
        sender.sendReadRequest(
                Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE,
                SendChatMessageCommand.newBuilder()
                        .setChannel(channel)
                        .setBody(body)
                        .setLinkedItemId(item.get().itemId())
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() == Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE) {
            ChatMessageEvent event = ChatMessageEvent.parseFrom(frame.payload());
            if (event.hasLinkedItem()) {
                lastSeen = Optional.of(event.getLinkedItem());
            }
        }
        return false; // KHÔNG nuốt — ChatCoordinator in tin
    }

    /** Nhãn món khoe dùng catalog của lớp này. */
    String label(LinkedItemView item) {
        return label(item, this::nameOf);
    }

    /** Nhãn "[Tên +N | Ngọc xS]" — từ dữ liệu server, chữ người gửi không giả được. Tĩnh để test. */
    static String label(LinkedItemView item, java.util.function.LongFunction<String> names) {
        StringBuilder out = new StringBuilder("[").append(names.apply(item.getItemTemplateId()));
        if (item.getEnhanceLevel() > 0) {
            out.append(" +").append(item.getEnhanceLevel());
        }
        if (item.getSocketGemTemplateId() != 0L) {
            out.append(" | ").append(names.apply(item.getSocketGemTemplateId()));
        }
        if (item.getQuantity() > 1) {
            out.append(" x").append(item.getQuantity());
        }
        return out.append(']').toString();
    }

    /** Một dòng chỉ số món khoe. */
    String details(LinkedItemView item) {
        return KageI18n.formatOrKey(
                i18n,
                "item_link.details",
                label(item),
                item.getBonusAtk(),
                item.getBonusDef(),
                item.getBonusRes(),
                item.getBonusHp());
    }

    private String nameOf(long templateId) {
        return itemCatalog.nameKey(templateId).map(this::text).orElseGet(() -> "#" + templateId);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }
}
