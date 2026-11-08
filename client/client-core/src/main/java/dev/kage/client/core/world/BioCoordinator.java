package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetBioCommand;
import dev.kage.protocol.SetBioOutcome;
import dev.kage.protocol.SetBioResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Hồ sơ cá nhân (ADR-218) — {@code /gioithieu <lời giới thiệu>} (hoặc {@code /bio}); không kèm gì là xoá. Dòng này hiện
 * khi người khác soi trang bị của mình.
 */
final class BioCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/gioithieu";
    private static final String COMMAND_EN = "/bio";
    private static final String OUTCOME_PREFIX = "SET_BIO_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    BioCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SET_BIO_COMMAND_VALUE,
                SetBioCommand.newBuilder()
                        .setBio(words.length > 1 ? words[1] : "")
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SET_BIO_RESPONSE_VALUE) {
            return false;
        }
        SetBioResponse response = SetBioResponse.parseFrom(frame.payload());
        if (response.getOutcome() == SetBioOutcome.SET_BIO_OUTCOME_OK) {
            chat.showSystemNotice(
                    response.getBio().isEmpty()
                            ? KageI18n.textOrKey(i18n, "bio.cleared")
                            : KageI18n.formatOrKey(i18n, "bio.saved", ChatController.sanitize(response.getBio())));
        } else {
            String key = response.getOutcome() == SetBioOutcome.UNRECOGNIZED
                    ? "unspecified"
                    : response.getOutcome()
                            .name()
                            .substring(OUTCOME_PREFIX.length())
                            .toLowerCase(Locale.ROOT);
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "bio." + key));
        }
        return true;
    }
}
