package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.protocol.GetGoldHistoryRequest;
import dev.kage.protocol.GetGoldHistoryResponse;
import dev.kage.protocol.GoldHistoryEntry;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Sổ vàng cá nhân (ADR-169) — cửa sổ mở bằng lệnh chat {@code /sovang} (hoặc {@code /goldlog}): 50 lần vàng đổi gần
 * nhất (30 ngày), mới nhất trên cùng — lúc đổi, số đổi, số dư sau đó, lý do. Chỉ đọc.
 */
final class GoldHistoryCoordinator implements WindowFlow {

    private static final List<String> COMMANDS = List.of("/sovang", "/goldlog");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    /** Server gửi tối đa 50 — gói dị dạng không được kéo theo hàng nghìn dòng. */
    private static final int MAX_ROWS = 60;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final I18NBundle i18n = KageI18n.load();

    GoldHistoryCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        panel.setOnRefresh(this::request);
        panel.setStatus(KageI18n.textOrKey(i18n, "gold_history.hint"));
        feeds.chat().addSlashCommands(this::onSlash);
    }

    static GoldHistoryCoordinator create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new GoldHistoryCoordinator(
                new ReportPanel(skin, "gold_history.title", Input.Keys.UNKNOWN, List.of()), sender, feeds);
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
        sender.sendReadRequest(
                Opcode.OPCODE_GET_GOLD_HISTORY_REQUEST_VALUE, GetGoldHistoryRequest.getDefaultInstance());
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
        if (frame.header().opcode() != Opcode.OPCODE_GET_GOLD_HISTORY_RESPONSE_VALUE) {
            return false;
        }
        GetGoldHistoryResponse response = GetGoldHistoryResponse.parseFrom(frame.payload());
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (GoldHistoryEntry entry : response.getEntriesList()) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            rows.add(new ReportPanel.Row(describe(entry, ZoneId.systemDefault()), entry.getDelta() > 0));
        }
        if (rows.isEmpty()) {
            rows.add(new ReportPanel.Row(KageI18n.textOrKey(i18n, "gold_history.empty"), false));
        }
        panel.setRows(rows);
        return true;
    }

    /** Một dòng sổ — tách ra để test không cần màn hình. Lý do lạ (server mới hơn client) hiện khoá thô. */
    String describe(GoldHistoryEntry entry, ZoneId zone) {
        String sign = entry.getDelta() > 0 ? "+" : "";
        return KageI18n.formatOrKey(
                i18n,
                "gold_history.row",
                WHEN.format(Instant.ofEpochMilli(entry.getAtEpochMs()).atZone(zone)),
                sign + entry.getDelta(),
                entry.getBalance(),
                KageI18n.textOrKey(i18n, entry.getReasonKey()));
    }

    @Override
    public void reset() {
        panel.close();
        panel.setRows(List.of());
    }
}
