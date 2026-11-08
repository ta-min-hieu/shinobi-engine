package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GuildMemberOnlineEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Báo thành viên bang online (ADR-266) — nhận {@code GuildMemberOnlineEvent} (782) và in một dòng hệ thống; {@code
 * /baobang} (hoặc {@code /guildonline}) bật/tắt, {@code bat|tat} đặt hẳn. Mặc định BẬT; lựa chọn lưu Preferences
 * (server vẫn gửi, client không hiện — không đổi gì phía server).
 */
final class GuildOnlineNoticeCoordinator implements WindowFlow {

    private static final String PREF_KEY = "guild.online_notice";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private boolean enabled;

    GuildOnlineNoticeCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.enabled =
                preferences.map(prefs -> prefs.getBoolean(PREF_KEY, true)).orElse(true);
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] parts = line.strip().split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (!"/baobang".equals(command) && !"/guildonline".equals(command)) {
            return false;
        }
        Optional<Boolean> wanted = ChatTimestampCoordinator.next(enabled, parts.length > 1 ? parts[1].strip() : "");
        if (wanted.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "guildonline.usage"));
            return true;
        }
        enabled = wanted.get();
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(PREF_KEY, enabled);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "guildonline.on" : "guildonline.off"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GUILD_MEMBER_ONLINE_EVENT_VALUE) {
            return false;
        }
        if (enabled) {
            String name = GuildMemberOnlineEvent.parseFrom(frame.payload()).getName();
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "guildonline.notice", name));
        }
        return true;
    }
}
