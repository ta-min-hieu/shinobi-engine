package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.BuyFromMerchantCommand;
import dev.kage.protocol.BuyFromMerchantResponse;
import dev.kage.protocol.GetTravelingMerchantRequest;
import dev.kage.protocol.GetTravelingMerchantResponse;
import dev.kage.protocol.MerchantBuyResult;
import dev.kage.protocol.MerchantStockView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.TravelingMerchantArrivedEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Thương nhân du hành (ADR-118) — cửa sổ mở bằng lệnh chat {@code /thuongnhan}; khi thương nhân tới, server báo cả
 * server (1028) thành một dòng hệ thống. Mua một cái mỗi lần bấm; server kiểm đứng gần trạm, còn hàng, đủ linh thạch.
 */
final class MerchantCoordinator implements WindowFlow {

    private static final String COMMAND = "/thuongnhan";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final List<Long> rowItems = new ArrayList<>();

    MerchantCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        this.chat = feeds.chat();
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::buy);
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        if (!COMMAND.equals(line.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        panel.open();
        return true;
    }

    private void request() {
        sender.sendReadRequest(
                Opcode.OPCODE_GET_TRAVELING_MERCHANT_REQUEST_VALUE, GetTravelingMerchantRequest.getDefaultInstance());
    }

    private void buy(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowItems.size()) {
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_BUY_FROM_MERCHANT_COMMAND_VALUE,
                BuyFromMerchantCommand.newBuilder()
                        .setItemTemplateId(rowItems.get(rowIndex))
                        .build());
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
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GET_TRAVELING_MERCHANT_RESPONSE_VALUE) {
            show(GetTravelingMerchantResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode == Opcode.OPCODE_BUY_FROM_MERCHANT_RESPONSE_VALUE) {
            MerchantBuyResult result =
                    BuyFromMerchantResponse.parseFrom(frame.payload()).getResult();
            String suffix =
                    result.name().substring("MERCHANT_BUY_RESULT_".length()).toLowerCase(Locale.ROOT);
            panel.setStatus(text("merchant.result." + suffix));
            if (result == MerchantBuyResult.MERCHANT_BUY_RESULT_OK) {
                request();
            }
            return true;
        }
        if (opcode == Opcode.OPCODE_TRAVELING_MERCHANT_ARRIVED_EVENT_VALUE) {
            TravelingMerchantArrivedEvent arrived = TravelingMerchantArrivedEvent.parseFrom(frame.payload());
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "merchant.notice.arrived",
                    text(arrived.getWaypointNameKey()),
                    clock(arrived.getEndsAtEpochMs())));
            return true;
        }
        return false;
    }

    private void show(GetTravelingMerchantResponse response) {
        rowItems.clear();
        List<ReportPanel.Row> rows = new ArrayList<>();
        if (!response.getPresent()) {
            panel.setStatus(text("merchant.status.away"));
            panel.setRows(rows);
            return;
        }
        panel.setStatus(KageI18n.formatOrKey(
                i18n,
                "merchant.status.here",
                text(response.getWaypointNameKey()),
                Math.round(response.getX()),
                clock(response.getEndsAtEpochMs())));
        for (MerchantStockView line : response.getStockList()) {
            String name = itemCatalog
                    .nameKey(line.getItemTemplateId())
                    .map(this::text)
                    .orElseGet(() -> "#" + line.getItemTemplateId());
            rows.add(new ReportPanel.Row(
                    KageI18n.formatOrKey(i18n, "merchant.row", name, line.getPrice(), line.getRemaining()),
                    false,
                    "merchant.action.buy"));
            rowItems.add(line.getItemTemplateId());
        }
        panel.setRows(rows);
    }

    private static String clock(long epochMillis) {
        return CLOCK.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        rowItems.clear();
        panel.setRows(List.of());
    }
}
