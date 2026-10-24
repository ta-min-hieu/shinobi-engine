package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetSpiritVeinStatusRequest;
import dev.kage.protocol.GetSpiritVeinStatusResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SpiritVeinScoreView;
import dev.kage.protocol.SpiritVeinView;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tranh đoạt Linh Mạch (ADR-090) — {@link ReportPanel} chỉ đọc: mỗi Linh Mạch một khối (vùng, đang mở/còn bao lâu,
 * bang đang giữ, bảng điểm khung đang mở). Mở từ nút trong cửa sổ Bang (G). Server chấm điểm, client chỉ hiển thị.
 */
final class SpiritVeinCoordinator implements WindowFlow {

    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long MINUTES_PER_HOUR = 60L;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final I18NBundle i18n = KageI18n.load();

    SpiritVeinCoordinator(ReportPanel panel, WorldCommandSender sender) {
        this.panel = panel;
        this.sender = sender;
        panel.setCanOpen(() -> false);
        panel.setOnRefresh(this::request);
    }

    private void request() {
        sender.sendReadRequest(
                Opcode.OPCODE_GET_SPIRIT_VEIN_STATUS_REQUEST_VALUE, GetSpiritVeinStatusRequest.getDefaultInstance());
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
        if (frame.header().opcode() != Opcode.OPCODE_GET_SPIRIT_VEIN_STATUS_RESPONSE_VALUE) {
            return false;
        }
        show(GetSpiritVeinStatusResponse.parseFrom(frame.payload()));
        if (!panel.isOpen()) {
            panel.show();
        }
        return true;
    }

    private void show(GetSpiritVeinStatusResponse status) {
        panel.setStatus(text(status.getVeinsCount() == 0 ? "spiritvein.none" : "spiritvein.hint"));
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (SpiritVeinView vein : status.getVeinsList()) {
            String when = duration(vein.getSecondsUntilChange());
            rows.add(new ReportPanel.Row(
                    KageI18n.formatOrKey(
                            i18n,
                            vein.getActive() ? "spiritvein.row.active" : "spiritvein.row.idle",
                            text(vein.getNameKey()),
                            Math.round(vein.getMinX()),
                            Math.round(vein.getMaxX()),
                            when),
                    true,
                    ""));
            rows.add(row(
                    vein.getHolderGuildName().isEmpty()
                            ? text("spiritvein.holder.none")
                            : KageI18n.formatOrKey(i18n, "spiritvein.holder", vein.getHolderGuildName())));
            for (SpiritVeinScoreView score : vein.getScoresList()) {
                rows.add(row(KageI18n.formatOrKey(i18n, "spiritvein.score", score.getGuildName(), score.getPoints())));
            }
        }
        panel.setRows(rows);
    }

    private String duration(long seconds) {
        long minutes = Math.max(0L, seconds) / SECONDS_PER_MINUTE;
        return KageI18n.formatOrKey(
                i18n, "spiritvein.duration", minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR);
    }

    private static ReportPanel.Row row(String text) {
        return new ReportPanel.Row(text, false, "");
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        panel.setRows(List.of());
    }
}
