package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.friend.api.FriendFacade;
import dev.kage.protocol.CaveAction;
import dev.kage.protocol.CaveCommand;
import dev.kage.protocol.CaveDecorationView;
import dev.kage.protocol.CaveInviteEvent;
import dev.kage.protocol.CaveOutcome;
import dev.kage.protocol.CaveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.world.api.CaveFacade;
import dev.kage.world.domain.CavePort;
import dev.kage.world.domain.CaveRules;

/**
 * Động phủ (ADR-129) — chạy trên Virtual Thread. Động phủ là một World không quái mang khoá riêng theo CHỦ
 * ({@link #caveKey}); chủ và khách được mời vào cùng khoá thì vào CHUNG một World. Thu linh thạch chỉ khi người gửi
 * đang đứng trong động phủ của chính mình — server kiểm qua khoá instance, không tin client.
 */
final class CaveCommands {

    /** Đầu dải khoá instance của động phủ — xa dải id nhóm (dương) và dải {@code -characterId} của đài Thiên Kiếp. */
    static final long CAVE_KEY_BASE = -(1L << 40);

    static final String INSTANCE_CODE = "cave_dwelling";

    /** Thứ lệnh này cần. */
    record Deps(
            CaveFacade caves,
            CharacterFacade characters,
            FriendFacade friends,
            DungeonInstanceRegistry dungeons,
            ConnectedCharacterRegistry registry,
            Clock clock) {}

    private final Deps deps;

    CaveCommands(Deps deps) {
        this.deps = deps;
    }

    /** ADR-134 — cấp linh mạch của mọi động phủ (Bia Anh Danh). Chạm DB — gọi trên Virtual Thread. */
    java.util.Map<Long, Integer> veinLevels() {
        return deps.caves().veinLevels();
    }

    static long caveKey(long ownerId) {
        return CAVE_KEY_BASE - ownerId;
    }

    CaveResponse handle(long characterId, CaveCommand command) {
        Instant now = deps.clock().instant();
        CaveAction action = command.getAction();
        return switch (action) {
            case CAVE_ACTION_ENTER -> entered(characterId, characterId, action, now);
            case CAVE_ACTION_COLLECT ->
                inOwnCave(characterId)
                        ? result(characterId, action, deps.caves().collect(characterId, now), now)
                        : view(characterId, action, CaveOutcome.CAVE_OUTCOME_NOT_IN_OWN_CAVE, now);
            case CAVE_ACTION_UPGRADE -> result(characterId, action, deps.caves().upgrade(characterId, now), now);
            case CAVE_ACTION_DECORATE ->
                result(characterId, action, deps.caves().decorate(characterId, command.getDecorationCode(), now), now);
            case CAVE_ACTION_INVITE -> invite(characterId, command.getTargetName(), now);
            case CAVE_ACTION_VISIT -> visit(characterId, command.getTargetName(), now);
            default -> view(characterId, CaveAction.CAVE_ACTION_VIEW, CaveOutcome.CAVE_OUTCOME_OK, now);
        };
    }

    private boolean inOwnCave(long characterId) {
        return deps.dungeons().instanceKeyOf(characterId).equals(Optional.of(caveKey(characterId)));
    }

    private CaveResponse entered(long characterId, long ownerId, CaveAction action, Instant now) {
        DungeonInstanceRegistry.EnterOutcome outcome =
                deps.dungeons().enterShared(characterId, caveKey(ownerId), INSTANCE_CODE);
        return view(
                characterId,
                action,
                outcome == DungeonInstanceRegistry.EnterOutcome.OK
                        ? CaveOutcome.CAVE_OUTCOME_OK
                        : CaveOutcome.CAVE_OUTCOME_BUSY,
                now);
    }

