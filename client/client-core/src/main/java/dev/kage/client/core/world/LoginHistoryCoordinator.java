package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetLoginHistoryRequest;
import dev.kage.protocol.GetLoginHistoryResponse;
import dev.kage.protocol.LoginHistoryEntry;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lịch sử đăng nhập (ADR-238) — {@code /lichsudangnhap} (hoặc {@code /loginlog}): 10 lần đăng nhập gần nhất của tài
 * khoản (giờ máy người chơi + IP đã che), để tự phát hiện người lạ dùng tài khoản.
 */
final class LoginHistoryCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/lichsudangnhap";
    private static final String COMMAND_EN = "/loginlog";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    LoginHistoryCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Giờ hiển thị theo múi {@code zone}. Tĩnh để test. */
    static String formatTime(long epochSeconds, ZoneId zone) {
        return TIME.format(Instant.ofEpochSecond(epochSeconds).atZone(zone));
    }

    private boolean onSlash(String line) {
        String command = line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_LOGIN_HISTORY_REQUEST_VALUE, GetLoginHistoryRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_LOGIN_HISTORY_RESPONSE_VALUE) {
            return false;
        }
        GetLoginHistoryResponse response = GetLoginHistoryResponse.parseFrom(frame.payload());
        if (response.getEntriesCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "loginlog.empty"));
            return true;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "loginlog.header"));
        ZoneId zone = ZoneId.systemDefault();
        for (LoginHistoryEntry entry : response.getEntriesList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n, "loginlog.line", formatTime(entry.getAtEpochSeconds(), zone), entry.getMaskedIp()));
        }
        return true;
    }
}
