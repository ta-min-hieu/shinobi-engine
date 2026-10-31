package dev.kage.client.core.world;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.DayNightEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Ngày đêm (ADR-123) — nhận gói 327 (server gửi lặp mỗi 30 giây), đổi độ tối của {@link DayNightShade} và báo một dòng
 * hệ thống lúc trời chuyển (không báo ở gói đầu tiên sau khi vào world). Không có cửa sổ.
 */
final class DayNightCoordinator implements WindowFlow {

    private final DayNightShade shade = new DayNightShade();
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private boolean known;
    private boolean night;

    DayNightCoordinator(ChatCoordinator chat) {
        this.chat = chat;
    }

    DayNightShade shade() {
        return shade;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_DAY_NIGHT_EVENT_VALUE) {
            return false;
        }
        boolean nowNight = DayNightEvent.parseFrom(frame.payload()).getNight();
        if (known && nowNight != night) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, nowNight ? "daynight.notice.dusk" : "daynight.notice.dawn"));
        }
        known = true;
        night = nowNight;
        shade.setNight(nowNight);
        return true;
    }

    @Override
    public void reset() {
        known = false;
        night = false;
        shade.reset();
    }
}
