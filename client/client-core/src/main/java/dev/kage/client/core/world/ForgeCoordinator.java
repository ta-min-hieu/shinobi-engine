package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.CraftItemOutcome;
import dev.kage.protocol.ForgeItemCommand;
import dev.kage.protocol.ForgeItemResponse;
import dev.kage.protocol.ListForgeRecipesRequest;
import dev.kage.protocol.ListForgeRecipesResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RecipeMaterialView;
import dev.kage.protocol.RecipeView;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Luyện khí (ADR-120) — cửa sổ mở bằng lệnh chat {@code /luyenkhi}, mỗi công thức một dòng kèm nút "Luyện". Lò đặt ở
 * trưởng lão như lò đan; server kiểm đứng gần, cấp, nguyên liệu, linh thạch, túi và TUNG phẩm chất — client chỉ báo
 * lại phẩm chất vừa ra.
 */
final class ForgeCoordinator implements WindowFlow {

    private static final String COMMAND = "/luyenkhi";

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final I18NBundle i18n = KageI18n.load();

    /** Mã công thức của từng dòng, cùng chỉ số với {@link ReportPanel#setRows}. */
    private final List<String> rowCodes = new ArrayList<>();

    ForgeCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
        panel.setStatus(text("forge.hint"));
        feeds.chat().addSlashCommands(this::onSlash);
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
                Opcode.OPCODE_LIST_FORGE_RECIPES_REQUEST_VALUE, ListForgeRecipesRequest.getDefaultInstance());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowCodes.size()) {
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_FORGE_ITEM_COMMAND_VALUE,
                ForgeItemCommand.newBuilder()
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
        if (opcode == Opcode.OPCODE_LIST_FORGE_RECIPES_RESPONSE_VALUE) {
            show(ListForgeRecipesResponse.parseFrom(frame.payload()));
            // ADR-131 — thợ rèn mở lò qua hội thoại: danh sách tới mà cửa sổ chưa mở thì hiện (không xin lại).
            if (!panel.isOpen()) {
                panel.show();
            }
            return true;
        }
        if (opcode == Opcode.OPCODE_FORGE_ITEM_RESPONSE_VALUE) {
            ForgeItemResponse response = ForgeItemResponse.parseFrom(frame.payload());
            if (response.getOutcome() == CraftItemOutcome.CRAFT_ITEM_OUTCOME_OK) {
                String suffix = response.getQuality()
                        .name()
                        .substring("FORGE_QUALITY_".length())
                        .toLowerCase(Locale.ROOT);
                panel.setStatus(text("forge.result." + suffix));
                sender.listInventory(); // túi vừa đổi
            } else if (response.getOutcome() == CraftItemOutcome.CRAFT_ITEM_OUTCOME_UNSPECIFIED) {
                panel.setStatus(text("crafting.error.unknown"));
            } else {
                String suffix = response.getOutcome()
                        .name()
                        .substring("CRAFT_ITEM_OUTCOME_".length())
                        .toLowerCase(Locale.ROOT);
                panel.setStatus(text("crafting.error." + suffix));
            }
            return true;
        }
        return false;
    }

    private void show(ListForgeRecipesResponse response) {
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
            rows.add(new ReportPanel.Row(line, false, "forge.action.forge"));
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
        panel.setStatus(text("forge.hint"));
    }
}
