package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.InventoryItemView;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SocketGemCommand;
import dev.kage.protocol.SocketGemOutcome;
import dev.kage.protocol.SocketGemResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Khảm Linh Ngọc (phím V, ADR-064 D7): chọn một trang bị ("Chọn"), rồi bấm "Khảm" ở viên ngọc muốn dùng.
 * Thành/hỏng, phí, bảo hiểm — server phán quyết; lớp này chỉ hiện kết quả.
 *
 * <p>Đọc chung {@code ListInventoryResponse} với túi đồ ({@code PanelHub#dispatch} hỏi mọi luồng). "Viên
 * nào là ngọc" chỉ để LỌC danh sách hiển thị (mã {@code spirit_jade_*} trong catalog) — server vẫn kiểm lại.
 */
final class GemCoordinator implements WindowFlow {

    private static final String GEM_CODE_PREFIX = "spirit_jade_";

    private static final Map<SocketGemOutcome, String> OUTCOME_KEYS = new EnumMap<>(SocketGemOutcome.class);

    static {
        OUTCOME_KEYS.put(SocketGemOutcome.SOCKET_GEM_OUTCOME_SUCCESS, "gem.status.success");
        OUTCOME_KEYS.put(SocketGemOutcome.SOCKET_GEM_OUTCOME_FAILED, "gem.status.failed");
        OUTCOME_KEYS.put(SocketGemOutcome.SOCKET_GEM_OUTCOME_DUPLICATE_REQUEST, "gem.status.success");
        OUTCOME_KEYS.put(SocketGemOutcome.SOCKET_GEM_OUTCOME_NOT_FOUND, "gem.error.not_found");
        OUTCOME_KEYS.put(SocketGemOutcome.SOCKET_GEM_OUTCOME_NOT_EQUIPMENT, "gem.error.not_equipment");
        OUTCOME_KEYS.put(SocketGemOutcome.SOCKET_GEM_OUTCOME_NOT_A_GEM, "gem.error.not_a_gem");
        OUTCOME_KEYS.put(SocketGemOutcome.SOCKET_GEM_OUTCOME_INSUFFICIENT_GOLD, "gem.error.insufficient_gold");
    }

    /** Hành động của một dòng: chọn trang bị, hoặc khảm viên ngọc này vào món đã chọn. */
    private record Action(boolean pickTarget, long itemId) {}

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final IntConsumer onOwnMaxHpChanged;
    private final I18NBundle i18n = KageI18n.load();

    private final List<InventoryItemView> equipment = new ArrayList<>();
    private final List<InventoryItemView> gems = new ArrayList<>();

    /** Cùng chỉ số với {@link ReportPanel#setRows}; {@code null} = dòng không nút. */
    private final List<Action> actions = new ArrayList<>();

    private long targetItemId;

    /** {@code sequenceId} của lệnh khảm đang chờ; {@code 0} = không có. */
    private int pendingSequenceId;

    GemCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        this.onOwnMaxHpChanged = feeds.onOwnMaxHpChanged();
        panel.setOnRefresh(sender::listInventory);
        panel.setOnRowAction(this::onRowAction);
        panel.setStatus(text("gem.hint.open"));
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= actions.size() || actions.get(rowIndex) == null) {
            return;
        }
        Action action = actions.get(rowIndex);
        if (action.pickTarget()) {
            targetItemId = action.itemId();
            redraw();
            return;
        }
        pendingSequenceId = sender.sendReadRequest(
                Opcode.OPCODE_SOCKET_GEM_COMMAND_VALUE,
                SocketGemCommand.newBuilder()
                        .setTargetItemId(targetItemId)
                        .setGemItemId(action.itemId())
                        .setIdempotencyKey(UUID.randomUUID().toString())
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
        if (opcode == Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE) {
            onBag(ListInventoryResponse.parseFrom(frame.payload()));
            return false; // KHÔNG nuốt — túi đồ, kho, phường thị cũng cần gói này
        }
        if (opcode == Opcode.OPCODE_SOCKET_GEM_RESPONSE_VALUE) {
            pendingSequenceId = 0;
            onSocketed(SocketGemResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode == Opcode.OPCODE_ERROR_VALUE) {
            return onRejected(ErrorResponse.parseFrom(frame.payload()));
        }
        return false;
    }

    /**
     * Món/ngọc đang chào trong giao dịch (docs/README.md dòng 82) tới bằng {@code ErrorResponse}, không bằng
     * {@code SocketGemOutcome} — cùng đường {@code InventoryCoordinator#onEnhanceRejected}. Lỗi của lệnh KHÁC
     * trả {@code false}: opcode 5 dùng chung cho cả giao thức.
     */
    private boolean onRejected(ErrorResponse error) {
        if (pendingSequenceId == 0 || error.getRelatedSequenceId() != pendingSequenceId) {
            return false;
        }
        pendingSequenceId = 0;
        panel.setStatus(text(
                "error.item_in_trade".equals(error.getErrorKey()) ? "gem.error.item_in_trade" : "gem.error.unknown"));
        return true;
    }

    private void onSocketed(SocketGemResponse response) {
        String status = text(OUTCOME_KEYS.getOrDefault(response.getOutcome(), "gem.error.unknown"));
        if (response.getOutcome() == SocketGemOutcome.SOCKET_GEM_OUTCOME_FAILED) {
            status = status + " " + format("gem.status.pity", response.getFailuresInARow(), response.getPityAfter());
        }
        panel.setStatus(status);
        if (response.getMaxHp() > 0) {
            onOwnMaxHpChanged.accept(response.getMaxHp());
        }
        sender.listInventory();
    }

    private void onBag(ListInventoryResponse response) {
        equipment.clear();
        gems.clear();
        for (InventoryItemView item : response.getItemsList()) {
            if (itemCatalog.equipSlot(item.getItemTemplateId()) != EquipmentSlot.EQUIPMENT_SLOT_UNSPECIFIED) {
                equipment.add(item);
            } else if (isGem(item.getItemTemplateId())) {
                gems.add(item);
            }
        }
        if (panel.isOpen()) {
            redraw();
        }
    }

    private boolean isGem(long itemTemplateId) {
        return itemCatalog
                .byId(itemTemplateId)
                .map(entry -> entry.getCode().startsWith(GEM_CODE_PREFIX))
                .orElse(false);
    }

    private void redraw() {
        List<ReportPanel.Row> rows = new ArrayList<>();
        actions.clear();
        rows.add(new ReportPanel.Row(text("gem.section.equipment"), true));
        actions.add(null);
        String targetName = text("gem.target.none");
        for (InventoryItemView item : equipment) {
            boolean chosen = item.getItemId() == targetItemId;
            if (chosen) {
                targetName = name(item.getItemTemplateId());
            }
            rows.add(new ReportPanel.Row(equipmentText(item, chosen), chosen, chosen ? "" : "gem.action.pick"));
            actions.add(chosen ? null : new Action(true, item.getItemId()));
        }
        rows.add(new ReportPanel.Row(format("gem.section.gems", targetName), true));
        actions.add(null);
        boolean canSocket = targetItemId != 0L;
        for (InventoryItemView gem : gems) {
            String label = name(gem.getItemTemplateId()) + (gem.getQuantity() > 1 ? " x" + gem.getQuantity() : "");
            rows.add(new ReportPanel.Row(label, false, canSocket ? "gem.action.socket" : ""));
            actions.add(canSocket ? new Action(false, gem.getItemId()) : null);
        }
        panel.setRows(rows);
    }

    /** {@code [Đang chọn] Tên +3 — Ngọc: X}; dấu bằng CHỮ, không chỉ bằng màu. */
    private String equipmentText(InventoryItemView item, boolean chosen) {
        String enhance = item.getEnhanceLevel() > 0 ? " +" + item.getEnhanceLevel() : "";
        String socket =
                item.getSocketGemTemplateId() == 0L ? text("gem.socket.empty") : name(item.getSocketGemTemplateId());
        String row = format("gem.row.equipment", name(item.getItemTemplateId()) + enhance, socket);
        return chosen ? text("gem.row.chosen_prefix") + " " + row : row;
    }

    private String name(long itemTemplateId) {
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
        equipment.clear();
        gems.clear();
        actions.clear();
        targetItemId = 0L;
        pendingSequenceId = 0;
        panel.setRows(List.of());
        panel.setStatus(text("gem.hint.open"));
    }
}
