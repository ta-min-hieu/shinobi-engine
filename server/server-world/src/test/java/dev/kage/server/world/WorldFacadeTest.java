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
 * Verify vòng lặp tick thật (60Hz, thread riêng) của {@link WorldFacade} — không mock tick loop
 * vì đây chính là hành vi cần test (ROADMAP Phase 3: "2 client thấy nhau di chuyển"; Phase 4 lát
 * cắt 1: "đánh quái, quái chết").
 */
@Timeout(10)
class WorldFacadeTest {

    // STR/AGI/INT/VIT giả định cho test — không verify số liệu chỉ số cụ thể ở file này (đã có
    // PostgresCharacterAdapterIntegrationTest cho việc đó), chỉ cần attach thành công.
    private static final CharacterBaseStats DEFAULT_STATS = new CharacterBaseStats(10, 10, 10, 10);

    private record Spawned(long target, long spawned, float x, float y) {
    }

    private record Moved(long target, long moved, float x, float y) {
    }

    private record Despawned(long target, long despawned) {
    }

    private record Accepted(long characterId, int sequenceId, float x, float y) {
    }

    private record MonsterSpawned(long target, long monsterId, String code, float x, float y, int hp, int maxHp) {
    }

    private record MonsterDespawned(long target, long monsterId) {
    }

    private record MonsterMoved(long target, long monsterId, float x, float y) {
    }

    private record MonsterKilled(long killerCharacterId, String monsterCode) {
    }

    private record AttackResult(
            long target, long attacker, long defender, int damage, boolean crit, boolean miss, int hp,
            boolean died) {
    }

    private record SkillResult(
            long target, String skillCode, long attacker, long defender, int damage, boolean crit, boolean miss,
            int hp, boolean died) {
    }

    private static final class RecordingSink implements WorldOutboundSink {
        final List<Spawned> spawned = new CopyOnWriteArrayList<>();
        final List<Moved> moved = new CopyOnWriteArrayList<>();
        final List<Despawned> despawned = new CopyOnWriteArrayList<>();
        final List<Accepted> accepted = new CopyOnWriteArrayList<>();
        final List<MonsterSpawned> monsterSpawned = new CopyOnWriteArrayList<>();
        final List<MonsterDespawned> monsterDespawned = new CopyOnWriteArrayList<>();
        final List<MonsterMoved> monsterMoved = new CopyOnWriteArrayList<>();
        final List<MonsterKilled> monsterKilled = new CopyOnWriteArrayList<>();
        final List<AttackResult> attackResults = new CopyOnWriteArrayList<>();
        final List<SkillResult> skillResults = new CopyOnWriteArrayList<>();

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
                    targetCharacterId, monster.monsterId(), monster.monsterCode(), monster.x(), monster.y(),
                    monster.currentHp(), monster.maxHp()));
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
        public void onAttackResult(long targetCharacterId, WorldOutboundSink.AttackOutcome outcome) {
            attackResults.add(new AttackResult(
                    targetCharacterId, outcome.attackerEntityId(), outcome.targetEntityId(), outcome.damage(),
                    outcome.crit(), outcome.miss(), outcome.targetCurrentHp(), outcome.targetDied()));
        }

        @Override
        public void onSkillResult(long targetCharacterId, WorldOutboundSink.SkillOutcome outcome) {
            skillResults.add(new SkillResult(
                    targetCharacterId, outcome.skillCode(), outcome.attackerEntityId(), outcome.targetEntityId(),
                    outcome.damage(), outcome.crit(), outcome.miss(), outcome.targetCurrentHp(),
                    outcome.targetDied()));
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
        var firstMoved = sink.moved.stream()
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

        awaitTrue(
                () -> sink.monsterSpawned.stream()
                        .anyMatch(m -> m.target() == 1L && m.code().equals("training_dummy")));
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
        assertThat(sink.attackResults.stream().anyMatch(a -> a.defender() == monsterId && a.died())).isTrue();
        // Server-world báo ĐÚNG người hạ gục — gateway dùng cái này để roll rơi đồ (GAMEPLAY.md §5).
        assertThat(sink.monsterKilled)
                .containsExactly(new MonsterKilled(1L, "training_dummy"));
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
                () -> sink.monsterSpawned.stream()
                        .anyMatch(m -> m.target() == 1L && m.monsterId() != firstMonsterId),
                Duration.ofSeconds(15));
        var respawned = sink.monsterSpawned.stream()
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

        long attacksLandedOnMonster =
                sink.attackResults.stream().filter(a -> a.defender() == monsterId && !a.miss()).count();
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

        var hit = sink.skillResults.stream()
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

        long castsLanded = sink.skillResults.stream().filter(r -> r.defender() == monsterId).count();
        assertThat(castsLanded).isLessThanOrEqualTo(1);
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
        awaitTrue(() -> sink.attackResults.stream()
                .anyMatch(a -> a.defender() == monsterId && !a.miss() && a.died()));
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
        assertThat(sink.attackResults.stream().anyMatch(a -> a.defender() == 1L)).isFalse();
    }

    /** Poll với timeout thay vì {@code Thread.sleep} cố định — tick loop chạy real-time 60Hz. */
    private static void awaitTrue(Supplier<Boolean> condition) {
        awaitTrue(condition, Duration.ofSeconds(5));
    }

    private static void awaitTrue(Supplier<Boolean> condition, Duration timeout) {
        var deadline = Instant.now().plus(timeout);
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
