package dev.kage.server.gateway;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.party.api.PartyFacade;
import dev.kage.server.world.CharacterBaseStats;
import dev.kage.server.world.Element;
import dev.kage.server.world.WorldFacade;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Vòng đời dungeon instance + bảng định tuyến {@code characterId -> WorldFacade} (Phase 8 lát cắt
 * 1, ADR-026). CHƯA phải {@code feature-dungeon} (Clean Architecture đầy đủ) — lát cắt này không
 * có dữ liệu nghiệp vụ nào cần persist (không cooldown, không reward, không dungeon template DB),
 * nên logic "cả party cùng vào" đặt thẳng ở đây (gateway đã compose {@link PartyFacade}/
 * {@link CharacterFacade} sẵn, giống {@code SocialFacades}). Khi lát cắt sau cần dữ liệu thật, đó
 * là lúc tách ra module riêng.
 *
 * <p>Chỉ 1 dungeon hardcode ({@link #DUNGEON_CODE}) — cùng phong cách
 * {@code WorldFacade.TRAINING_DUMMY_CODE}/{@code SAMPLE_NPC_CODE}. Bất kỳ thành viên nào gửi
 * {@code EnterDungeonCommand} sẽ kéo THEO CẢ party đang online vào CHUNG 1 instance (GAMEPLAY.md
 * §6 "Mỗi party 1 instance riêng") — không cần leader riêng cho hành động này ở lát cắt 1.
 *
 * <p>Mọi phương thức được gọi từ {@code authExecutor} (Virtual Thread) của
 * {@code GatewayChannelHandler} — chạm {@link PartyFacade}/{@link CharacterFacade} (DB, blocking),
 * KHÔNG được gọi từ Netty EventLoop hay world tick thread (server/CLAUDE.md).
 */
final class DungeonInstanceRegistry {

    private static final String DUNGEON_CODE = "sample_dungeon";
    private static final float DUNGEON_ENTRY_SPAWN_X = 0f;
    private static final float DUNGEON_ENTRY_SPAWN_Y = 0f;

    private final ConnectedCharacterRegistry registry;
    private final CharacterFacade characterFacade;
    private final PartyFacade partyFacade;
    private final MainWorldChannelRegistry mainWorldChannels;

    private final Map<Long, DungeonInstance> instancesByPartyId = new HashMap<>();
    private final Map<Long, DungeonInstance> instanceByCharacterId = new HashMap<>();
    private final Map<Long, PositionSnapshot> returnPositionByCharacterId = new HashMap<>();

    DungeonInstanceRegistry(
            ConnectedCharacterRegistry registry, CharacterFacade characterFacade, PartyFacade partyFacade,
            MainWorldChannelRegistry mainWorldChannels) {
        this.registry = registry;
        this.characterFacade = characterFacade;
        this.partyFacade = partyFacade;
        this.mainWorldChannels = mainWorldChannels;
    }

    /** {@code WorldFacade} nhân vật này đang ở TRONG (dungeon), rỗng nếu đang ở world chính. */
    Optional<WorldFacade> worldFacadeFor(long characterId) {
        DungeonInstance instance = instanceByCharacterId.get(characterId);
        return instance == null ? Optional.empty() : Optional.of(instance.facade());
    }

    EnterOutcome enterDungeon(long requestingCharacterId, String dungeonCode) {
        if (!DUNGEON_CODE.equals(dungeonCode)) {
            return EnterOutcome.UNKNOWN_DUNGEON;
        }
        if (instanceByCharacterId.containsKey(requestingCharacterId)) {
            return EnterOutcome.ALREADY_IN_DUNGEON;
        }
        Optional<PartyFacade.MyPartyView> myParty = partyFacade.findMyParty(requestingCharacterId);
        if (myParty.isEmpty()) {
            return EnterOutcome.NOT_IN_PARTY;
        }
        List<PartyFacade.MemberView> members = partyFacade.listMembers(requestingCharacterId);
        for (PartyFacade.MemberView member : members) {
            if (!registry.isOnline(member.characterId())) {
                return EnterOutcome.PARTY_MEMBER_OFFLINE;
            }
        }

        long partyId = myParty.get().partyId();
        DungeonInstance instance = instancesByPartyId.computeIfAbsent(partyId, id -> createInstance(id));
        for (PartyFacade.MemberView member : members) {
            if (!instanceByCharacterId.containsKey(member.characterId())) {
                moveCharacterIntoDungeon(member.characterId(), instance);
            }
        }
        return EnterOutcome.OK;
    }

    ExitOutcome exitDungeon(long characterId) {
        DungeonInstance instance = instanceByCharacterId.get(characterId);
        if (instance == null) {
            return ExitOutcome.NOT_IN_DUNGEON;
        }
        instance.facade().detachCharacter(characterId);
        CharacterBaseStats baseStats = loadBaseStats(characterId);
        PositionSnapshot returnPosition = returnPositionByCharacterId.remove(characterId);
        mainWorldChannels.attachCharacter(characterId, returnPosition.x(), returnPosition.y(), baseStats);
        leaveInstance(characterId, instance);
        return ExitOutcome.OK;
    }

    /**
     * Gọi từ {@code channelInactive} (mất kết nối) — dọn dungeon facade + bookkeeping, KHÔNG
     * re-attach vào world chính (đã ngắt kết nối, không còn ai để gửi packet). {@code false} nếu
     * nhân vật không ở dungeon nào — caller tự xử lý detach world chính như bình thường.
     */
    boolean handleDisconnectIfInDungeon(long characterId) {
        DungeonInstance instance = instanceByCharacterId.get(characterId);
        if (instance == null) {
            return false;
        }
        instance.facade().detachCharacter(characterId);
        returnPositionByCharacterId.remove(characterId);
        leaveInstance(characterId, instance);
        return true;
    }

    /** Graceful shutdown (SERVER.md §10) — dừng MỌI dungeon World đang chạy trước khi thoát process. */
    void stopAll() {
        instancesByPartyId.values().forEach(instance -> instance.facade().stop());
        instancesByPartyId.clear();
        instanceByCharacterId.clear();
        returnPositionByCharacterId.clear();
    }

    private DungeonInstance createInstance(long partyId) {
        WorldFacade dungeonFacade = new WorldFacade(new DungeonOutboundSink(registry), false);
        dungeonFacade.start();
        // Phase 8 lát cắt 2 — NƠI DUY NHẤT "dungeon" và "boss" được nối với nhau; WorldFacade vẫn
        // không biết khái niệm "dungeon" (chỉ biết "có ai bảo spawn boss hay không").
        dungeonFacade.spawnBoss();
        return new DungeonInstance(partyId, dungeonFacade);
    }

    private void moveCharacterIntoDungeon(long characterId, DungeonInstance instance) {
        CharacterBaseStats baseStats = loadBaseStats(characterId);
        PositionSnapshot returnPosition = registry.lastKnownPosition(characterId);
        if (returnPosition == null) {
            returnPosition = characterFacade.loadPosition(characterId);
        }
        returnPositionByCharacterId.put(characterId, returnPosition);

        mainWorldChannels.detachCharacter(characterId);
        instance.facade().attachCharacter(characterId, DUNGEON_ENTRY_SPAWN_X, DUNGEON_ENTRY_SPAWN_Y, baseStats);
        instance.characterIds().add(characterId);
        instanceByCharacterId.put(characterId, instance);
    }

    /** Gỡ bookkeeping của {@code characterId} khỏi {@code instance} — dừng luôn World nếu rỗng. */
    private void leaveInstance(long characterId, DungeonInstance instance) {
        instance.characterIds().remove(characterId);
        instanceByCharacterId.remove(characterId);
        if (instance.characterIds().isEmpty()) {
            instance.facade().stop();
            instancesByPartyId.remove(instance.partyId());
        }
    }

    private CharacterBaseStats loadBaseStats(long characterId) {
        CharacterStats stats = characterFacade.loadStats(characterId);
        Optional<Character> character = characterFacade.findById(characterId);
        Element element = character.map(c -> FactionMapping.toElement(c.faction())).orElse(Element.NONE);
        int level = character.map(Character::level).orElse(1);
        return new CharacterBaseStats(stats.str(), stats.agi(), stats.intelligence(), stats.vit(), element, level);
    }

    /** 1 dungeon World đang chạy cho 1 party — {@code characterIds} mutable (thành viên vào/ra lẻ tẻ). */
    private record DungeonInstance(long partyId, WorldFacade facade, Set<Long> characterIds) {
        private DungeonInstance(long partyId, WorldFacade facade) {
            this(partyId, facade, new HashSet<>());
        }
    }

    enum EnterOutcome {
        OK,
        NOT_IN_PARTY,
        PARTY_MEMBER_OFFLINE,
        ALREADY_IN_DUNGEON,
        UNKNOWN_DUNGEON
    }

    enum ExitOutcome {
        OK,
        NOT_IN_DUNGEON
    }
}
