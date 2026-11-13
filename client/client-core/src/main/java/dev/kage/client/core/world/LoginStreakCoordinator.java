package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetLoginStreakRequest;
import dev.kage.protocol.GetLoginStreakResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Chuỗi đăng nhập liên tiếp (ADR-327) — {@code /chuoi} (hoặc {@code /streak}): chuỗi hiện tại, dài nhất, mốc thưởng
 * kế tiếp. Server tự tính lúc vào world và gửi thư quà ở mốc.
 */
final class LoginStreakCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    LoginStreakCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/chuoi".equals(command) && !"/streak".equals(command)) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_LOGIN_STREAK_REQUEST_VALUE, GetLoginStreakRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_LOGIN_STREAK_RESPONSE_VALUE) {
            return false;
        }
        GetLoginStreakResponse response = GetLoginStreakResponse.parseFrom(frame.payload());
        chat.showSystemNotice(KageI18n.formatOrKey(
                i18n,
                "streak.line",
                response.getCurrent(),
                response.getBest(),
                response.getNextRewardDay(),
                response.getNextRewardGold()));
        return true;
    }
}
