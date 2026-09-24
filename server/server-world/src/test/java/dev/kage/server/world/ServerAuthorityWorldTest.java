package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Server Authority (/CLAUDE.md §5) ở hai chỗ client điều khiển trực tiếp một thực thể: hướng di
 * chuyển và nút đánh/skill.
 *
 * <p><b>Hướng di chuyển</b>: {@code clampDirection} chỉ chuẩn hoá khi độ dài bình phương {@code > 1}.
 * {@code NaN > 1} là {@code false}, nên {@code NaN} đi thẳng vào vận tốc rồi vào vị trí — một gói
 * {@code MoveCommand} là nhân vật mang toạ độ {@code NaN} vĩnh viễn (và được lưu xuống DB lúc đăng
 * xuất). {@code Infinity} hay {@code 1e30} cũng thành {@code NaN} ({@code Inf/Inf}).
 *
 * <p><b>Người đã chết</b>: world chính hồi sinh ngay, nhưng World hầm ngục ({@code
 * spawnOverworldContent = false}) để nhân vật nằm 0 máu. Cả {@code CombatSystem} lẫn
 * {@code SkillSystem} chỉ kiểm máu của MỤC TIÊU, không kiểm của người ra đòn.
 *
 * <p>Lớp riêng thay vì thêm vào {@link WorldFacadeTest}: lớp đó đã quá trần CyclomaticComplexity
 * cả lớp của PMD (xem javadoc {@code SafeZoneShieldTest}).
 */
@Timeout(20)
class ServerAuthorityWorldTest {

