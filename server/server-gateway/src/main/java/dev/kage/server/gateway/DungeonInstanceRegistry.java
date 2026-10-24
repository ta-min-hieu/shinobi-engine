package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.party.api.PartyFacade;
import dev.kage.protocol.DungeonEnteredEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.WorldChangeReason;
import dev.kage.protocol.WorldKind;
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
 *
 * <p><b>World cũ im hẳn rồi mới báo 308 (2026-10-01, cùng quyết định P1 của đấu trường).</b> Lệnh
 * detach chỉ được xếp hàng, nên World cũ có thể phát nốt gói của tick đang chạy dở SAU
 * {@code WorldChangedEvent} — entity id = characterId ở mọi World, client có thể xoá nhầm đồng đội.
 * Nay mỗi lượt dời chia hai nửa: nửa đầu (trong khoá) detach + giữ định tuyến về hầm ngục; chờ
 * {@link WorldFacade#inboxBarrier()} của World cũ NGOÀI khoá; nửa sau (trong khoá) báo 308 + attach,
 * bỏ qua nếu trong lúc chờ người đó đã rớt mạng/rời đi. Định tuyến KHÔNG được để trống trong lúc chờ:
 * {@code GatewayChannelHandler.currentWorldFacade} ném (đóng kết nối) với người không thuộc World nào,
 * mà gói di chuyển tới mỗi frame.
 */
final class DungeonInstanceRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(DungeonInstanceRegistry.class);

    /**
     * Trần chờ World cũ xử lý xong lệnh detach. Mốc thường tới trong một tick (~17ms); hết trần thì
     * vẫn dời — chỉ lùi về thứ tự cũ (có thể lọt gói muộn), không bao giờ treo người chơi ở giữa.
     */
    private static final long OLD_WORLD_DRAIN_TIMEOUT_MILLIS = 1000;

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
    private final LongPredicate inDuel;
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

    /** Người đã qua nửa đầu lượt rời, đang chờ World hầm ngục im — xem {@link #beginExit}. */
    private final Set<Long> exitingCharacterIds = new HashSet<>();

    /**
     * Đã {@link #stopAll} (sửa 2026-10-11) — cùng lý do với {@code MainWorldChannelRegistry#stopped}.
     * Lệnh vào hầm ngục còn chạy dở lúc tắt server (đang đọc DB ngoài khoá) trước đây vẫn dựng và
     * {@code start()} một World hầm ngục SAU khi mọi World đã dừng: không ai dừng nó nữa, nó tick tiếp
     * trong lúc pool DB đóng. Nay lượt vào muộn không nhận ai. Chỉ đọc/ghi trong khoá.
     */
    private boolean stopped;

    private final WorldTransfer transfer;

    /**
     * ADR-051 D5 — gục trong hầm ngục thì đưa về world chính. Lời báo gục tới trên tick thread của
     * World hầm ngục, còn đường về đọc DB (chỉ số, trang bị) và {@code stop()} World khi người cuối
     * rời (join chính tick thread đó) ⇒ PHẢI chạy ở đây, không bao giờ ngay trên tick thread. Cùng
     * khuôn {@code ArenaInstanceRegistry.settlementExecutor}.
     */
    private final ExecutorService defeatExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /** ADR-099 — lượt mỗi ngày + thưởng lần đầu hạ boss. */
    private final DungeonRewards rewards;

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
        this.inDuel = facades.inDuel();
        this.rewards = facades.rewards();
        this.mainWorldChannels = mainWorldChannels;
        this.worldContent = worldContent;
        this.transfer = new WorldTransfer(registry);
    }

    /**
     * Hai facade hầm ngục cần — xem javadoc constructor về vì sao chúng bị gói lại — kèm câu hỏi "người
     * này có đang ở đấu trường không" ({@code ArenaInstanceRegistry#isInDuel}, 2026-10-02): lượt kéo
     * cả nhóm KHÔNG được gắn một người đang đấu vào thêm World hầm ngục.
     */
    record DungeonFacades(
            CharacterFacade characterFacade, PartyFacade partyFacade, LongPredicate inDuel, DungeonRewards rewards) {

        /** Không có đấu trường — chỉ cho test và dựng thử, nơi không ai đấu được. */
        DungeonFacades(CharacterFacade characterFacade, PartyFacade partyFacade) {
            this(characterFacade, partyFacade, characterId -> false);
        }

        /** Không giới hạn lượt, không thưởng lần đầu (ADR-099) — test. */
        DungeonFacades(CharacterFacade characterFacade, PartyFacade partyFacade, LongPredicate inDuel) {
            this(characterFacade, partyFacade, inDuel, DungeonRewards.NONE);
        }
    }

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
        Optional<EnterOutcome> busy = busyElsewhere(requestingCharacterId);
        if (busy.isPresent()) {
            return busy.get();
        }
        Optional<PartyFacade.MyPartyView> myParty = partyFacade.findMyParty(requestingCharacterId);
        if (myParty.isEmpty()) {
            return EnterOutcome.NOT_IN_PARTY;
        }
        List<PartyFacade.MemberView> members = partyFacade.listMembers(requestingCharacterId);
        if (!members.stream().allMatch(member -> registry.isOnline(member.characterId()))) {
            return EnterOutcome.PARTY_MEMBER_OFFLINE;
        }

        // Đọc DB cho từng người TRƯỚC khi vào khoá — xem javadoc lớp.
        List<PendingEntry> entries = new ArrayList<>(members.size());
        for (PartyFacade.MemberView member : members) {
            long memberId = member.characterId();
            if (!isInDungeon(memberId)) {
                entries.add(new PendingEntry(memberId, loadoutOf(memberId), returnPositionOf(memberId)));
            }
        }
        // ADR-099 — trừ lượt cho những người sắp được kéo vào; ai hết lượt thì cả nhóm bị từ chối.
        if (rewards.anyExhausted(entries.stream().map(PendingEntry::characterId).toList(), dungeonCode)) {
            return EnterOutcome.DAILY_LIMIT_REACHED;
        }
        Optional<AdmittedEntries> maybeAdmitted =
                admitBuildingWorldIfNeeded(myParty.get().partyId(), entries);
        if (maybeAdmitted.isEmpty()) {
            // Không còn ai để dời: đồng đội vừa kéo cả nhóm vào trước (người gửi đã ở trong — cùng
            // kết quả với lượt thắng), hoặc ai cũng vừa rớt mạng (không còn ai đọc response).
            return EnterOutcome.OK;
        }
        AdmittedEntries admitted = maybeAdmitted.get();
        // Ngoài khoá — xem javadoc lớp. Người gửi vẫn nhận response SAU nửa sau (ADR-047), vì
        // cả lượt này chạy trên luồng của chính lệnh vào.
        awaitOldWorldSilent(
                CompletableFuture.allOf(admitted.mainWorldDrained().toArray(CompletableFuture[]::new)),
                requestingCharacterId);
        finishEnter(admitted, dungeonCode, requestingCharacterId);
        resyncLevels(admitted);
        return EnterOutcome.OK;
    }

    /** Ngoài khoá (đọc DB): cú lên cấp rơi đúng lúc đang dời không được mất — xem {@link CharacterLevelResync}. */
    private void resyncLevels(AdmittedEntries admitted) {
        for (PendingEntry entry : admitted.entries()) {
            CharacterLevelResync.afterMoveInto(admitted.instance().facade(), characterFacade, entry.characterId());
        }
    }

    /** Thử nhận vào instance; nhóm chưa có instance thì dựng World NGOÀI khoá rồi thử lại (2026-10-02). */
    private Optional<AdmittedEntries> admitBuildingWorldIfNeeded(long partyId, List<PendingEntry> entries) {
        AdmitAttempt attempt = admitIntoInstance(partyId, entries, Optional.empty());
        if (attempt.needsWorld()) {
            attempt = admitIntoInstance(partyId, entries, Optional.of(newDungeonWorld()));
        }
        return attempt.admitted();
    }

    /**
     * Nửa đầu của {@link #enterDungeon}, nguyên tử: tra/dựng instance của nhóm, ghi định tuyến và
     * detach khỏi world chính. Kiểm lại TRONG khoá hai điều mà ảnh chụp ngoài khoá có thể đã sai:
     *
     * <ul>
     *   <li>"đã ở hầm ngục chưa" — lượt vào song song của đồng đội có thể vừa kéo người này vào;
     *   <li>"còn online không" (sửa 2026-10-02) — người rớt mạng trong lúc đọc DB đã được
     *       {@code channelInactive} dọn TRỌN (gỡ đăng ký → {@link #handleDisconnectIfInDungeon} thấy
     *       họ chưa ở đây → gỡ khỏi world chính). Nhận họ lúc này là gắn một thực thể bóng ma vào hầm
     *       ngục mà không kết nối nào còn dọn nữa: World hầm ngục tick mãi dù cả nhóm đã ra, và lần
     *       đăng nhập sau bị định tuyến nhầm vào đây. Gỡ đăng ký chạy TRƯỚC lời gọi vào khoá này, nên
     *       hoặc ta thấy họ offline, hoặc {@link #handleDisconnectIfInDungeon} tới sau và dọn họ.
     *   <li>"có đang ở đấu trường không" (sửa 2026-10-02) — thực thể của người đang đấu nằm trong World
     *       đấu trường; kéo họ là gắn THÊM một thực thể vào hầm ngục (định tuyến ưu tiên hầm ngục nên
     *       thực thể trong trận đứng im chịu đòn, và hết trận đấu trường gắn họ về world chính: hai
     *       thực thể sống). Họ bị BỎ QUA chứ không chặn cả nhóm — hết trận tự bấm vào là gia nhập đúng
     *       instance của nhóm. Hỏi trong khoá này ⇒ thứ tự khoá hầm ngục → đấu trường, xem
     *       {@code ArenaInstanceRegistry#isInDuel}.
     * </ul>
     *
     * <p>Không nhận được ai ⇒ {@code admitted} rỗng và KHÔNG dựng instance (bản cũ dựng trước rồi mới
     * lọc, để lại một World rỗng kèm boss không ai dừng).
     *
     * <p><b>Không dựng World trong khoá (2026-10-02).</b> EventLoop xin khoá này cho MỌI gói di
     * chuyển/tấn công của MỌI người chơi ({@link #worldFacadeFor}, qua {@code currentWorldFacade}),
     * còn dựng một {@link WorldFacade} tốn ~55–110ms (chạy thử từng script skill trong GraalJS). Nhóm
     * chưa có instance mà {@code spare} rỗng ⇒ {@code needsWorld}, caller dựng World ngoài khoá rồi gọi
     * lại. {@code spare} không dùng tới (đồng đội vừa dựng trước, hoặc không nhận được ai) chưa từng
     * {@code start()} — bỏ đi là xong.
     */
    private synchronized AdmitAttempt admitIntoInstance(
            long partyId, List<PendingEntry> entries, Optional<WorldFacade> spare) {
        List<PendingEntry> admitted = new ArrayList<>(entries.size());
        for (PendingEntry entry : entries) {
            if (isAdmissible(entry.characterId())) {
                admitted.add(entry);
            }
        }
        if (admitted.isEmpty()) {
            return AdmitAttempt.NOBODY;
        }
        DungeonInstance instance = instancesByPartyId.get(partyId);
        if (instance == null) {
            if (spare.isEmpty()) {
                return AdmitAttempt.NEEDS_WORLD;
            }
            instance = startInstance(partyId, spare.get());
            instancesByPartyId.put(partyId, instance);
        }
        List<CompletableFuture<Void>> drained = new ArrayList<>(admitted.size());
        for (PendingEntry entry : admitted) {
            long characterId = entry.characterId();
            returnPositionByCharacterId.put(characterId, entry.returnPosition());
            instance.characterIds().add(characterId);
            instanceByCharacterId.put(characterId, instance);
            drained.add(mainWorldChannels.detachCharacterAndDrain(characterId));
        }
        return new AdmitAttempt(false, Optional.of(new AdmittedEntries(instance, admitted, drained)));
    }

    /**
     * Ba lượt kiểm lại trong khoá của {@link #admitIntoInstance}, cộng "server chưa tắt" (xem
     * {@link #stopped}): sau {@link #stopAll} không ai được nhận ⇒ không instance nào được dựng, và
     * {@code spare} chưa start bị bỏ. Chỉ gọi khi đang giữ khoá.
     */
    private boolean isAdmissible(long characterId) {
        return !stopped
                && !instanceByCharacterId.containsKey(characterId)
                && registry.isOnline(characterId)
                && !inDuel.test(characterId);
    }

    /**
     * Nửa sau của {@link #enterDungeon}, khi world chính đã im với mọi người được nhận. Trong lúc
     * chờ, một người có thể đã rớt mạng ({@link #handleDisconnectIfInDungeon} gỡ họ, có khi dừng luôn
     * instance) ⇒ chỉ dời ai còn trỏ đúng instance này — và chưa bắt đầu rời ({@link #beginExit}).
     */
    private synchronized void finishEnter(AdmittedEntries admitted, String dungeonCode, long requestingCharacterId) {
        for (PendingEntry entry : admitted.entries()) {
            long characterId = entry.characterId();
            if (!admitted.instance().equals(instanceByCharacterId.get(characterId))) {
                continue;
            }
            if (exitingCharacterIds.contains(characterId)) {
                // Lệnh RỜI tới trong lúc chờ (2026-10-03, DungeonEnterExitRaceTest): nó đã xếp lệnh gỡ
                // khỏi World hầm ngục — lúc người này CHƯA được gắn — và sắp đưa họ về world chính. Gắn
                // bây giờ là gắn SAU lệnh gỡ đó: thực thể bóng ma ở lại hầm ngục mãi. Để lượt rời làm nốt.
                continue;
            }
            WorldChangeReason reason = WorldChangeReason.WORLD_CHANGE_REASON_REQUESTED;
            if (characterId != requestingCharacterId) {
                // ADR-051 D4 — 1504 vẫn phát cho client cũ; client mới chỉ dùng nó để báo chữ.
                // Cũng đi SAU mốc: client cũ dọn thực thể khi nhận nó.
                notifyPulledIn(characterId, dungeonCode, requestingCharacterId);
                reason = WorldChangeReason.WORLD_CHANGE_REASON_PULLED_BY_PARTY;
            }
            moveCharacterIntoDungeon(entry, admitted.instance(), reason);
        }
    }

    /**
     * Kết quả một lần thử {@link #admitIntoInstance}: cần dựng World ngoài khoá rồi thử lại, hoặc ai
     * được nhận (rỗng = không còn ai để dời).
     */
    private record AdmitAttempt(boolean needsWorld, Optional<AdmittedEntries> admitted) {
        static final AdmitAttempt NOBODY = new AdmitAttempt(false, Optional.empty());
        static final AdmitAttempt NEEDS_WORLD = new AdmitAttempt(true, Optional.empty());
    }

    /** Kết quả nửa đầu của lượt vào: ai được nhận, và mốc "world chính đã im" của từng người. */
    private record AdmittedEntries(
            DungeonInstance instance, List<PendingEntry> entries, List<CompletableFuture<Void>> mainWorldDrained) {}

    /** Chờ World cũ im, có trần — xem {@link #OLD_WORLD_DRAIN_TIMEOUT_MILLIS}. Chỉ gọi NGOÀI khoá. */
    private static void awaitOldWorldSilent(CompletableFuture<?> drained, long characterId) {
        try {
            drained.get(OLD_WORLD_DRAIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOG.warn(
                    "World cũ chưa im sau {}ms, vẫn dời: characterId={}",
                    OLD_WORLD_DRAIN_TIMEOUT_MILLIS,
                    characterId,
                    e);
        } catch (ExecutionException e) {
            LOG.warn("Chờ World cũ im lỗi, vẫn dời: characterId={}", characterId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Bị ngắt khi chờ World cũ im, vẫn dời: characterId={}", characterId, e);
        }
    }

    /**
     * Người bấm đã ở trong một instance — tách khỏi {@link #enterDungeon} để giữ hàm đó dưới trần
     * CyclomaticComplexity. Đang ở đấu trường mà cho qua là gắn họ vào HAI World (2026-10-02, xem
     * admitIntoInstance); outcome riêng {@code IN_DUEL} từ 2026-10-14 (README dòng 36(iii)) — trước đó
     * dùng tạm {@code ALREADY_IN_DUNGEON}, và client tin mình đã vào bí cảnh.
     */
    private Optional<EnterOutcome> busyElsewhere(long characterId) {
        if (isInDungeon(characterId)) {
            return Optional.of(EnterOutcome.ALREADY_IN_DUNGEON);
        }
        return inDuel.test(characterId) ? Optional.of(EnterOutcome.IN_DUEL) : Optional.empty();
    }

    /**
     * Đang ở (hoặc đang rời) một instance hầm ngục. Đấu trường hỏi câu này NGOÀI khoá của nó trước khi
     * dời ai vào trận — xem {@code ArenaInstanceRegistry#refuseCharactersIn}.
     */
    synchronized boolean isInDungeon(long characterId) {
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
     * hồi sinh ngay". Nếu người này vừa tự bấm rời hoặc vừa mất kết nối, {@link #beginExit} thấy họ
     * không còn trong hầm ngục và bỏ qua an toàn.
     */
    void handleCharacterDefeated(long defeatedCharacterId) {
        defeatExecutor.execute(() -> exitDungeon(defeatedCharacterId, WorldChangeReason.WORLD_CHANGE_REASON_DEFEATED));
    }

    private ExitOutcome exitDungeon(long characterId, WorldChangeReason reason) {
        if (!isInDungeon(characterId)) {
            return ExitOutcome.NOT_IN_DUNGEON;
        }
        // Đọc DB TRƯỚC khi vào khoá — xem javadoc lớp. Bonus trang bị đi CÙNG lệnh attach
        // (2026-09-18): thiếu nó thì rời hầm ngục là về map chính với chỉ số NỀN — máu 206 → 100.
        ExitEntry exit = new ExitEntry(loadoutOf(characterId), reason);
        Optional<DungeonInstance> leaving = beginExit(characterId);
        if (leaving.isEmpty()) {
            return ExitOutcome.NOT_IN_DUNGEON;
        }
        // Ngoài khoá — xem javadoc lớp. World hầm ngục còn chạy cho đồng đội nên phải chờ mốc, không
        // dừng nó trước được như đấu trường.
        awaitOldWorldSilent(leaving.get().facade().inboxBarrier(), characterId);
        // finishExit gắn lại vào world chính TRONG khoá này — nếu world chính phải mở khu vực mới thì
        // dựng World đó ngay bây giờ, ngoài khoá (2026-10-02, xem admitIntoInstance).
        mainWorldChannels.ensureRoomForOneMore();
        ExitOutcome outcome = finishExit(characterId, leaving.get(), exit);
        if (outcome == ExitOutcome.OK) {
            // Ngoài khoá (đọc DB) — xem CharacterLevelResync.
            mainWorldChannels
                    .worldFacadeFor(characterId)
                    .ifPresent(world -> CharacterLevelResync.afterMoveInto(world, characterFacade, characterId));
        }
        return outcome;
    }

    /** Dữ liệu DB của một lượt rời hầm ngục, đọc sẵn ngoài khoá, kèm lý do rời (ADR-051). */
    private record ExitEntry(CharacterLoadout loadout, WorldChangeReason reason) {}

    /**
     * Nửa đầu của lượt rời: detach khỏi World hầm ngục nhưng GIỮ định tuyến — xem javadoc lớp. Rỗng
     * nếu người này không ở hầm ngục, hoặc đã có một lượt rời khác đang chờ (vd. gục ngay lúc bấm
     * rời): lượt đó sẽ đưa họ về, lượt này nhận {@code NOT_IN_DUNGEON} như thể tới sau.
     */
    private synchronized Optional<DungeonInstance> beginExit(long characterId) {
        // Kiểm lại trong khoá: người này có thể đã mất kết nối (handleDisconnectIfInDungeon trên
        // EventLoop) trong lúc đọc DB ở trên.
        DungeonInstance instance = instanceByCharacterId.get(characterId);
        if (instance == null || !exitingCharacterIds.add(characterId)) {
            return Optional.empty();
        }
        instance.facade().detachCharacter(characterId);
        return Optional.of(instance);
    }

    /**
     * Nửa sau của lượt rời, khi World hầm ngục đã im với người này. Bỏ qua nếu trong lúc chờ họ đã
     * rớt mạng — {@link #handleDisconnectIfInDungeon} đã dọn, không còn ai để đưa về.
     */
    private synchronized ExitOutcome finishExit(long characterId, DungeonInstance instance, ExitEntry exit) {
        exitingCharacterIds.remove(characterId);
        if (!instance.equals(instanceByCharacterId.get(characterId))) {
            return ExitOutcome.NOT_IN_DUNGEON;
        }
        PositionSnapshot returnPosition = returnPositionByCharacterId.remove(characterId);
        transfer.move(
                characterId,
                () -> {},
                WorldTransfer.event(
                        WorldKind.WORLD_KIND_MAIN,
                        exit.reason(),
                        returnPosition,
                        exit.loadout().maxHp()),
                () -> mainWorldChannels.attachCharacter(exit.loadout().entryAt(characterId, returnPosition)));
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
        exitingCharacterIds.remove(characterId);
        leaveInstance(characterId, instance);
        return true;
    }

    /** Số instance (World hầm ngục) đang chạy — mỗi cái giữ một lịch tick, nên rò một cái là rò thật. */
    synchronized int instanceCount() {
        return instancesByPartyId.size();
    }

    /**
     * Số dòng sổ sách còn giữ về người trong hầm ngục (định tuyến, vị trí trả về, lượt rời đang dở) —
     * chỉ để quan sát (test, chẩn đoán). Không ai ở hầm ngục thì phải về 0.
     */
    synchronized int trackedEntryCount() {
        return instanceByCharacterId.size() + returnPositionByCharacterId.size() + exitingCharacterIds.size();
    }

    /** Graceful shutdown (SERVER.md §10) — dừng MỌI dungeon World đang chạy trước khi thoát process. */
    void stopAll() {
        stopAllInstances();
        ShutdownDrain.await(defeatExecutor, "Đưa người gục khỏi hầm ngục");
    }

    private synchronized void stopAllInstances() {
        stopped = true;
        instancesByPartyId.values().forEach(instance -> instance.facade().stop());
        instancesByPartyId.clear();
        instanceByCharacterId.clear();
        returnPositionByCharacterId.clear();
        exitingCharacterIds.clear();
        defeatExecutor.shutdown();
    }

    /** Lượt Bí Cảnh còn hôm nay (ADR-099). Chạm DB. */
    int entriesLeft(long characterId) {
        return rewards.entriesLeft(characterId, DUNGEON_CODE);
    }

    /**
     * ADR-099 — boss chết (tick thread của hầm ngục): chụp danh sách người trong instance rồi trao thưởng lần đầu trên
     * Virtual Thread.
     */
    private void onMonsterKilled(String monsterCode, long killerCharacterId) {
        if (!DUNGEON_BOSS_CODE.equals(monsterCode)) {
            return;
        }
        List<Long> members;
        synchronized (this) {
            DungeonInstance instance = instanceByCharacterId.get(killerCharacterId);
            members = instance == null ? List.of(killerCharacterId) : List.copyOf(instance.characterIds());
        }
        defeatExecutor.execute(() -> rewards.onBossKilled(members, DUNGEON_CODE));
    }

    /** Dựng World hầm ngục, CHƯA start — gọi NGOÀI khoá, xem {@link #admitIntoInstance}. */
    private WorldFacade newDungeonWorld() {
        // ADR-048 — catalog PHẢI đi vào đây, nếu không `spawnBoss` tra không ra mã và hầm ngục
        // rỗng không một lời báo. `spawnOverworldContent = false` vẫn giữ: World này không
        // spawn nội dung overworld, nó chỉ cần TRA được loài boss.
        //
        // CHỈ danh mục quái, KHÔNG vùng an toàn/NPC (sửa 2026-09-24): WorldFacade cài vùng an toàn
        // từ catalog bất kể là World nào, nên truyền nguyên catalog world chính thì `sect_grounds`
        // [-15, 5] phủ đúng điểm xuất hiện x=0 trong hầm ngục ⇒ boss không bao giờ đánh người đứng
        // yên. ADR-050 D8 để "vùng cho dungeon/arena" NGOÀI phạm vi. Xem DungeonWorldContentTest.
        WorldFacade facade = new WorldFacade(
                new DungeonOutboundSink(registry, this::handleCharacterDefeated, this::onMonsterKilled),
                false,
                TickMetricsPort.NO_OP,
                WorldContentCatalog.ofMonsters(worldContent.monsters()));
        facade.rememberSkillCooldownsIn(mainWorldChannels.skillCooldowns());
        return facade;
    }

    /** Start World đã dựng sẵn + gọi boss — rẻ (chỉ xếp lịch tick + xếp lệnh), chạy được trong khoá. */
    private static DungeonInstance startInstance(long partyId, WorldFacade dungeonFacade) {
        dungeonFacade.start();
        // Phase 8 lát cắt 2 — NƠI DUY NHẤT "dungeon" và "boss" được nối với nhau; WorldFacade vẫn
        // không biết khái niệm "dungeon" (chỉ biết "có ai bảo spawn boss hay không").
        dungeonFacade.spawnBoss(DUNGEON_BOSS_CODE);
        return new DungeonInstance(partyId, dungeonFacade);
    }

    private void moveCharacterIntoDungeon(PendingEntry entry, DungeonInstance instance, WorldChangeReason reason) {
        long characterId = entry.characterId();
        // Attach KÈM bonus trang bị (sửa 2026-09-29) — trước đây đường vào dùng chỉ số NỀN nên
        // người mặc giáp +máu vào hầm ngục với 100 máu, và WorldChangedEvent cũng báo 100.
        // Detach world chính (và chờ nó im) đã làm ở admitIntoInstance/enterDungeon.
        transfer.move(
                characterId,
                () -> {},
                WorldTransfer.event(
                        WorldKind.WORLD_KIND_DUNGEON,
                        reason,
                        DUNGEON_ENTRY_POSITION,
                        entry.loadout().maxHp()),
                () -> instance.facade().attachCharacter(entry.loadout().entryAt(characterId, DUNGEON_ENTRY_POSITION)));
    }

    /** Dữ liệu DB của một người sắp vào hầm ngục, đọc sẵn ngoài khoá. */
    private record PendingEntry(long characterId, CharacterLoadout loadout, PositionSnapshot returnPosition) {}

    /**
     * Gỡ bookkeeping của {@code characterId} khỏi {@code instance} — dừng luôn World nếu rỗng.
     *
     * <p>Dừng trên {@link #defeatExecutor}, không ngay đây (2026-10-02): hàm này chạy TRONG khoá, có
     * lúc trên EventLoop ({@link #handleDisconnectIfInDungeon}), còn {@code stop()} chờ lần tick dở
     * dang. Không thứ tự nào cần World này dừng xong trước khi trả về: nó đã rỗng và đã rời bảng (lượt
     * vào sau dựng instance mới); người vừa rời đã chờ mốc chặn của nó trước khi nhận 308; ai lấy mốc
     * chặn sau đó (kết nối vừa rớt) thì mốc chờ cả {@code stop()} đang dở.
     */
    private void leaveInstance(long characterId, DungeonInstance instance) {
        instance.characterIds().remove(characterId);
        instanceByCharacterId.remove(characterId);
        if (instance.characterIds().isEmpty()) {
            WorldStopper.stopInBackground(defeatExecutor, instance.facade());
            instancesByPartyId.remove(instance.partyId());
        }
    }

    private CharacterLoadout loadoutOf(long characterId) {
        return CharacterLoadout.load(characterFacade, registry, characterId);
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
        UNKNOWN_DUNGEON,
        IN_DUEL,
        DAILY_LIMIT_REACHED
    }

    enum ExitOutcome {
        OK,
        NOT_IN_DUNGEON
    }
}
