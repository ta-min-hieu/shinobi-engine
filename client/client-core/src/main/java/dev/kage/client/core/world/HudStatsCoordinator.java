package dev.kage.client.core.world;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.Timer;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.CalendarEntry;
import dev.kage.protocol.GetEventCalendarRequest;
import dev.kage.protocol.GetEventCalendarResponse;
import dev.kage.protocol.GoldChangedEvent;
import dev.kage.protocol.ListMailRequest;
import dev.kage.protocol.ListMailResponse;
import dev.kage.protocol.MailView;
import dev.kage.protocol.MarkMailReadResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SkillResultEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bốn dòng số trên HUD lô 60, mỗi dòng một công tắc nhớ trên máy (mặc định tắt): sự kiện kế tiếp (ADR-411, {@code
 * /sukienhud} — hỏi lịch sự kiện mỗi {@value #CALENDAR_REFRESH_SECONDS} giây), thư chưa đọc (ADR-412, {@code
 * /thuhud}), DPS của mình trong trận đang đánh (ADR-413, {@code /dpshud}), linh thạch kiếm trong phiên (ADR-414,
 * {@code /vanghud}). Chỉ nghe gói, không nuốt.
 */
final class HudStatsCoordinator implements WindowFlow {

    /** Một dòng HUD lô 60. */
    enum Line {
        NEXT_EVENT("hud.next_event", "/sukienhud", "/nexteventhud", true),
        UNREAD_MAIL("hud.unread_mail", "/thuhud", "/mailhud", false),
        DPS("hud.dps", "/dpshud", "/dpshud", false),
        SESSION_GOLD("hud.session_gold", "/vanghud", "/goldhud", false);

        final String prefixKey;
        final String commandVi;
        final String commandEn;
        final boolean clock;

        Line(String prefixKey, String commandVi, String commandEn, boolean clock) {
            this.prefixKey = prefixKey;
            this.commandVi = commandVi;
            this.commandEn = commandEn;
            this.clock = clock;
        }

