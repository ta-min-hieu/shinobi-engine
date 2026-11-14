package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.LongSupplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetResetTimersRequest;
import dev.kage.protocol.GetResetTimersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Đồng hồ giờ server (ADR-260) — {@code /gio} (hoặc {@code /time}) hỏi đúng gói đếm ngược reset sẵn có (ADR-200, có
 * {@code server_now}); in giờ server (giờ Việt Nam — mốc reset), giờ máy mình và độ lệch. Bảng đếm ngược của ADR-200
 * cũng hiện theo (cùng gói).
 */
final class ServerClockCoordinator implements WindowFlow {

    /** Múi giờ mốc reset của server ({@code DailyReset.ZONE}). */
    static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM");
    private static final DateTimeFormatter TIME_12H = DateTimeFormatter.ofPattern("h:mm:ss a dd/MM", Locale.ROOT);
    private static final long MILLIS_PER_SECOND = 1000L;

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final LongSupplier clockMillis;
    private final I18NBundle i18n = KageI18n.load();
    private boolean pending;

    ServerClockCoordinator(WorldCommandSender sender, ChatCoordinator chat, LongSupplier clockMillis) {
        this.sender = sender;
        this.chat = chat;
        this.clockMillis = clockMillis;
        chat.addSlashCommands(this::onSlash);
    }

    /** Giờ hiển thị của {@code epochSeconds} ở {@code zone}. Tĩnh để test. */
    static String format(long epochSeconds, ZoneId zone) {
        DateTimeFormatter pattern = dev.kage.client.core.ui.hud.ClockFormat.twelveHour() ? TIME_12H : TIME; // ADR-406
        return pattern.format(Instant.ofEpochSecond(epochSeconds).atZone(zone));
    }

    private boolean onSlash(String line) {
        String command = line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!"/gio".equals(command) && !"/time".equals(command)) {
            return false;
        }
        pending = true;
        sender.sendReadRequest(
                Opcode.OPCODE_GET_RESET_TIMERS_REQUEST_VALUE, GetResetTimersRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_RESET_TIMERS_RESPONSE_VALUE || !pending) {
            return false;
        }
        pending = false;
        long serverNow = GetResetTimersResponse.parseFrom(frame.payload()).getServerNowEpochSeconds();
        long localNow = clockMillis.getAsLong() / MILLIS_PER_SECOND;
        chat.showSystemNotice(KageI18n.formatOrKey(
                i18n,
                "clock.line",
                format(serverNow, SERVER_ZONE),
                format(localNow, ZoneId.systemDefault()),
                localNow - serverNow));
        return false;
    }
}
