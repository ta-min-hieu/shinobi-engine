package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.BuyGuildShopItemCommand;
import dev.kage.protocol.BuyGuildShopItemOutcome;
import dev.kage.protocol.BuyGuildShopItemResponse;
import dev.kage.protocol.GuildShopItemView;
import dev.kage.protocol.ListGuildShopRequest;
import dev.kage.protocol.ListGuildShopResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Cửa hàng bang (ADR-080) — {@link ReportPanel} mỗi món một dòng kèm nút "Đổi". Mở từ nút "Hàng bang" của cửa sổ
 * Bang (G): không có phím riêng. Client không kiểm cấp bang hay điểm — chỉ hiện; server kiểm và trả outcome.
 */
final class GuildShopCoordinator implements WindowFlow {

    private static final Map<BuyGuildShopItemOutcome, String> OUTCOME_KEYS =
            new EnumMap<>(BuyGuildShopItemOutcome.class);

    static {
        OUTCOME_KEYS.put(BuyGuildShopItemOutcome.BUY_GUILD_SHOP_ITEM_OUTCOME_OK, "guildshop.status.ok");
        OUTCOME_KEYS.put(
                BuyGuildShopItemOutcome.BUY_GUILD_SHOP_ITEM_OUTCOME_NOT_IN_GUILD, "guildshop.error.not_in_guild");
        OUTCOME_KEYS.put(BuyGuildShopItemOutcome.BUY_GUILD_SHOP_ITEM_OUTCOME_UNKNOWN_ITEM, "guildshop.error.unknown");
        OUTCOME_KEYS.put(
                BuyGuildShopItemOutcome.BUY_GUILD_SHOP_ITEM_OUTCOME_GUILD_LEVEL_TOO_LOW,
                "guildshop.error.level_too_low");
        OUTCOME_KEYS.put(
                BuyGuildShopItemOutcome.BUY_GUILD_SHOP_ITEM_OUTCOME_NOT_ENOUGH_CONTRIBUTION,
                "guildshop.error.not_enough");
        OUTCOME_KEYS.put(BuyGuildShopItemOutcome.BUY_GUILD_SHOP_ITEM_OUTCOME_BAG_FULL, "guildshop.error.bag_full");
    }

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final I18NBundle i18n = KageI18n.load();

    /** Mẫu vật phẩm của từng dòng, cùng chỉ số với {@link ReportPanel#setRows}. */
    private final List<Long> rowItems = new ArrayList<>();

    GuildShopCoordinator(ReportPanel panel, WorldCommandSender sender, ItemCatalogCoordinator itemCatalog) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = itemCatalog;
        panel.setCanOpen(() -> false);
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
    }

    private void request() {
        sender.sendReadRequest(Opcode.OPCODE_LIST_GUILD_SHOP_REQUEST_VALUE, ListGuildShopRequest.getDefaultInstance());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowItems.size()) {
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_BUY_GUILD_SHOP_ITEM_COMMAND_VALUE,
                BuyGuildShopItemCommand.newBuilder()
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
        if (opcode == Opcode.OPCODE_LIST_GUILD_SHOP_RESPONSE_VALUE) {
            show(ListGuildShopResponse.parseFrom(frame.payload()), "");
            if (!panel.isOpen()) {
                panel.show();
            }
            return true;
        }
        if (opcode == Opcode.OPCODE_BUY_GUILD_SHOP_ITEM_RESPONSE_VALUE) {
            BuyGuildShopItemResponse response = BuyGuildShopItemResponse.parseFrom(frame.payload());
            show(response.getShop(), text(OUTCOME_KEYS.getOrDefault(response.getOutcome(), "guildshop.error.unknown")));
            if (response.getOutcome() == BuyGuildShopItemOutcome.BUY_GUILD_SHOP_ITEM_OUTCOME_OK) {
                sender.listInventory();
            }
            return true;
        }
        return false;
    }

    private void show(ListGuildShopResponse shop, String notice) {
        String wallet = shop.getInGuild()
                ? KageI18n.formatOrKey(i18n, "guildshop.wallet", shop.getContributionAvailable(), shop.getGuildLevel())
                : text("guildshop.error.not_in_guild");
        panel.setStatus(notice.isEmpty() ? wallet : notice + " " + wallet);
        rowItems.clear();
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (GuildShopItemView item : shop.getItemsList()) {
            String name = itemCatalog
                    .nameKey(item.getItemTemplateId())
                    .map(this::text)
                    .orElseGet(() -> "#" + item.getItemTemplateId());
            rows.add(new ReportPanel.Row(
                    KageI18n.formatOrKey(
                            i18n, "guildshop.row", name, item.getContributionCost(), item.getRequiredGuildLevel()),
                    false,
                    "guildshop.action.buy"));
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
