package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.IntSupplier;

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
 * Rung màn hình khi trúng đòn nặng (ADR-342) — một đòn trúng CHÍNH mình mất ≥ {@value #HEAVY_PERCENT}% máu tối đa thì
 * camera thế giới rung nhẹ {@value #SECONDS} giây. {@code /rung} (hoặc {@code /shake}) bật/tắt, mặc định bật, nhớ trên
 * máy. Chỉ nghe gói, thuần client.
 */
final class ScreenShakeCoordinator implements WindowFlow {

    /** Ngưỡng "đòn nặng" theo % máu tối đa. */
    static final int HEAVY_PERCENT = 10;

    private static final float SECONDS = 0.25f;
    private static final float AMPLITUDE = 0.15f;
    private static final int HUNDRED = 100;
    private static final String PREF_KEY = "camera.shake";

    /** Nơi rung camera (không boxing). */
    interface Camera {
        void shake(float amplitude, float seconds);
    }

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private Camera camera = (amplitude, seconds) -> {};
    private IntSupplier maxHp = () -> 0;
    private boolean enabled;
    private long localId;

    ScreenShakeCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.enabled =
                preferences.map(prefs -> prefs.getBoolean(PREF_KEY, true)).orElse(true);
        chat.addSlashCommands(this::onSlash);
    }

    void useCamera(Camera worldCamera) {
        this.camera = worldCamera;
    }

    void useMaxHp(IntSupplier playerMaxHp) {
        this.maxHp = playerMaxHp;
    }

    void useLocalId(long id) {
        this.localId = id;
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/rung".equals(command) && !"/shake".equals(command)) {
            return false;
        }
        enabled = !enabled;
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(PREF_KEY, enabled);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "shake.on" : "shake.off"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE) {
            AttackResultEvent event = AttackResultEvent.parseFrom(frame.payload());
            onHit(event.getTargetEntityId(), event.getDamage());
        } else if (opcode == Opcode.OPCODE_SKILL_RESULT_EVENT_VALUE) {
            SkillResultEvent event = SkillResultEvent.parseFrom(frame.payload());
            onHit(event.getTargetEntityId(), event.getDamage());
        }
        return false; // chỉ nghe
    }

    private void onHit(long targetId, int damage) {
        if (enabled && localId != 0L && targetId == localId && isHeavy(damage, maxHp.getAsInt())) {
            camera.shake(AMPLITUDE, SECONDS);
        }
    }

    /** Đòn mất ≥ {@value #HEAVY_PERCENT}% máu tối đa. Tĩnh để test. */
    static boolean isHeavy(int damage, int maxHp) {
        return damage > 0 && maxHp > 0 && damage * HUNDRED >= maxHp * HEAVY_PERCENT;
    }
}
