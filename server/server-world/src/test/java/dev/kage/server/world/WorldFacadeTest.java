package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verify vòng lặp tick thật (60Hz, qua {@link WorldTickScheduler}) của {@link WorldFacade} — không
 * mock tick loop vì đây chính là hành vi cần test (ROADMAP Phase 3: "2 client thấy nhau di
 * chuyển"; Phase 4 lát cắt 1: "đánh quái, quái chết").
 */
@Timeout(10)
class WorldFacadeTest {

    // STR/AGI/INT/VIT giả định cho test — không verify số liệu chỉ số cụ thể ở file này (đã có
    // PostgresCharacterAdapterIntegrationTest cho việc đó), chỉ cần attach thành công.
    private static final CharacterBaseStats DEFAULT_STATS = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 1);
    // Phase 8 (4 hệ phái mới) — cùng STR/AGI/INT/VIT với DEFAULT_STATS, chỉ khác Element, để so
    // sánh công bằng (không lẫn khác biệt do chỉ số nền) giữa các hệ trong test khắc chế/skill mới.
    private static final CharacterBaseStats THUY_STATS = new CharacterBaseStats(10, 10, 10, 10, Element.THUY, 1);
    private static final CharacterBaseStats PHONG_STATS = new CharacterBaseStats(10, 10, 10, 10, Element.PHONG, 1);
    private static final CharacterBaseStats THO_STATS = new CharacterBaseStats(10, 10, 10, 10, Element.THO, 1);
    private static final CharacterBaseStats LOI_STATS = new CharacterBaseStats(10, 10, 10, 10, Element.LOI, 1);

    private record Spawned(long target, long spawned, float x, float y) {}

    private record Moved(long target, long moved, float x, float y) {}

    private record Despawned(long target, long despawned) {}

    private record Accepted(long characterId, int sequenceId, float x, float y, float z, boolean grounded) {}

    private record MonsterSpawned(long target, long monsterId, String code, float x, float y, int hp, int maxHp) {}

    private record MonsterDespawned(long target, long monsterId) {}

    private record MonsterMoved(long target, long monsterId, float x, float y) {}

    private record MonsterKilled(long killerCharacterId, String monsterCode) {}

    private record NpcSpawned(long target, long npcId, String code, float x, float y) {}

    private record NpcDespawned(long target, long npcId) {}

    private record AttackResult(
            long target, long attacker, long defender, int damage, boolean crit, boolean miss, int hp, boolean died) {}

    private record SkillResult(
            long target,
            String skillCode,
            long attacker,
            long defender,
            int damage,
            boolean crit,
            boolean miss,
            int hp,
            boolean died,
            SkillEffectKind effectKind) {}

    private record AreaChatMessage(long target, long senderCharacterId, String senderName, String body) {}

    private record CharacterDefeated(long defeatedCharacterId, long killerEntityId) {}

    private record CharacterRespawned(
            long characterId, float x, float y, int currentHp, int maxHp, int currentChi, int maxChi) {}

    private static final class RecordingSink implements WorldOutboundSink {
        final List<Spawned> spawned = new CopyOnWriteArrayList<>();
        final List<Moved> moved = new CopyOnWriteArrayList<>();
        final List<Despawned> despawned = new CopyOnWriteArrayList<>();
        final List<Accepted> accepted = new CopyOnWriteArrayList<>();
        final List<MonsterSpawned> monsterSpawned = new CopyOnWriteArrayList<>();
        final List<MonsterDespawned> monsterDespawned = new CopyOnWriteArrayList<>();
        final List<MonsterMoved> monsterMoved = new CopyOnWriteArrayList<>();
        final List<MonsterKilled> monsterKilled = new CopyOnWriteArrayList<>();
        final List<NpcSpawned> npcSpawned = new CopyOnWriteArrayList<>();
        final List<NpcDespawned> npcDespawned = new CopyOnWriteArrayList<>();
        final List<AttackResult> attackResults = new CopyOnWriteArrayList<>();
        final List<SkillResult> skillResults = new CopyOnWriteArrayList<>();
        final List<AreaChatMessage> areaChatMessages = new CopyOnWriteArrayList<>();
        final List<CharacterDefeated> characterDefeated = new CopyOnWriteArrayList<>();
        final List<CharacterRespawned> characterRespawned = new CopyOnWriteArrayList<>();

        @Override
        public void onMoveAccepted(WorldOutboundSink.MoveAcceptedSnapshot snapshot) {
            accepted.add(new Accepted(
                    snapshot.characterId(),
                    snapshot.sequenceId(),
                    snapshot.x(),
                    snapshot.y(),
                    snapshot.z(),
                    snapshot.grounded()));
        }

        @Override
        public void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y) {
            spawned.add(new Spawned(targetCharacterId, spawnedCharacterId, x, y));
        }

        @Override
        public void onCharacterDespawned(long targetCharacterId, long despawnedCharacterId) {
            despawned.add(new Despawned(targetCharacterId, despawnedCharacterId));
        }

        @Override
        public void onCharacterMoved(long targetCharacterId, long movedCharacterId, float x, float y) {
            moved.add(new Moved(targetCharacterId, movedCharacterId, x, y));
        }

        @Override
        public void onMonsterSpawned(long targetCharacterId, WorldOutboundSink.MonsterSnapshot monster) {
            monsterSpawned.add(new MonsterSpawned(
                    targetCharacterId,
                    monster.monsterId(),
                    monster.monsterCode(),
                    monster.x(),
                    monster.y(),
                    monster.currentHp(),
                    monster.maxHp()));
        }

        @Override
        public void onMonsterDespawned(long targetCharacterId, long monsterId) {
            monsterDespawned.add(new MonsterDespawned(targetCharacterId, monsterId));
        }

        @Override
        public void onMonsterMoved(long targetCharacterId, long monsterId, float x, float y) {
            monsterMoved.add(new MonsterMoved(targetCharacterId, monsterId, x, y));
        }

        @Override
        public void onMonsterKilled(long killerCharacterId, String monsterCode, long expReward) {
            monsterKilled.add(new MonsterKilled(killerCharacterId, monsterCode));
        }

        @Override
        public void onNpcSpawned(long targetCharacterId, WorldOutboundSink.NpcSnapshot npc) {
            npcSpawned.add(new NpcSpawned(targetCharacterId, npc.npcId(), npc.npcCode(), npc.x(), npc.y()));
        }

        @Override
        public void onNpcDespawned(long targetCharacterId, long npcId) {
            npcDespawned.add(new NpcDespawned(targetCharacterId, npcId));
        }

        @Override
        public void onAttackResult(long targetCharacterId, WorldOutboundSink.AttackOutcome outcome) {
            attackResults.add(new AttackResult(
                    targetCharacterId,
                    outcome.attackerEntityId(),
                    outcome.targetEntityId(),
                    outcome.damage(),
                    outcome.crit(),
                    outcome.miss(),
                    outcome.targetCurrentHp(),
                    outcome.targetDied()));
        }

        @Override
        public void onSkillResult(long targetCharacterId, WorldOutboundSink.SkillOutcome outcome) {
            skillResults.add(new SkillResult(
                    targetCharacterId,
                    outcome.skillCode(),
                    outcome.attackerEntityId(),
                    outcome.targetEntityId(),
                    outcome.damage(),
                    outcome.crit(),
                    outcome.miss(),
                    outcome.targetCurrentHp(),
                    outcome.targetDied(),
                    outcome.effectKind()));
        }

        @Override
        public void onAreaChatMessage(long targetCharacterId, long senderCharacterId, String senderName, String body) {
            areaChatMessages.add(new AreaChatMessage(targetCharacterId, senderCharacterId, senderName, body));
        }

        @Override
        public void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {
            characterDefeated.add(new CharacterDefeated(defeatedCharacterId, killerEntityId));
        }

        @Override
        public void onCharacterRespawned(WorldOutboundSink.RespawnSnapshot respawn) {
            characterRespawned.add(new CharacterRespawned(
                    respawn.characterId(),
                    respawn.x(),
                    respawn.y(),
                    respawn.currentHp(),
                    respawn.maxHp(),
                    respawn.currentChi(),
                    respawn.maxChi()));
        }
    }

    private final RecordingSink sink = new RecordingSink();
    private final WorldFacade world =
            new WorldFacade(sink, true, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());

    @AfterEach
    void stopWorld() {
        world.stop();
    }

    @Test
    void shouldSpawnBothCharactersToEachOther_whenBothAttachAtSameSpawnPoint() {
        world.start();

        world.attachCharacter(1L, 0f, 0f, DEFAULT_STATS);
        world.attachCharacter(2L, 0f, 0f, DEFAULT_STATS);

        awaitTrue(() -> sink.spawned.stream().anyMatch(s -> s.target() == 1L && s.spawned() == 2L));
        awaitTrue(() -> sink.spawned.stream().anyMatch(s -> s.target() == 2L && s.spawned() == 1L));
    }

    @Test
    void shouldAcceptMoveAndBroadcastPositionToNearbyCharacter() {
        world.start();
        world.attachCharacter(1L, 0f, 0f, DEFAULT_STATS);
        world.attachCharacter(2L, 0f, 0f, DEFAULT_STATS);
        awaitTrue(() -> sink.spawned.size() >= 2);

        world.enqueueMoveCommand(1L, 42, 1f, 0f);

        awaitTrue(() -> sink.accepted.stream().anyMatch(a -> a.characterId() == 1L && a.sequenceId() == 42));
        awaitTrue(() -> sink.moved.stream().anyMatch(m -> m.target() == 2L && m.moved() == 1L));
    }

    /** ADR-033 — nhảy thật: grounded chuyển false NGAY khi lệnh được xử lý (trước cả khi z kịp đổi). */
    @Test
    void shouldApplyJumpVelocity_whenCharacterGrounded() {
        world.start();
        world.attachCharacter(1L, 0f, 0f, DEFAULT_STATS);
        // Không cần chờ attach xử lý xong trước khi gọi enqueueJumpCommand — inbox là FIFO, cả 2
        // lệnh cùng vào hàng đợi TRƯỚC lần tick đầu tiên xử lý, drainInbox() xử lý đúng thứ tự
        // (attach trước, jump sau) trong CÙNG 1 lượt drain.
        world.enqueueJumpCommand(1L, 7);

        awaitTrue(() ->
                sink.accepted.stream().anyMatch(a -> a.characterId() == 1L && a.sequenceId() == 7 && !a.grounded()));
    }

    /**
     * Nhảy đôi (chủ dự án yêu cầu 2026-08-09): đúng MỘT cú nhảy thêm giữa không trung, cao hơn cú
     * đầu — và spam lệnh nhảy KHÔNG cho bay vô hạn.
     *
     * <p>Đo bằng độ cao {@code z} lớn nhất từng thấy trong các ack (mỗi {@code JumpCommand} đều
     * được ack kèm {@code z} hiện tại, kể cả khi bị từ chối — xem {@code handleJumpIntent}), vì
     * {@code MoveAcceptedSnapshot} không mang {@code vz}. Hai ngưỡng đối lập nhau nên không có cách
     * nào cùng đạt bằng ăn may: dưới cận trên = có trần thật, trên cận dưới = cú thứ hai có xảy ra.
     */
    @Test
    void shouldAllowExactlyOneAirJump_whenJumpCommandSpammed() {
        // Đỉnh lý thuyết của MỘT cú nhảy đất: v²/(2g) = 7²/40 = 1.225m.
        float singleJumpApex = (WorldConstants.JUMP_VELOCITY_MPS * WorldConstants.JUMP_VELOCITY_MPS)
                / (2f * WorldConstants.GRAVITY_MPS2);
        // Trần tuyệt đối khi được nhảy thêm ĐÚNG 1 lần: đỉnh cú đầu + đỉnh cú thứ hai (8²/40 = 1.6m),
        // đạt được chỉ khi bấm chính xác lúc đỉnh. Vượt mốc này = hạn mức không có tác dụng.
        float doubleJumpCeiling = singleJumpApex
                + ((WorldConstants.AIR_JUMP_VELOCITY_MPS * WorldConstants.AIR_JUMP_VELOCITY_MPS)
                        / (2f * WorldConstants.GRAVITY_MPS2));

        world.start();
        world.attachCharacter(1L, 0f, 0f, DEFAULT_STATS);
        // Spam suốt ~0.4s — dài hơn thời gian tới đỉnh của cú thứ hai (8/20 = 0.4s) nên chắc chắn có
        // ack lấy mẫu ở gần đỉnh, và đủ nhiều lệnh để phơi ra lỗi "không có trần" nếu hạn mức hỏng.
        for (int sequenceId = 1; sequenceId <= 40; sequenceId++) {
            world.enqueueJumpCommand(1L, sequenceId);
            sleepQuietly(10);
        }
        awaitTrue(
                () -> sink.accepted.stream().filter(a -> a.characterId() == 1L).count() >= 40);

        float peakZ = (float) sink.accepted.stream()
                .filter(a -> a.characterId() == 1L)
                .mapToDouble(Accepted::z)
                .max()
                .orElseThrow();
        assertThat(peakZ).isGreaterThan(singleJumpApex);
        assertThat(peakZ).isLessThanOrEqualTo(doubleJumpCeiling);
    }

    /**
     * WorldTickScheduler (Phase 9 — thay "1 OS thread riêng/World" bằng pool CỐ ĐỊNH dùng chung):
     * số World khởi tạo ở đây CỐ TÌNH vượt xa số lõi CPU thông thường để xác nhận
     * {@code scheduleAtFixedRate} dùng chung không làm ĐÓI World nào — mọi World đều phải tự xử lý
     * được lệnh move của chính nó (đi qua {@code drainInbox()} trong tick), không chỉ 1 vài World
     * đầu tiên chiếm hết pool.
     */
    @Test
    @Timeout(15)
    void shouldTickManyWorldsConcurrently_onSharedPoolWithoutStarvation() {
        int worldCount = 32;
        List<RecordingSink> sinks = new java.util.ArrayList<>();
        List<WorldFacade> worlds = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < worldCount; i++) {
                RecordingSink extraSink = new RecordingSink();
                WorldFacade extraWorld = new WorldFacade(extraSink, false);
                extraWorld.start();
                sinks.add(extraSink);
                worlds.add(extraWorld);
            }
            for (int i = 0; i < worldCount; i++) {
                worlds.get(i).attachCharacter(1000L + i, 0f, 0f, DEFAULT_STATS);
                worlds.get(i).enqueueMoveCommand(1000L + i, 1, 1f, 0f);
            }
            for (int i = 0; i < worldCount; i++) {
                int idx = i;
                awaitTrue(() -> sinks.get(idx).accepted.stream()
                        .anyMatch(a -> a.characterId() == 1000L + idx && a.sequenceId() == 1));
            }
        } finally {
            worlds.forEach(WorldFacade::stop);
        }
    }

    /**
     * {@code stop()} một World không được ảnh hưởng World khác đang chạy chung pool — mirror lại
     * đúng ngữ nghĩa "interrupt + join thread riêng" cũ, giờ dựa trên "cancel + khoá" (xem
     * {@code WorldFacade.stop()}).
     */
    @Test
    void shouldNotAffectOtherWorlds_whenStoppingOneWorldOnSharedPool() {
        RecordingSink sinkA = new RecordingSink();
        RecordingSink sinkB = new RecordingSink();
        WorldFacade worldA = new WorldFacade(sinkA, false);
        WorldFacade worldB = new WorldFacade(sinkB, false);
        try {
            worldA.start();
            worldB.start();
            worldA.attachCharacter(2001L, 0f, 0f, DEFAULT_STATS);
            worldB.attachCharacter(2002L, 0f, 0f, DEFAULT_STATS);
            worldA.enqueueMoveCommand(2001L, 1, 1f, 0f);
            worldB.enqueueMoveCommand(2002L, 1, 1f, 0f);
            awaitTrue(() -> sinkA.accepted.stream().anyMatch(a -> a.characterId() == 2001L));
            awaitTrue(() -> sinkB.accepted.stream().anyMatch(a -> a.characterId() == 2002L));

            worldA.stop();
            int acceptedCountBeforeWait = sinkB.accepted.size();
            worldB.enqueueMoveCommand(2002L, 2, 0f, 1f);
            awaitTrue(() -> sinkB.accepted.stream().anyMatch(a -> a.characterId() == 2002L && a.sequenceId() == 2));
            assertThat(sinkB.accepted.size()).isGreaterThan(acceptedCountBeforeWait);
        } finally {
            worldA.stop();
            worldB.stop();
        }
    }

    @Test
    void shouldClampExtremeDirection_soClientCannotSpeedHack() {
        // Chống speed-hack BẰNG THIẾT KẾ (GAMEPLAY.md §12, movement.proto): client gửi hướng
        // "gian lận" cực lớn — nếu server không chuẩn hoá về unit vector trước khi nhân
        // MOVE_SPEED_MPS, nhân vật sẽ bay xa gấp hàng trăm lần chỉ trong 1 tick.
        world.start();
        world.attachCharacter(1L, 0f, 0f, DEFAULT_STATS);
        world.attachCharacter(2L, 0f, 0f, DEFAULT_STATS);
        awaitTrue(() -> sink.spawned.size() >= 2);

        world.enqueueMoveCommand(1L, 1, 1000f, 0f);

        awaitTrue(() -> sink.moved.stream().anyMatch(m -> m.target() == 2L && m.moved() == 1L));
        Moved firstMoved = sink.moved.stream()
                .filter(m -> m.target() == 2L && m.moved() == 1L)
                .findFirst()
                .orElseThrow();

        // Không clamp: dirX=1000 → 1000 world unit/tick ngay tick đầu. Có clamp đúng: tối đa
        // MOVE_SPEED_MPS (5f) unit/giây — cách biên rất xa nên không flaky theo tốc độ máy chạy test.
        assertThat(firstMoved.x()).isLessThan(2f);
    }

    @Test
    void shouldDespawnForRemainingCharacter_whenOneDetaches() {
        world.start();
        world.attachCharacter(1L, 0f, 0f, DEFAULT_STATS);
        world.attachCharacter(2L, 0f, 0f, DEFAULT_STATS);
        awaitTrue(() -> sink.spawned.size() >= 2);

        world.detachCharacter(1L);

        awaitTrue(() -> sink.despawned.stream().anyMatch(d -> d.target() == 2L && d.despawned() == 1L));
    }

    @Test
    void shouldSpawnTrainingDummy_toCharacterNearby() {
        world.start();

        world.attachCharacter(1L, 5f, 6f, DEFAULT_STATS); // gần quái bù nhìn (spawn cố định (5,5), WorldFacade)

        awaitTrue(() -> sink.monsterSpawned.stream()
                .anyMatch(m -> m.target() == 1L && m.code().equals("training_dummy")));
    }

    @Test
    void shouldSpawnNpc_toCharacterNearby() {
        world.start();

        // Trưởng lão ở (-5, 0) — từ ADR-040 con số này đến từ catalog (TestWorldContents), không
        // còn là hằng số SAMPLE_NPC_SPAWN_X trong WorldFacade.
        world.attachCharacter(1L, -5f, -6f, DEFAULT_STATS);

        awaitTrue(() -> sink.npcSpawned.stream()
                .anyMatch(n -> n.target() == 1L && n.code().equals("town_elder")));
    }

    @Test
    void shouldSpawnEveryNpcFromCatalog_atTheCoordinatesTheCatalogGives() {
        // ADR-040 — thước đo thật của cả đợt: mã VÀ vị trí NPC đến từ dữ liệu, không từ hằng số.
        // Dùng hai NPC ở hai chỗ KHÁC hằng số cũ (-5, 0); nếu WorldFacade còn hardcode ở đâu đó thì
        // test này đỏ, còn shouldSpawnNpc_toCharacterNearby vẫn xanh (nó trùng số cũ).
        RecordingSink dataSink = new RecordingSink();
        NpcCatalogSnapshot twoNpcs = new NpcCatalogSnapshot(List.of(
                new NpcCatalogSnapshot.NpcPlacementDefinition("sect_gatekeeper", 30f, 0f),
                new NpcCatalogSnapshot.NpcPlacementDefinition("herb_trader", 31f, 0f)));
        WorldFacade dataWorld = new WorldFacade(
                dataSink, true, TickMetricsPort.NO_OP, new WorldContentCatalog(MonsterCatalogSnapshot.EMPTY, twoNpcs));
        try {
            dataWorld.start();
            dataWorld.attachCharacter(1L, 30f, 0f, DEFAULT_STATS);

            awaitTrue(() ->
                    dataSink.npcSpawned.stream().filter(n -> n.target() == 1L).count() == 2);
            assertThat(dataSink.npcSpawned)
                    .filteredOn(n -> n.target() == 1L)
                    .extracting(NpcSpawned::code, NpcSpawned::x, NpcSpawned::y)
                    .containsExactlyInAnyOrder(tuple("sect_gatekeeper", 30f, 0f), tuple("herb_trader", 31f, 0f));
        } finally {
            dataWorld.stop();
        }
    }

    @Test
    void shouldSpawnNoNpc_whenCatalogHasNone_evenOnTheMainWorld() {
        // Hai cờ trả lời hai câu khác nhau: spawnOverworldContent=true vẫn cho bệ + hồi sinh nhân
        // vật, nhưng catalog rỗng nghĩa là không NPC nào — đúng như đã đúng với quái từ ADR-037.
        RecordingSink emptySink = new RecordingSink();
        WorldFacade emptyWorld = new WorldFacade(
                emptySink, true, TickMetricsPort.NO_OP, WorldContentCatalog.ofMonsters(MonsterCatalogSnapshot.EMPTY));
        try {
            emptyWorld.start();
            emptyWorld.attachCharacter(1L, -5f, 0f, DEFAULT_STATS);
            emptyWorld.attachCharacter(2L, -5f, 1f, DEFAULT_STATS);

            awaitTrue(() -> emptySink.spawned.size() >= 2); // world vẫn tick + AoI hoạt động
            assertThat(emptySink.npcSpawned).isEmpty();
        } finally {
            emptyWorld.stop();
        }
    }

    @Test
    void shouldNotSpawnTrainingDummyOrNpc_whenCreatedForDungeonInstance() {
        // ADR-026 — dungeon instance (spawnOverworldContent=false) khởi động RỖNG, khác world
        // chính. Dùng WorldFacade RIÊNG (không phải field `world` dùng chung ở các test khác).
        RecordingSink dungeonSink = new RecordingSink();
        WorldFacade dungeonWorld = new WorldFacade(dungeonSink, false);
        try {
            dungeonWorld.start();
            // Toạ độ giống hệt quái bù nhìn (5,5)/NPC mẫu (-5,-5) của world chính NẾU có spawn —
            // nhưng đủ gần nhau (trong AOI_RADIUS_METERS) để xác nhận world vẫn tick/AoI bình thường.
            dungeonWorld.attachCharacter(1L, 5f, 5f, DEFAULT_STATS);
            dungeonWorld.attachCharacter(2L, -5f, -5f, DEFAULT_STATS);

            awaitTrue(() -> dungeonSink.spawned.size() >= 2); // world vẫn tick + AoI hoạt động bình thường
            assertThat(dungeonSink.monsterSpawned).isEmpty();
            assertThat(dungeonSink.npcSpawned).isEmpty();
        } finally {
            dungeonWorld.stop();
        }
    }

    @Test
    @Timeout(30) // Trận đấu boss thời gian thực ~12-13s (BOSS_MAX_HP=320) — dài hơn @Timeout(10) lớp.
    void shouldFightThroughAllPhases_whenBossEngaged() {
        // Phase 8 lát cắt 2 — boss trong dungeon instance (spawnOverworldContent=false, giống
        // test shouldNotSpawnTrainingDummyOrNpc...). Verify CẢ 2 chiêu đặc biệt (heavy_strike <70%
        // HP, enrage <30% HP) xuất hiện thật trước khi boss chết, và chết KHÔNG kích hoạt respawn
        // kiểu training dummy (PHÁT HIỆN THẬT lát cắt này — xem WorldFacade.handleMonsterDeath).
        RecordingSink dungeonSink = new RecordingSink();
        WorldFacade dungeonWorld = new WorldFacade(dungeonSink, false);
        try {
            dungeonWorld.start();
            dungeonWorld.spawnBoss();
            // Attach TRƯỚC khi chờ monsterSpawned — AoI chỉ báo spawn khi có NHÂN VẬT ở gần để
            // thông báo (aoiTracker.sync() so cặp nhân vật-quái, không tự báo quái "mồ côi" không
            // ai quan sát). Spawn cách boss (3,0) đúng 1m — trong tầm đánh (2m) VÀ tầm aggro (6m).
            dungeonWorld.attachCharacter(1L, 3f, 1f, DEFAULT_STATS);
            awaitTrue(() ->
                    dungeonSink.monsterSpawned.stream().anyMatch(m -> m.code().equals("sample_boss")));
            long bossId = dungeonSink.monsterSpawned.get(0).monsterId();

            targetAndAttackUntil(
                    dungeonWorld,
                    bossId,
                    () -> dungeonSink.monsterDespawned.stream().anyMatch(d -> d.monsterId() == bossId),
                    Duration.ofSeconds(25));

            assertThat(dungeonSink.skillResults.stream().anyMatch(r -> "heavy_strike".equals(r.skillCode())))
                    .as("boss phải dùng heavy_strike khi HP < 70%%")
                    .isTrue();
            assertThat(dungeonSink.skillResults.stream().anyMatch(r -> "enrage".equals(r.skillCode())))
                    .as("boss phải dùng enrage khi HP < 30%%")
                    .isTrue();
            assertThat(dungeonSink.monsterKilled).containsExactly(new MonsterKilled(1L, "sample_boss"));
        } finally {
            dungeonWorld.stop();
        }
    }

    @Test
    @Timeout(15) // > CombatConstants.MONSTER_RESPAWN_SECONDS (10s) — chờ xác nhận KHÔNG respawn.
    void shouldNotRespawnBoss_afterDeathInDungeon() {
        // PHÁT HIỆN THẬT (Phase 8 lát cắt 2): trước khi thêm guard `spawnOverworldContent` ở
        // WorldFacade.handleMonsterDeath, MỌI cái chết quái (kể cả boss trong dungeon) đều lên
        // lịch spawnTrainingDummy() sau MONSTER_RESPAWN_SECONDS — sai nội dung, sai world. Hạ gục
        // nhanh bằng applyEquipmentBonus (cùng pattern shouldOneShotMonster_...) để tách biệt
        // khỏi kịch bản nhiều pha ở test trên, tránh @Timeout dài gấp đôi không cần thiết.
        RecordingSink dungeonSink = new RecordingSink();
        WorldFacade dungeonWorld = new WorldFacade(dungeonSink, false);
        try {
            dungeonWorld.start();
            dungeonWorld.spawnBoss();
            dungeonWorld.attachCharacter(1L, 3f, 1f, DEFAULT_STATS);
            awaitTrue(() ->
                    dungeonSink.monsterSpawned.stream().anyMatch(m -> m.code().equals("sample_boss")));
            long bossId = dungeonSink.monsterSpawned.get(0).monsterId();

            // đủ lớn để hạ gục BOSS_MAX_HP (320) ngay đòn đầu.
            dungeonWorld.applyEquipmentBonus(1L, new EquipmentBonusSnapshot(1000, 0, 0, 0));

            targetAndAttackUntil(dungeonWorld, bossId, () -> dungeonSink.monsterDespawned.stream()
                    .anyMatch(d -> d.monsterId() == bossId));

            // Chờ đủ lâu hơn MONSTER_RESPAWN_SECONDS (10s, hằng số dùng chung với world chính) rồi
            // xác nhận KHÔNG có spawn thứ 2 — khác awaitTrue (chờ 1 điều kiện TRỞ THÀNH true), ở
            // đây phải chờ HẾT deadline rồi mới kết luận được "không xảy ra".
            sleepUninterruptibly(Duration.ofSeconds(11));
            assertThat(dungeonSink.monsterSpawned).hasSize(1);
        } finally {
            dungeonWorld.stop();
        }
    }

    @Test
    void shouldDespawnNpc_whenCharacterMovesAway() {
        // KHÔNG dùng detachCharacter để test nhánh này — onDetach() không báo despawn quái/NPC
        // cho CHÍNH nhân vật rời đi (nó không còn kết nối để nhận nữa), chỉ báo nhân vật khác về
        // sự rời đi đó. Despawn NPC chỉ phát sinh qua nhánh "ra khỏi AOI_RADIUS" thật trong
        // syncCharacterNpcPair — phải di chuyển thật xa NPC (khác quái, NPC không tự đứng yên chờ
        // rồi hồi sinh gần hơn).
        world.start();
        world.attachCharacter(1L, -5f, -6f, DEFAULT_STATS); // gần NPC mẫu (spawn cố định (-5,-5))
        awaitTrue(() -> sink.npcSpawned.stream().anyMatch(n -> n.target() == 1L));
        long npcId = sink.npcSpawned.get(0).npcId();

        world.enqueueMoveCommand(1L, 1, 1f, 1f); // đi xa dần theo hướng (1,1), MOVE_SPEED_MPS=5f

        awaitTrue(
                () -> sink.npcDespawned.stream().anyMatch(d -> d.target() == 1L && d.npcId() == npcId),
                Duration.ofSeconds(8)); // > AOI_RADIUS_METERS(15f)/MOVE_SPEED_MPS(5f) ≈ 3s + đệm jitter
    }

    @Test
    void shouldDealDamageAndEventuallyKillMonster_whenAttackCommandRepeated() {
        world.start();
        world.attachCharacter(1L, 5f, 6f, DEFAULT_STATS); // cách quái (5,5) đúng 1m — trong tầm đánh (2m)
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));
        long monsterId = sink.monsterSpawned.get(0).monsterId();

        targetAndAttackUntil(world, monsterId, () -> sink.monsterDespawned.stream()
                .anyMatch(d -> d.target() == 1L && d.monsterId() == monsterId));
        // Đòn hạ gục PHẢI có targetDied=true — không chỉ despawn suông không rõ lý do.
        assertThat(sink.attackResults.stream().anyMatch(a -> a.defender() == monsterId && a.died()))
                .isTrue();
        // Server-world báo ĐÚNG người hạ gục — gateway dùng cái này để roll rơi đồ (GAMEPLAY.md §5).
        assertThat(sink.monsterKilled).containsExactly(new MonsterKilled(1L, "training_dummy"));
    }

    @Test
    @Timeout(20) // > CombatConstants.MONSTER_RESPAWN_SECONDS (10s) — ghi đè @Timeout(10) của lớp.
    void shouldRespawnMonster_afterDeathDelay() {
        world.start();
        world.attachCharacter(1L, 5f, 6f, DEFAULT_STATS);
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));
        long firstMonsterId = sink.monsterSpawned.get(0).monsterId();

        targetAndAttackUntil(world, firstMonsterId, () -> sink.monsterDespawned.stream()
                .anyMatch(d -> d.monsterId() == firstMonsterId));

        // Quái hồi sinh là entity MỚI (ID khác — WorldFacade không tái dùng ID đã cấp), đầy máu.
        // MONSTER_RESPAWN_SECONDS=10s > 5s mặc định của awaitTrue — dùng deadline dài hơn.
        awaitTrue(
                () -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L && m.monsterId() != firstMonsterId),
                Duration.ofSeconds(15));
        MonsterSpawned respawned = sink.monsterSpawned.stream()
                .filter(m -> m.monsterId() != firstMonsterId)
                .findFirst()
                .orElseThrow();
        assertThat(respawned.hp()).isEqualTo(respawned.maxHp());
    }

    @Test
    void shouldNotBypassAttackCooldown_bySpammingAttackCommand() {
        // Chống cooldown hack BẰNG THIẾT KẾ (SECURITY.md §8) — client chỉ gửi Ý ĐỊNH đánh, KHÔNG
        // gửi cooldown/damage. Từ 2026-08-09 đánh thường phải bấm tay, nên đây thành đường tấn công
        // THẬT SỰ đáng lo: một client sửa đổi gửi AttackCommand mỗi khung hình. ATTACK_INTERVAL
        // của server phải giữ tuyệt đối bất kể gửi bao nhiêu gói.
        world.start();
        world.attachCharacter(1L, 5f, 6f, DEFAULT_STATS);
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));
        long monsterId = sink.monsterSpawned.get(0).monsterId();
        world.setTarget(1L, monsterId);

        for (int i = 0; i < 500; i++) {
            world.attack(1L); // spam — không phải input hợp lệ của UI thật nào
        }

        sleepQuietly(1200); // ~1.2s thật — ATK_SPEED placeholder = 1 đòn/giây (CombatConstants)

        long attacksLandedOnMonster = sink.attackResults.stream()
                .filter(a -> a.defender() == monsterId && !a.miss())
                .count();
        // Đúng thiết kế: tối đa ~1-2 đòn trong 1.2s dù gửi 500 lệnh — KHÔNG phải 500 đòn.
        assertThat(attacksLandedOnMonster).isLessThanOrEqualTo(2);
    }

    @Test
    @Timeout(15) // vài lần thử cast, mỗi lần chờ hết cooldown 3s (SkillConstants) — dài hơn @Timeout(10) mặc định.
    void shouldDealSkillDamage_withScriptComputedAmount_whenSkillCast() {
        // Lát cắt 6: UseSkillCommand thật, damage tính bởi script GraalJS (fireball.js, ADR-016) —
        // không phải Java hardcode. HIT% placeholder ~91% với DEFAULT_STATS nên thử vài lần thay vì
        // giả định trúng ngay lần đầu (tránh test bị flaky vì roll né/trúng thật, ENGINE.md §10).
        world.start();
        world.attachCharacter(1L, 5f, 6f, DEFAULT_STATS); // cách quái (5,5) 1m — trong tầm skill (5m)
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));
        long monsterId = sink.monsterSpawned.get(0).monsterId();

        castUntilHit(monsterId);

        SkillResult hit = sink.skillResults.stream()
                .filter(r -> r.defender() == monsterId && !r.miss())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không lần cast fireball nào trúng sau nhiều lần thử"));
        assertThat(hit.skillCode()).isEqualTo("fireball");
        assertThat(hit.attacker()).isEqualTo(1L);
        assertThat(hit.damage()).isGreaterThan(0);
    }

    @Test
    @Timeout(15) // vài lần thử cast — cooldown 2s (hoa_tia_lua) ngắn hơn fireball (3s).
    void shouldDealSkillDamage_whenHoaTiaLuaCast() {
        // Hỏa active 2 (hoa_tia_lua.js) — "sát thương liên tục" (GDD §5 dòng 60): cooldown thấp
        // nhất trong mọi skill hiện có, verify vẫn deal damage thật qua script GraalJS như fireball.
        world.start();
        world.attachCharacter(1L, 5f, 6f, DEFAULT_STATS);
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));
        long monsterId = sink.monsterSpawned.get(0).monsterId();

        castUntilHit(monsterId, "hoa_tia_lua", 2f);

        SkillResult hit = sink.skillResults.stream()
                .filter(r -> r.defender() == monsterId && !r.miss() && "hoa_tia_lua".equals(r.skillCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không lần cast hoa_tia_lua nào trúng sau nhiều lần thử"));
        assertThat(hit.attacker()).isEqualTo(1L);
        assertThat(hit.damage()).isGreaterThan(0);
    }

    @Test
    @Timeout(15)
    void shouldDealSkillDamage_whenHoaBungNoCast() {
        // Hỏa active 3 (hoa_bung_no.js) — "sát thương bùng nổ" (GDD §5 dòng 60): đòn nặng hơn, có
        // armor_pierce nhẹ (0.15) — verify vẫn deal damage thật qua CombatConstants.resolveSkillAttack
        // overload 4 tham số (armor_pierce), không chỉ overload thường (giống loi_tia_set.js).
        world.start();
        world.attachCharacter(1L, 5f, 6f, DEFAULT_STATS);
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));
        long monsterId = sink.monsterSpawned.get(0).monsterId();

        castUntilHit(monsterId, "hoa_bung_no", 7f);

        SkillResult hit = sink.skillResults.stream()
                .filter(r -> r.defender() == monsterId && !r.miss() && "hoa_bung_no".equals(r.skillCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không lần cast hoa_bung_no nào trúng sau nhiều lần thử"));
        assertThat(hit.attacker()).isEqualTo(1L);
        assertThat(hit.damage()).isGreaterThan(0);
    }

    @Test
    @Timeout(15)
    void shouldSilentlyRejectHoaUltimate_whenBelowRequiredLevel() {
        // GDD §5 "Tuyệt Kỹ Lv50" — hoa_diem_nguc (Hỏa) phải bị chặn ÂM THẦM giống thuy_thuy_trieu
        // (shouldSilentlyRejectUltimate_whenBelowRequiredLevel), xác nhận Tuyệt Kỹ mới của Hỏa cũng
        // đi qua đúng gate CharacterLevelComponent, không phải case đặc biệt bị bỏ sót lúc đăng ký.
        world.start();
        world.attachCharacter(1L, 5f, 6f, DEFAULT_STATS); // level=1 < ULTIMATE_REQUIRED_LEVEL (50)
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));
        long monsterId = sink.monsterSpawned.get(0).monsterId();

        world.useSkill(1L, "hoa_diem_nguc", monsterId);
        sleepQuietly(500);

        assertThat(sink.skillResults.stream().anyMatch(r -> "hoa_diem_nguc".equals(r.skillCode())))
                .isFalse();
    }

    @Test
    void shouldRespectSkillCooldown_whenSpammingUseSkillCommand() {
        // Chống cooldown hack giống SetTargetCommand: gửi dồn dập UseSkillCommand KHÔNG được cast
        // nhiều hơn 1 lần trong lúc còn cooldown (SkillConstants.FIREBALL_COOLDOWN_SECONDS = 3s).
        world.start();
        world.attachCharacter(1L, 5f, 6f, DEFAULT_STATS);
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));
        long monsterId = sink.monsterSpawned.get(0).monsterId();

        for (int i = 0; i < 200; i++) {
            world.useSkill(1L, "fireball", monsterId); // spam — không phải input hợp lệ của UI thật nào
        }
        sleepQuietly(500); // << 3s cooldown — chỉ đủ thời gian cho ĐÚNG 1 lần cast thật xử lý xong

        long castsLanded = sink.skillResults.stream()
                .filter(r -> r.defender() == monsterId)
                .count();
        assertThat(castsLanded).isLessThanOrEqualTo(1);
    }

    @Test
    @Timeout(15)
    void shouldDealMoreDamage_whenAttackerElementCountersDefenderElement() {
        // GDD §4/§6 khắc chế nguyên tố — Hỏa khắc Phong (×1.15) nhưng trung lập với Lôi (×1.0).
        // Dùng đánh thường (không phải skill) để tránh nhánh "đòn kết liễu" của fireball.js (dựa
        // theo %HP mục tiêu) làm nhiễu phép so sánh. DEF của Phong/Lôi bằng nhau (không hệ nào có
        // passive +DEF ở lát cắt này) nên chênh lệch damage CHỈ có thể tới từ hệ số khắc chế.
        world.start();
        world.attachCharacter(1L, 0f, 0f, DEFAULT_STATS); // Hỏa
        world.attachCharacter(2L, 1f, 0f, PHONG_STATS); // bị Hỏa khắc
        world.attachCharacter(3L, -1f, 0f, LOI_STATS); // trung lập với Hỏa
        awaitTrue(() -> sink.spawned.size() >= 6); // 3 nhân vật thấy nhau đủ 2 chiều

        targetAndAttackUntil(
                world, 2L, () -> sink.attackResults.stream().anyMatch(a -> a.defender() == 2L && !a.miss()));
        int damageVsPhong = sink.attackResults.stream()
                .filter(a -> a.defender() == 2L && !a.miss())
                .findFirst()
                .orElseThrow()
                .damage();

        targetAndAttackUntil(
                world, 3L, () -> sink.attackResults.stream().anyMatch(a -> a.defender() == 3L && !a.miss()));
        int damageVsLoi = sink.attackResults.stream()
                .filter(a -> a.defender() == 3L && !a.miss())
                .findFirst()
                .orElseThrow()
                .damage();

        assertThat(damageVsPhong).isGreaterThan(damageVsLoi);
    }

    @Test
    @Timeout(10)
    void shouldFireCharacterDefeated_whenCharacterKillsAnotherCharacter() {
        // Phase 8 lát cắt 4 (Arena) — WorldOutboundSink#onCharacterDefeated là hạ tầng dùng chung
        // cho duel (ArenaOutboundSink chốt kết quả trận đấu); world CHÍNH không có UI nào phơi bày
        // đường này (xem feature-pvp/CLAUDE.md), test trực tiếp bằng setTarget như test khắc chế
        // nguyên tố ở trên.
        world.start();
        world.attachCharacter(1L, 0f, 0f, DEFAULT_STATS);
        world.attachCharacter(2L, 1f, 0f, DEFAULT_STATS);
        awaitTrue(() -> sink.spawned.size() >= 2);

        // đủ lớn để hạ gục CHARACTER_MAX_HP ngay đòn đầu.
        world.applyEquipmentBonus(1L, new EquipmentBonusSnapshot(1000, 0, 0, 0));

        targetAndAttackUntil(
                world, 2L, () -> sink.characterDefeated.stream().anyMatch(d -> d.defeatedCharacterId() == 2L));
        CharacterDefeated defeat = sink.characterDefeated.stream()
                .filter(d -> d.defeatedCharacterId() == 2L)
                .findFirst()
                .orElseThrow();
        assertThat(defeat.killerEntityId()).isEqualTo(1L);

        // Đòn hạ gục chỉ bắn ĐÚNG 1 lần (CombatSystem tự bỏ chọn target sau khi thấy mục tiêu đã
        // chết — xem javadoc WorldFacade#handleCombatResults) dù nhân vật vẫn còn attach trong World.
        // VẪN bấm đánh suốt 1.2s tiếp theo — giữ nguyên ý định gốc của test (CombatSystem tự bỏ
        // chọn mục tiêu đã chết nên không có đòn thứ hai). Chỉ ngồi im chờ thì không kiểm được gì
        // kể từ khi đánh thường phải bấm tay (2026-08-09).
        attackFor(world, Duration.ofMillis(1200));
        assertThat(sink.characterDefeated.stream()
                        .filter(d -> d.defeatedCharacterId() == 2L)
                        .count())
                .isEqualTo(1);
    }

    @Test
    @Timeout(10)
    void shouldRespawnCharacter_whenKilledInMainWorld() {
        // World CHÍNH (spawnOverworldContent=true, field `world` mặc định) — hồi sinh tại chỗ ngay
        // sau onCharacterDefeated (WorldFacade#respawnCharacter). Cùng cách hạ gục tức thời như
        // shouldFireCharacterDefeated_whenCharacterKillsAnotherCharacter ở trên.
        world.start();
        world.attachCharacter(1L, 0f, 0f, DEFAULT_STATS);
        world.attachCharacter(2L, 1f, 0f, DEFAULT_STATS);
        awaitTrue(() -> sink.spawned.size() >= 2);

        world.applyEquipmentBonus(1L, new EquipmentBonusSnapshot(1000, 0, 0, 0));

        targetAndAttackUntil(world, 2L, () -> sink.characterRespawned.stream().anyMatch(r -> r.characterId() == 2L));
        CharacterRespawned respawn = sink.characterRespawned.stream()
                .filter(r -> r.characterId() == 2L)
                .findFirst()
                .orElseThrow();
        assertThat(respawn.currentHp()).isEqualTo(CombatConstants.CHARACTER_MAX_HP);
        assertThat(respawn.maxHp()).isEqualTo(CombatConstants.CHARACTER_MAX_HP);
        assertThat(respawn.currentChi()).isEqualTo(SkillConstants.CHARACTER_MAX_CHI);
        assertThat(respawn.maxChi()).isEqualTo(SkillConstants.CHARACTER_MAX_CHI);

        // Đánh lại 2L sau khi hồi sinh — HP đòn đầu phải bắt đầu lại từ MAX (đã reset thật trên
        // HealthComponent, không chỉ báo sự kiện suông).
        targetAndAttackUntil(world, 2L, () -> sink.attackResults.stream()
                .anyMatch(a -> a.defender() == 2L && a.hp() < CombatConstants.CHARACTER_MAX_HP));
    }

    @Test
    @Timeout(10)
    void shouldNotRespawnCharacter_whenDefeatedInDungeonOrArenaInstance() {
        // spawnOverworldContent=false (dungeon/arena instance) — WorldFacade#respawnCharacter bị
        // guard, chỉ world CHÍNH mới hồi sinh tại chỗ (Arena có luồng kết thúc trận riêng qua
        // ArenaOutboundSink#onCharacterDefeated, KHÔNG hồi sinh ngay).
        RecordingSink dungeonSink = new RecordingSink();
        WorldFacade dungeonWorld = new WorldFacade(dungeonSink, false);
        try {
            dungeonWorld.start();
            dungeonWorld.attachCharacter(1L, 0f, 0f, DEFAULT_STATS);
            dungeonWorld.attachCharacter(2L, 1f, 0f, DEFAULT_STATS);
            awaitTrue(() -> dungeonSink.spawned.size() >= 2);

            dungeonWorld.applyEquipmentBonus(1L, new EquipmentBonusSnapshot(1000, 0, 0, 0));

            targetAndAttackUntil(dungeonWorld, 2L, () -> dungeonSink.characterDefeated.stream()
                    .anyMatch(d -> d.defeatedCharacterId() == 2L));
            sleepQuietly(500);
            assertThat(dungeonSink.characterRespawned).isEmpty();
        } finally {
            dungeonWorld.stop();
        }
    }

    @Test
    @Timeout(15)
    void shouldHealAlly_whenHealSkillCast() {
        // Thủy "hồi phục" THẬT (GDD §4) — đứng gần quái bù nhìn để bị đánh trước (HP tụt dưới max
        // mới đo được hiệu quả heal), rồi tự cast thuy_hoi_phuc nhắm CHÍNH MÌNH (ALLY, GDD cho
        // phép heal bản thân).
        world.start();
        world.attachCharacter(1L, 5f, 9f, THUY_STATS); // trong tầm aggro quái bù nhìn (xem shouldChaseAndAttackBack)
        awaitTrue(() -> sink.attackResults.stream()
                .anyMatch(a -> a.defender() == 1L && !a.miss() && a.hp() < CombatConstants.CHARACTER_MAX_HP));

        world.useSkill(1L, "thuy_hoi_phuc", 1L);

        awaitTrue(() -> sink.skillResults.stream()
                .anyMatch(r -> "thuy_hoi_phuc".equals(r.skillCode())
                        && r.effectKind() == SkillEffectKind.HEAL
                        && r.damage() > 0));
    }

    @Test
    @Timeout(15)
    void shouldAbsorbDamageWithShield_beforeReducingHp() {
        // Thổ "khiên" THẬT (GDD §4) — cast khiên TRƯỚC khi quái kịp đánh (đứng trong tầm aggro
        // nhưng ngoài tầm đánh lúc cast, xem AiConstants), pool khiên (~60, xem tho_khien_da.js)
        // lớn hơn NHIỀU 1 đòn quái bù nhìn placeholder (~5-8 sau giảm giáp) nên đòn KHÔNG TRƯỢT
        // đầu tiên phải bị hấp thụ HẾT — HP nhân vật còn NGUYÊN ở CHARACTER_MAX_HP.
        world.start();
        world.attachCharacter(1L, 5f, 9f, THO_STATS);
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));

        world.useSkill(1L, "tho_khien_da", 1L);
        awaitTrue(() -> sink.skillResults.stream()
                .anyMatch(r -> "tho_khien_da".equals(r.skillCode()) && r.effectKind() == SkillEffectKind.SHIELD));

        awaitTrue(() -> sink.attackResults.stream().anyMatch(a -> a.defender() == 1L && !a.miss()));
        AttackResult firstHit = sink.attackResults.stream()
                .filter(a -> a.defender() == 1L && !a.miss())
                .findFirst()
                .orElseThrow();
        assertThat(firstHit.hp()).isEqualTo(CombatConstants.CHARACTER_MAX_HP);
    }

    @Test
    @Timeout(15)
    void shouldBlockMonsterAttack_whileStunnedByDebuffSkill() {
        // Lôi "khống chế đơn mục tiêu" (loi_dien_giat, choáng 2.5s — dài hơn hẳn 1 chu kỳ
        // ATTACK_INTERVAL_SECONDS=1s bình thường, đủ để phân biệt "bị chặn vì stun" với "chỉ đang
        // chờ cooldown tự nhiên"). Đứng NGAY trong tầm đánh để quái tự aggro + đánh trả trước.
        world.start();
        world.attachCharacter(1L, 5f, 6f, LOI_STATS);
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));
        long monsterId = sink.monsterSpawned.get(0).monsterId();

        awaitTrue(() -> sink.attackResults.stream().anyMatch(a -> a.attacker() == monsterId));
        long attacksBeforeStun = sink.attackResults.stream()
                .filter(a -> a.attacker() == monsterId)
                .count();

        world.useSkill(1L, "loi_dien_giat", monsterId);
        awaitTrue(() -> sink.skillResults.stream()
                .anyMatch(r -> "loi_dien_giat".equals(r.skillCode()) && r.effectKind() == SkillEffectKind.DEBUFF));

        // > 1 chu kỳ ATTACK_INTERVAL_SECONDS (1s) bình thường NHƯNG < thời lượng choáng (2.5s) —
        // không bị stun thì quái đã kịp đánh thêm ít nhất 1 lần trong khoảng này.
        sleepQuietly(1800);

        long attacksAfterStun = sink.attackResults.stream()
                .filter(a -> a.attacker() == monsterId)
                .count();
        assertThat(attacksAfterStun).isEqualTo(attacksBeforeStun);
    }

    @Test
    @Timeout(15)
    void shouldReduceMoveDistance_whenTargetSlowedByDebuffSkill() {
        // Thủy "khống chế" THẬT (thuy_phong_toa, SLOW -40%) — nhắm nhân vật KHÁC (ENEMY target,
        // đơn giản hoá PvP-style thay vì cần quái di chuyển bằng MoveCommand). Không bị slow: 5
        // m/s (MOVE_SPEED_MPS) × 1s = 5m. Bị slow 40%: 3 m/s × 1s = 3m — ngưỡng 4m đủ phân biệt kể
        // cả dưới jitter tick loop thật.
        world.start();
        world.attachCharacter(1L, 0f, 0f, THUY_STATS);
        world.attachCharacter(2L, 3f, 0f, LOI_STATS); // trong tầm skill Thủy (5m)
        awaitTrue(() -> sink.spawned.size() >= 2);

        world.useSkill(1L, "thuy_phong_toa", 2L);
        awaitTrue(() -> sink.skillResults.stream()
                .anyMatch(r -> "thuy_phong_toa".equals(r.skillCode()) && r.effectKind() == SkillEffectKind.DEBUFF));

        world.enqueueMoveCommand(2L, 1, 1f, 0f);
        sleepQuietly(1000);

        Moved lastMoved = sink.moved.stream()
                .filter(m -> m.target() == 1L && m.moved() == 2L)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(lastMoved.x()).isLessThan(3f + 4f);
    }

    @Test
    @Timeout(15)
    void shouldSilentlyRejectSkill_whenCasterWrongFaction() {
        // Chống dùng skill hệ khác (SkillSystem.tryCast, SECURITY.md) — nhân vật Hỏa gửi skillCode
        // của hệ Thủy, server phải bỏ qua ÂM THẦM, không có SkillResultEvent nào phát sinh.
        world.start();
        world.attachCharacter(1L, 5f, 6f, DEFAULT_STATS); // Hỏa
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));
        long monsterId = sink.monsterSpawned.get(0).monsterId();

        world.useSkill(1L, "thuy_song_du", monsterId);
        sleepQuietly(500);

        assertThat(sink.skillResults.stream().anyMatch(r -> "thuy_song_du".equals(r.skillCode())))
                .isFalse();
    }

    @Test
    @Timeout(15)
    void shouldSilentlyRejectUltimate_whenBelowRequiredLevel() {
        // GDD §5 "Tuyệt Kỹ Lv50" — DEFAULT_STATS/THUY_STATS ở test này đều level=1, phải bị chặn
        // ÂM THẦM (CharacterLevelComponent < SkillConstants.ULTIMATE_REQUIRED_LEVEL).
        world.start();
        world.attachCharacter(1L, 5f, 6f, THUY_STATS);
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));
        long monsterId = sink.monsterSpawned.get(0).monsterId();

        world.useSkill(1L, "thuy_thuy_trieu", monsterId);
        sleepQuietly(500);

        assertThat(sink.skillResults.stream().anyMatch(r -> "thuy_thuy_trieu".equals(r.skillCode())))
                .isFalse();
    }

    @Test
    void shouldOneShotMonster_whenEquipmentBonusMakesAttackLethal() {
        // GAMEPLAY.md §2/§7 "tính lại chỉ số dẫn xuất khi đổi trang bị". DEFAULT_STATS (10,10,10,10)
        // → ATK=30, không đủ 1 đòn hạ quái 50 HP (shouldDealDamageAndEventuallyKillMonster đã
        // chứng minh cần "eventually", nhiều đòn). Cộng bonus lớn (200) → đòn đầu tiên trúng phải
        // hạ gục ngay — verify applyEquipmentBonus thật sự thay đổi combat, không chỉ set field suông.
        world.start();
        world.attachCharacter(1L, 5f, 6f, DEFAULT_STATS);
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));
        long monsterId = sink.monsterSpawned.get(0).monsterId();

        world.applyEquipmentBonus(1L, new EquipmentBonusSnapshot(200, 0, 0, 0));

        // HIT% placeholder ~91% — bấm đánh tới khi có đòn trúng, không giả định đòn đầu chắc trúng.
        targetAndAttackUntil(world, monsterId, () -> sink.attackResults.stream()
                .anyMatch(a -> a.defender() == monsterId && !a.miss() && a.died()));
    }

    @Test
    @Timeout(10)
    void shouldRaiseMaxHp_whenEquipmentGrantsBonusHp() {
        // ADR-036: bonus_hp cộng vào MÁU TỐI ĐA trên nền CHARACTER_MAX_HP (placeholder, chủ dự án
        // chấp nhận maxHp = 100 + bonus_hp khi duyệt ADR). Đọc qua RespawnSnapshot vì đó là chỗ
        // DUY NHẤT máu tối đa của nhân vật đi ra ngoài world — cũng chứng minh luôn hồi sinh dùng
        // máu tối đa MỚI chứ không phải hằng số.
        world.start();
        world.attachCharacter(1L, 0f, 0f, DEFAULT_STATS);
        world.attachCharacter(2L, 1f, 0f, DEFAULT_STATS);
        awaitTrue(() -> sink.spawned.size() >= 2);

        world.applyEquipmentBonus(2L, new EquipmentBonusSnapshot(0, 0, 0, 58));
        world.applyEquipmentBonus(1L, new EquipmentBonusSnapshot(1000, 0, 0, 0)); // đủ để hạ gục ngay

        targetAndAttackUntil(world, 2L, () -> sink.characterRespawned.stream().anyMatch(r -> r.characterId() == 2L));
        CharacterRespawned respawn = sink.characterRespawned.stream()
                .filter(r -> r.characterId() == 2L)
                .findFirst()
                .orElseThrow();
        assertThat(respawn.maxHp()).isEqualTo(CombatConstants.CHARACTER_MAX_HP + 58);
        assertThat(respawn.currentHp()).isEqualTo(CombatConstants.CHARACTER_MAX_HP + 58);
    }

    @Test
    @Timeout(10)
    void shouldReduceIncomingDamage_whenEquipmentGrantsBonusDef() {
        // ADR-036: giáp cộng DEF THẬT (trước ADR này không có đường nào để trang bị chạm DEF —
        // V21 phải cho áo giáp cộng ATK). mitigation = 100/(100+DEF): DEF nền 20 ⇒ nhận ~83%
        // damage; +200 DEF ⇒ DEF 220 ⇒ chỉ còn ~31%.
        //
        // MỘT người đánh (1L), HAI mục tiêu giống hệt nhau (2L có giáp, 3L không) — chênh lệch chỉ
        // có thể do DEF. Không so với một con số cứng vì damage còn nhánh crit/miss ngẫu nhiên.
        world.start();
        world.attachCharacter(1L, 0f, 0f, DEFAULT_STATS);
        world.attachCharacter(2L, 1f, 0f, DEFAULT_STATS);
        world.attachCharacter(3L, 2f, 0f, DEFAULT_STATS);
        awaitTrue(() -> sink.spawned.size() >= 3);

        world.applyEquipmentBonus(2L, new EquipmentBonusSnapshot(0, 200, 0, 0));

        targetAndAttackUntil(world, 2L, () -> firstCleanHitDamageFrom1L(2L) > 0);
        targetAndAttackUntil(world, 3L, () -> firstCleanHitDamageFrom1L(3L) > 0);

        assertThat(firstCleanHitDamageFrom1L(2L)).isLessThan(firstCleanHitDamageFrom1L(3L));
    }

    /**
     * Damage của đòn ĐẦU TIÊN trúng gọn (không miss, không crit) mà {@code defenderId} nhận TỪ 1L.
     *
     * <p>Lọc theo NGƯỜI TẤN CÔNG là bắt buộc, không phải cho chắc: world chính có quái tự đi tìm
     * người đánh (AiSystem), nên đòn đầu tiên trúng một nhân vật rất có thể là của QUÁI
     * (MONSTER_STATS atk=8) chứ không phải của 1L (atk 30) — chính cái đó làm bản đầu của test này
     * đỏ với hai con số không so sánh được với nhau.
     */
    private int firstCleanHitDamageFrom1L(long defenderId) {
        return sink.attackResults.stream()
                .filter(a -> a.attacker() == 1L && a.defender() == defenderId && !a.miss() && !a.crit())
                .mapToInt(AttackResult::damage)
                .findFirst()
                .orElse(0);
    }

    @Test
    void shouldChaseAndAttackBack_whenCharacterEntersAggroRange() {
        // AI.md §3: quái Idle -> Chase khi phát hiện người chơi (aggro theo LOÀI kể từ ADR-039;
        // fixture `trainingGround()` dùng bộ mặc định 6m), rồi -> Attack khi vào tầm đánh. KHÔNG
        // gọi world.setTarget — nhân vật đứng yên, hoàn toàn thụ động, để chứng minh CHÍNH quái
        // chủ động đuổi + đánh, không phải do người chơi tấn công trước.
        //
        // ⚠️ Test này kiểm CƠ CHẾ FSM, không kiểm con hình nộm: `training_dummy` thật trong DB nay
        // là loài THỤ ĐỘNG (ADR-039 D4) — xem shouldNeverAggro_whenSpeciesHasZeroAggroRadius.
        world.start();
        world.attachCharacter(1L, 5f, 9f, DEFAULT_STATS); // cách quái (5,5) 4m — trong aggro, ngoài tầm đánh
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));
        long monsterId = sink.monsterSpawned.get(0).monsterId();

        awaitTrue(() -> sink.monsterMoved.stream().anyMatch(m -> m.monsterId() == monsterId));
        awaitTrue(() -> sink.attackResults.stream().anyMatch(a -> a.attacker() == monsterId));
    }

    @Test
    void shouldBroadcastAreaChatMessage_toNearbyCharacterAndBackToSender() {
        world.start();
        world.attachCharacter(1L, 0f, 0f, DEFAULT_STATS);
        world.attachCharacter(2L, 0f, 0f, DEFAULT_STATS); // cùng vị trí — chắc chắn trong AOI nhau
        awaitTrue(() -> sink.spawned.size() >= 2);

        world.enqueueAreaChatMessage(1L, "Naruto", "xin chào khu vực");

        awaitTrue(() -> sink.areaChatMessages.stream()
                .anyMatch(m -> m.target() == 2L
                        && m.senderCharacterId() == 1L
                        && m.body().equals("xin chào khu vực")));
        awaitTrue(() -> sink.areaChatMessages.stream()
                .anyMatch(m -> m.target() == 1L && m.senderCharacterId() == 1L)); // người gửi tự thấy tin của mình
    }

    @Test
    void shouldNeverAggro_whenSpeciesHasZeroAggroRadius() {
        // ADR-039 D4 — `training_dummy` THẬT trong monster_template nay có aggro_radius = 0, tức
        // cây cọc luyện công thôi rời cọc đuổi theo người chơi (lỗi sống từ Phase 4, khi thế giới
        // có ĐÚNG MỘT loài quái nên "quái" và "hình nộm" là cùng một thứ).
        //
        // Đứng ĐÈ LÊN con quái (x=8): khoảng cách 0 — trong tầm đánh, trong mọi tầm aggro tưởng
        // tượng được. Nếu nó còn gây hấn thì đây là chỗ chắc chắn bắt được, kể cả khi nhánh "thụ
        // động" bị viết hớ thành phép so sánh `d <= 0`.
        RecordingSink passiveSink = new RecordingSink();
        WorldFacade passiveWorld =
                new WorldFacade(passiveSink, true, TickMetricsPort.NO_OP, TestMonsterCatalogs.passiveTrainingGround());
        try {
            passiveWorld.start();
            passiveWorld.attachCharacter(1L, 8f, 0f, DEFAULT_STATS);
            awaitTrue(() -> passiveSink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));

            sleepQuietly(500); // đủ lâu để AiSystem chạy nhiều chục tick nếu (sai) có aggro

            assertThat(passiveSink.attackResults.stream().anyMatch(a -> a.defender() == 1L))
                    .isFalse();
            // Và nó cũng KHÔNG lang thang: patrol_radius = 0 ⇒ điểm tuần tra TRÙNG điểm spawn, nên
            // mọi vị trí từng phát ra vẫn là đúng x=8 (assert theo GIÁ TRỊ chứ không phải "không có
            // sự kiện nào" — AoI có thể phát một sự kiện lúc đồng bộ lần đầu).
            assertThat(passiveSink.monsterMoved)
                    .allSatisfy(m -> assertThat(m.x()).isEqualTo(8f));
        } finally {
            passiveWorld.stop();
        }
    }

    @Test
    void shouldNotAggro_whenCharacterOutsideAggroRange() {
        world.start();
        // ADR-033: KHÔNG tách được bằng trục Y nữa (attach ép y về đường chơi — xem
        // WorldFacade.CHARACTER_PLAY_LINE_Y), phải tách bằng trục X. Quái gần nhất ở x=8
        // (fixture `trainingGround()`), nên đứng ở x=0 cách 8m: NGOÀI tầm aggro của fixture đó
        // (6m) nhưng TRONG WorldConstants.AOI_RADIUS_METERS (15m)
        // — verify thấy quái trên màn hình mà vẫn không bị đuổi.
        world.attachCharacter(1L, 0f, 0f, DEFAULT_STATS);
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));

        sleepQuietly(500); // đủ lâu để AiSystem chạy nhiều tick nếu (sai) có aggro

        // defender() = ai BỊ đánh (không phải target() — đó là người NHẬN broadcast, có thể là
        // chính người quan sát trận đánh của người khác). Không quái nào đánh nhân vật 1 cả.
        assertThat(sink.attackResults.stream().anyMatch(a -> a.defender() == 1L))
                .isFalse();
    }

    /**
     * Nhắm {@code targetEntityId} rồi BẤM ĐÁNH liên tục tới khi {@code condition} đúng.
     *
     * <p>Thay cho {@code setTarget} + {@link #awaitTrue} thuần kể từ 2026-08-09: nhân vật KHÔNG tự
     * đánh nữa (chủ dự án quyết sau playtest, xem {@code CombatSystem}), {@code setTarget} giờ chỉ
     * còn nghĩa "nhắm ai". Luôn dùng nhân vật {@code 1L} — mọi test combat ở file này đều để
     * {@code 1L} làm bên tấn công.
     */
    private static void targetAndAttackUntil(WorldFacade world, long targetEntityId, Supplier<Boolean> condition) {
        targetAndAttackUntil(world, targetEntityId, condition, Duration.ofSeconds(5));
    }

    private static void targetAndAttackUntil(
            WorldFacade world, long targetEntityId, Supplier<Boolean> condition, Duration timeout) {
        world.setTarget(1L, targetEntityId);
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            // Bấm dày hơn cooldown thật (1 đòn/giây) — thừa lệnh là no-op, nhưng thưa quá thì test
            // dài lê thê chờ đúng nhịp. Cùng nhịp poll 10ms của awaitTrue.
            world.attack(1L);
            sleepQuietly(10);
        }
        throw new AssertionError("Điều kiện không đúng sau " + timeout + " vừa chờ vừa bấm đánh");
    }

    /** Bấm đánh liên tục ĐỦ {@code duration} — dùng để xác nhận một điều KHÔNG xảy ra dù vẫn bấm. */
    private static void attackFor(WorldFacade world, Duration duration) {
        Instant deadline = Instant.now().plus(duration);
        while (Instant.now().isBefore(deadline)) {
            world.attack(1L);
            sleepQuietly(10);
        }
    }

    /** Poll với timeout thay vì {@code Thread.sleep} cố định — tick loop chạy real-time 60Hz. */
    private static void awaitTrue(Supplier<Boolean> condition) {
        awaitTrue(condition, Duration.ofSeconds(5));
    }

    private static void awaitTrue(Supplier<Boolean> condition, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted khi chờ điều kiện tick loop", e);
            }
        }
        throw new AssertionError("Điều kiện không đúng sau " + timeout + " chờ tick loop");
    }

    /** Chờ ĐỦ {@code duration} (không dừng sớm) — dùng để xác nhận 1 điều KHÔNG xảy ra, khác {@link #awaitTrue}. */
    private static void sleepUninterruptibly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted khi chờ xác nhận không có spawn mới", e);
        }
    }

    /** Cast fireball tối đa vài lần, mỗi lần chờ hết cooldown, tới khi trúng 1 lần hoặc hết lượt thử. */
    private void castUntilHit(long monsterId) {
        castUntilHit(monsterId, "fireball", SkillConstants.FIREBALL_COOLDOWN_SECONDS);
    }

    /** Cast 1 skill bất kỳ tối đa vài lần, mỗi lần chờ hết cooldown, tới khi trúng 1 lần hoặc hết lượt thử. */
    private void castUntilHit(long monsterId, String skillCode, float cooldownSeconds) {
        long waitMillis = (long) (cooldownSeconds * 1000) + 200; // đệm jitter tick loop thật
        for (int attempt = 0; attempt < 3; attempt++) {
            world.useSkill(1L, skillCode, monsterId);
            sleepQuietly(waitMillis); // > cooldown skill — đợi xong trước lần thử tiếp
            if (sink.skillResults.stream()
                    .anyMatch(r -> r.defender() == monsterId && !r.miss() && skillCode.equals(r.skillCode()))) {
                return;
            }
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted khi chờ tick loop chạy thật", e);
        }
    }
}
