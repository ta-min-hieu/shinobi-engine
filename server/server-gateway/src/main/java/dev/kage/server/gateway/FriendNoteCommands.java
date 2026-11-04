package dev.kage.server.gateway;

import java.util.Optional;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.friend.api.FriendFacade;
import dev.kage.friend.api.FriendNoteFacade;
import dev.kage.protocol.SetFriendNoteCommand;
import dev.kage.protocol.SetFriendNoteOutcome;
import dev.kage.protocol.SetFriendNoteResponse;

/** Ghi chú bạn bè (ADR-173) — chỉ ghi chú được người đang là bạn; ghi chú rỗng = xoá. */
final class FriendNoteCommands {

    /** Nguồn dữ liệu. */
    record Deps(CharacterFacade characters, FriendFacade friends, FriendNoteFacade notes) {}

    private final Deps deps;

    FriendNoteCommands(Deps deps) {
        this.deps = deps;
    }

    /** Virtual Thread; {@code characterId} (chủ ghi chú) từ session. */
    SetFriendNoteResponse setNote(long characterId, SetFriendNoteCommand command) {
        Optional<Character> target =
                deps.characters().findByName(command.getTargetName().strip());
        if (target.isEmpty()) {
            return outcome(SetFriendNoteOutcome.SET_FRIEND_NOTE_OUTCOME_TARGET_NOT_FOUND)
                    .build();
        }
        Character friend = target.get();
        SetFriendNoteResponse.Builder response =
                outcome(SetFriendNoteOutcome.SET_FRIEND_NOTE_OUTCOME_OK).setTargetName(friend.name());
        if (!deps.friends().listFriends(characterId).contains(friend.id())) {
            return response.setOutcome(SetFriendNoteOutcome.SET_FRIEND_NOTE_OUTCOME_NOT_FRIENDS)
                    .build();
        }
        Optional<String> note = FriendNoteFacade.normalize(command.getNote());
        if (note.isEmpty()) {
            return response.setOutcome(SetFriendNoteOutcome.SET_FRIEND_NOTE_OUTCOME_TOO_LONG)
                    .build();
        }
        deps.notes().set(characterId, friend.id(), note.get());
        return response.setNote(note.get()).build();
    }

    private static SetFriendNoteResponse.Builder outcome(SetFriendNoteOutcome outcome) {
        return SetFriendNoteResponse.newBuilder().setOutcome(outcome);
    }
}
