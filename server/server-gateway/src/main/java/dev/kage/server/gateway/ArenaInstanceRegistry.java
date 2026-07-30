package dev.kage.server.gateway;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.protocol.DuelResultEvent;
import dev.kage.protocol.Opcode;
import dev.kage.pvp.api.PvpFacade;
import dev.kage.pvp.application.RecordDuelResultUseCase;
import dev.kage.server.world.CharacterBaseStats;
import dev.kage.server.world.Element;
import dev.kage.server.world.WorldFacade;

/**
 * Vòng đời thách đấu + trận Arena 1v1 (Phase 8 lát cắt 4, GDD §6) — bảng định tuyến
 * {@code characterId -> WorldFacade} riêng cho duel, cùng tinh thần {@code DungeonInstanceRegistry}
 * (ADR-026: World riêng cùng process, KHÔNG {@code feature-pvp} lo phần này vì không có dữ liệu
 * nghiệp vụ nào cần persist — xem feature-pvp/CLAUDE.md). Chỉ rating/wins/losses (feature-pvp) mới
 * cần DB.
 *
 * <p>⚠️ Khác {@code DungeonInstanceRegistry} (chỉ gọi từ {@code authExecutor}): registry NÀY còn bị
 * gọi từ {@code handleCharacterDefeated} TRÊN world tick thread của CHÍNH duel instance (qua
 * {@link ArenaOutboundSink}) — các map dưới đây dùng {@link ConcurrentHashMap} vì lý do đó, khác
 * {@code HashMap} thường của dungeon.
 *
 * <p>⚠️ Giới hạn CHỦ Ý (ngoài phạm vi lát cắt này, xem feature-pvp/CLAUDE.md): KHÔNG cross-check
 * với {@code DungeonInstanceRegistry} — 1 nhân vật đang ở dungeon về lý thuyết vẫn có thể bị thách
 * đấu/thách đấu người khác (constructor đã đủ 4 tham số, thêm phụ thuộc chéo cần tách record gộp
 * mới, không đáng cho 1 edge-case hiếm ở dự án cá nhân).
 */
final class ArenaInstanceRegistry {

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
    private final Map<Long, PendingChallenge> pendingChallengeByTargetId = new ConcurrentHashMap<>();
    private final Map<Long, DuelInstance> instancesByChallengeId = new ConcurrentHashMap<>();
    private final Map<Long, DuelInstance> instanceByCharacterId = new ConcurrentHashMap<>();
    private final Map<Long, PositionSnapshot> returnPositionByCharacterId = new ConcurrentHashMap<>();
    private final AtomicLong nextChallengeId = new AtomicLong(1);
    // settleDuel chạm DB (PvpFacade.recordDuelResult, blocking) — CẤM chạy trên world tick thread
    // hay Netty EventLoop (server/CLAUDE.md). Virtual Thread riêng, cùng pattern
    // ConnectedCharacterRegistry.lootExecutor.
    private final ExecutorService settlementExecutor = Executors.newVirtualThreadPerTaskExecutor();

    ArenaInstanceRegistry(
            ConnectedCharacterRegistry registry,
            CharacterFacade characterFacade,
            PvpFacade pvpFacade,
            MainWorldChannelRegistry mainWorldChannels) {
        this.registry = registry;
        this.characterFacade = characterFacade;
        this.pvpFacade = pvpFacade;
        this.mainWorldChannels = mainWorldChannels;
    }

    /** {@code WorldFacade} nhân vật này đang ở TRONG (duel), rỗng nếu đang ở world chính. */
    Optional<WorldFacade> worldFacadeFor(long characterId) {
        DuelInstance instance = instanceByCharacterId.get(characterId);
        return instance == null ? Optional.empty() : Optional.of(instance.facade());
    }

