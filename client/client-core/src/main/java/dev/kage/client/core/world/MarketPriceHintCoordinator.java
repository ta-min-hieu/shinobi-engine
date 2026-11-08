package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetMarketPriceHintRequest;
import dev.kage.protocol.GetMarketPriceHintResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Giá gợi ý phường thị (ADR-234) — chọn một món trong túi rồi gõ {@code /giacho} (hoặc {@code /pricecheck}): giá rẻ
 * nhất đang rao + giá trung bình đã bán 7 ngày qua, tính cho MỘT cái.
 */
final class MarketPriceHintCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/giacho";
    private static final String COMMAND_EN = "/pricecheck";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private Supplier<Optional<InventoryController.Item>> selection = Optional::empty;

    MarketPriceHintCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối món đang chọn trong túi đồ. */
    void useSelection(Supplier<Optional<InventoryController.Item>> selection) {
        this.selection = selection;
    }

    private boolean onSlash(String line) {
        String command = line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        Optional<InventoryController.Item> item = selection.get();
        if (item.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "pricehint.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_MARKET_PRICE_HINT_REQUEST_VALUE,
                GetMarketPriceHintRequest.newBuilder()
                        .setItemTemplateId(item.get().itemTemplateId())
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_MARKET_PRICE_HINT_RESPONSE_VALUE) {
            return false;
        }
        GetMarketPriceHintResponse response = GetMarketPriceHintResponse.parseFrom(frame.payload());
        if (response.getLowestActive() == 0 && response.getSoldListings() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "pricehint.none"));
            return true;
        }
        chat.showSystemNotice(KageI18n.formatOrKey(
                i18n,
                "pricehint.line",
                response.getLowestActive(),
                response.getAverageSold(),
                response.getSoldListings()));
        return true;
    }
}
