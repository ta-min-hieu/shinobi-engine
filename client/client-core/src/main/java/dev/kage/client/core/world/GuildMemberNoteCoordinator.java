package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GuildMemberNote;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetGuildMemberNoteCommand;
import dev.kage.protocol.SetGuildMemberNoteOutcome;
import dev.kage.protocol.SetGuildMemberNoteResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Ghi chú thành viên bang (ADR-319) — {@code /ghichubang} xem mọi ghi chú; {@code /ghichubang <tên> <ghi chú>} đặt;
 * {@code /ghichubang <tên> xoa} xoá. Chỉ bang chủ / phó (server quyết).
 */
final class GuildMemberNoteCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    GuildMemberNoteCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 3);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/ghichubang".equals(command) && !"/membernote".equals(command)) {
            return false;
        }
        SetGuildMemberNoteCommand.Builder request = SetGuildMemberNoteCommand.newBuilder();
        if (words.length < 2) {
            request.setQueryOnly(true);
        } else if (words.length < 3) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "membernote.usage"));
            return true;
        } else {
            request.setMemberName(words[1])
                    .setNote(
                            ChatLayoutCoordinator.isOff(words[2]) || "xoa".equals(MentionWatch.fold(words[2]))
                                    ? ""
                                    : words[2]);
        }
        sender.sendReadRequest(Opcode.OPCODE_SET_GUILD_MEMBER_NOTE_COMMAND_VALUE, request.build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SET_GUILD_MEMBER_NOTE_RESPONSE_VALUE) {
            return false;
        }
        SetGuildMemberNoteResponse response = SetGuildMemberNoteResponse.parseFrom(frame.payload());
        if (response.getOutcome() != SetGuildMemberNoteOutcome.SET_GUILD_MEMBER_NOTE_OUTCOME_OK) {
            chat.showSystemNotice(KageI18n.textOrKey(
                    i18n,
                    "membernote."
                            + response.getOutcome()
                                    .name()
                                    .replace("SET_GUILD_MEMBER_NOTE_OUTCOME_", "")
                                    .toLowerCase(Locale.ROOT)));
            return true;
        }
        if (response.getNotesCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "membernote.empty"));
            return true;
        }
        for (GuildMemberNote note : response.getNotesList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n, "membernote.line", note.getName(), ChatController.sanitize(note.getNote())));
        }
        return true;
    }
}
