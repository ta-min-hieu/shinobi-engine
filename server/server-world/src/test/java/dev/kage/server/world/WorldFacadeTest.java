package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

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

    private record Accepted(long characterId, int sequenceId, float x, float y) {}

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

        @Override
        public void onMoveAccepted(long characterId, int sequenceId, float x, float y) {
            accepted.add(new Accepted(characterId, sequenceId, x, y));
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
        public void onMonsterKilled(long killerCharacterId, String monsterCode) {
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
    }

    private final RecordingSink sink = new RecordingSink();
    private final WorldFacade world = new WorldFacade(sink);

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

        world.attachCharacter(1L, -5f, -6f, DEFAULT_STATS); // gần NPC mẫu (spawn cố định (-5,-5))

        awaitTrue(() -> sink.npcSpawned.stream()
                .anyMatch(n -> n.target() == 1L && n.code().equals("town_elder")));
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

            dungeonWorld.setTarget(1L, bossId);

            awaitTrue(
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

            dungeonWorld.applyEquipmentBonus(1L, 1000); // đủ lớn để hạ gục BOSS_MAX_HP (320) ngay đòn đầu.
            dungeonWorld.setTarget(1L, bossId);

            awaitTrue(() -> dungeonSink.monsterDespawned.stream().anyMatch(d -> d.monsterId() == bossId));

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
    void shouldDealDamageAndEventuallyKillMonster_whenTargetSet() {
        world.start();
        world.attachCharacter(1L, 5f, 6f, DEFAULT_STATS); // cách quái (5,5) đúng 1m — trong tầm đánh (2m)
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));
        long monsterId = sink.monsterSpawned.get(0).monsterId();

        world.setTarget(1L, monsterId);

        awaitTrue(() -> sink.monsterDespawned.stream().anyMatch(d -> d.target() == 1L && d.monsterId() == monsterId));
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

        world.setTarget(1L, firstMonsterId);
        awaitTrue(() -> sink.monsterDespawned.stream().anyMatch(d -> d.monsterId() == firstMonsterId));

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
    void shouldNotBypassAttackCooldown_bySpammingSetTargetCommand() {
        // Chống cooldown hack BẰNG THIẾT KẾ (SECURITY.md §8) — client chỉ gửi ID mục tiêu, KHÔNG
        // gửi cooldown/thời điểm đánh; gửi SetTargetCommand dồn dập không được rút ngắn khoảng
        // cách giữa các đòn đánh thật (ATTACK_INTERVAL_SECONDS server giữ tuyệt đối).
        world.start();
        world.attachCharacter(1L, 5f, 6f, DEFAULT_STATS);
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));
        long monsterId = sink.monsterSpawned.get(0).monsterId();

        for (int i = 0; i < 500; i++) {
            world.setTarget(1L, monsterId); // spam — không phải input hợp lệ của UI thật nào
        }

        sleepQuietly(1200); // ~1.2s thật — ATK_SPEED placeholder = 1 đòn/giây (CombatConstants)

        long attacksLandedOnMonster = sink.attackResults.stream()
                .filter(a -> a.defender() == monsterId && !a.miss())
                .count();
        // Đúng thiết kế: tối đa ~1-2 đòn trong 1.2s dù gửi 500 lệnh — KHÔNG phải 500 đòn (sẽ xảy
        // ra nếu SetTargetCommand vô tình reset/rút ngắn cooldown).
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

        world.setTarget(1L, 2L);
        awaitTrue(() -> sink.attackResults.stream().anyMatch(a -> a.defender() == 2L && !a.miss()));
        int damageVsPhong = sink.attackResults.stream()
                .filter(a -> a.defender() == 2L && !a.miss())
                .findFirst()
                .orElseThrow()
                .damage();

        world.setTarget(1L, 3L);
        awaitTrue(() -> sink.attackResults.stream().anyMatch(a -> a.defender() == 3L && !a.miss()));
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

        world.applyEquipmentBonus(1L, 1000); // đủ lớn để hạ gục CHARACTER_MAX_HP ngay đòn đầu.
        world.setTarget(1L, 2L);

        awaitTrue(() -> sink.characterDefeated.stream().anyMatch(d -> d.defeatedCharacterId() == 2L));
        CharacterDefeated defeat = sink.characterDefeated.stream()
                .filter(d -> d.defeatedCharacterId() == 2L)
                .findFirst()
                .orElseThrow();
        assertThat(defeat.killerEntityId()).isEqualTo(1L);

        // Đòn hạ gục chỉ bắn ĐÚNG 1 lần (CombatSystem tự bỏ chọn target sau khi thấy mục tiêu đã
        // chết — xem javadoc WorldFacade#handleCombatResults) dù nhân vật vẫn còn attach trong World.
        sleepQuietly(1200);
        assertThat(sink.characterDefeated.stream()
                        .filter(d -> d.defeatedCharacterId() == 2L)
                        .count())
                .isEqualTo(1);
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

        world.applyEquipmentBonus(1L, 200);
        world.setTarget(1L, monsterId);

        // HIT% placeholder ~91% — chờ đòn trúng đầu tiên, không giả định đòn đầu chắc chắn trúng.
        awaitTrue(() -> sink.attackResults.stream().anyMatch(a -> a.defender() == monsterId && !a.miss() && a.died()));
    }

    @Test
    void shouldChaseAndAttackBack_whenCharacterEntersAggroRange() {
        // AI.md §3: quái Idle -> Chase khi phát hiện người chơi (AiConstants.AGGRO_RADIUS_METERS
        // = 6m), rồi -> Attack khi vào tầm đánh. KHÔNG gọi world.setTarget — nhân vật đứng yên,
        // hoàn toàn thụ động, để chứng minh CHÍNH quái chủ động đuổi + đánh, không phải do người
        // chơi tấn công trước.
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
    void shouldNotAggro_whenCharacterOutsideAggroRange() {
        world.start();
        // Cách quái (5,5) đúng 20m — ngoài AiConstants.AGGRO_RADIUS_METERS (6m) NHƯNG vẫn trong
        // WorldConstants.AOI_RADIUS_METERS (15f)... KHÔNG, 20m cũng ngoài AOI nên sẽ không thấy
        // quái spawn — dùng khoảng cách 10m: ngoài aggro (6m), trong AOI (15m) để verify KHÔNG bị
        // đuổi dù thấy quái trên màn hình.
        world.attachCharacter(1L, 5f, 15f, DEFAULT_STATS);
        awaitTrue(() -> sink.monsterSpawned.stream().anyMatch(m -> m.target() == 1L));

        sleepQuietly(500); // đủ lâu để AiSystem chạy nhiều tick nếu (sai) có aggro

        // defender() = ai BỊ đánh (không phải target() — đó là người NHẬN broadcast, có thể là
        // chính người quan sát trận đánh của người khác). Không quái nào đánh nhân vật 1 cả.
        assertThat(sink.attackResults.stream().anyMatch(a -> a.defender() == 1L))
                .isFalse();
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
        for (int attempt = 0; attempt < 3; attempt++) {
            world.useSkill(1L, "fireball", monsterId);
            sleepQuietly(3200); // > FIREBALL_COOLDOWN_SECONDS (3s) — đợi cooldown xong trước lần thử tiếp
            if (sink.skillResults.stream().anyMatch(r -> r.defender() == monsterId && !r.miss())) {
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
