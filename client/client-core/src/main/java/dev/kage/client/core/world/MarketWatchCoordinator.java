package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ItemCatalogEntry;
import dev.kage.protocol.ListMarketWatchRequest;
import dev.kage.protocol.ListMarketWatchResponse;
import dev.kage.protocol.MarketWatchAlertEvent;
import dev.kage.protocol.MarketWatchView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetMarketWatchCommand;
import dev.kage.protocol.SetMarketWatchOutcome;
import dev.kage.protocol.SetMarketWatchResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Theo dõi phường thị (ADR-196) — {@code /theodoi} (hoặc {@code /watch}) liệt kê; {@code /theodoi <giá mỗi cái> <tên
 * hoặc mã món>} đặt / sửa; {@code /theodoi bo <tên hoặc mã món>} bỏ. Có người bày món khớp thì server báo, lớp này in
 * một dòng vào khung chat.
 */
final class MarketWatchCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/theodoi";
    private static final String COMMAND_EN = "/watch";
    private static final String OUTCOME_PREFIX = "SET_MARKET_WATCH_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final ItemCatalogCoordinator catalog;
    private final I18NBundle i18n = KageI18n.load();

    MarketWatchCoordinator(WorldCommandSender sender, ChatCoordinator chat, ItemCatalogCoordinator catalog) {
        this.sender = sender;
        this.chat = chat;
        this.catalog = catalog;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 3);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        if (words.length == 1) {
            sender.sendReadRequest(
                    Opcode.OPCODE_LIST_MARKET_WATCH_REQUEST_VALUE, ListMarketWatchRequest.getDefaultInstance());
            return true;
        }
        String verb = words[1].toLowerCase(Locale.ROOT);
        boolean remove = "bo".equals(verb) || "remove".equals(verb);
        OptionalLong price = remove ? OptionalLong.of(0L) : parsePrice(words[1]);
        Optional<ItemCatalogEntry> item = words.length == 3 ? resolve(words[2].strip()) : Optional.empty();
        if (price.isEmpty() || item.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "market_watch.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SET_MARKET_WATCH_COMMAND_VALUE,
                SetMarketWatchCommand.newBuilder()
                        .setItemTemplateId(item.get().getId())
                        .setMaxUnitPrice(price.getAsLong())
                        .build());
        return true;
    }

    private static OptionalLong parsePrice(String text) {
        try {
            long value = Long.parseLong(text);
            return value > 0 ? OptionalLong.of(value) : OptionalLong.empty();
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /** Mã món, hoặc tên hiển thị (không phân biệt hoa thường). */
    private Optional<ItemCatalogEntry> resolve(String token) {
        Optional<ItemCatalogEntry> byCode = catalog.byCode(token.toLowerCase(Locale.ROOT));
        if (byCode.isPresent()) {
            return byCode;
        }
        return catalog.entries().stream()
                .filter(entry -> KageI18n.textOrKey(i18n, entry.getNameKey()).equalsIgnoreCase(token))
                .findFirst();
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_SET_MARKET_WATCH_RESPONSE_VALUE) {
            SetMarketWatchResponse response = SetMarketWatchResponse.parseFrom(frame.payload());
            chat.showSystemNotice(describe(response));
            return true;
        }
        if (opcode == Opcode.OPCODE_LIST_MARKET_WATCH_RESPONSE_VALUE) {
            showList(ListMarketWatchResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode == Opcode.OPCODE_MARKET_WATCH_ALERT_EVENT_VALUE) {
            MarketWatchAlertEvent alert = MarketWatchAlertEvent.parseFrom(frame.payload());
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "market_watch.alert",
                    ChatController.sanitize(alert.getSellerName()),
                    itemName(alert.getItemTemplateId()),
                    alert.getQuantity(),
                    alert.getUnitPrice()));
            return true;
        }
        return false;
    }

    private String describe(SetMarketWatchResponse response) {
        SetMarketWatchOutcome outcome = response.getOutcome();
        String suffix = outcome == SetMarketWatchOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.formatOrKey(
                i18n,
                "market_watch.outcome." + suffix,
                itemName(response.getItemTemplateId()),
                response.getMaxUnitPrice());
    }

    private void showList(ListMarketWatchResponse response) {
        if (response.getWatchesCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "market_watch.empty"));
            return;
        }
        chat.showSystemNotice(KageI18n.formatOrKey(
                i18n, "market_watch.header", response.getWatchesCount(), response.getMaxWatches()));
        for (MarketWatchView watch : response.getWatchesList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n, "market_watch.row", itemName(watch.getItemTemplateId()), watch.getMaxUnitPrice()));
        }
    }

    private String itemName(long itemTemplateId) {
        return catalog.nameKey(itemTemplateId)
                .map(key -> KageI18n.textOrKey(i18n, key))
                .orElseGet(() -> "#" + itemTemplateId);
    }

    /** Khoá i18n của một kết quả — tĩnh để test đối chiếu bundle. */
    static String outcomeKey(SetMarketWatchOutcome outcome) {
        return "market_watch.outcome."
                + outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
    }
}
