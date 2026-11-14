package dev.kage.client.core.ui.hud;

/**
 * Định dạng giờ của các đồng hồ hiển thị (ADR-406): 24h ({@code HH:MM}, mặc định) hoặc 12h ({@code h:MM AM/PM}). Một
 * lựa chọn hiển thị của máy này — một client một người chơi — nên giữ ở một chỗ tĩnh cho HUD và các lệnh chat cùng đọc;
 * coordinator đặt giá trị đã nhớ lúc vào world.
 */
public final class ClockFormat {

    private static final int MINUTES_PER_HOUR = 60;
    private static final int HOURS_HALF_DAY = 12;
    private static final int TWO_DIGITS = 10;

    private static volatile boolean twelveHour;

    private ClockFormat() {}

    /** Đang dùng 12h không. */
    public static boolean twelveHour() {
        return twelveHour;
    }

    /** Chọn 12h ({@code true}) hoặc 24h. */
    public static void setTwelveHour(boolean enabled) {
        twelveHour = enabled;
    }

    /** Giờ:phút của {@code minuteOfDay} vào bộ đệm có sẵn (không cấp phát), theo định dạng {@code twelve}. */
    public static void appendHourMinute(StringBuilder out, int minuteOfDay, boolean twelve) {
        int hour = minuteOfDay / MINUTES_PER_HOUR;
        int minute = minuteOfDay % MINUTES_PER_HOUR;
        if (twelve) {
            int shown = hour % HOURS_HALF_DAY == 0 ? HOURS_HALF_DAY : hour % HOURS_HALF_DAY;
            out.append(shown).append(':');
        } else {
            if (hour < TWO_DIGITS) {
                out.append('0');
            }
            out.append(hour).append(':');
        }
        if (minute < TWO_DIGITS) {
            out.append('0');
        }
        out.append(minute);
        if (twelve) {
            out.append(hour < HOURS_HALF_DAY ? " AM" : " PM");
        }
    }
}
