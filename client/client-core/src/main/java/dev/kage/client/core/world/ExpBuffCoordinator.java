package dev.kage.client.core.world;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.Timer;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ExpBuffChangedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Đan x2 kinh nghiệm (ADR-164) — nhận {@code ExpBuffChangedEvent} (vừa dùng đan, hoặc vào world khi còn hạn) và hiện
 * dòng "x2 EXP tới HH:mm" ở góc trên-phải; tự gỡ khi hết giờ. Chỉ HIỂN THỊ — server mới là bên nhân EXP.
 */
final class ExpBuffCoordinator implements WindowFlow {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final long SECONDS_PER_MINUTE = 60L;

    private final I18NBundle i18n = KageI18n.load();
    private Consumer<String> banner = text -> {};
    private Timer.Task clearTask = new Timer.Task() {
        @Override
        public void run() {
            // rỗng tới lần hẹn đầu
        }
    };

    /** Nối dòng HUD — {@code ExpansionWindows.wireDisplay}. */
    void useBanner(Consumer<String> setter) {
        this.banner = setter;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_EXP_BUFF_CHANGED_EVENT_VALUE) {
            return false;
        }
        long secondsLeft = ExpBuffChangedEvent.parseFrom(frame.payload()).getSecondsLeft();
        banner.accept(describe(secondsLeft, LocalTime.now(ZoneId.systemDefault())));
        clearTask.cancel();
        if (secondsLeft > 0) {
            clearTask = Timer.schedule(
                    new Timer.Task() {
                        @Override
                        public void run() {
                            banner.accept("");
                        }
                    },
                    (float) secondsLeft);
        }
        return true;
    }

    /** Chữ HUD cho {@code secondsLeft} tính từ {@code now} — tách ra để test không cần màn hình. */
    String describe(long secondsLeft, LocalTime now) {
        if (secondsLeft <= 0) {
            return "";
        }
        return KageI18n.formatOrKey(
                i18n,
                "exp_buff.banner",
                CLOCK.format(now.plusSeconds(secondsLeft)),
                (secondsLeft + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE);
    }

    @Override
    public void reset() {
        clearTask.cancel();
        banner.accept("");
    }
}
