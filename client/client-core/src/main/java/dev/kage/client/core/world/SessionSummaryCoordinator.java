package dev.kage.client.core.world;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.ExperienceChangedEvent;
import dev.kage.protocol.GoldChangedEvent;
import dev.kage.protocol.ItemGrantedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SkillResultEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tóm tắt phiên chơi (ADR-271) — {@code /phien} (hoặc {@code /session}) và đồng hồ EXP/giờ (ADR-272) — {@code
 * /exph}. Nghe (không giành) các gói server đã gửi sẵn: EXP (208), linh thạch (209), vật phẩm nhận (500), đòn đánh của
 * chính mình hạ mục tiêu ({@code target_died}). Đếm trong RAM, mất khi rời màn world.
 */
final class SessionSummaryCoordinator implements WindowFlow {

    private static final long MILLIS_PER_MINUTE = 60_000L;

    private final ChatCoordinator chat;
    private final LongSupplier clockMillis;
    private final I18NBundle i18n = KageI18n.load();
    private SessionTracker tracker;
    private long localId;

    SessionSummaryCoordinator(ChatCoordinator chat, LongSupplier clockMillis) {
        this.chat = chat;
        this.clockMillis = clockMillis;
        this.tracker = new SessionTracker(clockMillis.getAsLong());
        chat.addSlashCommands(this::onSlash);
    }

    /** Id thực thể của chính mình — để nhận ra đòn hạ gục của mình. */
    void useLocalId(long id) {
        this.localId = id;
    }

    private boolean onSlash(String line) {
        String command = line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        long now = clockMillis.getAsLong();
        if ("/phien".equals(command) || "/session".equals(command)) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "session.summary",
                    tracker.elapsedMillis(now) / MILLIS_PER_MINUTE,
                    tracker.expGained(),
                    tracker.levelsGained(),
                    tracker.goldGained(),
                    tracker.goldSpent(),
                    tracker.kills(),
                    tracker.itemsLooted()));
            return true;
        }
        if ("/exph".equals(command)) {
            OptionalLong rate = tracker.expPerHour(now);
            if (rate.isEmpty()) {
                chat.showSystemNotice(KageI18n.textOrKey(i18n, "exprate.too_early"));
                return true;
            }
            OptionalLong eta = tracker.minutesToNextLevel(now);
            chat.showSystemNotice(
                    eta.isPresent()
                            ? KageI18n.formatOrKey(i18n, "exprate.line", rate.getAsLong(), eta.getAsLong())
                            : KageI18n.formatOrKey(i18n, "exprate.line_no_eta", rate.getAsLong()));
            return true;
        }
        return false;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_EXPERIENCE_CHANGED_EVENT_VALUE) {
            ExperienceChangedEvent event = ExperienceChangedEvent.parseFrom(frame.payload());
            tracker.onExperience(
                    event.getTotalExp(), event.getExpIntoLevel(), event.getExpForNextLevel(), event.getLeveledUp());
        } else if (opcode == Opcode.OPCODE_GOLD_CHANGED_EVENT_VALUE) {
            tracker.onGold(GoldChangedEvent.parseFrom(frame.payload()).getDelta());
        } else if (opcode == Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE) {
            tracker.onItemGranted(ItemGrantedEvent.parseFrom(frame.payload()).getQuantity());
        } else if (opcode == Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE) {
            AttackResultEvent event = AttackResultEvent.parseFrom(frame.payload());
            countKill(event.getAttackerEntityId(), event.getTargetDied());
        } else if (opcode == Opcode.OPCODE_SKILL_RESULT_EVENT_VALUE) {
            SkillResultEvent event = SkillResultEvent.parseFrom(frame.payload());
            countKill(event.getAttackerEntityId(), event.getTargetDied());
        }
        return false; // chỉ nghe — các luồng khác vẫn xử lý những gói này
    }

    private void countKill(long attackerId, boolean targetDied) {
        if (targetDied && localId != 0L && attackerId == localId) {
            tracker.onKill();
        }
    }

    @Override
    public void reset() {
        tracker = new SessionTracker(clockMillis.getAsLong());
    }
}