    private CaveResponse invite(long characterId, String targetName, Instant now) {
        Optional<Character> target = deps.characters().findByName(targetName.strip());
        CaveOutcome refused = refusal(characterId, target);
        if (refused != CaveOutcome.CAVE_OUTCOME_OK) {
            return view(characterId, CaveAction.CAVE_ACTION_INVITE, refused, now);
        }
        long guestId = target.get().id();
        if (!deps.registry().isOnline(guestId)) {
            return view(characterId, CaveAction.CAVE_ACTION_INVITE, CaveOutcome.CAVE_OUTCOME_NOT_ONLINE, now);
        }
        deps.caves().invite(characterId, guestId, now);
        String ownerName =
                deps.characters().findById(characterId).map(Character::name).orElse("");
        deps.registry()
                .sendToCharacterIfOnline(
                        guestId,
                        Opcode.OPCODE_CAVE_INVITE_EVENT_VALUE,
                        0,
                        CaveInviteEvent.newBuilder()
                                .setOwnerName(ownerName)
                                .setMinutes((int) CaveRules.INVITE_TTL.toMinutes())
                                .build()
                                .toByteArray());
        return view(characterId, CaveAction.CAVE_ACTION_INVITE, CaveOutcome.CAVE_OUTCOME_OK, now).toBuilder()
                .setTargetName(target.get().name())
                .build();
    }

    private CaveOutcome refusal(long characterId, Optional<Character> target) {
        if (target.isEmpty() || target.get().id() == characterId) {
            return CaveOutcome.CAVE_OUTCOME_UNKNOWN_CHARACTER;
        }
        if (!deps.friends().listFriends(characterId).contains(target.get().id())) {
            return CaveOutcome.CAVE_OUTCOME_NOT_FRIEND;
        }
        return CaveOutcome.CAVE_OUTCOME_OK;
    }

    private CaveResponse visit(long characterId, String ownerName, Instant now) {
        Optional<Character> owner = deps.characters().findByName(ownerName.strip());
        if (owner.isEmpty()) {
            return view(characterId, CaveAction.CAVE_ACTION_VISIT, CaveOutcome.CAVE_OUTCOME_UNKNOWN_CHARACTER, now);
        }
        if (!deps.caves().mayEnter(characterId, owner.get().id(), now)) {
            return view(characterId, CaveAction.CAVE_ACTION_VISIT, CaveOutcome.CAVE_OUTCOME_NOT_INVITED, now);
        }
        return entered(characterId, owner.get().id(), CaveAction.CAVE_ACTION_VISIT, now).toBuilder()
                .setTargetName(owner.get().name())
                .build();
    }

    private CaveResponse result(long characterId, CaveAction action, CavePort.Result result, Instant now) {
        CaveOutcome outcome =
                CaveOutcome.valueOf("CAVE_OUTCOME_" + result.outcome().name());
        return view(characterId, action, outcome, now).toBuilder()
                .setCollected(result.collected())
                .build();
    }

    private CaveResponse view(long characterId, CaveAction action, CaveOutcome outcome, Instant now) {
        CavePort.Cave cave = deps.caves().cave(characterId, now);
        int bonus = deps.caves().bonusPercentOf(cave);
        CaveResponse.Builder response = CaveResponse.newBuilder()
                .setAction(action)
                .setOutcome(outcome)
                .setVeinLevel(cave.veinLevel())
                .setPerHour(CaveRules.perHour(cave.veinLevel(), bonus))
                .setStored(deps.caves().stored(cave, now))
                .setCapacity(CaveRules.capacity(cave.veinLevel(), bonus))
                .setUpgradeCost(
                        cave.veinLevel() >= CaveRules.MAX_VEIN_LEVEL ? 0L : CaveRules.upgradeCost(cave.veinLevel()));
        for (CavePort.Decoration decoration : deps.caves().catalog()) {
            response.addDecorations(CaveDecorationView.newBuilder()
                    .setCode(decoration.code())
                    .setNameKey(decoration.nameKey())
                    .setGoldCost(decoration.goldCost())
                    .setBonusPercent(decoration.bonusPercent())
                    .setPlaced(cave.decorations().contains(decoration.code())));
        }
        return response.build();
    }
}
