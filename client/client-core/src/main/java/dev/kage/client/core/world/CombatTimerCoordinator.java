package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.LongSupplier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SkillResultEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Đồng hồ giao chiến (ADR-405) — đòn đánh / kỹ năng mà mình đánh hoặc bị đánh mở một trận; {@value #IDLE_MILLIS} ms
 * không có đòn nào thì hết trận. HUD hiện "Giao chiến H:MM:SS" khi đang đánh, trống khi không. {@code /giaochien} (hoặc
 * {@code /combattimer}) bật/tắt, nhớ trên máy. Chỉ nghe gói, không nuốt.
 */
final class CombatTimerCoordinator implements WindowFlow {

    /** Bao lâu không có đòn nào thì coi như hết giao chiến. */
    static final long IDLE_MILLIS = 5_000L;

    private static final String PREF_KEY = "hud.combat_timer";
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long NOT_IN_COMBAT = -1L;

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private final LongSupplier clockMillis;
    private java.util.function.Consumer<Boolean> hud = visible -> {};
    private long localId;
    private long startedAt = NOT_IN_COMBAT;
    private long lastHitAt = NOT_IN_COMBAT;
    private boolean enabled;

    CombatTimerCoordinator(ChatCoordinator chat, LongSupplier clockMillis) {
        this.chat = chat;
        this.clockMillis = clockMillis;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.enabled =
                preferences.map(prefs -> prefs.getBoolean(PREF_KEY, true)).orElse(true);
        chat.addSlashCommands(this::onSlash);
    }

    void useLocalId(long characterId) {
        this.localId = characterId;
    }

    /** Nối dòng HUD (áp ngay trạng thái đã nhớ). */
    void useHud(java.util.function.Consumer<Boolean> setVisible) {
        this.hud = setVisible;
        setVisible.accept(enabled);
    }

    /** Một đòn giữa {@code attacker} và {@code target} lúc {@code now}. */
    void onHit(long attacker, long target, long now) {
        if (localId == 0L || (attacker != localId && target != localId)) {
            return;
        }
        if (startedAt == NOT_IN_COMBAT || now - lastHitAt > IDLE_MILLIS) {
            startedAt = now;
        }
        lastHitAt = now;
    }

    /** Số giây đã giao chiến lúc {@code now}; {@code -1} nếu không trong trận. */
    long secondsAt(long now) {
        if (startedAt == NOT_IN_COMBAT || now - lastHitAt > IDLE_MILLIS) {
            return NOT_IN_COMBAT;
        }
        return (now - startedAt) / MILLIS_PER_SECOND;
    }

    /** Nguồn cho dòng HUD (đọc mỗi khung). */
    long seconds() {
        return secondsAt(clockMillis.getAsLong());
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/giaochien".equals(command) && !"/combattimer".equals(command)) {
            return false;
        }
        enabled = !enabled;
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(PREF_KEY, enabled);
            prefs.flush();
        });
        hud.accept(enabled);
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "combattimer.on" : "combattimer.off"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE) {
            AttackResultEvent event = AttackResultEvent.parseFrom(frame.payload());
            onHit(event.getAttackerEntityId(), event.getTargetEntityId(), clockMillis.getAsLong());
        } else if (opcode == Opcode.OPCODE_SKILL_RESULT_EVENT_VALUE) {
            SkillResultEvent event = SkillResultEvent.parseFrom(frame.payload());
            onHit(event.getAttackerEntityId(), event.getTargetEntityId(), clockMillis.getAsLong());
        }
        return false; // chỉ nghe — phản hồi chiến đấu vẫn nhận gói
    }

    @Override
    public void reset() {
        startedAt = NOT_IN_COMBAT;
        lastHitAt = NOT_IN_COMBAT;
    }
}
