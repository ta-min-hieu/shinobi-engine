package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GoldChangedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Chữ "+N linh thạch" bay lên trên đầu nhân vật (ADR-379) mỗi lần nhận linh thạch — lấy thẳng {@code delta} của
 * {@code GoldChangedEvent} (chỉ phần dương; tiêu thì không hiện). {@code /chuvang} (hoặc {@code /goldgain}) bật/tắt,
 * nhớ trên máy. Chỉ nghe gói, không nuốt.
 */
final class GoldGainCoordinator implements WindowFlow {

    private static final String PREF_KEY = "hud.gold_gain";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private Consumer<String> sink = text -> {};
    private boolean enabled;

    GoldGainCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.enabled =
                preferences.map(prefs -> prefs.getBoolean(PREF_KEY, true)).orElse(true);
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối nơi vẽ chữ bay (lớp phản hồi chiến đấu). */
    void useSink(Consumer<String> floatingText) {
        this.sink = floatingText;
    }

    boolean enabled() {
        return enabled;
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/chuvang".equals(command) && !"/goldgain".equals(command)) {
            return false;
        }
        enabled = !enabled;
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(PREF_KEY, enabled);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "goldgain.on" : "goldgain.off"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() == Opcode.OPCODE_GOLD_CHANGED_EVENT_VALUE) {
            long delta = GoldChangedEvent.parseFrom(frame.payload()).getDelta();
            if (enabled && delta > 0L) {
                sink.accept(KageI18n.formatOrKey(i18n, "goldgain.float", delta));
            }
        }
        return false; // chỉ nghe — số vàng và các luồng khác vẫn nhận gói
    }
}
