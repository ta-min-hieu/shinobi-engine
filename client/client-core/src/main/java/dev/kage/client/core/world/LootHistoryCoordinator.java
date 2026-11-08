package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetLootHistoryRequest;
import dev.kage.protocol.GetLootHistoryResponse;
import dev.kage.protocol.LootHistoryEntry;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/** Lịch sử nhặt đồ (ADR-253) — {@code /lichsunhat} (hoặc {@code /lootlog}): 20 lần nhặt gần nhất vào khung chat. */
final class LootHistoryCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/lichsunhat";
    private static final String COMMAND_EN = "/lootlog";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final ItemCatalogCoordinator catalog;
    private final I18NBundle i18n = KageI18n.load();

    LootHistoryCoordinator(WorldCommandSender sender, ChatCoordinator chat, ItemCatalogCoordinator catalog) {
        this.sender = sender;
        this.chat = chat;
        this.catalog = catalog;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_LOOT_HISTORY_REQUEST_VALUE, GetLootHistoryRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_LOOT_HISTORY_RESPONSE_VALUE) {
            return false;
        }
        GetLootHistoryResponse response = GetLootHistoryResponse.parseFrom(frame.payload());
        if (response.getEntriesCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "lootlog.empty"));
            return true;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "lootlog.header"));
        for (LootHistoryEntry entry : response.getEntriesList()) {
            String name = catalog.nameKey(entry.getItemTemplateId())
                    .map(key -> KageI18n.textOrKey(i18n, key))
                    .orElse("#" + entry.getItemTemplateId());
            chat.showSystemNotice(
                    KageI18n.formatOrKey(i18n, "lootlog.line", name, entry.getQuantity(), entry.getSource()));
        }
        return true;
    }
}
