package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.party.api.PartyFacade;
import dev.kage.protocol.DungeonEnteredEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.WorldChangeReason;
import dev.kage.protocol.WorldKind;
import dev.kage.server.world.CharacterBaseStats;
import dev.kage.server.world.CharacterEntry;
import dev.kage.server.world.Element;
import dev.kage.server.world.EquipmentBonusSnapshot;
import dev.kage.server.world.TickMetricsPort;
import dev.kage.server.world.WorldContentCatalog;
import dev.kage.server.world.WorldFacade;

/**
 * Vòng đời dungeon instance + bảng định tuyến {@code characterId -> WorldFacade} (Phase 8 lát cắt
 * 1, ADR-026). CHƯA phải {@code feature-dungeon} (Clean Architecture đầy đủ) — lát cắt này không
 * có dữ liệu nghiệp vụ nào cần persist (không cooldown, không reward, không dungeon template DB),
 * nên logic "cả party cùng vào" đặt thẳng ở đây (gateway đã compose {@link PartyFacade}/
 * {@link CharacterFacade} sẵn, giống {@code SocialFacades}). Khi lát cắt sau cần dữ liệu thật, đó
 * là lúc tách ra module riêng.
 *
 * <p>Chỉ 1 dungeon hardcode ({@link #DUNGEON_CODE}) — quái, NPC và nay cả BOSS đều đã thoát khỏi
 * hardcode (ADR-037/ADR-040/ADR-048), dungeon thì chưa: nó vẫn là hằng số Java chứ chưa phải một
 * hàng trong bảng. Bất kỳ thành viên nào gửi
 * {@code EnterDungeonCommand} sẽ kéo THEO CẢ party đang online vào CHUNG 1 instance (GAMEPLAY.md
 * §6 "Mỗi party 1 instance riêng") — không cần leader riêng cho hành động này ở lát cắt 1.
 *
 * <p><b>Thread-safety (sửa 2026-09-24).</b> Bản cũ ghi "mọi phương thức được gọi từ
 * {@code authExecutor}" và dùng {@link HashMap} trần — sai ở hai chỗ: {@code authExecutor} là
 * một-Virtual-Thread-mỗi-task (hai thành viên bấm "vào hầm ngục" cùng lúc chạy SONG SONG), và
 * {@link #handleDisconnectIfInDungeon}/{@link #worldFacadeFor} chạy trên Netty EventLoop. Hai
 * lượt vào đồng thời từng có thể dựng HAI instance cho một nhóm (mỗi cái một boss, cái bị ghi đè
 * chạy tick mãi không ai dừng) và tách nhóm ra hai World. Nay mọi đọc/ghi bảng định tuyến nằm
 * trong {@code synchronized} ngắn — cùng cách {@link MainWorldChannelRegistry} — còn mọi lời gọi
 * DB ({@link PartyFacade}/{@link CharacterFacade}/trang bị) chạy TRƯỚC, ngoài khoá, để EventLoop
 * không bao giờ phải chờ một câu query (server/CLAUDE.md: EventLoop cấm chờ lock lâu).
 */
final class DungeonInstanceRegistry {

    private static final String DUNGEON_CODE = "sample_dungeon";

    /**
     * Boss của hầm ngục mẫu — mã trong {@code monster_template} (ADR-048).
     *
     * <p>Trước ADR-048 không có hằng số này vì `spawnBoss()` không có tham số: cả dự án chỉ có một
     * boss và nó hardcode trong `BossConstants`. Nay mã đi kèm lệnh, nên hầm ngục THỨ HAI chỉ cần
     * đổi chuỗi này thay vì sửa `server-world`.
     */
    private static final String DUNGEON_BOSS_CODE = "sample_boss";

    private static final float DUNGEON_ENTRY_SPAWN_X = 0f;
    private static final float DUNGEON_ENTRY_SPAWN_Y = 0f;
    private static final PositionSnapshot DUNGEON_ENTRY_POSITION =
            new PositionSnapshot(DUNGEON_ENTRY_SPAWN_X, DUNGEON_ENTRY_SPAWN_Y);

    private final ConnectedCharacterRegistry registry;
    private final CharacterFacade characterFacade;
    private final PartyFacade partyFacade;
    private final MainWorldChannelRegistry mainWorldChannels;

    /**
     * Nội dung tĩnh để dựng World của instance (ADR-048).
     *
     * <p>Trước ADR-048 hầm ngục dựng World bằng {@code new WorldFacade(sink, false)} — overload đó
     * truyền {@code WorldContentCatalog.EMPTY}, và điều đó KHÔNG sao vì boss đọc chỉ số từ hằng số
     * Java. Nay boss là một hàng trong {@code monster_template}, nên World không có catalog sẽ tra
     * không ra mã boss và hầm ngục lặng lẽ rỗng. Đây là mắt xích ADR-048 không lường trước.
     */
    private final WorldContentCatalog worldContent;

