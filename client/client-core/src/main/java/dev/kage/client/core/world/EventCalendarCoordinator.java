package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.CalendarEntry;
import dev.kage.protocol.CalendarEventKind;
import dev.kage.protocol.GetEventCalendarRequest;
import dev.kage.protocol.GetEventCalendarResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lịch sự kiện tuần (ADR-224) — {@code /lich} (hoặc {@code /calendar}) in các mốc sắp tới: boss thế giới, làm mới ngày
 * / tuần, luận võ đại hội; mỗi dòng có giờ theo máy và thời gian còn lại (tính theo đồng hồ server).
 */
final class EventCalendarCoordinator implements WindowFlow {

    private static final String KIND_PREFIX = "CALENDAR_EVENT_KIND_";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ROOT);
    private static final long SECONDS_PER_HOUR = 3600L;
    private static final long SECONDS_PER_MINUTE = 60L;

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    EventCalendarCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!"/lich".equals(command) && !"/calendar".equals(command)) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_EVENT_CALENDAR_REQUEST_VALUE, GetEventCalendarRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_EVENT_CALENDAR_RESPONSE_VALUE) {
            return false;
        }
        GetEventCalendarResponse response = GetEventCalendarResponse.parseFrom(frame.payload());
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "calendar.title"));
        for (CalendarEntry entry : response.getEntriesList()) {
            chat.showSystemNotice(describe(i18n, entry, response.getServerNowEpochSeconds(), ZoneId.systemDefault()));
        }
        return true;
    }

    /** Một dòng lịch. Tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, CalendarEntry entry, long serverNow, ZoneId zone) {
        String kind = entry.getKind() == CalendarEventKind.UNRECOGNIZED
                ? "unspecified"
                : entry.getKind().name().substring(KIND_PREFIX.length()).toLowerCase(Locale.ROOT);
        String name = KageI18n.textOrKey(i18n, "calendar.kind." + kind);
        if (entry.getActive()) {
            return KageI18n.formatOrKey(i18n, "calendar.line_active", name);
        }
        long left = Math.max(0L, entry.getAtEpochSeconds() - serverNow);
        return KageI18n.formatOrKey(
                i18n,
                "calendar.line",
                name,
                WHEN.format(Instant.ofEpochSecond(entry.getAtEpochSeconds()).atZone(zone)),
                left / SECONDS_PER_HOUR,
                (left % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE);
    }
}
