package dev.kage.client.core.world;

import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

import dev.kage.client.core.ui.hud.MailPanel;
import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;

/**
 * Thư nháp (ADR-277) — giữ bốn ô của khung soạn thư (người nhận, tiêu đề, nội dung, linh thạch) trong Preferences khi
 * đóng khung thư, điền lại khi mở nếu khung đang trống, xoá khi gửi thành công. Chỉ một bản nháp; chỉ trên máy này.
 */
final class MailDraftStore {

    private static final String PREFIX = "mail.draft.";
    private static final String[] FIELDS = {"recipient", "subject", "body", "gold"};

    private final Optional<Preferences> preferences;

    MailDraftStore() {
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
    }

    /** Bốn ô đều trống (sau khi bỏ khoảng trắng). Tĩnh để test. */
    static boolean isBlank(MailPanel.Composed draft) {
        return draft.recipient().isBlank()
                && draft.subject().isBlank()
                && draft.body().isBlank()
                && draft.gold().isBlank();
    }

    /** Lưu {@code draft}; trống thì xoá bản nháp cũ. */
    void save(MailPanel.Composed draft) {
        if (isBlank(draft)) {
            clear();
            return;
        }
        String[] values = {draft.recipient(), draft.subject(), draft.body(), draft.gold()};
        preferences.ifPresent(prefs -> {
            for (int i = 0; i < FIELDS.length; i++) {
                prefs.putString(PREFIX + FIELDS[i], values[i]);
            }
            prefs.flush();
        });
    }

    /** Bản nháp đã lưu, nếu có. */
    Optional<MailPanel.Composed> load() {
        return preferences
                .map(prefs -> new MailPanel.Composed(
                        read(prefs, FIELDS[0]), read(prefs, FIELDS[1]), read(prefs, FIELDS[2]), read(prefs, FIELDS[3])))
                .filter(draft -> !isBlank(draft));
    }

    void clear() {
        preferences.ifPresent(prefs -> {
            for (String field : FIELDS) {
                prefs.remove(PREFIX + field);
            }
            prefs.flush();
        });
    }

    private static String read(Preferences prefs, String field) {
        return java.util.Objects.requireNonNullElse(prefs.getString(PREFIX + field, ""), "");
    }
}
