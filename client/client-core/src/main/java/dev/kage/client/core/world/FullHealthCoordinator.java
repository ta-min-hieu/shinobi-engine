package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.HealthChangedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SkillResultEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Báo hồi đầy máu (ADR-340) — sau khi mất máu (trúng đòn, hoặc một gói máu dưới tối đa), lần đầu máu của chính mình về
 * 100% ({@code HealthChangedEvent}) thì in một dòng hệ thống. {@code /baodaymau} (hoặc {@code /fullhp}) bật/tắt, mặc
 * định bật, nhớ trên máy. Chỉ nghe gói.
 */
final class FullHealthCoordinator implements WindowFlow {

    private static final String PREF_KEY = "notice.full_hp";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private boolean enabled;
    private final Tracker tracker = new Tracker();
    private long localId;

    FullHealthCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.enabled =
                preferences.map(prefs -> prefs.getBoolean(PREF_KEY, true)).orElse(true);
        chat.addSlashCommands(this::onSlash);
    }

    void useLocalId(long id) {
        this.localId = id;
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/baodaymau".equals(command) && !"/fullhp".equals(command)) {
            return false;
        }
        enabled = !enabled;
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(PREF_KEY, enabled);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "fullhp.on" : "fullhp.off"));
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
        } else if (opcode == Opcode.OPCODE_HEALTH_CHANGED_EVENT_VALUE) {
            HealthChangedEvent event = HealthChangedEvent.parseFrom(frame.payload());
            if (localId != 0L
                    && event.getEntityId() == localId
                    && tracker.onHealth(event.getCurrentHp(), event.getMaxHp())
                    && enabled) {
                chat.showSystemNotice(KageI18n.textOrKey(i18n, "fullhp.notice"));
            }
        }
        return false; // chỉ nghe
    }

    private void onHit(long targetId, int damage) {
        if (localId != 0L && targetId == localId && damage > 0) {
            tracker.hurt = true;
        }
    }

    @Override
    public void reset() {
        tracker.hurt = false;
    }

    /** Trạng thái "đang bị thương" — tách riêng để test không cần chat. */
    static final class Tracker {
        private boolean hurt;

        /** Máu mình vừa đổi; {@code true} nếu vừa về đầy sau khi bị thương. */
        boolean onHealth(int hp, int maxHp) {
            if (maxHp <= 0 || hp <= 0) {
                return false;
            }
            if (hp < maxHp) {
                hurt = true;
                return false;
            }
            boolean recovered = hurt;
            hurt = false;
            return recovered;
        }
    }
}
