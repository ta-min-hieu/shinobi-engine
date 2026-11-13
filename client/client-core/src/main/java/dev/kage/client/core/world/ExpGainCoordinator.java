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
import dev.kage.protocol.ExperienceChangedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Chữ "+N EXP" bay lên trên đầu nhân vật (ADR-331) mỗi lần tổng kinh nghiệm tăng — tính từ hiệu hai {@code
 * ExperienceChangedEvent} (gói đầu lúc vào world chỉ ghi mốc). {@code /chuexp} (hoặc {@code /expgain}) bật/tắt, nhớ
 * trên máy. Chỉ nghe gói, không nuốt.
 */
final class ExpGainCoordinator implements WindowFlow {

    private static final String PREF_KEY = "hud.exp_gain";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private Consumer<String> sink = text -> {};
    private boolean enabled;
    private long lastTotal = -1L;

    ExpGainCoordinator(ChatCoordinator chat) {
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
        if (!"/chuexp".equals(command) && !"/expgain".equals(command)) {
            return false;
        }
        enabled = !enabled;
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(PREF_KEY, enabled);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "expgain.on" : "expgain.off"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() == Opcode.OPCODE_EXPERIENCE_CHANGED_EVENT_VALUE) {
            long total = ExperienceChangedEvent.parseFrom(frame.payload()).getTotalExp();
            long gained = gainOf(lastTotal, total);
            lastTotal = Math.max(lastTotal, total);
            if (enabled && gained > 0L) {
                sink.accept(KageI18n.formatOrKey(i18n, "expgain.float", gained));
            }
        }
        return false; // chỉ nghe — vạch EXP và các luồng khác vẫn nhận gói
    }

    /** Phần tăng so với lần trước; lần đầu (chưa có mốc) ⇒ 0. Tĩnh để test. */
    static long gainOf(long lastTotal, long total) {
        return lastTotal < 0L ? 0L : Math.max(0L, total - lastTotal);
    }

    @Override
    public void reset() {
        lastTotal = -1L;
    }
}
