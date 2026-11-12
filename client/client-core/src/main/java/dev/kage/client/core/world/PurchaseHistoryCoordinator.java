package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetShopPurchaseHistoryRequest;
import dev.kage.protocol.GetShopPurchaseHistoryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.ShopPurchaseEntry;
import dev.kage.protocol.wire.PacketCodec;

/** Lịch sử mua cửa hàng NPC (ADR-288) — {@code /lichsumua} (hoặc {@code /purchases}): 20 lần mua gần nhất. */
final class PurchaseHistoryCoordinator implements WindowFlow {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final ItemCatalogCoordinator catalog;
    private final I18NBundle i18n = KageI18n.load();

    PurchaseHistoryCoordinator(WorldCommandSender sender, ChatCoordinator chat, ItemCatalogCoordinator catalog) {
        this.sender = sender;
        this.chat = chat;
        this.catalog = catalog;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/lichsumua".equals(command) && !"/purchases".equals(command)) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_SHOP_PURCHASE_HISTORY_REQUEST_VALUE,
                GetShopPurchaseHistoryRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_SHOP_PURCHASE_HISTORY_RESPONSE_VALUE) {
            return false;
        }
        GetShopPurchaseHistoryResponse response = GetShopPurchaseHistoryResponse.parseFrom(frame.payload());
        if (response.getEntriesCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "purchases.empty"));
            return true;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "purchases.header"));
        ZoneId zone = ZoneId.systemDefault();
        for (ShopPurchaseEntry entry : response.getEntriesList()) {
            String name = catalog.byId(entry.getItemTemplateId())
                    .map(item -> KageI18n.textOrKey(i18n, item.getNameKey()))
                    .orElse("#" + entry.getItemTemplateId());
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "purchases.line",
                    TIME.format(Instant.ofEpochSecond(entry.getAtEpochSeconds()).atZone(zone)),
                    name,
                    entry.getQuantity(),
                    entry.getGoldCost()));
        }
        return true;
    }
}
