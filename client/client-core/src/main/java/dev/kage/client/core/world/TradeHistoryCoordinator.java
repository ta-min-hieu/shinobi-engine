package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongFunction;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.protocol.GetTradeHistoryRequest;
import dev.kage.protocol.GetTradeHistoryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.TradeHistoryEntry;
import dev.kage.protocol.TradeHistoryItem;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lịch sử giao dịch (ADR-189) — cửa sổ mở bằng lệnh chat {@code /lichsugd} (hoặc {@code /tradehistory}): 30 lần giao
 * dịch trực tiếp gần nhất (30 ngày), mới nhất trên cùng — với ai, đã đưa gì, đã nhận gì. Chỉ đọc.
 */
final class TradeHistoryCoordinator implements WindowFlow {

    private static final List<String> COMMANDS = List.of("/lichsugd", "/tradehistory");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    /** Server gửi tối đa 30 — gói dị dạng không được kéo theo hàng nghìn dòng. */
    private static final int MAX_ROWS = 40;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final I18NBundle i18n = KageI18n.load();

    TradeHistoryCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        panel.setOnRefresh(this::request);
        panel.setStatus(KageI18n.textOrKey(i18n, "trade_history.hint"));
        feeds.chat().addSlashCommands(this::onSlash);
    }

    static TradeHistoryCoordinator create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new TradeHistoryCoordinator(
                new ReportPanel(skin, "trade_history.title", Input.Keys.UNKNOWN, List.of()), sender, feeds);
    }

    ReportPanel panel() {
        return panel;
    }

    private boolean onSlash(String line) {
        if (!COMMANDS.contains(line.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        panel.open();
        return true;
    }

    private void request() {
        sender.sendReadRequest(
                Opcode.OPCODE_GET_TRADE_HISTORY_REQUEST_VALUE, GetTradeHistoryRequest.getDefaultInstance());
    }

    @Override
    public boolean isOpen() {
        return panel.isOpen();
    }

    @Override
    public void close() {
        panel.close();
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_TRADE_HISTORY_RESPONSE_VALUE) {
            return false;
        }
        GetTradeHistoryResponse response = GetTradeHistoryResponse.parseFrom(frame.payload());
        LongFunction<String> names = id -> itemCatalog
                .nameKey(id)
                .map(key -> KageI18n.textOrKey(i18n, key))
                .orElseGet(() -> "#" + id);
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (TradeHistoryEntry entry : response.getEntriesList()) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            rows.add(new ReportPanel.Row(describe(i18n, entry, names, ZoneId.systemDefault()), false));
        }
        if (rows.isEmpty()) {
            rows.add(new ReportPanel.Row(KageI18n.textOrKey(i18n, "trade_history.empty"), false));
        }
        panel.setRows(rows);
        return true;
    }

    /** Một dòng lịch sử — tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, TradeHistoryEntry entry, LongFunction<String> itemName, ZoneId zone) {
        String counterpart = entry.getCounterpartName().isEmpty()
                ? KageI18n.textOrKey(i18n, "trade_history.unknown")
                : ChatController.sanitize(entry.getCounterpartName());
        return KageI18n.formatOrKey(
                i18n,
                "trade_history.row",
                WHEN.format(Instant.ofEpochSecond(entry.getAtEpochSeconds()).atZone(zone)),
                counterpart,
                side(i18n, entry.getGivenList(), entry.getGoldGiven(), itemName),
                side(i18n, entry.getReceivedList(), entry.getGoldReceived(), itemName));
    }

    private static String side(I18NBundle i18n, List<TradeHistoryItem> items, long gold, LongFunction<String> name) {
        List<String> parts = new ArrayList<>();
        for (TradeHistoryItem item : items) {
            String label = item.getItemTemplateId() == 0L ? "?" : name.apply(item.getItemTemplateId());
            if (item.getEnhanceLevel() > 0) {
                label += " +" + item.getEnhanceLevel();
            }
            parts.add(item.getQuantity() > 1 ? label + " x" + item.getQuantity() : label);
        }
        if (gold > 0) {
            parts.add(KageI18n.formatOrKey(i18n, "trade_history.gold", gold));
        }
        return parts.isEmpty() ? KageI18n.textOrKey(i18n, "trade_history.nothing") : String.join(", ", parts);
    }

    @Override
    public void reset() {
        panel.close();
        panel.setRows(List.of());
    }
}