    private final Map<Long, DungeonInstance> instancesByPartyId = new HashMap<>();
    private final Map<Long, DungeonInstance> instanceByCharacterId = new HashMap<>();
    private final Map<Long, PositionSnapshot> returnPositionByCharacterId = new HashMap<>();

    private final WorldTransfer transfer;

    /**
     * ADR-051 D5 — gục trong hầm ngục thì đưa về world chính. Lời báo gục tới trên tick thread của
     * World hầm ngục, còn đường về đọc DB (chỉ số, trang bị) và {@code stop()} World khi người cuối
     * rời (join chính tick thread đó) ⇒ PHẢI chạy ở đây, không bao giờ ngay trên tick thread. Cùng
     * khuôn {@code ArenaInstanceRegistry.settlementExecutor}.
     */
    private final ExecutorService defeatExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * @param facades hai facade đi CHUNG một tham số vì trần Checkstyle {@code ParameterNumber} là
     *     4 và tham số thứ tư nay là {@code worldContent} (ADR-048) — cùng cách ADR-040 D6 gói quái
     *     với NPC vào {@code WorldContentCatalog} khi constructor {@code WorldFacade} chạm trần.
     */
    DungeonInstanceRegistry(
            ConnectedCharacterRegistry registry,
            DungeonFacades facades,
            MainWorldChannelRegistry mainWorldChannels,
            WorldContentCatalog worldContent) {
        this.registry = registry;
        this.characterFacade = facades.characterFacade();
        this.partyFacade = facades.partyFacade();
        this.mainWorldChannels = mainWorldChannels;
        this.worldContent = worldContent;
        this.transfer = new WorldTransfer(registry);
    }

    /** Hai facade hầm ngục cần — xem javadoc constructor về vì sao chúng bị gói lại. */
    record DungeonFacades(CharacterFacade characterFacade, PartyFacade partyFacade) {}

    /** {@code WorldFacade} nhân vật này đang ở TRONG (dungeon), rỗng nếu đang ở world chính. */
    synchronized Optional<WorldFacade> worldFacadeFor(long characterId) {
        DungeonInstance instance = instanceByCharacterId.get(characterId);
        return instance == null ? Optional.empty() : Optional.of(instance.facade());
    }

