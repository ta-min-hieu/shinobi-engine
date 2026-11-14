package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bốn tiện ích hiển thị lô 63, nhớ trên máy: báo kỹ năng sẵn sàng (ADR-435, {@code /baokynang} hoặc {@code
 * /skillready} — chữ bay "Sẵn sàng!" lúc hết hồi chiêu), màu tên mình trong chat (ADR-436, {@code /mautenminh <màu>}
 * hoặc {@code /mycolor} — viết tắt của {@code /maukenh minh <màu>}), hẹn giờ nhắc trên HUD (ADR-437, dòng "Nhắc việc"
 * đếm tới lời nhắc {@code /hengio} gần nhất), ẩn/hiện mọi dòng HUD phụ (ADR-438, {@code /hudphu} hoặc {@code
 * /infohud}).
 */
final class HudPolishCoordinator implements WindowFlow {

    private static final String SKILL_READY_KEY = "hud.skill_ready";
    private static final String INFO_LINES_KEY = "hud.info_lines";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private Consumer<String> sink = text -> {};
    private Consumer<Boolean> infoLines = visible -> {};
    private boolean skillReady;
    private boolean infoLinesOn;

    HudPolishCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.skillReady =
                preferences.map(p -> p.getBoolean(SKILL_READY_KEY, true)).orElse(true);
        this.infoLinesOn =
                preferences.map(p -> p.getBoolean(INFO_LINES_KEY, true)).orElse(true);
        chat.useLineRewriter(HudPolishCoordinator::colorAlias);
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối nơi vẽ chữ bay. */
    void useSink(Consumer<String> floatingText) {
        this.sink = floatingText;
    }

    /** Nối công tắc cả cụm dòng HUD phụ (áp ngay trạng thái đã nhớ). */
    void useInfoLines(Consumer<Boolean> setVisible) {
        this.infoLines = setVisible;
        setVisible.accept(infoLinesOn);
    }

    /** ADR-435 — kỹ năng vừa sẵn sàng. */
    void onSkillReady() {
        if (skillReady) {
            sink.accept(KageI18n.textOrKey(i18n, "skillready.float"));
        }
    }

    /** ADR-436 — {@code /mautenminh <màu>} ⇒ {@code /maukenh minh <màu>}. Tĩnh để test. */
    static String colorAlias(String typed) {
        String[] words = typed.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/mautenminh".equals(command) && !"/mycolor".equals(command)) {
            return typed;
        }
        return "/maukenh minh " + (words.length > 1 ? words[1].strip() : "");
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if ("/baokynang".equals(command) || "/skillready".equals(command)) {
            skillReady = remember(SKILL_READY_KEY, !skillReady);
            notice(skillReady ? "skillready.on" : "skillready.off");
            return true;
        }
        if ("/hudphu".equals(command) || "/infohud".equals(command)) {
            infoLinesOn = remember(INFO_LINES_KEY, !infoLinesOn);
            infoLines.accept(infoLinesOn);
            notice(infoLinesOn ? "infohud.on" : "infohud.off");
            return true;
        }
        return false;
    }

    private boolean remember(String key, boolean value) {
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(key, value);
            prefs.flush();
        });
        return value;
    }

    private void notice(String key) {
        chat.showSystemNotice(KageI18n.textOrKey(i18n, key));
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
