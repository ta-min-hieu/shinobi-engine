package dev.kage.server.gateway;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.protocol.DuelResultEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.WorldChangeReason;
import dev.kage.protocol.WorldKind;
import dev.kage.pvp.api.PvpFacade;
import dev.kage.pvp.application.RecordDuelResultUseCase;
import dev.kage.server.world.WorldFacade;

/**
 * Vòng đời thách đấu + trận Arena 1v1 (Phase 8 lát cắt 4, GDD §6) — bảng định tuyến
 * {@code characterId -> WorldFacade} riêng cho duel, cùng tinh thần {@code DungeonInstanceRegistry}
 * (ADR-026: World riêng cùng process, KHÔNG {@code feature-pvp} lo phần này vì không có dữ liệu
 * nghiệp vụ nào cần persist — xem feature-pvp/CLAUDE.md). Chỉ rating/wins/losses (feature-pvp) mới
 * cần DB.
 *
 * <p><b>Thread-safety (sửa 2026-09-24).</b> Registry bị gọi từ bốn loại luồng: {@code authExecutor}
 * (một-Virtual-Thread-mỗi-task — hai lệnh chạy SONG SONG), Netty EventLoop ({@link #worldFacadeFor},
 * {@link #handleDisconnectIfInDuel}), tick thread của chính trận ({@link #handleCharacterDefeated})
 * và {@link #settlementExecutor}. Bản cũ dùng {@link ConcurrentHashMap} cho TỪNG map, nhưng mọi
 * bước chuyển trạng thái đều là kiểm-rồi-làm XUYÊN nhiều map, nên an toàn từng map không cứu được:
 * hai người cùng chấp nhận lời thách của một người thì người đó bị gắn vào HAI trận. Nay mọi bước
 * chuyển nằm trong {@code synchronized} ngắn — cùng cách {@code DungeonInstanceRegistry}/
 * {@link MainWorldChannelRegistry} — còn mọi lời gọi DB (chỉ số, vị trí, trang bị, rating) chạy
 * ngoài khoá, để EventLoop không bao giờ phải chờ một câu query:
 *
 * <ul>
 *   <li>Chấp nhận = giữ chỗ cả hai người TRONG khoá ({@link #startingDuelCharacterIds}), đọc DB
 *       ngoài khoá, rồi mới dựng trận trong khoá.
 *   <li>Chốt trận = một "vé" duy nhất: ai gỡ được trận khỏi {@link #instancesByChallengeId} thì
 *       người đó chốt. Bản cũ lấy vé bằng cách gỡ NGƯỜI THUA khỏi bảng — hai đường chốt với hai người
 *       thua khác nhau (bỏ cuộc vs bị hạ gục, rớt mạng vs bị hạ gục) có thể cùng lấy được vé.
 * </ul>
 *
 * <p>⚠️ Giới hạn CHỦ Ý (ngoài phạm vi lát cắt này, xem feature-pvp/CLAUDE.md): KHÔNG cross-check
 * với {@code DungeonInstanceRegistry} — 1 nhân vật đang ở dungeon về lý thuyết vẫn có thể bị thách
 * đấu/thách đấu người khác (constructor đã đủ 4 tham số, thêm phụ thuộc chéo cần tách record gộp
 * mới, không đáng cho 1 edge-case hiếm ở dự án cá nhân).
 */
