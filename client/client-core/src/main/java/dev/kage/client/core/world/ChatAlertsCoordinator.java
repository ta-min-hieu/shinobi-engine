package dev.kage.client.core.world;

import java.util.function.Consumer;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Hai cảnh báo lô 57 không cần mở khung chat: tin riêng tới lúc đang ẩn chat (ADR-389 — chữ bay "Tin riêng: tên" trên
 * đầu nhân vật) và ping cao kéo dài (ADR-390 — {@value #HIGH_PING_STREAK} nhịp heartbeat liên tiếp trên {@value
 * #HIGH_PING_MS} ms thì báo, rồi im {@value #COOLDOWN_SAMPLES} nhịp). Chỉ nghe gói, không nuốt.
 */
final class ChatAlertsCoordinator implements WindowFlow {

    /** Ngưỡng ping cao (ms). */
    static final long HIGH_PING_MS = 300L;

    /** Số nhịp liên tiếp vượt ngưỡng mới báo. */
    static final int HIGH_PING_STREAK = 3;

    /** Số nhịp im lặng sau mỗi lần báo. */
    static final int COOLDOWN_SAMPLES = 20;

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private Consumer<String> sink = text -> {};
    private int streak;
    private int cooldown;

    ChatAlertsCoordinator(ChatCoordinator chat) {
        this.chat = chat;
    }

    /** Nối nơi vẽ chữ bay (lớp phản hồi chiến đấu). */
    void useSink(Consumer<String> floatingText) {
        this.sink = floatingText;
    }

    /** ADR-390 — một nhịp đo độ trễ (từ HUD). */
    void onLatency(long roundTripMs) {
        if (cooldown > 0) {
            cooldown--;
        }
        streak = roundTripMs > HIGH_PING_MS ? streak + 1 : 0;
        if (streak >= HIGH_PING_STREAK && cooldown == 0) {
            String text = KageI18n.formatOrKey(i18n, "highping.warn", roundTripMs);
            chat.showSystemNotice(text);
            sink.accept(text);
            cooldown = COOLDOWN_SAMPLES;
            streak = 0;
        }
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE
                || !chat.panel().isHidden()) {
            return false;
        }
        ChatMessageEvent event = ChatMessageEvent.parseFrom(frame.payload());
        if (event.getChannel() == ChatChannel.CHAT_CHANNEL_PRIVATE
                && !event.getSenderName().isEmpty()
                && !chat.isOwnName(event.getSenderName())) {
            sink.accept(KageI18n.formatOrKey(i18n, "hiddenwhisper.float", event.getSenderName()));
        }
        return false;
    }
}
