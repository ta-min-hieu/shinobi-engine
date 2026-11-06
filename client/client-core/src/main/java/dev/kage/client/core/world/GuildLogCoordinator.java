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
import dev.kage.protocol.GetGuildLogRequest;
import dev.kage.protocol.GetGuildLogResponse;
import dev.kage.protocol.GuildLogEntry;
import dev.kage.protocol.GuildLogKind;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nhật ký bang (ADR-180) — cửa sổ mở bằng lệnh chat {@code /nhatkybang} (hoặc {@code /guildlog}): 50 việc gần nhất
 * của bang (vào/rời/bị đuổi, góp bang, lên cấp, đổi tên, đổi thông báo), mới nhất trên cùng. Chỉ đọc.
 */
final class GuildLogCoordinator implements WindowFlow {

    private static final List<String> COMMANDS = List.of("/nhatkybang", "/guildlog");
    private static final String KIND_PREFIX = "GUILD_LOG_KIND_";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    /** Server gửi tối đa 50 — gói dị dạng không được kéo theo hàng nghìn dòng. */
    private static final int MAX_ROWS = 60;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final I18NBundle i18n = KageI18n.load();

    GuildLogCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        panel.setOnRefresh(this::request);
        panel.setStatus(KageI18n.textOrKey(i18n, "guild_log.hint"));
        feeds.chat().addSlashCommands(this::onSlash);
    }

    static GuildLogCoordinator create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new GuildLogCoordinator(
                new ReportPanel(skin, "guild_log.title", Input.Keys.UNKNOWN, List.of()), sender, feeds);
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
        sender.sendReadRequest(Opcode.OPCODE_GET_GUILD_LOG_REQUEST_VALUE, GetGuildLogRequest.getDefaultInstance());
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
        if (frame.header().opcode() != Opcode.OPCODE_GET_GUILD_LOG_RESPONSE_VALUE) {
            return false;
        }
        GetGuildLogResponse response = GetGuildLogResponse.parseFrom(frame.payload());
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (GuildLogEntry entry : response.getEntriesList()) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            rows.add(new ReportPanel.Row(describe(i18n, entry, ZoneId.systemDefault()), false));
        }
        if (rows.isEmpty()) {
            rows.add(new ReportPanel.Row(KageI18n.textOrKey(i18n, "guild_log.empty"), false));
        }
        panel.setRows(rows);
        return true;
    }

    /** Một dòng nhật ký — tĩnh để test không cần màn hình. Loại lạ (server mới hơn client) hiện câu chung. */
    static String describe(I18NBundle i18n, GuildLogEntry entry, ZoneId zone) {
        String kind = entry.getKind() == GuildLogKind.UNRECOGNIZED
                ? "unspecified"
                : entry.getKind().name().substring(KIND_PREFIX.length()).toLowerCase(Locale.ROOT);
        String what = KageI18n.formatOrKey(
                i18n,
                "guild_log.kind." + kind,
                name(i18n, entry.getActorName()),
                name(i18n, entry.getSubjectName()),
                entry.getAmount(),
                ChatController.sanitize(entry.getDetail()));
        return WHEN.format(Instant.ofEpochSecond(entry.getAtEpochSeconds()).atZone(zone)) + "  " + what;
    }

    private static String name(I18NBundle i18n, String raw) {
        return raw.isEmpty() ? KageI18n.textOrKey(i18n, "guild_log.someone") : ChatController.sanitize(raw);
    }

    @Override
    public void reset() {
        panel.close();
        panel.setRows(List.of());
    }
}
