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
import dev.kage.protocol.GetMarketHistoryRequest;
import dev.kage.protocol.GetMarketHistoryResponse;
import dev.kage.protocol.MarketHistoryEntry;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lịch sử phường thị (ADR-177) — cửa sổ mở bằng lệnh chat {@code /lichsupt} (hoặc {@code /markethistory}): 50 món mình
 * đã bán / đã mua ở phường thị trong 30 ngày, mới nhất trên cùng — lúc chốt, bán hay mua, món, số vàng đổi, người kia.
 * Chỉ đọc.
 */
final class MarketHistoryCoordinator implements WindowFlow {

    private static final List<String> COMMANDS = List.of("/lichsupt", "/markethistory");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    /** Server gửi tối đa 50 — gói dị dạng không được kéo theo hàng nghìn dòng. */
    private static final int MAX_ROWS = 60;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final I18NBundle i18n = KageI18n.load();

    MarketHistoryCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        panel.setOnRefresh(this::request);
        panel.setStatus(KageI18n.textOrKey(i18n, "market_history.hint"));
        feeds.chat().addSlashCommands(this::onSlash);
    }

    static MarketHistoryCoordinator create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new MarketHistoryCoordinator(
                new ReportPanel(skin, "market_history.title", Input.Keys.UNKNOWN, List.of()), sender, feeds);
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
                Opcode.OPCODE_GET_MARKET_HISTORY_REQUEST_VALUE, GetMarketHistoryRequest.getDefaultInstance());
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
        if (frame.header().opcode() != Opcode.OPCODE_GET_MARKET_HISTORY_RESPONSE_VALUE) {
            return false;
        }
        GetMarketHistoryResponse response = GetMarketHistoryResponse.parseFrom(frame.payload());
        LongFunction<String> names = id -> itemCatalog
                .nameKey(id)
                .map(key -> KageI18n.textOrKey(i18n, key))
                .orElseGet(() -> "#" + id);
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (MarketHistoryEntry entry : response.getEntriesList()) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            rows.add(new ReportPanel.Row(describe(i18n, entry, names, ZoneId.systemDefault()), entry.getSold()));
        }
        if (rows.isEmpty()) {
            rows.add(new ReportPanel.Row(KageI18n.textOrKey(i18n, "market_history.empty"), false));
        }
        panel.setRows(rows);
        return true;
    }

    /** Một dòng lịch sử — tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, MarketHistoryEntry entry, LongFunction<String> itemName, ZoneId zone) {
        String counterpart = entry.getCounterpartName().isEmpty()
                ? KageI18n.textOrKey(i18n, "market_history.unknown")
                : ChatController.sanitize(entry.getCounterpartName());
        String sign = entry.getGoldDelta() > 0 ? "+" : "";
        return KageI18n.formatOrKey(
                i18n,
                entry.getSold() ? "market_history.sold" : "market_history.bought",
                WHEN.format(Instant.ofEpochSecond(entry.getAtEpochSeconds()).atZone(zone)),
                itemName.apply(entry.getItemTemplateId()) + " x" + entry.getQuantity(),
                sign + entry.getGoldDelta(),
                counterpart);
    }

    @Override
    public void reset() {
        panel.close();
        panel.setRows(List.of());
    }
}
