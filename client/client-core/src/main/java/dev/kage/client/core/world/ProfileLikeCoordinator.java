package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.LikeProfileCommand;
import dev.kage.protocol.LikeProfileResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/** Thích hồ sơ (ADR-298) — {@code /thich <tên>} (hoặc {@code /like}): mỗi ngày thích một người một lần. */
final class ProfileLikeCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    ProfileLikeCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/thich".equals(command) && !"/like".equals(command)) {
            return false;
        }
        if (words.length < 2 || words[1].isBlank()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "like.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_LIKE_PROFILE_COMMAND_VALUE,
                LikeProfileCommand.newBuilder().setTargetName(words[1].strip()).build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_LIKE_PROFILE_RESPONSE_VALUE) {
            return false;
        }
        LikeProfileResponse response = LikeProfileResponse.parseFrom(frame.payload());
        String key = "like."
                + response.getOutcome()
                        .name()
                        .replace("LIKE_PROFILE_OUTCOME_", "")
                        .toLowerCase(Locale.ROOT);
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, key, response.getTargetName(), response.getLikes()));
        return true;
    }
}
