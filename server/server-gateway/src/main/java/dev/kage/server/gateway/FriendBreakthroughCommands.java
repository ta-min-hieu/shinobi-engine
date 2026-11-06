package dev.kage.server.gateway;

import java.time.Clock;
import java.util.Optional;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.friend.api.FriendFacade;
import dev.kage.protocol.CongratulateFriendCommand;
import dev.kage.protocol.CongratulateFriendOutcome;
import dev.kage.protocol.CongratulateFriendResponse;
import dev.kage.protocol.FriendBreakthroughEvent;
import dev.kage.protocol.FriendCongratulatedEvent;
import dev.kage.protocol.Opcode;

/**
 * ADR-182 — báo bạn bè đột phá: một người mở cảnh giới mới (đúng mốc của ADR-136) thì mọi người bạn đang online nhận
 * {@code FriendBreakthroughEvent}; trong 30 phút họ chúc mừng được một lần ({@code FriendCongratulatedEvent} tới người
 * vừa đột phá). Chỉ là lời chúc — không vật phẩm, không DB.
 */
final class FriendBreakthroughCommands {

    /** Nguồn dữ liệu. */
    record Deps(CharacterFacade characters, FriendFacade friends, ConnectedCharacterRegistry registry, Clock clock) {}

    private final Deps deps;

    FriendBreakthroughCommands(Deps deps) {
        this.deps = deps;
    }

    /** Nghe lên cấp (chạy trên luồng cộng EXP — Virtual Thread). */
    CharacterLevelSync.LevelListener listener() {
        return (characterId, level) ->
                BreakthroughHooks.realmOpenedAt(level).ifPresent(realm -> onBreakthrough(characterId, level, realm));
    }

    private void onBreakthrough(long characterId, int level, int realm) {
        Optional<Character> character = deps.characters().findById(characterId);
        if (character.isEmpty()) {
            return;
        }
        deps.registry()
                .friendBreakthroughs
                .record(characterId, realm, deps.clock().instant());
        byte[] payload = FriendBreakthroughEvent.newBuilder()
                .setFriendCharacterId(characterId)
                .setFriendName(character.get().name())
                .setRealm(realm)
                .setLevel(level)
                .build()
                .toByteArray();
        for (long friendId : deps.friends().listFriends(characterId)) {
            deps.registry()
                    .sendToCharacterIfOnline(friendId, Opcode.OPCODE_FRIEND_BREAKTHROUGH_EVENT_VALUE, 0, payload);
        }
    }

    /** Virtual Thread; {@code characterId} (người chúc) từ session. */
    CongratulateFriendResponse congratulate(long characterId, CongratulateFriendCommand command) {
        Optional<Character> target =
                deps.characters().findByName(command.getFriendName().strip());
        if (target.isEmpty()) {
            return outcome(CongratulateFriendOutcome.CONGRATULATE_FRIEND_OUTCOME_TARGET_NOT_FOUND, "");
        }
        Character friend = target.get();
        if (!deps.friends().listFriends(characterId).contains(friend.id())) {
            return outcome(CongratulateFriendOutcome.CONGRATULATE_FRIEND_OUTCOME_NOT_FRIENDS, friend.name());
        }
        FriendBreakthroughs.Congratulation result = deps.registry()
                .friendBreakthroughs
                .congratulate(characterId, friend.id(), deps.clock().instant());
        if (result.outcome() == FriendBreakthroughs.Outcome.OK) {
            String fromName =
                    deps.characters().findById(characterId).map(Character::name).orElse("");
            deps.registry()
                    .sendToCharacterIfOnline(
                            friend.id(),
                            Opcode.OPCODE_FRIEND_CONGRATULATED_EVENT_VALUE,
                            0,
                            FriendCongratulatedEvent.newBuilder()
                                    .setFromName(fromName)
                                    .setRealm(result.realm())
                                    .build()
                                    .toByteArray());
        }
        return outcome(
                CongratulateFriendOutcome.valueOf(
                        "CONGRATULATE_FRIEND_OUTCOME_" + result.outcome().name()),
                friend.name());
    }

    private static CongratulateFriendResponse outcome(CongratulateFriendOutcome outcome, String name) {
        return CongratulateFriendResponse.newBuilder()
                .setOutcome(outcome)
                .setFriendName(name)
                .build();
    }
}
