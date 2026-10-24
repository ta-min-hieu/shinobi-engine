package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.CraftItemCommand;
import dev.kage.protocol.CraftItemOutcome;
import dev.kage.protocol.CraftItemResponse;
import dev.kage.protocol.ListRecipesRequest;
import dev.kage.protocol.ListRecipesResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RecipeMaterialView;
import dev.kage.protocol.RecipeView;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lò đan (ADR-079) — {@link ReportPanel} mỗi công thức một dòng kèm nút "Luyện". Không phím mở: lò là một NƠI, server
 * gửi {@code ListRecipesResponse} khi người chơi chọn "nhờ trưởng lão luyện đan" trong hội thoại Chu Hoành. Client
 * không kiểm nguyên liệu/vàng/cấp — server kiểm và trả outcome.
 */
final class CraftingCoordinator implements WindowFlow {

    private static final Map<CraftItemOutcome, String> OUTCOME_KEYS = new EnumMap<>(CraftItemOutcome.class);

    static {
        OUTCOME_KEYS.put(CraftItemOutcome.CRAFT_ITEM_OUTCOME_OK, "crafting.status.ok");
        OUTCOME_KEYS.put(CraftItemOutcome.CRAFT_ITEM_OUTCOME_UNKNOWN_RECIPE, "crafting.error.unknown_recipe");
        OUTCOME_KEYS.put(CraftItemOutcome.CRAFT_ITEM_OUTCOME_LEVEL_TOO_LOW, "crafting.error.level_too_low");
        OUTCOME_KEYS.put(CraftItemOutcome.CRAFT_ITEM_OUTCOME_MISSING_MATERIALS, "crafting.error.missing_materials");
        OUTCOME_KEYS.put(CraftItemOutcome.CRAFT_ITEM_OUTCOME_INSUFFICIENT_GOLD, "crafting.error.insufficient_gold");
        OUTCOME_KEYS.put(CraftItemOutcome.CRAFT_ITEM_OUTCOME_BAG_FULL, "crafting.error.bag_full");
        OUTCOME_KEYS.put(CraftItemOutcome.CRAFT_ITEM_OUTCOME_TOO_FAR, "crafting.error.too_far");
    }

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final I18NBundle i18n = KageI18n.load();

    /** Mã công thức của từng dòng, cùng chỉ số với {@link ReportPanel#setRows}. */
    private final List<String> rowCodes = new ArrayList<>();

    CraftingCoordinator(ReportPanel panel, WorldCommandSender sender, ItemCatalogCoordinator itemCatalog) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = itemCatalog;
        panel.setCanOpen(() -> false);
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
        panel.setStatus(text("crafting.hint.open"));
    }

    private void request() {
        sender.sendReadRequest(Opcode.OPCODE_LIST_RECIPES_REQUEST_VALUE, ListRecipesRequest.getDefaultInstance());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowCodes.size()) {
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_CRAFT_ITEM_COMMAND_VALUE,
                CraftItemCommand.newBuilder()
                        .setRecipeCode(rowCodes.get(rowIndex))
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
        if (opcode == Opcode.OPCODE_LIST_RECIPES_RESPONSE_VALUE) {
            show(ListRecipesResponse.parseFrom(frame.payload()));
            panel.setStatus(text("crafting.hint.open"));
            if (!panel.isOpen()) {
                panel.show();
            }
            return true;
        }
        if (opcode == Opcode.OPCODE_CRAFT_ITEM_RESPONSE_VALUE) {
            CraftItemOutcome outcome =
                    CraftItemResponse.parseFrom(frame.payload()).getOutcome();
            panel.setStatus(text(OUTCOME_KEYS.getOrDefault(outcome, "crafting.error.unknown")));
            if (outcome == CraftItemOutcome.CRAFT_ITEM_OUTCOME_OK) {
                sender.listInventory(); // túi vừa đổi
            }
            return true;
        }
        return false;
    }

    private void show(ListRecipesResponse response) {
        rowCodes.clear();
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (RecipeView recipe : response.getRecipesList()) {
            List<String> parts = new ArrayList<>();
            for (RecipeMaterialView material : recipe.getMaterialsList()) {
                parts.add(nameOf(material.getItemTemplateId()) + " x" + material.getQuantity());
            }
            String line = KageI18n.formatOrKey(
                    i18n,
                    "crafting.row",
                    nameOf(recipe.getOutputItemTemplateId()),
                    recipe.getOutputQuantity(),
                    String.join(", ", parts),
                    recipe.getGoldCost(),
                    recipe.getRequiredLevel());
            rows.add(new ReportPanel.Row(line, false, "crafting.action.craft"));
            rowCodes.add(recipe.getCode());
        }
        panel.setRows(rows);
    }

    /** Tên tra catalog; chưa có catalog thì hiện dạng số, không bịa. */
    private String nameOf(long itemTemplateId) {
        return itemCatalog.nameKey(itemTemplateId).map(this::text).orElseGet(() -> "#" + itemTemplateId);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        rowCodes.clear();
        panel.setRows(List.of());
        panel.setStatus(text("crafting.hint.open"));
    }
}
