package dev.kage.client.core.world;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Ba tiện ích "cho chat yên" lô 64: {@code /xoachat} (ADR-443, {@code /clearchat}) xoá khung chat trên máy mình; ẩn
 * chat khi giao chiến (ADR-444, {@code /anchatgiaochien} hoặc {@code /combatchat}, nhớ trên máy); {@code /tatieng
 * <tên>} (ADR-446, {@code /mute}) ẩn tin của một người trong phiên này — gõ lại để bỏ, không đối số để xem danh sách.
 */
final class ChatQuietCoordinator implements WindowFlow {

    private static final String COMBAT_KEY = "chat.hide_in_combat";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private final Set<String> muted = new HashSet<>();
    private LongSupplier combatSeconds = () -> -1L;
    private boolean hideInCombat;

    ChatQuietCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.hideInCombat =
                preferences.map(p -> p.getBoolean(COMBAT_KEY, false)).orElse(false);
        chat.addEventFilter(event -> muted.contains(fold(ChatCoordinator.bareName(event.getSenderName()))));
        chat.panel().useAutoHide(this::hiddenNow);
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối nguồn "số giây đang giao chiến" (−1 = không trong trận) — đồng hồ giao chiến ADR-405. */
    void useCombatSeconds(LongSupplier seconds) {
        this.combatSeconds = seconds;
    }

    private boolean hiddenNow() {
        return hideInCombat && combatSeconds.getAsLong() >= 0L;
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        switch (command) {
            case "/xoachat", "/clearchat" -> chat.clearLines();
            case "/anchatgiaochien", "/combatchat" -> toggleCombat();
            case "/tatieng", "/mute" -> mute(words.length > 1 ? words[1].strip() : "");
            default -> {
                return false;
            }
        }
        return true;
    }

    private void toggleCombat() {
        hideInCombat = !hideInCombat;
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(COMBAT_KEY, hideInCombat);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.textOrKey(i18n, hideInCombat ? "combatchat.on" : "combatchat.off"));
    }

    private void mute(String name) {
        if (name.isEmpty()) {
            chat.showSystemNotice(
                    muted.isEmpty()
                            ? KageI18n.textOrKey(i18n, "mute.none")
                            : KageI18n.formatOrKey(i18n, "mute.list", String.join(", ", muted)));
            return;
        }
        boolean nowMuted = toggle(muted, fold(name));
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, nowMuted ? "mute.on" : "mute.off", name));
    }

    /** Thêm nếu chưa có, bỏ nếu đã có; trả {@code true} khi giờ đang có. Tĩnh để test. */
    static boolean toggle(Set<String> names, String name) {
        if (names.remove(name)) {
            return false;
        }
        names.add(name);
        return true;
    }

    /** Khoá so tên không phân biệt hoa thường. Tĩnh để test. */
    static String fold(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
