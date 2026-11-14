package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.DisplaySettings;
import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Ba công tắc hiển thị lô 58, nhớ trên máy: lọc chat chỉ bạn bè (ADR-397, {@code /chatbanbe} hoặc {@code
 * /friendchat} — ẩn tin kênh thế giới / khu vực / giao dịch của người không phải bạn; bang, nhóm, tin riêng, hệ thống
 * giữ nguyên), toạ độ trên HUD (ADR-396, {@code /toadohud} hoặc {@code /coordshud}), ẩn nhãn của chính mình (ADR-398,
 * {@code /annhanminh} hoặc {@code /hidemytitle} — cùng công tắc trong bảng Cài đặt hiển thị).
 */
final class ChatViewCoordinator implements WindowFlow {

    private static final String FRIENDS_ONLY_KEY = "chat.friends_only";
    private static final String COORDS_KEY = "hud.coords";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private Predicate<String> isFriend = name -> false;
    private Optional<DisplaySettings> display = Optional.empty();
    private java.util.function.Consumer<Boolean> coordsLine = visible -> {};
    private boolean friendsOnly;
    private boolean coordsOn;

    ChatViewCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.friendsOnly = preferences
                .map(prefs -> prefs.getBoolean(FRIENDS_ONLY_KEY, false))
                .orElse(false);
        this.coordsOn =
                preferences.map(prefs -> prefs.getBoolean(COORDS_KEY, false)).orElse(false);
        chat.addSlashCommands(this::onSlash);
        chat.addEventFilter(this::hides);
    }

    /** Nối nguồn "tên này là bạn bè". */
    void useFriends(Predicate<String> friendNamed) {
        this.isFriend = friendNamed;
    }

    /** Nối bảng Cài đặt hiển thị (công tắc ẩn nhãn của chính mình). */
    void useDisplaySettings(DisplaySettings settings) {
        this.display = Optional.of(settings);
    }

    /** Nối dòng toạ độ của HUD: {@code add} nhận nguồn số mét, trả công tắc hiện/ẩn. */
    void useCoordsHud(
            java.util.function.Function<LongSupplier, java.util.function.Consumer<Boolean>> add, LongSupplier meters) {
        this.coordsLine = add.apply(meters);
        coordsLine.accept(coordsOn);
    }

    /** ADR-397 — tin này có bị ẩn vì đang lọc chỉ bạn bè không. */
    boolean hides(ChatMessageEvent event) {
        return friendsOnly
                && isPublic(event.getChannel())
                && !chat.isOwnName(event.getSenderName())
                && !isFriend.test(event.getSenderName());
    }

    /** Kênh công cộng (người lạ nói được). Tĩnh để test. */
    static boolean isPublic(ChatChannel channel) {
        return channel == ChatChannel.CHAT_CHANNEL_WORLD
                || channel == ChatChannel.CHAT_CHANNEL_AREA
                || channel == ChatChannel.CHAT_CHANNEL_TRADE;
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        switch (command) {
            case "/chatbanbe", "/friendchat" -> {
                friendsOnly = !friendsOnly;
                remember(FRIENDS_ONLY_KEY, friendsOnly);
                notice(friendsOnly ? "friendchat.on" : "friendchat.off");
            }
            case "/toadohud", "/coordshud" -> {
                coordsOn = !coordsOn;
                remember(COORDS_KEY, coordsOn);
                coordsLine.accept(coordsOn);
                notice(coordsOn ? "coordshud.on" : "coordshud.off");
            }
            case "/annhanminh", "/hidemytitle" ->
                display.ifPresent(settings -> {
                    settings.toggle(DisplaySettings.Option.HIDE_OWN_TITLE);
                    notice(settings.isOn(DisplaySettings.Option.HIDE_OWN_TITLE) ? "mytitle.hidden" : "mytitle.shown");
                });
            default -> {
                return false;
            }
        }
        return true;
    }

    private void remember(String key, boolean value) {
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(key, value);
            prefs.flush();
        });
    }

    private void notice(String key) {
        chat.showSystemNotice(KageI18n.textOrKey(i18n, key));
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
