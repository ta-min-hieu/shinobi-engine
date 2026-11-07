package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RenamePetCommand;
import dev.kage.protocol.RenamePetOutcome;
import dev.kage.protocol.RenamePetResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Đặt tên linh thú (ADR-193) — lệnh chat {@code /tenlinhthu <tên>} (hoặc {@code /petname}); không kèm tên = xoá tên
 * riêng. Server kiểm luật tên và từ cấm; tên hiện trên bảng nhân vật.
 */
final class PetNameCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/tenlinhthu";
    private static final String COMMAND_EN = "/petname";
    private static final String OUTCOME_PREFIX = "RENAME_PET_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    PetNameCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
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
                Opcode.OPCODE_RENAME_PET_COMMAND_VALUE,
                RenamePetCommand.newBuilder()
                        .setName(words.length < 2 ? "" : words[1].strip())
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_RENAME_PET_RESPONSE_VALUE) {
            return false;
        }
        chat.showSystemNotice(describe(i18n, RenamePetResponse.parseFrom(frame.payload())));
        return true;
    }

    /** Dòng hệ thống cho một phản hồi — tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, RenamePetResponse response) {
        RenamePetOutcome outcome = response.getOutcome();
        String suffix = outcome == RenamePetOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.formatOrKey(i18n, "pet_name.outcome." + suffix, ChatController.sanitize(response.getName()));
    }
}
