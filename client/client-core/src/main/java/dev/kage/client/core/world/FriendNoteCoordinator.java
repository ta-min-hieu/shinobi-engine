package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetFriendNoteCommand;
import dev.kage.protocol.SetFriendNoteOutcome;
import dev.kage.protocol.SetFriendNoteResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Ghi chú bạn bè (ADR-173) — lệnh chat {@code /ghichu <tên bạn> [ghi chú]} (hoặc {@code /note}); bỏ trống ghi chú để
 * xoá. Server kiểm "là bạn" và độ dài; ghi xong thì hỏi lại danh sách bạn để ghi chú hiện kèm tên.
 */
final class FriendNoteCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/ghichu";
    private static final String COMMAND_EN = "/note";
    private static final String OUTCOME_PREFIX = "SET_FRIEND_NOTE_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    FriendNoteCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 3);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        if (words.length < 2) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "friend_note.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SET_FRIEND_NOTE_COMMAND_VALUE,
                SetFriendNoteCommand.newBuilder()
                        .setTargetName(words[1])
                        .setNote(words.length > 2 ? words[2] : "")
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SET_FRIEND_NOTE_RESPONSE_VALUE) {
            return false;
        }
        SetFriendNoteResponse response = SetFriendNoteResponse.parseFrom(frame.payload());
        chat.showSystemNotice(describe(i18n, response));
        if (response.getOutcome() == SetFriendNoteOutcome.SET_FRIEND_NOTE_OUTCOME_OK) {
            sender.listFriends();
        }
        return true;
    }

    /** Dòng hệ thống cho một phản hồi — tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, SetFriendNoteResponse response) {
        SetFriendNoteOutcome outcome = response.getOutcome();
        String name = ChatController.sanitize(response.getTargetName());
        if (outcome == SetFriendNoteOutcome.SET_FRIEND_NOTE_OUTCOME_OK) {
            return response.getNote().isEmpty()
                    ? KageI18n.formatOrKey(i18n, "friend_note.cleared", name)
                    : KageI18n.formatOrKey(i18n, "friend_note.ok", name, ChatController.sanitize(response.getNote()));
        }
        String suffix = outcome == SetFriendNoteOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.formatOrKey(i18n, "friend_note.outcome." + suffix, name);
    }
}
