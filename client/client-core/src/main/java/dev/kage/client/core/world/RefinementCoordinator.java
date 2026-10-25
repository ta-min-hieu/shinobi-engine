package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ListRefinementsRequest;
import dev.kage.protocol.ListRefinementsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RefineItemCommand;
import dev.kage.protocol.RefineItemResponse;
import dev.kage.protocol.RefinedItemView;
import dev.kage.protocol.RefinementLineView;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tẩy luyện pháp khí (ADR-111 D5) — {@link ReportPanel} phím X: mỗi món đang mặc một dòng tiêu đề (nút Tẩy) + các dòng
 * chỉ số (nút "Giữ dòng này rồi tẩy"). Server gieo dòng và trừ đá; client chỉ gửi ý định.
 */
final class RefinementCoordinator implements WindowFlow {

    /** Không khoá dòng — khớp {@code RefinementPolicy.NO_LOCK} của server. */
    private static final int NO_LOCK = -1;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final IntConsumer onOwnMaxHpChanged;
    private final I18NBundle i18n = KageI18n.load();

    /** Cùng chỉ số với {@link ReportPanel#setRows}; {@code null} = dòng không nút. */
    private final List<RefineItemCommand> actions = new ArrayList<>();

    RefinementCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        this.onOwnMaxHpChanged = feeds.onOwnMaxHpChanged();
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
    }

    private void request() {
        sender.sendReadRequest(
                Opcode.OPCODE_LIST_REFINEMENTS_REQUEST_VALUE, ListRefinementsRequest.getDefaultInstance());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= actions.size() || actions.get(rowIndex) == null) {
            return;
        }
        sender.sendReadRequest(Opcode.OPCODE_REFINE_ITEM_COMMAND_VALUE, actions.get(rowIndex));
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
        if (opcode == Opcode.OPCODE_LIST_REFINEMENTS_RESPONSE_VALUE) {
            ListRefinementsResponse response = ListRefinementsResponse.parseFrom(frame.payload());
            panel.setStatus(
                    format("refine.hint", response.getStones(), response.getStoneCost(), response.getLockedCost()));
            show(response);
            return true;
        }
        if (opcode == Opcode.OPCODE_REFINE_ITEM_RESPONSE_VALUE) {
            RefineItemResponse response = RefineItemResponse.parseFrom(frame.payload());
            String name = response.getOutcome().name();
            panel.setStatus(text("refine.outcome."
                            + name.replace("REFINE_ITEM_OUTCOME_", "").toLowerCase(Locale.ROOT))
                    + " " + format("refine.stones", response.getStatus().getStones()));
            if (response.getMaxHp() > 0) {
                onOwnMaxHpChanged.accept(response.getMaxHp());
            }
            show(response.getStatus());
            return true;
        }
        return false;
    }

    private void show(ListRefinementsResponse response) {
        List<ReportPanel.Row> rows = new ArrayList<>();
        actions.clear();
        if (response.getItemsCount() == 0) {
            rows.add(new ReportPanel.Row(text("refine.empty"), false));
            actions.add(null);
        }
        for (RefinedItemView item : response.getItemsList()) {
            String itemName = itemCatalog
                    .nameKey(item.getItemTemplateId())
                    .map(this::text)
                    .orElseGet(() -> "#" + item.getItemTemplateId());
            rows.add(new ReportPanel.Row(
                    format("refine.row.item", itemName, item.getLinesCount()), true, "refine.action.refine"));
            actions.add(command(item.getItemId(), NO_LOCK));
            for (RefinementLineView line : item.getLinesList()) {
                rows.add(new ReportPanel.Row(
                        "  " + format("refine.line." + line.getStat().toLowerCase(Locale.ROOT), line.getValue()),
                        false,
                        "refine.action.lock"));
                actions.add(command(item.getItemId(), line.getIndex()));
            }
        }
        panel.setRows(rows);
    }

    private static RefineItemCommand command(long itemId, int lockedLine) {
        return RefineItemCommand.newBuilder()
                .setItemId(itemId)
                .setLockedLine(lockedLine)
                .build();
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
        panel.setRows(List.of());
        actions.clear();
    }
}