final class ArenaInstanceRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ArenaInstanceRegistry.class);

    // Cách nhau 1m — TRONG tầm CombatConstants.ATTACK_RANGE_METERS (2m), đánh được NGAY không cần
    // di chuyển (khác dungeon: boss spawn cách entry point trong tầm AGGRO_RADIUS, cùng lý do).
    private static final float DUEL_SPAWN_X_CHALLENGER = -0.5f;
    private static final float DUEL_SPAWN_X_TARGET = 0.5f;
    private static final float DUEL_SPAWN_Y = 0f;

    private final ConnectedCharacterRegistry registry;
    private final CharacterFacade characterFacade;
    private final PvpFacade pvpFacade;
    private final MainWorldChannelRegistry mainWorldChannels;

    // Khoá theo characterId ĐANG BỊ thách đấu — chỉ 1 thách đấu ĐẾN đang chờ tại 1 thời điểm/người
    // (thách đấu mới ghi đè thách đấu cũ, ephemeral hoàn toàn trong bộ nhớ, giống trade session).
    // Bốn trường dưới đây chỉ được chạm khi đang giữ monitor của `this` — xem javadoc lớp.
    private final Map<Long, PendingChallenge> pendingChallengeByTargetId = new HashMap<>();
    private final Map<Long, DuelInstance> instancesByChallengeId = new HashMap<>();
    private final Map<Long, DuelInstance> instanceByCharacterId = new HashMap<>();
    // Đã chấp nhận, đang đọc DB để dựng trận — coi như ĐANG BẬN, chưa có World.
    private final Set<Long> startingDuelCharacterIds = new HashSet<>();
    // Không cần khoá: mỗi người chỉ được một đường ghi (dựng trận) và một đường gỡ (chốt trận, sau
    // khi đã lấy vé), và gỡ chạy ngoài khoá cùng lời gọi DB.
    private final Map<Long, PositionSnapshot> returnPositionByCharacterId = new ConcurrentHashMap<>();
    private final AtomicLong nextChallengeId = new AtomicLong(1);
    // settleDuel chạm DB (PvpFacade.recordDuelResult, blocking) — CẤM chạy trên world tick thread
    // hay Netty EventLoop (server/CLAUDE.md). Virtual Thread riêng, cùng pattern
    // ConnectedCharacterRegistry.lootExecutor.
    private final ExecutorService settlementExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final WorldTransfer transfer;

    ArenaInstanceRegistry(
            ConnectedCharacterRegistry registry,
            CharacterFacade characterFacade,
            PvpFacade pvpFacade,
            MainWorldChannelRegistry mainWorldChannels) {
        this.registry = registry;
        this.characterFacade = characterFacade;
        this.pvpFacade = pvpFacade;
        this.mainWorldChannels = mainWorldChannels;
        this.transfer = new WorldTransfer(registry);
    }

    /** {@code WorldFacade} nhân vật này đang ở TRONG (duel), rỗng nếu đang ở world chính. */
    synchronized Optional<WorldFacade> worldFacadeFor(long characterId) {
        DuelInstance instance = instanceByCharacterId.get(characterId);
        return instance == null ? Optional.empty() : Optional.of(instance.facade());
    }

    synchronized ChallengeResult challengeDuel(long challengerCharacterId, long targetCharacterId) {
        if (challengerCharacterId == targetCharacterId) {
            return new ChallengeResult(ChallengeOutcome.CANNOT_CHALLENGE_SELF, 0);
        }
        if (isBusy(challengerCharacterId)) {
            return new ChallengeResult(ChallengeOutcome.ALREADY_IN_DUEL, 0);
        }
        if (isBusy(targetCharacterId)) {
            return new ChallengeResult(ChallengeOutcome.TARGET_ALREADY_IN_DUEL, 0);
        }
        if (!registry.isOnline(targetCharacterId)) {
            return new ChallengeResult(ChallengeOutcome.TARGET_OFFLINE, 0);
        }
        long challengeId = nextChallengeId.getAndIncrement();
        pendingChallengeByTargetId.put(
                targetCharacterId, new PendingChallenge(challengeId, challengerCharacterId, targetCharacterId));
        return new ChallengeResult(ChallengeOutcome.OK, challengeId);
    }

    AcceptResult acceptDuel(long characterId, long challengeId) {
        Optional<Long> reservedChallenger = reserveForDuel(characterId, challengeId);
        if (reservedChallenger.isEmpty()) {
            return new AcceptResult(AcceptOutcome.NOT_FOUND, 0);
        }
        long challengerCharacterId = reservedChallenger.get();
        boolean started = false;
        try {
            // Đọc DB NGOÀI khoá — xem javadoc lớp.
            DuelEntrant challenger = loadEntrant(challengerCharacterId);
            DuelEntrant target = loadEntrant(characterId);
            started = commitStartDuel(challengeId, challenger, target);
        } finally {
            if (!started) {
                releaseReservation(challengerCharacterId, characterId);
            }
        }
        return started
                ? new AcceptResult(AcceptOutcome.OK, challengerCharacterId)
                : new AcceptResult(AcceptOutcome.NOT_FOUND, 0);
    }

    /**
     * Tiêu lời thách và giữ chỗ cả hai người, nguyên tử. Rỗng nếu lời thách không còn, hoặc một
     * trong hai người đang bận — kể cả NGƯỜI CHẤP NHẬN: bản cũ chỉ kiểm người thách, nên người đang
     * đánh trận khác vẫn chấp nhận được một lời thách cũ và bị gắn vào trận thứ hai.
     */
    private synchronized Optional<Long> reserveForDuel(long targetCharacterId, long challengeId) {
        PendingChallenge challenge = pendingChallengeByTargetId.get(targetCharacterId);
        if (challenge == null || challenge.challengeId() != challengeId) {
            return Optional.empty();
        }
        pendingChallengeByTargetId.remove(targetCharacterId);
        long challengerCharacterId = challenge.challengerCharacterId();
        // Challenger có thể đã offline/bận trận khác giữa lúc thách đấu và lúc được chấp nhận.
        if (!registry.isOnline(challengerCharacterId) || isBusy(challengerCharacterId) || isBusy(targetCharacterId)) {
            return Optional.empty();
        }
        startingDuelCharacterIds.add(challengerCharacterId);
        startingDuelCharacterIds.add(targetCharacterId);
        return Optional.of(challengerCharacterId);
    }

    private synchronized void releaseReservation(long challengerCharacterId, long targetCharacterId) {
        startingDuelCharacterIds.remove(challengerCharacterId);
        startingDuelCharacterIds.remove(targetCharacterId);
    }

    private boolean isBusy(long characterId) {
        return instanceByCharacterId.containsKey(characterId) || startingDuelCharacterIds.contains(characterId);
    }

    synchronized DeclineResult declineDuel(long characterId, long challengeId) {
        PendingChallenge challenge = pendingChallengeByTargetId.get(characterId);
        if (challenge == null || challenge.challengeId() != challengeId) {
            return new DeclineResult(DeclineOutcome.NOT_FOUND, 0);
        }
        pendingChallengeByTargetId.remove(characterId);
        return new DeclineResult(DeclineOutcome.OK, challenge.challengerCharacterId());
    }

    /** Bỏ cuộc giữa chừng — tính THUA thật (khác {@code DungeonInstanceRegistry.exitDungeon}). */
    ExitResult forfeitDuel(long characterId) {
        Optional<DuelInstance> instance = currentInstance(characterId);
        if (instance.isEmpty()) {
            return new ExitResult(ExitOutcome.NOT_IN_DUEL, 0);
        }
        long winnerCharacterId = opponentOf(characterId, instance.get());
        // Gọi trên authExecutor (Virtual Thread, KHÔNG phải tick thread hay EventLoop) — settleDuel
        // block an toàn ở đây (xem javadoc field settlementExecutor + handleCharacterDefeated).
        // false = đường khác (bị hạ gục, rớt mạng) vừa chốt trận trước — trận đã xong, không phải
        // lượt bỏ cuộc này.
        return settleDuel(instance.get(), winnerCharacterId, characterId)
                ? new ExitResult(ExitOutcome.OK, winnerCharacterId)
                : new ExitResult(ExitOutcome.NOT_IN_DUEL, 0);
    }

    private synchronized Optional<DuelInstance> currentInstance(long characterId) {
        return Optional.ofNullable(instanceByCharacterId.get(characterId));
    }

    /**
     * Gọi TRÊN world tick thread của CHÍNH duel instance (qua {@link ArenaOutboundSink}, khi
     * {@code WorldFacade} phát hiện 1 nhân vật hạ gục nhân vật kia). {@code settleDuel} gọi
     * {@code WorldFacade.stop()} (join tick thread) + chạm DB — TUYỆT ĐỐI không được chạy trực tiếp
     * ở đây (self-join deadlock + vi phạm "tick thread cấm blocking I/O"), phải đẩy toàn bộ sang
     * {@link #settlementExecutor}.
     */
    void handleCharacterDefeated(long defeatedCharacterId, long killerEntityId) {
        Optional<DuelInstance> instance = currentInstance(defeatedCharacterId);
        if (instance.isEmpty()) {
            return; // đã settle bởi lời gọi khác (forfeit gần như đồng thời) — bỏ qua an toàn.
        }
        long winnerCharacterId = killerEntityId > 0 ? killerEntityId : opponentOf(defeatedCharacterId, instance.get());
        settlementExecutor.execute(() -> settleDuel(instance.get(), winnerCharacterId, defeatedCharacterId));
    }

    /**
     * Gọi từ {@code channelInactive} (Netty EventLoop, mất kết nối) — người mất kết nối tính THUA
     * (không được "trốn thua" bằng cách rút dây mạng). Dừng {@code WorldFacade} instance đồng bộ ở
     * đây (join tick thread NGẮN, cùng pattern {@code DungeonInstanceRegistry.handleDisconnectIfInDungeon}
     * đã chấp nhận) NHƯNG đẩy MỌI phần chạm DB sang {@link #settlementExecutor} — EventLoop cấm
     * blocking I/O (server/CLAUDE.md). Bản cũ đưa đối phương về world chính NGAY trên EventLoop,
     * mà việc đó đọc chỉ số + trang bị từ DB. {@code false} nếu nhân vật không ở duel nào.
     */
    boolean handleDisconnectIfInDuel(long characterId) {
        Optional<DuelInstance> current = currentInstance(characterId);
        if (current.isEmpty()) {
            return false;
        }
        DuelInstance instance = current.get();
        if (!claimSettlement(instance)) {
            return true; // đã settle bởi lời gọi khác gần như đồng thời — coi như đã xử lý xong.
        }
        long winnerCharacterId = opponentOf(characterId, instance);
        instance.facade().detachCharacter(winnerCharacterId);
        instance.facade().detachCharacter(characterId);
        returnPositionByCharacterId.remove(characterId); // người mất kết nối: không re-attach, dọn rác.
        instance.facade().stop();
        settlementExecutor.execute(() -> {
            reattachToMainWorld(winnerCharacterId); // đối phương còn kết nối — đưa về world chính thật.
            recordResultAndNotify(winnerCharacterId, characterId);
        });
        return true;
    }

    /**
     * Vé chốt trận: đúng MỘT đường (bỏ cuộc, bị hạ gục, rớt mạng) lấy được, mọi đường khác nhận
     * {@code false} — xem javadoc lớp về vì sao gỡ người thua khỏi bảng không làm được vé.
     */
    private synchronized boolean claimSettlement(DuelInstance instance) {
        // equals của record = so từng trường; challengeId là duy nhất (AtomicLong) nên tương đương so
        // danh tính.
        if (!instance.equals(instancesByChallengeId.get(instance.challengeId()))) {
            return false;
        }
        instancesByChallengeId.remove(instance.challengeId());
        instanceByCharacterId.remove(instance.challengerCharacterId());
        instanceByCharacterId.remove(instance.targetCharacterId());
        return true;
    }

    /** Graceful shutdown (SERVER.md §10) — dừng MỌI duel World đang chạy trước khi thoát process. */
    synchronized void stopAll() {
        instancesByChallengeId.values().forEach(instance -> instance.facade().stop());
        instancesByChallengeId.clear();
        instanceByCharacterId.clear();
        startingDuelCharacterIds.clear();
        returnPositionByCharacterId.clear();
        settlementExecutor.shutdown();
    }

    private DuelEntrant loadEntrant(long characterId) {
        PositionSnapshot returnPosition = registry.lastKnownPosition(characterId);
        if (returnPosition == null) {
            returnPosition = characterFacade.loadPosition(characterId);
        }
        return new DuelEntrant(characterId, loadoutOf(characterId), returnPosition);
    }

    /**
     * Phần ghi của {@link #acceptDuel}: dựng World rồi dời cả hai người vào, nguyên tử. {@code false}
     * nếu một trong hai vừa rớt mạng trong lúc đọc DB ngoài khoá — {@code channelInactive} gỡ đăng ký
     * TRƯỚC khi gọi {@link #handleDisconnectIfInDuel}, mà lúc đó chưa có trận nào để nó dọn, nên kiểm
     * ở đây là chốt chặn cuối cho việc gắn một người đã ngắt kết nối vào trận.
     */
    private synchronized boolean commitStartDuel(long challengeId, DuelEntrant challenger, DuelEntrant target) {
        startingDuelCharacterIds.remove(challenger.characterId());
        startingDuelCharacterIds.remove(target.characterId());
        if (!registry.isOnline(challenger.characterId()) || !registry.isOnline(target.characterId())) {
            return false;
        }
        WorldFacade duelFacade = new WorldFacade(new ArenaOutboundSink(registry, this), false);
        // World DUY NHẤT được phép PvP (2026-09-24, tạm thời tới khi có vùng Hoang Vực — xem
        // WorldFacade#allowPlayerVersusPlayer). Thiếu dòng này thì đấu Arena không ai gây nổi sát thương.
        duelFacade.allowPlayerVersusPlayer();
        duelFacade.start();
        DuelInstance instance =
                new DuelInstance(challengeId, duelFacade, challenger.characterId(), target.characterId());
        instancesByChallengeId.put(challengeId, instance);
        moveCharacterIntoDuel(challenger, instance, DUEL_SPAWN_X_CHALLENGER);
        moveCharacterIntoDuel(target, instance, DUEL_SPAWN_X_TARGET);
        return true;
    }

    /**
     * ADR-051 — cả hai người nhận {@code WorldChangedEvent} lý do {@code REQUESTED}: người chấp nhận
     * vừa bấm, còn người thách chính là người đã xin trận này. Attach KÈM bonus trang bị (sửa
     * 2026-09-29 — trước đây vào trận bằng chỉ số NỀN, máu 206 → 100) ⇒ máu trong event là máu đầy
     * của máu tối đa hiệu lực.
     *
     * <p><b>Chờ world chính xử lý xong lệnh detach rồi mới báo 308 (quyết định chủ dự án 2026-10-01).</b>
     * Kiểm sống 2026-09-25 đo được gói despawn {@code 304} của world chính tới SAU {@code 308 ARENA} ở
     * 2/3 lượt: lệnh detach chỉ được xếp hàng, tick đang chạy dở vẫn phát nốt. Entity id = characterId ở
     * mọi World ⇒ client có thể xoá đối thủ khỏi màn đấu trường. Nay 308 + attach chạy sau mốc chặn hàng
     * đợi của world chính, trên {@link #settlementExecutor} (không chiếm tick thread của world chính,
     * không giữ khoá trong lúc chờ — EventLoop cũng xin khoá này).
     */
    @SuppressWarnings("FutureReturnValueIgnored") // lỗi đã được whenComplete ghi log
    private void moveCharacterIntoDuel(DuelEntrant entrant, DuelInstance instance, float spawnX) {
        long characterId = entrant.characterId();
        returnPositionByCharacterId.put(characterId, entrant.returnPosition());
        instanceByCharacterId.put(characterId, instance);

        PositionSnapshot spawn = new PositionSnapshot(spawnX, DUEL_SPAWN_Y);
        mainWorldChannels
                .detachCharacterAndDrain(characterId)
                .thenRunAsync(() -> finishMoveIntoDuel(entrant, instance, spawn), settlementExecutor)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        LOG.warn("Đưa nhân vật vào trận đấu lỗi: characterId={}", characterId, error);
                    }
                });
    }

    /**
     * Nửa sau của {@link #moveCharacterIntoDuel}, khi world chính đã im. Trong khoá, và chỉ khi trận
     * còn là trận của người này: một đường chốt (rớt mạng ngay đầu trận) có thể đã lấy vé trong lúc
     * chờ — khi đó nó tự đưa người còn lại về world chính, gửi 308 ARENA ở đây sẽ tới SAU 308 MAIN của
     * nó. Chốt trận lấy vé cũng trong khoá này ⇒ hoặc bước này xong trọn trước, hoặc bỏ qua.
     */
    private synchronized void finishMoveIntoDuel(DuelEntrant entrant, DuelInstance instance, PositionSnapshot spawn) {
        long characterId = entrant.characterId();
        if (!instance.equals(instanceByCharacterId.get(characterId))) {
            return;
        }
        // Lệnh detach đã chạy (và đã chờ) ở moveCharacterIntoDuel.
        transfer.move(
                characterId,
                () -> {},
                WorldTransfer.event(
                        WorldKind.WORLD_KIND_ARENA,
                        WorldChangeReason.WORLD_CHANGE_REASON_REQUESTED,
                        spawn,
                        entrant.loadout().maxHp()),
                () -> instance.facade().attachCharacter(entrant.loadout().entryAt(characterId, spawn)));
    }

    /**
     * Chốt kết quả trận — dọn bookkeeping, đưa cả 2 về world chính, ghi rating + báo kết quả.
     *
     * @return {@code false} nếu đường chốt khác đã lấy vé trước (xem {@link #claimSettlement})
     */
    private boolean settleDuel(DuelInstance instance, long winnerCharacterId, long loserCharacterId) {
        if (!claimSettlement(instance)) {
            return false; // đã settle bởi lời gọi khác (2 đòn hạ gục/forfeit gần như đồng thời) — bỏ qua an toàn.
        }

        instance.facade().detachCharacter(winnerCharacterId);
        instance.facade().detachCharacter(loserCharacterId);
        // Dừng TRƯỚC khi báo 308 MAIN (2026-10-01): stop() join tick thread ⇒ mọi gói của World đấu
        // trường đã ghi ra kênh trước 308, cùng lý do moveCharacterIntoDuel. Đường rớt mạng
        // (handleDisconnectIfInDuel) vốn đã theo thứ tự này.
        instance.facade().stop();
        reattachToMainWorld(winnerCharacterId);
        reattachToMainWorld(loserCharacterId);

        recordResultAndNotify(winnerCharacterId, loserCharacterId);
        return true;
    }

    private void recordResultAndNotify(long winnerCharacterId, long loserCharacterId) {
        RecordDuelResultUseCase.DuelSettlement settlement =
                pvpFacade.recordDuelResult(winnerCharacterId, loserCharacterId);
        DuelResultEvent event = DuelResultEvent.newBuilder()
                .setWinnerCharacterId(settlement.winnerCharacterId())
                .setLoserCharacterId(settlement.loserCharacterId())
                .setWinnerNewRating(settlement.winnerNewRating())
                .setLoserNewRating(settlement.loserNewRating())
                .build();
        byte[] payload = event.toByteArray();
        registry.sendToCharacterIfOnline(winnerCharacterId, Opcode.OPCODE_DUEL_RESULT_EVENT_VALUE, 0, payload);
        registry.sendToCharacterIfOnline(loserCharacterId, Opcode.OPCODE_DUEL_RESULT_EVENT_VALUE, 0, payload);
    }

    private void reattachToMainWorld(long characterId) {
        // Bonus trang bị đi CÙNG lệnh attach (2026-09-18): thiếu nó thì đấu xong là về map chính
        // với chỉ số NỀN và không bao giờ được cộng lại — máu 206 → 100, def 65 → 20.
        CharacterLoadout loadout = loadoutOf(characterId);
        PositionSnapshot returnPosition = returnPositionByCharacterId.remove(characterId);
        // Mọi đường gọi tới đây đã detach cả hai người khỏi World đấu trường từ trước ⇒ bước detach
        // của WorldTransfer không còn gì để làm.
        transfer.move(
                characterId,
                () -> {},
                WorldTransfer.event(
                        WorldKind.WORLD_KIND_MAIN,
                        WorldChangeReason.WORLD_CHANGE_REASON_DUEL_ENDED,
                        returnPosition,
                        loadout.maxHp()),
                () -> mainWorldChannels.attachCharacter(loadout.entryAt(characterId, returnPosition)));
        // Người này có thể đã rớt mạng trong lúc chốt (cả hai cùng rớt; hoặc người thắng rớt ngay sau
        // đòn kết liễu). channelInactive của họ đã/sẽ gỡ họ khỏi world chính — nhưng nếu nó chạy
        // TRƯỚC lệnh attach ở trên thì gỡ trượt, để lại thực thể bóng ma không ai dọn (sửa 2026-10-02).
        // Kiểm SAU attach đóng cả hai thứ tự: gỡ đăng ký chạy trước bước gỡ khỏi world chính của họ,
        // nên hoặc ta thấy họ offline và tự gỡ, hoặc bước gỡ của họ chạy sau attach này.
        if (!registry.isOnline(characterId)) {
            mainWorldChannels.detachCharacter(characterId);
        }
    }

    private static long opponentOf(long characterId, DuelInstance instance) {
        return instance.challengerCharacterId() == characterId
                ? instance.targetCharacterId()
                : instance.challengerCharacterId();
    }

    private CharacterLoadout loadoutOf(long characterId) {
        return CharacterLoadout.load(characterFacade, registry, characterId);
    }

    private record PendingChallenge(long challengeId, long challengerCharacterId, long targetCharacterId) {}

    /** Dữ liệu DB của một người sắp vào trận, đọc sẵn ngoài khoá. */
    private record DuelEntrant(long characterId, CharacterLoadout loadout, PositionSnapshot returnPosition) {}

    /** 1 trận Arena đang chạy — {@code challengerCharacterId}/{@code targetCharacterId} cố định cả trận. */
    private record DuelInstance(
            long challengeId, WorldFacade facade, long challengerCharacterId, long targetCharacterId) {}

    enum ChallengeOutcome {
        OK,
        CANNOT_CHALLENGE_SELF,
        ALREADY_IN_DUEL,
        TARGET_ALREADY_IN_DUEL,
        TARGET_OFFLINE
    }

    record ChallengeResult(ChallengeOutcome outcome, long challengeId) {}

    enum AcceptOutcome {
        OK,
        NOT_FOUND
    }

    record AcceptResult(AcceptOutcome outcome, long challengerCharacterId) {}

    enum DeclineOutcome {
        OK,
        NOT_FOUND
    }

    record DeclineResult(DeclineOutcome outcome, long challengerCharacterId) {}

    enum ExitOutcome {
        OK,
        NOT_IN_DUEL
    }

    record ExitResult(ExitOutcome outcome, long opponentCharacterId) {}
}
