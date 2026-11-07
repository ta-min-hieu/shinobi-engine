package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.FriendView;
import dev.kage.protocol.ListFriendsRequest;
import dev.kage.protocol.ListFriendsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RenameGuildCommand;
import dev.kage.protocol.RenameGuildResponse;
import dev.kage.protocol.SetFriendNoteCommand;
import dev.kage.protocol.SetFriendNoteResponse;

/**
 * Bước lô 30 (phần xã hội): đổi tên bang (ADR-172) {@code renameGuild:<tên>}; ghi chú bạn bè (ADR-173)
 * {@code noteFriend:<tên>=<ghi chú>} và {@code listFriends}. Tách khỏi {@link Lot30Steps} vì switch ở đó chạm trần
 * CyclomaticComplexity.
 */
final class Lot30SocialSteps {

    private final Lot23Scenario.Asker asker;
    private final Lot31Steps lot31;

    Lot30SocialSteps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot31 = new Lot31Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "renameGuild" -> renameGuild(arg);
            case "noteFriend" -> noteFriend(arg);
            case "listFriends" -> listFriends();
            default -> lot31.step(verb, arg);
        };
    }

    private String renameGuild(String name) throws IOException {
        return asker.ask(
                Opcode.OPCODE_RENAME_GUILD_COMMAND_VALUE,
                RenameGuildCommand.newBuilder().setNewName(name).build(),
                Opcode.OPCODE_RENAME_GUILD_RESPONSE_VALUE,
                p -> {
                    RenameGuildResponse r = RenameGuildResponse.parseFrom(p);
                    return r.getOutcome().name() + " tên=" + r.getName() + " phí=" + r.getFee();
                });
    }

    private String noteFriend(String arg) throws IOException {
        String[] parts = arg.split("=", 2);
        return asker.ask(
                Opcode.OPCODE_SET_FRIEND_NOTE_COMMAND_VALUE,
                SetFriendNoteCommand.newBuilder()
                        .setTargetName(parts[0])
                        .setNote(parts.length > 1 ? parts[1] : "")
                        .build(),
                Opcode.OPCODE_SET_FRIEND_NOTE_RESPONSE_VALUE,
                p -> {
                    SetFriendNoteResponse r = SetFriendNoteResponse.parseFrom(p);
                    return r.getOutcome().name() + " bạn=" + r.getTargetName() + " ghi chú=" + r.getNote();
                });
    }

    private String listFriends() throws IOException {
        return asker.ask(
                Opcode.OPCODE_LIST_FRIENDS_REQUEST_VALUE,
                ListFriendsRequest.getDefaultInstance(),
                Opcode.OPCODE_LIST_FRIENDS_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder();
                    for (FriendView friend : ListFriendsResponse.parseFrom(p).getFriendsList()) {
                        out.append(friend.getName())
                                .append('[')
                                .append(friend.getNote())
                                .append("] online=")
                                .append(friend.getOnline())
                                .append(" lastOnline=") // ADR-192
                                .append(friend.getLastOnlineEpochSeconds())
                                .append(' ');
                    }
                    return out.toString();
                });
    }
}