    ChallengeResult challengeDuel(long challengerCharacterId, long targetCharacterId) {
        if (challengerCharacterId == targetCharacterId) {
            return new ChallengeResult(ChallengeOutcome.CANNOT_CHALLENGE_SELF, 0);
        }
        if (instanceByCharacterId.containsKey(challengerCharacterId)) {
            return new ChallengeResult(ChallengeOutcome.ALREADY_IN_DUEL, 0);
        }
        if (instanceByCharacterId.containsKey(targetCharacterId)) {
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
        PendingChallenge challenge = pendingChallengeByTargetId.get(characterId);
        if (challenge == null || challenge.challengeId() != challengeId) {
            return new AcceptResult(AcceptOutcome.NOT_FOUND, 0);
        }
        pendingChallengeByTargetId.remove(characterId);
        long challengerCharacterId = challenge.challengerCharacterId();
        // Challenger có thể đã offline/bận trận khác giữa lúc thách đấu và lúc được chấp nhận.
        if (!registry.isOnline(challengerCharacterId) || instanceByCharacterId.containsKey(challengerCharacterId)) {
            return new AcceptResult(AcceptOutcome.NOT_FOUND, 0);
        }
        startDuel(challengeId, challengerCharacterId, characterId);
        return new AcceptResult(AcceptOutcome.OK, challengerCharacterId);
    }

    DeclineResult declineDuel(long characterId, long challengeId) {
        PendingChallenge challenge = pendingChallengeByTargetId.get(characterId);
        if (challenge == null || challenge.challengeId() != challengeId) {
            return new DeclineResult(DeclineOutcome.NOT_FOUND, 0);
        }
        pendingChallengeByTargetId.remove(characterId);
        return new DeclineResult(DeclineOutcome.OK, challenge.challengerCharacterId());
    }

    /** Bỏ cuộc giữa chừng — tính THUA thật (khác {@code DungeonInstanceRegistry.exitDungeon}). */
    ExitResult forfeitDuel(long characterId) {
        DuelInstance instance = instanceByCharacterId.get(characterId);
        if (instance == null) {
            return new ExitResult(ExitOutcome.NOT_IN_DUEL, 0);
        }
        long winnerCharacterId = opponentOf(characterId, instance);
        // Gọi trên authExecutor (Virtual Thread, KHÔNG phải tick thread hay EventLoop) — settleDuel
        // block an toàn ở đây (xem javadoc field settlementExecutor + handleCharacterDefeated).
        settleDuel(instance, winnerCharacterId, characterId);
        return new ExitResult(ExitOutcome.OK, winnerCharacterId);
    }

    /**
     * Gọi TRÊN world tick thread của CHÍNH duel instance (qua {@link ArenaOutboundSink}, khi
     * {@code WorldFacade} phát hiện 1 nhân vật hạ gục nhân vật kia). {@code settleDuel} gọi
     * {@code WorldFacade.stop()} (join tick thread) + chạm DB — TUYỆT ĐỐI không được chạy trực tiếp
     * ở đây (self-join deadlock + vi phạm "tick thread cấm blocking I/O"), phải đẩy toàn bộ sang
     * {@link #settlementExecutor}.
     */
    void handleCharacterDefeated(long defeatedCharacterId, long killerEntityId) {
        DuelInstance instance = instanceByCharacterId.get(defeatedCharacterId);
        if (instance == null) {
            return; // đã settle bởi lời gọi khác (forfeit gần như đồng thời) — bỏ qua an toàn.
        }
        long winnerCharacterId = killerEntityId > 0 ? killerEntityId : opponentOf(defeatedCharacterId, instance);
        settlementExecutor.execute(() -> settleDuel(instance, winnerCharacterId, defeatedCharacterId));
    }

    /**
     * Gọi từ {@code channelInactive} (Netty EventLoop, mất kết nối) — người mất kết nối tính THUA
     * (không được "trốn thua" bằng cách rút dây mạng). Dừng {@code WorldFacade} instance đồng bộ ở
     * đây (join tick thread NGẮN, cùng pattern {@code DungeonInstanceRegistry.handleDisconnectIfInDungeon}
     * đã chấp nhận) NHƯNG đẩy phần chạm DB (ghi rating) sang {@link #settlementExecutor} — EventLoop
     * cấm blocking I/O (server/CLAUDE.md). {@code false} nếu nhân vật không ở duel nào.
     */
    boolean handleDisconnectIfInDuel(long characterId) {
        DuelInstance instance = instanceByCharacterId.get(characterId);
        if (instance == null) {
            return false;
        }
        long winnerCharacterId = opponentOf(characterId, instance);
        if (instanceByCharacterId.remove(characterId) == null) {
            return true; // đã settle bởi lời gọi khác gần như đồng thời — coi như đã xử lý xong.
        }
        instanceByCharacterId.remove(winnerCharacterId);
        instancesByChallengeId.remove(instance.challengeId());
        instance.facade().detachCharacter(winnerCharacterId);
        instance.facade().detachCharacter(characterId);
        reattachToMainWorld(winnerCharacterId); // đối phương còn kết nối — đưa về world chính thật.
        returnPositionByCharacterId.remove(characterId); // người mất kết nối: không re-attach, dọn rác.
        instance.facade().stop();
        settlementExecutor.execute(() -> recordResultAndNotify(winnerCharacterId, characterId));
        return true;
    }

    /** Graceful shutdown (SERVER.md §10) — dừng MỌI duel World đang chạy trước khi thoát process. */
    void stopAll() {
        instancesByChallengeId.values().forEach(instance -> instance.facade().stop());
        instancesByChallengeId.clear();
        instanceByCharacterId.clear();
        returnPositionByCharacterId.clear();
        settlementExecutor.shutdown();
    }

    private void startDuel(long challengeId, long challengerCharacterId, long targetCharacterId) {
        WorldFacade duelFacade = new WorldFacade(new ArenaOutboundSink(registry, this), false);
        duelFacade.start();
        DuelInstance instance = new DuelInstance(challengeId, duelFacade, challengerCharacterId, targetCharacterId);
        instancesByChallengeId.put(challengeId, instance);
        moveCharacterIntoDuel(challengerCharacterId, instance, DUEL_SPAWN_X_CHALLENGER);
        moveCharacterIntoDuel(targetCharacterId, instance, DUEL_SPAWN_X_TARGET);
    }

    private void moveCharacterIntoDuel(long characterId, DuelInstance instance, float spawnX) {
        CharacterBaseStats baseStats = loadBaseStats(characterId);
        PositionSnapshot returnPosition = registry.lastKnownPosition(characterId);
        if (returnPosition == null) {
            returnPosition = characterFacade.loadPosition(characterId);
        }
        returnPositionByCharacterId.put(characterId, returnPosition);

        mainWorldChannels.detachCharacter(characterId);
        instance.facade().attachCharacter(characterId, spawnX, DUEL_SPAWN_Y, baseStats);
        instanceByCharacterId.put(characterId, instance);
    }

    /** Chốt kết quả trận — dọn bookkeeping, đưa cả 2 về world chính, ghi rating + báo kết quả. */
    private void settleDuel(DuelInstance instance, long winnerCharacterId, long loserCharacterId) {
        if (instanceByCharacterId.remove(loserCharacterId) == null) {
            return; // đã settle bởi lời gọi khác (2 đòn hạ gục/forfeit gần như đồng thời) — bỏ qua an toàn.
        }
        instanceByCharacterId.remove(winnerCharacterId);
        instancesByChallengeId.remove(instance.challengeId());

        instance.facade().detachCharacter(winnerCharacterId);
        instance.facade().detachCharacter(loserCharacterId);
        reattachToMainWorld(winnerCharacterId);
        reattachToMainWorld(loserCharacterId);
        instance.facade().stop();

        recordResultAndNotify(winnerCharacterId, loserCharacterId);
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
        CharacterBaseStats baseStats = loadBaseStats(characterId);
        PositionSnapshot returnPosition = returnPositionByCharacterId.remove(characterId);
        mainWorldChannels.attachCharacter(characterId, returnPosition.x(), returnPosition.y(), baseStats);
    }

    private static long opponentOf(long characterId, DuelInstance instance) {
        return instance.challengerCharacterId() == characterId
                ? instance.targetCharacterId()
                : instance.challengerCharacterId();
    }

    private CharacterBaseStats loadBaseStats(long characterId) {
        CharacterStats stats = characterFacade.loadStats(characterId);
        Optional<Character> character = characterFacade.findById(characterId);
        Element element =
                character.map(c -> FactionMapping.toElement(c.faction())).orElse(Element.NONE);
        int level = character.map(Character::level).orElse(1);
        return new CharacterBaseStats(stats.str(), stats.agi(), stats.intelligence(), stats.vit(), element, level);
    }

    private record PendingChallenge(long challengeId, long challengerCharacterId, long targetCharacterId) {}

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
