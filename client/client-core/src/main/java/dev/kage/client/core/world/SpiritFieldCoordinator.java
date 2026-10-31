package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.FieldSeedView;
import dev.kage.protocol.GetSpiritFieldRequest;
import dev.kage.protocol.GetSpiritFieldResponse;
import dev.kage.protocol.HarvestPlotCommand;
import dev.kage.protocol.HarvestPlotResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PlantSeedCommand;
import dev.kage.protocol.PlantSeedResponse;
import dev.kage.protocol.SpiritFieldOutcome;
import dev.kage.protocol.SpiritPlotView;
import dev.kage.protocol.UnlockPlotCommand;
import dev.kage.protocol.UnlockPlotResponse;
import dev.kage.protocol.WaterPlotCommand;
import dev.kage.protocol.WaterPlotResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Linh điền (ADR-119) — cửa sổ mở bằng lệnh chat {@code /linhdien}. Tab "ruộng của tôi": ô trống có một dòng Gieo cho
 * mỗi loại hạt, ô chín có nút Thu hoạch, dòng cuối mở thêm ô. Tab "ruộng bạn bè": gõ tên bạn vào ô nhập rồi Làm mới,
 * ô đang lớn chưa ai tưới có nút Tưới. Đếm ngược theo {@code server_time_ms}, không tin đồng hồ máy mình; server kiểm
 * lại hết (có hạt trong túi, đã chín, là bạn bè...).
 */
final class SpiritFieldCoordinator implements WindowFlow {

    static final List<String> TAB_KEYS = List.of("field.tab.mine", "field.tab.friend");

    private static final String COMMAND = "/linhdien";
    private static final int TAB_MINE = 0;
    private static final long MILLIS_PER_MINUTE = 60_000L;

    /** Việc của một dòng có nút, kèm opcode lệnh gửi đi. */
    private enum Kind {
        PLANT(Opcode.OPCODE_PLANT_SEED_COMMAND_VALUE),
        HARVEST(Opcode.OPCODE_HARVEST_PLOT_COMMAND_VALUE),
        WATER(Opcode.OPCODE_WATER_PLOT_COMMAND_VALUE),
        UNLOCK(Opcode.OPCODE_UNLOCK_PLOT_COMMAND_VALUE);

        private final int opcode;

        Kind(int opcode) {
            this.opcode = opcode;
        }
    }

    /** Hành động của một dòng có nút. */
    private record Action(Kind kind, int plotIndex, long seedTemplateId) {}

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final I18NBundle i18n = KageI18n.load();
    private final List<Action> actions = new ArrayList<>();
    private GetSpiritFieldResponse shown = GetSpiritFieldResponse.getDefaultInstance();
    private int tab = TAB_MINE;

    SpiritFieldCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        panel.setOnRefresh(this::request);
        panel.setOnTabSelected(index -> {
            tab = index;
            shown = GetSpiritFieldResponse.getDefaultInstance();
            panel.setStatus(text(tab == TAB_MINE ? "field.hint.mine" : "field.hint.friend"));
            request();
        });
        panel.setOnRowAction(this::onRowAction);
        panel.enableInput("field.input");
        panel.setStatus(text("field.hint.mine"));
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
        String owner = tab == TAB_MINE ? "" : panel.inputText().strip();
        if (tab != TAB_MINE && owner.isEmpty()) {
            panel.setRows(List.of());
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_SPIRIT_FIELD_REQUEST_VALUE,
                GetSpiritFieldRequest.newBuilder().setOwnerName(owner).build());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= actions.size() || actions.get(rowIndex) == null) {
            return;
        }
        Action action = actions.get(rowIndex);
        MessageLite command =
                switch (action.kind()) {
                    case PLANT ->
                        PlantSeedCommand.newBuilder()
                                .setPlotIndex(action.plotIndex())
                                .setSeedItemTemplateId(action.seedTemplateId())
                                .build();
                    case HARVEST ->
                        HarvestPlotCommand.newBuilder()
                                .setPlotIndex(action.plotIndex())
                                .build();
                    case WATER ->
                        WaterPlotCommand.newBuilder()
                                .setOwnerName(shown.getOwnerName())
                                .setPlotIndex(action.plotIndex())
                                .build();
                    case UNLOCK -> UnlockPlotCommand.getDefaultInstance();
                };
        sender.sendReadRequest(action.kind().opcode, command);
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
        byte[] payload = frame.payload();
        if (opcode == Opcode.OPCODE_GET_SPIRIT_FIELD_RESPONSE_VALUE) {
            GetSpiritFieldResponse response = GetSpiritFieldResponse.parseFrom(payload);
            if (response.getOutcome() != SpiritFieldOutcome.SPIRIT_FIELD_OUTCOME_OK) {
                settle(response.getOutcome());
                panel.setRows(List.of());
                return true;
            }
            shown = response;
            redraw();
            return true;
        }
        if (opcode == Opcode.OPCODE_PLANT_SEED_RESPONSE_VALUE) {
            settle(PlantSeedResponse.parseFrom(payload).getOutcome());
            return true;
        }
        if (opcode == Opcode.OPCODE_HARVEST_PLOT_RESPONSE_VALUE) {
            settle(HarvestPlotResponse.parseFrom(payload).getOutcome());
            return true;
        }
        if (opcode == Opcode.OPCODE_WATER_PLOT_RESPONSE_VALUE) {
            settle(WaterPlotResponse.parseFrom(payload).getOutcome());
            return true;
        }
        if (opcode == Opcode.OPCODE_UNLOCK_PLOT_RESPONSE_VALUE) {
            settle(UnlockPlotResponse.parseFrom(payload).getOutcome());
            return true;
        }
        return false;
    }

    private void settle(SpiritFieldOutcome outcome) {
        String suffix =
                outcome.name().substring("SPIRIT_FIELD_OUTCOME_".length()).toLowerCase(Locale.ROOT);
        panel.setStatus(text("field.outcome." + suffix));
        if (outcome == SpiritFieldOutcome.SPIRIT_FIELD_OUTCOME_OK) {
            request();
        }
    }

    private void redraw() {
        if (!panel.isOpen()) {
            return;
        }
        boolean own = tab == TAB_MINE;
        List<ReportPanel.Row> rows = new ArrayList<>();
        actions.clear();
        for (int index = 0; index < shown.getPlots(); index++) {
            Optional<SpiritPlotView> plot = planted(index);
            if (plot.isPresent()) {
                addPlanted(rows, plot.get(), own);
            } else {
                addEmpty(rows, index, own);
            }
        }
        if (own && shown.getUnlockCost() > 0) {
            add(
                    rows,
                    format("field.row.unlock", shown.getUnlockCost()),
                    "field.action.unlock",
                    new Action(Kind.UNLOCK, 0, 0));
        }
        panel.setRows(rows);
    }

    private void addEmpty(List<ReportPanel.Row> rows, int index, boolean own) {
        if (!own) {
            add(rows, format("field.row.empty", index + 1), null, null);
            return;
        }
        for (FieldSeedView seed : shown.getSeedsList()) {
            add(
                    rows,
                    format("field.row.plant", index + 1, itemName(seed.getItemTemplateId()), seed.getGrowMinutes()),
                    "field.action.plant",
                    new Action(Kind.PLANT, index, seed.getItemTemplateId()));
        }
    }

    private void addPlanted(List<ReportPanel.Row> rows, SpiritPlotView plot, boolean own) {
        String herb = itemName(plot.getYieldItemTemplateId());
        String watered = plot.getWatered() ? text("field.watered") : "";
        long left = plot.getRipeAtEpochMs() - shown.getServerTimeMs();
        int number = plot.getIndex() + 1;
        if (left <= 0) {
            add(
                    rows,
                    format("field.row.ripe", number, herb) + watered,
                    own ? "field.action.harvest" : null,
                    own ? new Action(Kind.HARVEST, plot.getIndex(), 0) : null);
            return;
        }
        long minutes = (left + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE;
        boolean canWater = !own && !plot.getWatered();
        add(
                rows,
                format("field.row.growing", number, herb, minutes) + watered,
                canWater ? "field.action.water" : null,
                canWater ? new Action(Kind.WATER, plot.getIndex(), 0) : null);
    }

    private Optional<SpiritPlotView> planted(int index) {
        for (SpiritPlotView plot : shown.getPlantedList()) {
            if (plot.getIndex() == index) {
                return Optional.of(plot);
            }
        }
        return Optional.empty();
    }

    private void add(List<ReportPanel.Row> rows, String label, String actionKey, Action action) {
        rows.add(new ReportPanel.Row(label, false, actionKey));
        actions.add(action);
    }

    private String itemName(long itemTemplateId) {
        return itemCatalog.nameKey(itemTemplateId).map(this::text).orElseGet(() -> "#" + itemTemplateId);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    private String format(String key, Object... args) {
        return KageI18n.formatOrKey(i18n, key, args);
    }

    @Override
    public void reset() {
        panel.close();
        actions.clear();
        shown = GetSpiritFieldResponse.getDefaultInstance();
        tab = TAB_MINE;
        panel.setRows(List.of());
    }
}
