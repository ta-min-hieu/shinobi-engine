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
import dev.kage.protocol.CombineGemsCommand;
import dev.kage.protocol.CombineGemsOutcome;
import dev.kage.protocol.CombineGemsResponse;
import dev.kage.protocol.GemRecipeView;
import dev.kage.protocol.GetGemRecipesRequest;
import dev.kage.protocol.GetGemRecipesResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Ghép Linh Ngọc (ADR-161) — cửa sổ mở bằng lệnh chat {@code /ghepngoc} (hoặc {@code /gemcombine}): mỗi công thức một
 * dòng (N viên + vàng ⇒ viên bậc trên, đang có bao nhiêu) kèm nút "Ghép". Server kiểm ngọc, vàng, túi; client chỉ bày.
 */
final class GemCombineCoordinator implements WindowFlow {

    private static final List<String> COMMANDS = List.of("/ghepngoc", "/gemcombine");
    private static final String OUTCOME_PREFIX = "COMBINE_GEMS_OUTCOME_";
    /** Bảng thật có 8 công thức — gói dị dạng không được kéo theo hàng nghìn dòng. */
    private static final int MAX_ROWS = 20;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final I18NBundle i18n = KageI18n.load();
    private final List<Long> rowSources = new ArrayList<>();

    GemCombineCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
        panel.setStatus(text("gem_combine.hint"));
        feeds.chat().addSlashCommands(this::onSlash);
    }

    static GemCombineCoordinator create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new GemCombineCoordinator(
                new ReportPanel(skin, "gem_combine.title", Input.Keys.UNKNOWN, List.of()), sender, feeds);
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
        sender.sendReadRequest(Opcode.OPCODE_GET_GEM_RECIPES_REQUEST_VALUE, GetGemRecipesRequest.getDefaultInstance());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowSources.size()) {
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_COMBINE_GEMS_COMMAND_VALUE,
                CombineGemsCommand.newBuilder()
                        .setSourceItemTemplateId(rowSources.get(rowIndex))
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
        if (opcode == Opcode.OPCODE_GET_GEM_RECIPES_RESPONSE_VALUE) {
            show(GetGemRecipesResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode != Opcode.OPCODE_COMBINE_GEMS_RESPONSE_VALUE) {
            return false;
        }
        CombineGemsResponse response = CombineGemsResponse.parseFrom(frame.payload());
        show(response.getRecipes());
        panel.setStatus(describe(response));
        if (response.getOutcome() == CombineGemsOutcome.COMBINE_GEMS_OUTCOME_OK) {
            sender.listInventory(); // ngọc mới vào túi, ngọc cũ ra
        }
        return true;
    }

    /** Dòng trạng thái sau một lần ghép — tách ra để test không cần màn hình. */
    String describe(CombineGemsResponse response) {
        CombineGemsOutcome outcome = response.getOutcome();
        if (outcome == CombineGemsOutcome.COMBINE_GEMS_OUTCOME_OK) {
            return KageI18n.formatOrKey(i18n, "gem_combine.outcome.ok", nameOf(response.getResultItemTemplateId()));
        }
        String suffix = outcome == CombineGemsOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return text("gem_combine.outcome." + suffix);
    }

    private void show(GetGemRecipesResponse recipes) {
        rowSources.clear();
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (GemRecipeView recipe : recipes.getRecipesList()) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            String line = KageI18n.formatOrKey(
                    i18n,
                    "gem_combine.row",
                    recipe.getSourceQuantity(),
                    nameOf(recipe.getSourceItemTemplateId()),
                    recipe.getGoldFee(),
                    nameOf(recipe.getResultItemTemplateId()),
                    recipe.getOwned());
            rows.add(new ReportPanel.Row(
                    line, recipe.getOwned() >= recipe.getSourceQuantity(), "gem_combine.action.combine"));
            rowSources.add(recipe.getSourceItemTemplateId());
        }
        panel.setRows(rows);
    }

    private String nameOf(long itemTemplateId) {
        return itemCatalog.nameKey(itemTemplateId).map(this::text).orElseGet(() -> "#" + itemTemplateId);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        rowSources.clear();
        panel.setRows(List.of());
    }
}