        String prefKey() {
            return "hud.line." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Hỏi lại lịch sự kiện bao lâu một lần khi dòng sự kiện đang bật. */
    static final float CALENDAR_REFRESH_SECONDS = 60f;

    /** Nghỉ bấy nhiêu ms không đánh thì hết trận (cùng đồng hồ giao chiến ADR-405). */
    static final long COMBAT_IDLE_MILLIS = CombatTimerCoordinator.IDLE_MILLIS;

    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long NOTHING = -1L;

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private final LongSupplier clockMillis;
    private final Map<Line, Boolean> enabled = new EnumMap<>(Line.class);
    private final Map<Line, Consumer<Boolean>> hud = new EnumMap<>(Line.class);
    private long localId;
    private long nextEventAtMillis = NOTHING;
    private long unread = NOTHING;
    private long sessionGold;
    private long combatStart = NOTHING;
    private long combatLastHit = NOTHING;
    private long combatDamage;
    private Optional<Timer.Task> calendarTask = Optional.empty();

    HudStatsCoordinator(WorldCommandSender sender, ChatCoordinator chat, LongSupplier clockMillis) {
        this.sender = sender;
        this.chat = chat;
        this.clockMillis = clockMillis;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        for (Line line : Line.values()) {
            enabled.put(
                    line,
                    preferences.map(p -> p.getBoolean(line.prefKey(), false)).orElse(false));
        }
        chat.addSlashCommands(this::onSlash);
    }

    void useLocalId(long characterId) {
        this.localId = characterId;
    }

    /** Nối các dòng HUD: {@code add(line, nguồn số)} trả công tắc hiện/ẩn. Áp ngay trạng thái đã nhớ. */
    void useHud(BiFunction<Line, LongSupplier, Consumer<Boolean>> add) {
        for (Line line : Line.values()) {
            Consumer<Boolean> toggle = add.apply(line, () -> valueOf(line));
            hud.put(line, toggle);
            toggle.accept(enabled.get(line));
        }
        refreshSources();
    }

    /** Giá trị hiện tại của một dòng; âm = để trống. */
    long valueOf(Line line) {
        long now = clockMillis.getAsLong();
        return switch (line) {
            case NEXT_EVENT ->
                nextEventAtMillis < 0L ? NOTHING : Math.max(0L, (nextEventAtMillis - now) / MILLIS_PER_SECOND);
            case UNREAD_MAIL -> unread;
            case DPS -> dps(combatStart, combatLastHit, combatDamage, now);
            case SESSION_GOLD -> sessionGold;
        };
    }

    /** Sát thương mỗi giây của trận đang diễn ra; ngoài trận ⇒ -1. Tĩnh để test. */
    static long dps(long start, long lastHit, long damage, long now) {
        if (start < 0L || now - lastHit > COMBAT_IDLE_MILLIS) {
            return NOTHING;
        }
        long seconds = Math.max(1L, (now - start) / MILLIS_PER_SECOND);
        return damage / seconds;
    }

    private boolean onSlash(String text) {
        String command = text.strip().toLowerCase(Locale.ROOT);
        for (Line line : Line.values()) {
            if (line.commandVi.equals(command) || line.commandEn.equals(command)) {
                boolean on = !enabled.get(line);
                enabled.put(line, on);
                preferences.ifPresent(prefs -> {
                    prefs.putBoolean(line.prefKey(), on);
                    prefs.flush();
                });
                Optional.ofNullable(hud.get(line)).ifPresent(toggle -> toggle.accept(on));
                refreshSources();
                chat.showSystemNotice(KageI18n.formatOrKey(
                        i18n, on ? "hudline.on" : "hudline.off", KageI18n.textOrKey(i18n, line.prefixKey)));
                return true;
            }
        }
        return false;
    }

    /** Bật dòng nào thì hỏi server dữ liệu ban đầu của dòng đó. */
    private void refreshSources() {
        if (enabled.get(Line.UNREAD_MAIL) && unread < 0L) {
            sender.sendReadRequest(Opcode.OPCODE_LIST_MAIL_REQUEST_VALUE, ListMailRequest.getDefaultInstance());
        }
        if (enabled.get(Line.NEXT_EVENT) && calendarTask.isEmpty()) {
            calendarTask = Optional.of(Timer.schedule(
                    new Timer.Task() {
                        @Override
                        public void run() {
                            sender.sendReadRequest(
                                    Opcode.OPCODE_GET_EVENT_CALENDAR_REQUEST_VALUE,
                                    GetEventCalendarRequest.getDefaultInstance());
                        }
                    },
                    0f,
                    CALENDAR_REFRESH_SECONDS));
        } else if (!enabled.get(Line.NEXT_EVENT)) {
            calendarTask.ifPresent(Timer.Task::cancel);
            calendarTask = Optional.empty();
        }
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        long now = clockMillis.getAsLong();
        switch (opcode) {
            case Opcode.OPCODE_GET_EVENT_CALENDAR_RESPONSE_VALUE ->
                onCalendar(GetEventCalendarResponse.parseFrom(frame.payload()), now);
            case Opcode.OPCODE_LIST_MAIL_RESPONSE_VALUE ->
                unread = countUnread(ListMailResponse.parseFrom(frame.payload()));
            case Opcode.OPCODE_NEW_MAIL_EVENT_VALUE -> unread = Math.max(0L, unread) + 1L;
            case Opcode.OPCODE_MARK_MAIL_READ_RESPONSE_VALUE ->
                unread = MarkMailReadResponse.parseFrom(frame.payload()).getUnread();
            case Opcode.OPCODE_MARK_ALL_MAIL_READ_RESPONSE_VALUE -> unread = 0L;
            case Opcode.OPCODE_GOLD_CHANGED_EVENT_VALUE ->
                sessionGold +=
                        Math.max(0L, GoldChangedEvent.parseFrom(frame.payload()).getDelta());
            case Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE -> {
                AttackResultEvent event = AttackResultEvent.parseFrom(frame.payload());
                onHit(event.getAttackerEntityId(), event.getDamage(), now);
            }
            case Opcode.OPCODE_SKILL_RESULT_EVENT_VALUE -> {
                SkillResultEvent event = SkillResultEvent.parseFrom(frame.payload());
                onHit(event.getAttackerEntityId(), event.getDamage(), now);
            }
            default -> {
                // không phải gói của lớp này
            }
        }
        return false;
    }

    private void onCalendar(GetEventCalendarResponse response, long now) {
        long serverNow = response.getServerNowEpochSeconds();
        nextEventAtMillis = NOTHING;
        for (CalendarEntry entry : response.getEntriesList()) {
            if (!entry.getActive() && entry.getAtEpochSeconds() > serverNow) {
                nextEventAtMillis = now + (entry.getAtEpochSeconds() - serverNow) * MILLIS_PER_SECOND;
                return; // sớm nhất trước
            }
        }
    }

    /** Số thư chưa đọc trong danh sách. Tĩnh để test. */
    static long countUnread(ListMailResponse response) {
        return response.getMailsList().stream().filter(MailView::getUnread).count();
    }

    private void onHit(long attacker, int damage, long now) {
        if (localId == 0L || attacker != localId) {
            return;
        }
        if (combatStart < 0L || now - combatLastHit > COMBAT_IDLE_MILLIS) {
            combatStart = now;
            combatDamage = 0L;
        }
        combatLastHit = now;
        combatDamage += Math.max(0, damage);
    }

    @Override
    public void reset() {
        combatStart = NOTHING;
        combatLastHit = NOTHING;
        combatDamage = 0L;
        unread = NOTHING;
    }
}
