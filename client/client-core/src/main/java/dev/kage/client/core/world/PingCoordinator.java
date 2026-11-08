package dev.kage.client.core.world;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.PongResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Đo ping (ADR-269) — {@code /ping} gửi MỘT gói {@code PING} (opcode 1, cùng gói heartbeat sẵn có) mang dấu thời gian
 * riêng, chờ {@code PONG} echo đúng dấu đó rồi in độ trễ khứ hồi. Pong của heartbeat thường (dấu khác) bị bỏ qua; luồng
 * kết nối vẫn nhận mọi pong như cũ (hub đưa gói cho tất cả).
 */
final class PingCoordinator implements WindowFlow {

    /** Dưới mức này coi là tốt. */
    static final long GOOD_MS = 100L;

    /** Dưới mức này coi là trung bình; từ đây trở lên là cao. */
    static final long FAIR_MS = 250L;

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final LongSupplier clockMillis;
    private final I18NBundle i18n = KageI18n.load();
    private OptionalLong pendingStamp = OptionalLong.empty();

    PingCoordinator(WorldCommandSender sender, ChatCoordinator chat, LongSupplier clockMillis) {
        this.sender = sender;
        this.chat = chat;
        this.clockMillis = clockMillis;
        chat.addSlashCommands(this::onSlash);
    }

    /** Khoá i18n nhãn chất lượng cho {@code roundTripMs}. Tĩnh để test. */
    static String qualityKey(long roundTripMs) {
        if (roundTripMs < GOOD_MS) {
            return "ping.quality.good";
        }
        return roundTripMs < FAIR_MS ? "ping.quality.fair" : "ping.quality.poor";
    }

    private boolean onSlash(String line) {
        if (!"/ping".equals(line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT))) {
            return false;
        }
        long stamp = clockMillis.getAsLong();
        pendingStamp = OptionalLong.of(stamp);
        sender.sendReadRequest(
                Opcode.OPCODE_PING_VALUE,
                PingCommand.newBuilder().setClientTimestampMs(stamp).build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_PONG_VALUE || pendingStamp.isEmpty()) {
            return false;
        }
        long echoed = PongResponse.parseFrom(frame.payload()).getClientTimestampMs();
        if (echoed != pendingStamp.getAsLong()) {
            return false; // pong của heartbeat định kỳ
        }
        pendingStamp = OptionalLong.empty();
        ConnectionCoordinator.plausibleRoundTrip(echoed, clockMillis.getAsLong())
                .ifPresent(roundTrip -> chat.showSystemNotice(KageI18n.formatOrKey(
                        i18n, "ping.line", roundTrip, KageI18n.textOrKey(i18n, qualityKey(roundTrip)))));
        return false; // không giành gói — luồng kết nối vẫn cập nhật HUD
    }

    @Override
    public void reset() {
        pendingStamp = OptionalLong.empty();
    }
}
