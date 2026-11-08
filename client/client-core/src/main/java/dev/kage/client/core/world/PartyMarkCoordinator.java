package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.MarkPartyTargetCommand;
import dev.kage.protocol.MarkPartyTargetOutcome;
import dev.kage.protocol.MarkPartyTargetResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PartyTargetMarkedEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Đánh dấu mục tiêu nhóm (ADR-213) — trưởng nhóm gõ {@code /danhdau} (hoặc {@code /mark}) để đánh dấu mục tiêu đang
 * nhắm, {@code /danhdau xoa} để bỏ; cả nhóm nhận một dòng chat và gõ {@code /theodau} (hoặc {@code /assist}) để nhắm
 * đúng mục tiêu đó. Nhắm vẫn là lệnh chọn mục tiêu bình thường — server kiểm mọi đòn như cũ.
 */
final class PartyMarkCoordinator implements WindowFlow {

    private static final String MARK_VI = "/danhdau";
    private static final String MARK_EN = "/mark";
    private static final String ASSIST_VI = "/theodau";
    private static final String ASSIST_EN = "/assist";
    private static final String OUTCOME_PREFIX = "MARK_PARTY_TARGET_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private LocalIntentSender targeting;
    private long markedTargetId;

    /** ADR-220 — {@link #markedTargetId} đã đóng hộp MỘT lần lúc nhận event, để vẽ mỗi khung không cấp phát. */
    private java.util.Optional<Long> markedKey = java.util.Optional.empty();

    PartyMarkCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối mục tiêu đang nhắm / chọn mục tiêu (một lần, lúc dựng màn hình). */
    void useTargeting(LocalIntentSender intents) {
        this.targeting = intents;
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (MARK_VI.equals(command) || MARK_EN.equals(command)) {
            mark(words.length > 1 ? words[1].strip().toLowerCase(Locale.ROOT) : "");
            return true;
        }
        if (ASSIST_VI.equals(command) || ASSIST_EN.equals(command)) {
            assist();
            return true;
        }
        return false;
    }

    private void mark(String argument) {
        boolean clear = "xoa".equals(argument) || "clear".equals(argument);
        long target = clear || targeting == null ? 0L : targeting.currentTargetId();
        if (!clear && target == 0L) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "party_mark.no_target"));
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_MARK_PARTY_TARGET_COMMAND_VALUE,
                MarkPartyTargetCommand.newBuilder().setTargetEntityId(target).build());
    }

    private void assist() {
        if (markedTargetId == 0L) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "party_mark.none"));
            return;
        }
        boolean focused = targeting != null && targeting.focusTarget(markedTargetId);
        chat.showSystemNotice(KageI18n.textOrKey(i18n, focused ? "party_mark.assisted" : "party_mark.not_here"));
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_MARK_PARTY_TARGET_RESPONSE_VALUE) {
            MarkPartyTargetOutcome outcome =
                    MarkPartyTargetResponse.parseFrom(frame.payload()).getOutcome();
            if (outcome != MarkPartyTargetOutcome.MARK_PARTY_TARGET_OUTCOME_OK) {
                String key = outcome == MarkPartyTargetOutcome.UNRECOGNIZED
                        ? "unspecified"
                        : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
                chat.showSystemNotice(KageI18n.textOrKey(i18n, "party_mark." + key));
            }
            return true;
        }
        if (opcode == Opcode.OPCODE_PARTY_TARGET_MARKED_EVENT_VALUE) {
            PartyTargetMarkedEvent event = PartyTargetMarkedEvent.parseFrom(frame.payload());
            markedTargetId = event.getTargetEntityId();
            markedKey = markedTargetId == 0L ? java.util.Optional.empty() : java.util.Optional.of(markedTargetId);
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n, markedTargetId == 0L ? "party_mark.cleared" : "party_mark.marked", event.getLeaderName()));
            return true;
        }
        return false;
    }

    /** ADR-220 — mục tiêu nhóm đang được đánh dấu (rỗng = không có). */
    java.util.Optional<Long> markedKey() {
        return markedKey;
    }

    @Override
    public void reset() {
        markedTargetId = 0L;
        markedKey = java.util.Optional.empty();
    }
}
