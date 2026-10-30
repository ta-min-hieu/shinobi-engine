package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.AwaitingRevivalEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.ReviveCommand;
import dev.kage.protocol.ReviveResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Hồi sinh tại chỗ (ADR-117) — server báo "đang nằm chờ" (321) thì tự mở cửa sổ hai lựa chọn: về điểm hồi sinh, hoặc
 * dùng Hồi Hồn Đan đứng dậy tại chỗ (không có nút này nếu bị người chơi hạ). Gói hồi sinh (305) tới thì đóng — kể cả
 * khi server tự đưa về sau 30 giây. Server quyết hết (đủ đan chưa, còn nằm không).
 */
final class RevivalCoordinator implements WindowFlow {

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final I18NBundle i18n = KageI18n.load();
    private final List<Boolean> rowInPlace = new ArrayList<>();

    RevivalCoordinator(ReportPanel panel, WorldCommandSender sender) {
        this.panel = panel;
        this.sender = sender;
        panel.setOnRowAction(this::choose);
    }

    private void choose(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowInPlace.size()) {
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_REVIVE_COMMAND_VALUE,
                ReviveCommand.newBuilder().setInPlace(rowInPlace.get(rowIndex)).build());
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
        if (opcode == Opcode.OPCODE_AWAITING_REVIVAL_EVENT_VALUE) {
            show(AwaitingRevivalEvent.parseFrom(frame.payload()));
            return true;
        }
        if (opcode == Opcode.OPCODE_REVIVE_RESPONSE_VALUE) {
            String name = ReviveResponse.parseFrom(frame.payload()).getOutcome().name();
            panel.setStatus(text("revive.outcome."
                    + name.substring("REVIVE_OUTCOME_".length()).toLowerCase(Locale.ROOT)));
            return true;
        }
        if (opcode == Opcode.OPCODE_CHARACTER_RESPAWNED_EVENT_VALUE) {
            panel.close();
            return false; // KHÔNG nuốt — màn hình thế giới đặt vị trí/máu theo gói này
        }
        return false;
    }

    private void show(AwaitingRevivalEvent event) {
        List<ReportPanel.Row> rows = new ArrayList<>();
        rowInPlace.clear();
        rows.add(new ReportPanel.Row(text("revive.row.town"), false, "revive.action.town"));
        rowInPlace.add(false);
        if (event.getCanReviveInPlace()) {
            rows.add(new ReportPanel.Row(text("revive.row.pill"), false, "revive.action.pill"));
            rowInPlace.add(true);
        }
        panel.setRows(rows);
        panel.setStatus(KageI18n.formatOrKey(
                i18n, event.getCanReviveInPlace() ? "revive.status" : "revive.status.pvp", event.getSeconds()));
        if (!panel.isOpen()) {
            panel.show();
        }
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        rowInPlace.clear();
        panel.setRows(List.of());
    }
}
