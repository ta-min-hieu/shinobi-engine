package dev.kage.client.core.world;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GameEventType;
import dev.kage.protocol.GameEventView;
import dev.kage.protocol.GameEventsChangedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * ADR-067 — sự kiện nhân đôi EXP/rơi đồ: mỗi lần server báo, nói vào nhật ký chat những sự kiện đang chạy
 * và giờ kết thúc (giờ máy người chơi). Client không nhân gì — chỉ báo.
 */
final class GameEventNoticeCoordinator implements WindowFlow {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private static final Map<GameEventType, String> KEYS = Map.of(
            GameEventType.GAME_EVENT_TYPE_EXP, "event.notice.exp",
            GameEventType.GAME_EVENT_TYPE_DROP, "event.notice.drop");

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    GameEventNoticeCoordinator(ChatCoordinator chat) {
        this.chat = chat;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GAME_EVENTS_CHANGED_EVENT_VALUE) {
            return false;
        }
        for (String line : lines(GameEventsChangedEvent.parseFrom(frame.payload()), ZoneId.systemDefault())) {
            chat.showSystemNotice(line);
        }
        return true;
    }

    /** Dòng chữ cho một gói — rỗng ⇒ một câu "đã kết thúc". Loại lạ (server mới hơn) bị bỏ qua. */
    List<String> lines(GameEventsChangedEvent event, ZoneId zone) {
        List<String> lines = new ArrayList<>();
        for (GameEventView view : event.getEventsList()) {
            String key = KEYS.get(view.getType());
            if (key != null) {
                LocalTime endsAt = LocalTime.ofInstant(Instant.ofEpochSecond(view.getEndsAtEpochSeconds()), zone);
                lines.add(KageI18n.formatOrKey(i18n, key, view.getMultiplier(), CLOCK.format(endsAt)));
            }
        }
        if (lines.isEmpty()) {
            lines.add(KageI18n.textOrKey(i18n, "event.notice.ended"));
        }
        return lines;
    }
}
