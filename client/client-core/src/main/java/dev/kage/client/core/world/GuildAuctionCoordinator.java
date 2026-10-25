package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.BidGuildAuctionCommand;
import dev.kage.protocol.BidGuildAuctionResponse;
import dev.kage.protocol.GuildAuctionLotView;
import dev.kage.protocol.ListGuildAuctionsRequest;
import dev.kage.protocol.ListGuildAuctionsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Đấu giá bang (ADR-112 D5) — {@link ReportPanel} mở từ nút trong cửa sổ Bang (G): mỗi lô một dòng (vật phẩm, giá,
 * người dẫn, còn bao lâu) với nút Trả giá; ô nhập số tiền (trống = trả đúng mức tối thiểu server báo). Server giữ vàng,
 * chốt và chia tiền; client chỉ gửi ý định.
 */
final class GuildAuctionCoordinator implements WindowFlow {

    private static final long SECONDS_PER_MINUTE = 60L;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final I18NBundle i18n = KageI18n.load();

    /** Cùng chỉ số với {@link ReportPanel#setRows}: lô của dòng đó. */
    private final List<GuildAuctionLotView> rowsLots = new ArrayList<>();

    GuildAuctionCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        panel.enableInput("auction.input");
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
    }

    /** Nút "Đấu giá" trong cửa sổ Bang. */
    void open() {
        panel.show();
        request();
    }

    private void request() {
        sender.sendReadRequest(
                Opcode.OPCODE_LIST_GUILD_AUCTIONS_REQUEST_VALUE, ListGuildAuctionsRequest.getDefaultInstance());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowsLots.size()) {
            return;
        }
        GuildAuctionLotView lot = rowsLots.get(rowIndex);
        long amount = lot.getMinimumBid();
        String typed = panel.inputText().trim();
        if (!typed.isEmpty()) {
            try {
                amount = Long.parseLong(typed);
            } catch (NumberFormatException e) {
                panel.setStatus(text("auction.input.invalid"));
                return;
            }
        }
        sender.sendReadRequest(
                Opcode.OPCODE_BID_GUILD_AUCTION_COMMAND_VALUE,
                BidGuildAuctionCommand.newBuilder()
                        .setLotId(lot.getLotId())
                        .setAmount(amount)
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
        if (opcode == Opcode.OPCODE_LIST_GUILD_AUCTIONS_RESPONSE_VALUE) {
            ListGuildAuctionsResponse response = ListGuildAuctionsResponse.parseFrom(frame.payload());
            panel.setStatus(text("auction.hint"));
            show(response);
            return true;
        }
        if (opcode == Opcode.OPCODE_BID_GUILD_AUCTION_RESPONSE_VALUE) {
            BidGuildAuctionResponse response = BidGuildAuctionResponse.parseFrom(frame.payload());
            String name = response.getOutcome().name();
            panel.setStatus(text("auction.outcome."
                    + name.replace("BID_GUILD_AUCTION_OUTCOME_", "").toLowerCase(Locale.ROOT)));
            show(response.getStatus());
            return true;
        }
        return false;
    }

    private void show(ListGuildAuctionsResponse response) {
        List<ReportPanel.Row> rows = new ArrayList<>();
        rowsLots.clear();
        if (response.getLotsCount() == 0) {
            rows.add(new ReportPanel.Row(text("auction.empty"), false));
            panel.setRows(rows);
            return;
        }
        for (GuildAuctionLotView lot : response.getLotsList()) {
            String item = itemCatalog
                    .nameKey(lot.getItemTemplateId())
                    .map(this::text)
                    .orElseGet(() -> "#" + lot.getItemTemplateId());
            String leader = lot.getLeaderName().isEmpty()
                    ? text("auction.no_bid")
                    : (lot.getLeadingIsMe() ? text("auction.leader_me") : lot.getLeaderName());
            rows.add(new ReportPanel.Row(
                    KageI18n.formatOrKey(
                            i18n,
                            "auction.row",
                            item + (lot.getQuantity() > 1 ? " x" + lot.getQuantity() : ""),
                            lot.getCurrentBid(),
                            leader,
                            Math.max(1L, lot.getSecondsLeft() / SECONDS_PER_MINUTE),
                            lot.getMinimumBid()),
                    lot.getLeadingIsMe(),
                    lot.getLeadingIsMe() ? "" : "auction.action.bid"));
            rowsLots.add(lot);
        }
        panel.setRows(rows);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        panel.setRows(List.of());
        rowsLots.clear();
    }
}
