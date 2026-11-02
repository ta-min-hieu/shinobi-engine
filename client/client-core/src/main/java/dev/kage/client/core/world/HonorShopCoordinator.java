package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.protocol.BuyHonorItemCommand;
import dev.kage.protocol.BuyHonorItemOutcome;
import dev.kage.protocol.BuyHonorItemResponse;
import dev.kage.protocol.GetHonorShopRequest;
import dev.kage.protocol.GetHonorShopResponse;
import dev.kage.protocol.HonorShopItemView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Cửa hàng điểm vinh dự (ADR-155) — cửa sổ mở bằng lệnh chat {@code /vinhdu} (hoặc {@code /honor}): dòng trạng thái là
 * điểm còn tiêu được; mỗi món một dòng (giá, số lần đổi tuần này) kèm nút "Đổi". Server kiểm điểm và trần tuần.
 */
final class HonorShopCoordinator implements WindowFlow {

    private static final List<String> COMMANDS = List.of("/vinhdu", "/honor");
    private static final String OUTCOME_PREFIX = "BUY_HONOR_ITEM_OUTCOME_";
    /** Cửa hàng thật có 5 món — gói dị dạng không được kéo theo hàng nghìn dòng. */
    private static final int MAX_ROWS = 20;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final I18NBundle i18n = KageI18n.load();
    private final List<Long> rowItems = new ArrayList<>();

    HonorShopCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
        panel.setStatus(text("honor.hint"));
        feeds.chat().addSlashCommands(this::onSlash);
    }

    static HonorShopCoordinator create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new HonorShopCoordinator(
                new ReportPanel(skin, "honor.title", Input.Keys.UNKNOWN, List.of()), sender, feeds);
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
        sender.sendReadRequest(Opcode.OPCODE_GET_HONOR_SHOP_REQUEST_VALUE, GetHonorShopRequest.getDefaultInstance());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowItems.size()) {
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_BUY_HONOR_ITEM_COMMAND_VALUE,
                BuyHonorItemCommand.newBuilder()
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
        if (opcode == Opcode.OPCODE_GET_HONOR_SHOP_RESPONSE_VALUE) {
            GetHonorShopResponse shop = GetHonorShopResponse.parseFrom(frame.payload());
            show(shop);
            panel.setStatus(KageI18n.formatOrKey(i18n, "honor.available", shop.getAvailable()));
            return true;
        }
        if (opcode != Opcode.OPCODE_BUY_HONOR_ITEM_RESPONSE_VALUE) {
            return false;
        }
        BuyHonorItemResponse response = BuyHonorItemResponse.parseFrom(frame.payload());
        show(response.getShop());
        BuyHonorItemOutcome outcome = response.getOutcome();
        String suffix = outcome == BuyHonorItemOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        panel.setStatus(text("honor.outcome." + suffix) + " "
                + KageI18n.formatOrKey(
                        i18n, "honor.available", response.getShop().getAvailable()));
        if (outcome == BuyHonorItemOutcome.BUY_HONOR_ITEM_OUTCOME_OK) {
            sender.listInventory(); // món vừa vào túi
        }
        return true;
    }

    private void show(GetHonorShopResponse shop) {
        rowItems.clear();
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (HonorShopItemView item : shop.getItemsList()) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            String name = itemCatalog
                    .nameKey(item.getItemTemplateId())
                    .map(this::text)
                    .orElseGet(() -> "#" + item.getItemTemplateId());
            String line = KageI18n.formatOrKey(
                    i18n,
                    "honor.row",
                    name,
                    item.getQuantity(),
                    item.getPrice(),
                    item.getBoughtThisWeek(),
                    item.getWeeklyLimit());
            boolean buyable =
                    item.getBoughtThisWeek() < item.getWeeklyLimit() && shop.getAvailable() >= item.getPrice();
            rows.add(new ReportPanel.Row(line, buyable, "honor.action.buy"));
            rowItems.add(item.getItemTemplateId());
        }
        panel.setRows(rows);
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