    private static final CharacterBaseStats DEFAULT_STATS = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 1);
    // Một đòn là chết — đủ để dựng "người đã chết" mà không phải đánh qua lại hàng chục giây.
    private static final CharacterBaseStats ONE_SHOT_STATS = new CharacterBaseStats(5000, 10, 10, 10, Element.HOA, 1);
    private static final float START_X = 5f;

    private record Accepted(long characterId, int sequenceId, float x) {}

    private record Hit(long attacker, long target) {}

    private final RecordingSink sink = new RecordingSink();
    private WorldFacade world;

    @AfterEach
    void stopWorld() {
        if (world != null) {
            world.stop();
        }
    }

    @Test
    void shouldIgnoreNanDirection_insteadOfPoisoningThePosition() {
        assertDirectionKeepsPositionFinite(Float.NaN, 0f);
    }

    @Test
    void shouldIgnoreInfiniteDirection_insteadOfPoisoningThePosition() {
        assertDirectionKeepsPositionFinite(Float.POSITIVE_INFINITY, 0f);
    }

    @Test
    void shouldStillMoveAtNormalSpeed_whenDirectionIsHugeButFinite() {
        world = new WorldFacade(sink, false);
        world.start();
        world.attachCharacter(1L, START_X, 0f, DEFAULT_STATS);

        world.enqueueMoveCommand(1L, 1, 1e30f, 0f);
        sleepQuietly(300);
        world.enqueueMoveCommand(1L, 2, 0f, 0f);

        awaitTrue(() -> ack(2) != null);
        // 1e30 là hướng "sang phải" hợp lệ về mặt ý nghĩa — phải đi được, và đi đúng tốc độ server.
        assertThat(ack(2).x()).isFinite().isGreaterThan(START_X);
        assertThat(ack(2).x() - START_X).isLessThanOrEqualTo(WorldConstants.MOVE_SPEED_MPS * 0.6f);
    }

    @Test
    void shouldNotLetADeadCharacterAttackOrCast() {
        world = new WorldFacade(sink, false);
        // Kịch bản người chơi hạ người chơi — chỉ dựng được trong World cho PvP (như Arena).
        world.allowPlayerVersusPlayer();
        world.start();
        world.attachCharacter(1L, 0f, 0f, ONE_SHOT_STATS);
        world.attachCharacter(2L, 0f, 0f, DEFAULT_STATS);
        sleepQuietly(100);
        world.setTarget(1L, 2L);
        world.attack(1L);
        awaitTrue(() -> sink.defeated.contains(2L));

        world.setTarget(2L, 1L);
        world.attack(2L);
        world.useSkill(2L, SkillConstants.FIREBALL_SKILL_CODE, 1L);
        sleepQuietly(500);

        assertThat(sink.hits).noneMatch(hit -> hit.attacker() == 2L);
    }

    private void assertDirectionKeepsPositionFinite(float dirX, float dirY) {
        world = new WorldFacade(sink, false);
        world.start();
        world.attachCharacter(1L, START_X, 0f, DEFAULT_STATS);

        world.enqueueMoveCommand(1L, 1, dirX, dirY);
        sleepQuietly(300);
        world.enqueueMoveCommand(1L, 2, 0f, 0f);

        awaitTrue(() -> ack(2) != null);
        assertThat(ack(2).x()).isEqualTo(START_X);
    }

    private Accepted ack(int sequenceId) {
        return sink.accepted.stream()
                .filter(a -> a.sequenceId() == sequenceId)
                .findFirst()
                .orElse(null);
    }

    private static void awaitTrue(BooleanSupplier condition) {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Điều kiện không thành sau 10 giây");
            }
            sleepQuietly(10);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("bị ngắt khi chờ", e);
        }
    }

    /** Sink tối thiểu — chỉ ghi ack di chuyển, đòn đánh/skill và lượt bị hạ gục. */
    private static final class RecordingSink implements WorldOutboundSink {
        final List<Accepted> accepted = new CopyOnWriteArrayList<>();
        final List<Hit> hits = new CopyOnWriteArrayList<>();
        final List<Long> defeated = new CopyOnWriteArrayList<>();

        @Override
        public void onMoveAccepted(WorldOutboundSink.MoveAcceptedSnapshot snapshot) {
            accepted.add(new Accepted(snapshot.characterId(), snapshot.sequenceId(), snapshot.x()));
        }

        @Override
        public void onAttackResult(long targetCharacterId, WorldOutboundSink.AttackOutcome outcome) {
            hits.add(new Hit(outcome.attackerEntityId(), outcome.targetEntityId()));
        }

        @Override
        public void onSkillResult(long targetCharacterId, WorldOutboundSink.SkillOutcome outcome) {
            hits.add(new Hit(outcome.attackerEntityId(), outcome.targetEntityId()));
        }

        @Override
        public void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {
            defeated.add(defeatedCharacterId);
        }

        @Override
        public void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y) {}

        @Override
        public void onCharacterDespawned(long targetCharacterId, long despawnedCharacterId) {}

        @Override
        public void onCharacterMoved(long targetCharacterId, long movedCharacterId, float x, float y) {}

        @Override
        public void onMonsterSpawned(long targetCharacterId, WorldOutboundSink.MonsterSnapshot monster) {}

        @Override
        public void onMonsterDespawned(long targetCharacterId, long monsterId) {}

        @Override
        public void onMonsterMoved(long targetCharacterId, long monsterId, float x, float y) {}

        @Override
        public void onMonsterKilled(long killerCharacterId, String monsterCode, long expReward) {}

        @Override
        public void onNpcSpawned(long targetCharacterId, WorldOutboundSink.NpcSnapshot npc) {}

        @Override
        public void onNpcDespawned(long targetCharacterId, long npcId) {}

        @Override
        public void onAreaChatMessage(long targetCharacterId, long senderCharacterId, String senderName, String body) {}

        @Override
        public void onCharacterAppearanceChanged(long targetCharacterId, long changedCharacterId) {}

        @Override
        public void onHealthChanged(long targetCharacterId, WorldOutboundSink.HealthSnapshot health) {}

        @Override
        public void onCharacterRespawned(WorldOutboundSink.RespawnSnapshot respawn) {}
    }
}
