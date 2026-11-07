package dev.kage.client.core.world;

import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;

/** Lần cuối online (ADR-192) — "vừa xong" / "N phút / giờ / ngày trước"; chuỗi rỗng khi không biết. */
final class LastOnlineText {

    private static final long MINUTE = 60L;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    private LastOnlineText() {}

    /**
     * @param lastOnlineEpochSeconds mốc server gửi; {@code <= 0} = không biết (hoặc đang online)
     * @param nowEpochSeconds giờ máy client
     */
    static String describe(I18NBundle i18n, long lastOnlineEpochSeconds, long nowEpochSeconds) {
        if (lastOnlineEpochSeconds <= 0L) {
            return "";
        }
        long ago = Math.max(0L, nowEpochSeconds - lastOnlineEpochSeconds);
        if (ago < MINUTE) {
            return KageI18n.textOrKey(i18n, "last_online.just_now");
        }
        if (ago < HOUR) {
            return KageI18n.formatOrKey(i18n, "last_online.minutes", ago / MINUTE);
        }
        if (ago < DAY) {
            return KageI18n.formatOrKey(i18n, "last_online.hours", ago / HOUR);
        }
        return KageI18n.formatOrKey(i18n, "last_online.days", ago / DAY);
    }
}
