package dev.kage.server.gateway;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.friend.api.SwornFacade;
import dev.kage.friend.domain.SwornOutcome;
import dev.kage.protocol.AcceptSwornResponse;
import dev.kage.protocol.InviteSwornResponse;
import dev.kage.protocol.LeaveSwornResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SwornInviteEvent;

/** Kết nghĩa huynh đệ (ADR-110) — tra tên, gọi feature-friend, báo người liên quan. Chạy trên Virtual Thread. */
final class SwornCommands {

    private static final Map<SwornOutcome, dev.kage.protocol.SwornOutcome> OUTCOMES = new EnumMap<>(SwornOutcome.class);

    static {
        for (SwornOutcome outcome : SwornOutcome.values()) {
            OUTCOMES.put(outcome, dev.kage.protocol.SwornOutcome.valueOf("SWORN_OUTCOME_" + outcome.name()));
        }
    }

    private final SwornFacade sworn;
    private final CharacterFacade characters;
    private final BlockCommands blocks;
    private final PlayerNotices notices;

    SwornCommands(SwornFacade sworn, CharacterFacade characters, BlockCommands blocks, PlayerNotices notices) {
        this.sworn = sworn;
        this.characters = characters;
        this.blocks = blocks;
        this.notices = notices;
    }

    InviteSwornResponse invite(long inviterId, String targetName) {
        Optional<Character> target = characters.findByName(targetName.strip());
        if (target.isEmpty()) {
            return InviteSwornResponse.newBuilder()
                    .setOutcome(OUTCOMES.get(SwornOutcome.TARGET_NOT_FOUND))
                    .build();
        }
        blocks.requireNotBlocked(inviterId, target.get().id());
        SwornOutcome outcome = sworn.invite(inviterId, target.get().id());
        if (outcome == SwornOutcome.OK) {
            notices.event(
                    target.get().id(),
                    Opcode.OPCODE_SWORN_INVITE_EVENT_VALUE,
                    SwornInviteEvent.newBuilder()
                            .setInviterName(nameOf(inviterId))
                            .build()
                            .toByteArray());
        }
        return InviteSwornResponse.newBuilder()
                .setOutcome(OUTCOMES.get(outcome))
                .build();
    }

    AcceptSwornResponse accept(long inviteeId, String inviterName) {
        Optional<Character> inviter = characters.findByName(inviterName.strip());
        if (inviter.isEmpty()) {
            return AcceptSwornResponse.newBuilder()
                    .setOutcome(OUTCOMES.get(SwornOutcome.TARGET_NOT_FOUND))
                    .build();
        }
        blocks.requireNotBlocked(inviteeId, inviter.get().id());
        SwornOutcome outcome = sworn.accept(inviteeId, inviter.get().id());
        if (outcome == SwornOutcome.OK) {
            String name = nameOf(inviteeId);
            for (long member : sworn.membersOf(inviteeId)) {
                if (member != inviteeId) {
                    notices.systemLine(
                            member,
                            "%s đã kết nghĩa huynh đệ. / %s joined your sworn brotherhood.".formatted(name, name));
                }
            }
        }
        return AcceptSwornResponse.newBuilder()
                .setOutcome(OUTCOMES.get(outcome))
                .build();
    }

    LeaveSwornResponse leave(long characterId) {
        List<Long> before = sworn.membersOf(characterId);
        if (before.isEmpty()) {
            return LeaveSwornResponse.newBuilder()
                    .setOutcome(OUTCOMES.get(SwornOutcome.NOT_SWORN))
                    .build();
        }
        String name = nameOf(characterId);
        for (long member : sworn.leave(characterId)) {
            notices.systemLine(
                    member, "%s đã rời nhóm kết nghĩa. / %s left your sworn brotherhood.".formatted(name, name));
        }
        return LeaveSwornResponse.newBuilder()
                .setOutcome(OUTCOMES.get(SwornOutcome.OK))
                .build();
    }

    private String nameOf(long characterId) {
        return characters.findById(characterId).map(Character::name).orElse("#" + characterId);
    }
}
