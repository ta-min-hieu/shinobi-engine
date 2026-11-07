package dev.kage.client.core.screen;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.DeleteCharacterOutcome;

/** Chữ của xoá nhân vật có chờ (ADR-205) — tách để test được không cần màn hình. */
final class CharacterDeletionText {

    private static final long HOUR = 3600L;
    private static final long DAY = 24L * HOUR;
    private static final String OUTCOME_PREFIX = "DELETE_CHARACTER_OUTCOME_";

    private CharacterDeletionText() {}

    /** "(chờ xoá: còn 2 ngày 5 giờ)" — tính từ mốc xoá hẳn server gửi. */
    static String pending(I18NBundle i18n, long deletionEpochSeconds, long nowEpochSeconds) {
        long left = Math.max(0L, deletionEpochSeconds - nowEpochSeconds);
        return KageI18n.formatOrKey(i18n, "charselect.pending_delete", left / DAY, Math.max(1L, left % DAY / HOUR));
    }

    /** Khoá i18n của một kết quả xoá. */
    static String outcomeKey(DeleteCharacterOutcome outcome) {
        String suffix = outcome == DeleteCharacterOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return "charselect.delete." + suffix;
    }
}
