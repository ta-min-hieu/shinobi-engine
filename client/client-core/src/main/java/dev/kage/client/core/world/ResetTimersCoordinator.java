package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Set;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetResetTimersRequest;
import dev.kage.protocol.GetResetTimersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Đếm ngược mốc làm mới (ADR-200) — {@code /lammoi} (hoặc {@code /reset}) in thời gian còn lại tới lúc làm mới hằng
 * ngày và hằng tuần. Tính bằng hiệu các mốc server gửi, không dùng đồng hồ máy.
 */
final class ResetTimersCoordinator implements WindowFlow {

    private static final Set<String> COMMANDS = Set.of("/lammoi", "/reset");
    private static final long MINUTE = 60L;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    ResetTimersCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        if (!COMMANDS.contains(line.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_RESET_TIMERS_REQUEST_VALUE, GetResetTimersRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_RESET_TIMERS_RESPONSE_VALUE) {
            return false;
        }
        GetResetTimersResponse response = GetResetTimersResponse.parseFrom(frame.payload());
        chat.showSystemNotice(describe(i18n, response));
        return true;
    }

    /** Dòng hệ thống — tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, GetResetTimersResponse response) {
        long now = response.getServerNowEpochSeconds();
        return KageI18n.formatOrKey(
                i18n,
                "reset_timers.line",
                duration(i18n, response.getNextDailyEpochSeconds() - now),
                duration(i18n, response.getNextWeeklyEpochSeconds() - now));
    }

    /** "2 ngày 3 giờ" / "3 giờ 12 phút" / "5 phút" — hai đơn vị lớn nhất là đủ để người chơi tính. */
    static String duration(I18NBundle i18n, long seconds) {
        long left = Math.max(0L, seconds);
        long days = left / DAY;
        long hours = left % DAY / HOUR;
        long minutes = Math.max(1L, left % HOUR / MINUTE);
        if (days > 0) {
            return KageI18n.formatOrKey(i18n, "reset_timers.days_hours", days, hours);
        }
        if (hours > 0) {
            return KageI18n.formatOrKey(i18n, "reset_timers.hours_minutes", hours, minutes);
        }
        return KageI18n.formatOrKey(i18n, "reset_timers.minutes", minutes);
    }
}
