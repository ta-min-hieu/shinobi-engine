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
 * Mục tiêu cấp độ (ADR-334) — {@code /muctieu <cấp>} (hoặc {@code /goal}) đặt cấp muốn đạt, {@code /muctieu xoa} bỏ.
 * HUD hiện "còn N cấp, cấp này X%" từ số server gửi trong {@code ExperienceChangedEvent} (client không tự tính đường
 * cong EXP). Tới nơi thì chúc mừng một dòng và tự bỏ mục tiêu. Nhớ trên máy.
 */
final class LevelGoalCoordinator implements WindowFlow {

    /** Cấp tối đa (6 cảnh giới × 9 tầng, ADR-038 — hiển thị tới 100). */
    static final int MAX_LEVEL = 100;

    private static final String PREF_KEY = "hud.level_goal";
    private static final int PERCENT = 100;

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private Consumer<String> hud = text -> {};
    private int goal;
    private int level;
    private long intoLevel;
    private long forNextLevel;

    LevelGoalCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.goal = preferences.map(prefs -> prefs.getInteger(PREF_KEY, 0)).orElse(0);
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối dòng HUD. */
    void useHud(Consumer<String> setText) {
        this.hud = setText;
        refresh();
    }

    int goal() {
        return goal;
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/muctieu".equals(command) && !"/goal".equals(command)) {
            return false;
        }
        String arg = words.length > 1 ? MentionWatch.fold(words[1].strip()) : "";
        if ("xoa".equals(arg) || "clear".equals(arg)) {
            setGoal(0);
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "goal.cleared"));
            return true;
        }
        int target = parse(arg);
        if (target <= level || target > MAX_LEVEL) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "goal.usage", level + 1, MAX_LEVEL));
            return true;
        }
        setGoal(target);
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "goal.set", target));
        return true;
    }

    private static int parse(String arg) {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return -1; // không phải số ⇒ coi như sai cú pháp
        }
    }

    private void setGoal(int target) {
        goal = target;
        preferences.ifPresent(prefs -> {
            prefs.putInteger(PREF_KEY, goal);
            prefs.flush();
        });
        refresh();
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() == Opcode.OPCODE_EXPERIENCE_CHANGED_EVENT_VALUE) {
            ExperienceChangedEvent event = ExperienceChangedEvent.parseFrom(frame.payload());
            level = event.getLevel();
            intoLevel = event.getExpIntoLevel();
            forNextLevel = event.getExpForNextLevel();
            if (goal > 0 && level >= goal) {
                chat.showSystemNotice(KageI18n.formatOrKey(i18n, "goal.reached", goal));
                setGoal(0);
            } else {
                refresh();
            }
        }
        return false; // chỉ nghe
    }

    private void refresh() {
        hud.accept(
                goal <= 0 || level <= 0
                        ? ""
                        : KageI18n.formatOrKey(
                                i18n, "goal.hud", goal, goal - level, percentOf(intoLevel, forNextLevel)));
    }

    /** Phần trăm đã đi trong cấp hiện tại (cấp tối đa ⇒ 100). Tĩnh để test. */
    static int percentOf(long intoLevel, long forNextLevel) {
        if (forNextLevel <= 0L) {
            return PERCENT;
        }
        return (int) Math.clamp(intoLevel * PERCENT / forNextLevel, 0L, PERCENT);
    }
}