    /**
     * Vào hầm ngục — kéo THEO CẢ nhóm đang online vào chung một instance.
     *
     * <p><b>ADR-047 D3 — báo TRƯỚC, attach SAU.</b> Người bị KÉO theo nhận
     * {@code DungeonEnteredEvent} ghi thẳng vào kênh của họ NGAY TRƯỚC {@code attachCharacter} của
     * chính họ. Đảo thứ tự này là làm hỏng cả tính năng chứ không phải một tối ưu nhỏ: client dọn
     * sạch thực thể khi nhận lời báo, nên lời báo tới SAU spawn sẽ xoá mất boss và đồng đội vừa
     * nhận đúng, và server KHÔNG phát lại (tập "đang thấy" phía server đã có họ).
     *
     * <p>Người GỬI không nhận event — họ đã có {@code EnterDungeonResponse} (D1).
     *
     * <p>⚠️ <b>Toàn bộ việc dời người phải xong TRƯỚC khi gateway trả response</b>, xem
     * {@code GatewayChannelHandler.handleEnterDungeonBlocking}. Mục "Phát hiện phụ" của ADR-047 đề
     * xuất ngược lại (trả lời trước, dời người sau) và <b>đã bị chính test bác</b> — giữ nguyên thứ
     * tự này.
     */
    EnterOutcome enterDungeon(long requestingCharacterId, String dungeonCode) {
        if (!DUNGEON_CODE.equals(dungeonCode)) {
            return EnterOutcome.UNKNOWN_DUNGEON;
        }
        if (isInDungeon(requestingCharacterId)) {
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

        // Đọc DB cho từng người TRƯỚC khi vào khoá — xem javadoc lớp.
        List<PendingEntry> entries = new ArrayList<>(members.size());
        for (PartyFacade.MemberView member : members) {
            long memberId = member.characterId();
            if (!isInDungeon(memberId)) {
                entries.add(new PendingEntry(memberId, loadBaseStats(memberId), returnPositionOf(memberId)));
            }
        }
        commitEnter(myParty.get().partyId(), dungeonCode, requestingCharacterId, entries);
        return EnterOutcome.OK;
    }

    /**
     * Phần ghi của {@link #enterDungeon}, nguyên tử: tra/dựng instance của nhóm rồi dời người. Kiểm
     * lại "đã ở hầm ngục chưa" TRONG khoá — lượt vào song song của đồng đội có thể vừa kéo người
     * này vào sau khi ảnh chụp ngoài khoá được đọc.
     */
    private synchronized void commitEnter(
            long partyId, String dungeonCode, long requestingCharacterId, List<PendingEntry> entries) {
        DungeonInstance instance = instancesByPartyId.computeIfAbsent(partyId, this::createInstance);
        for (PendingEntry entry : entries) {
            if (!instanceByCharacterId.containsKey(entry.characterId())) {
                WorldChangeReason reason = WorldChangeReason.WORLD_CHANGE_REASON_REQUESTED;
                if (entry.characterId() != requestingCharacterId) {
                    // ADR-051 D4 — 1504 vẫn phát cho client cũ; client mới chỉ dùng nó để báo chữ.
                    notifyPulledIn(entry.characterId(), dungeonCode, requestingCharacterId);
                    reason = WorldChangeReason.WORLD_CHANGE_REASON_PULLED_BY_PARTY;
                }
                moveCharacterIntoDungeon(entry, instance, reason);
            }
        }
    }

    private synchronized boolean isInDungeon(long characterId) {
        return instanceByCharacterId.containsKey(characterId);
    }

    private PositionSnapshot returnPositionOf(long characterId) {
        PositionSnapshot lastKnown = registry.lastKnownPosition(characterId);
        return lastKnown == null ? characterFacade.loadPosition(characterId) : lastKnown;
    }

    /** ADR-047 phương án B — xem {@link #enterDungeon} về vì sao lời báo phải đi TRƯỚC attach. */
    private void notifyPulledIn(long characterId, String dungeonCode, long initiatorCharacterId) {
        DungeonEnteredEvent event = DungeonEnteredEvent.newBuilder()
                .setDungeonCode(dungeonCode)
                .setInitiatorCharacterId(initiatorCharacterId)
                .build();
        registry.sendToCharacterIfOnline(
                characterId, Opcode.OPCODE_DUNGEON_ENTERED_EVENT_VALUE, 0, event.toByteArray());
    }

    ExitOutcome exitDungeon(long characterId) {
        return exitDungeon(characterId, WorldChangeReason.WORLD_CHANGE_REASON_REQUESTED);
    }

    /**
     * ADR-051 D5 — gọi TRÊN tick thread của World hầm ngục (qua {@link DungeonOutboundSink}) khi
     * nhân vật gục. Chỉ đẩy việc đi, không làm gì ở đây: xem javadoc {@link #defeatExecutor}.
     *
     * <p>Về world chính SỐNG và đầy máu (attach mới luôn cho máu đầy), đúng luật world chính "gục là
     * hồi sinh ngay". Nếu người này vừa tự bấm rời hoặc vừa mất kết nối, {@link #commitExit} thấy họ
     * không còn trong hầm ngục và bỏ qua an toàn.
     */
    void handleCharacterDefeated(long defeatedCharacterId) {
        defeatExecutor.execute(() -> exitDungeon(defeatedCharacterId, WorldChangeReason.WORLD_CHANGE_REASON_DEFEATED));
    }

    private ExitOutcome exitDungeon(long characterId, WorldChangeReason reason) {
        if (!isInDungeon(characterId)) {
            return ExitOutcome.NOT_IN_DUNGEON;
        }
        // Đọc DB TRƯỚC khi vào khoá — xem javadoc lớp.
        CharacterBaseStats baseStats = loadBaseStats(characterId);
        // Bonus trang bị đi CÙNG lệnh attach (2026-09-18): thiếu nó thì đấu xong / rời hầm ngục là
        // nhân vật về map chính với chỉ số NỀN và không bao giờ được cộng lại — máu 206 → 100,
        // def 65 → 20, tới tận khi mặc/gỡ đồ hoặc đăng nhập lại. `registry` đã cầm InventoryFacade.
        EquipmentBonusSnapshot equipmentBonus = registry.equipmentBonusOf(characterId);
        return commitExit(characterId, new ExitEntry(baseStats, equipmentBonus, reason));
    }

    /** Dữ liệu DB của một lượt rời hầm ngục, đọc sẵn ngoài khoá, kèm lý do rời (ADR-051). */
    private record ExitEntry(
            CharacterBaseStats baseStats, EquipmentBonusSnapshot equipmentBonus, WorldChangeReason reason) {}

    // Kiểm lại trong khoá: người này có thể đã mất kết nối (handleDisconnectIfInDungeon trên
    // EventLoop) trong lúc đọc DB ở trên.
    private synchronized ExitOutcome commitExit(long characterId, ExitEntry exit) {
        DungeonInstance instance = instanceByCharacterId.get(characterId);
        if (instance == null) {
            return ExitOutcome.NOT_IN_DUNGEON;
        }
        PositionSnapshot returnPosition = returnPositionByCharacterId.remove(characterId);
        transfer.move(
                characterId,
                () -> instance.facade().detachCharacter(characterId),
                WorldTransfer.event(
                        WorldKind.WORLD_KIND_MAIN,
                        exit.reason(),
                        returnPosition,
                        WorldFacade.characterMaxHp(exit.equipmentBonus())),
                () -> mainWorldChannels.attachCharacter(new CharacterEntry(
                        characterId, returnPosition.x(), returnPosition.y(), exit.baseStats(), exit.equipmentBonus())));
        leaveInstance(characterId, instance);
        return ExitOutcome.OK;
    }

    /**
     * Gọi từ {@code channelInactive} (mất kết nối) — dọn dungeon facade + bookkeeping, KHÔNG
     * re-attach vào world chính (đã ngắt kết nối, không còn ai để gửi packet). {@code false} nếu
     * nhân vật không ở dungeon nào — caller tự xử lý detach world chính như bình thường.
     */
    synchronized boolean handleDisconnectIfInDungeon(long characterId) {
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
    synchronized void stopAll() {
        instancesByPartyId.values().forEach(instance -> instance.facade().stop());
        instancesByPartyId.clear();
        instanceByCharacterId.clear();
        returnPositionByCharacterId.clear();
        defeatExecutor.shutdown();
    }

    private DungeonInstance createInstance(long partyId) {
        // ADR-048 — catalog PHẢI đi vào đây, nếu không `spawnBoss` tra không ra mã và hầm ngục
        // rỗng không một lời báo. `spawnOverworldContent = false` vẫn giữ: World này không
        // spawn nội dung overworld, nó chỉ cần TRA được loài boss.
        //
        // CHỈ danh mục quái, KHÔNG vùng an toàn/NPC (sửa 2026-09-24): WorldFacade cài vùng an toàn
        // từ catalog bất kể là World nào, nên truyền nguyên catalog world chính thì `sect_grounds`
        // [-15, 5] phủ đúng điểm xuất hiện x=0 trong hầm ngục ⇒ boss không bao giờ đánh người đứng
        // yên. ADR-050 D8 để "vùng cho dungeon/arena" NGOÀI phạm vi. Xem DungeonWorldContentTest.
        WorldFacade dungeonFacade = new WorldFacade(
                new DungeonOutboundSink(registry, this::handleCharacterDefeated),
                false,
                TickMetricsPort.NO_OP,
                WorldContentCatalog.ofMonsters(worldContent.monsters()));
        dungeonFacade.start();
        // Phase 8 lát cắt 2 — NƠI DUY NHẤT "dungeon" và "boss" được nối với nhau; WorldFacade vẫn
        // không biết khái niệm "dungeon" (chỉ biết "có ai bảo spawn boss hay không").
        dungeonFacade.spawnBoss(DUNGEON_BOSS_CODE);
        return new DungeonInstance(partyId, dungeonFacade);
    }

    private void moveCharacterIntoDungeon(PendingEntry entry, DungeonInstance instance, WorldChangeReason reason) {
        long characterId = entry.characterId();
        returnPositionByCharacterId.put(characterId, entry.returnPosition());

        // Attach vào hầm ngục KHÔNG kèm bonus trang bị (giới hạn có sẵn — javadoc
        // WorldFacade#attachCharacter(CharacterEntry)) ⇒ máu đặt lúc attach là máu nền.
        transfer.move(
                characterId,
                () -> mainWorldChannels.detachCharacter(characterId),
                WorldTransfer.event(
                        WorldKind.WORLD_KIND_DUNGEON,
                        reason,
                        DUNGEON_ENTRY_POSITION,
                        WorldFacade.characterMaxHp(EquipmentBonusSnapshot.NONE)),
                () -> instance.facade()
                        .attachCharacter(characterId, DUNGEON_ENTRY_SPAWN_X, DUNGEON_ENTRY_SPAWN_Y, entry.baseStats()));
        instance.characterIds().add(characterId);
        instanceByCharacterId.put(characterId, instance);
    }

    /** Dữ liệu DB của một người sắp vào hầm ngục, đọc sẵn ngoài khoá. */
    private record PendingEntry(long characterId, CharacterBaseStats baseStats, PositionSnapshot returnPosition) {}

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
        Element element =
                character.map(c -> FactionMapping.toElement(c.faction())).orElse(Element.NONE);
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
