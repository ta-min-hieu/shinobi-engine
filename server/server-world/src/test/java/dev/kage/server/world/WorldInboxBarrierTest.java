package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Mốc chặn hàng đợi ({@link WorldFacade#inboxBarrier()}) — gateway dùng để biết lúc nào world đã xử
 * lý xong MỌI lệnh của một phiên vừa bị đá (quyết định chủ dự án 2026-09-25: một nhân vật chỉ một
 * phiên sống, phiên mới đá phiên cũ).
 *
 * <p>Lý do cần: lệnh di chuyển của phiên cũ còn nằm trong hàng đợi TRƯỚC lệnh detach. Nếu phiên mới
 * đăng ký kết nối của mình trước khi world xử lý chúng, gói {@code MoveResponse} muộn của phiên cũ
 * rơi vào kết nối MỚI — mang {@code sequence_id} của phiên khác. Chờ mốc chặn là cách duy nhất biết
 * chắc hàng đợi đã đi qua điểm đó mà không cần đọc trạng thái ECS từ ngoài tick thread.
 */
@Timeout(30)
class WorldInboxBarrierTest {

    private static final CharacterBaseStats DEFAULT_STATS = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 1);
    private static final long CHARACTER_ID = 7L;

    @Test
    void shouldCompleteOnlyAfterEveryEarlierCommandWasProcessed() throws Exception {
        List<Integer> acknowledgedSequenceIds = new CopyOnWriteArrayList<>();
        WorldFacade world = new WorldFacade(new MoveRecordingSink(acknowledgedSequenceIds));
        world.start();
        try {
            world.attachCharacter(CHARACTER_ID, 0f, 0f, DEFAULT_STATS);
            world.enqueueMoveCommand(CHARACTER_ID, 41, 1f, 0f);
            world.enqueueMoveCommand(CHARACTER_ID, 42, 1f, 0f);
            world.detachCharacter(CHARACTER_ID);

            CompletableFuture<Void> barrier = world.inboxBarrier();
            barrier.get(5, TimeUnit.SECONDS);

            // Không chờ thêm: đúng lúc mốc chặn xong, hai gói xác nhận đã phải ra ngoài rồi.
            assertThat(acknowledgedSequenceIds).containsExactly(41, 42);
        } finally {
            world.stop();
        }
    }

    @Test
    void shouldBeAlreadyComplete_whenWorldIsNotTicking() {
        WorldFacade neverStarted = new WorldFacade(new MoveRecordingSink(new CopyOnWriteArrayList<>()));

        assertThat(neverStarted.inboxBarrier()).isDone();
    }

    @Test
    void shouldCompletePendingBarrier_whenWorldStops() {
        WorldFacade world = new WorldFacade(new MoveRecordingSink(new CopyOnWriteArrayList<>()));
        world.start();

        CompletableFuture<Void> barrier = world.inboxBarrier();
        world.stop();

        // Dừng rồi thì không còn gói nào có thể ra khỏi world này nữa — người chờ không được treo.
        assertThat(barrier).isDone();
        assertThat(world.inboxBarrier()).isDone();
    }

    /** Chỉ ghi lại {@code sequence_id} của các gói xác nhận di chuyển; mọi tín hiệu khác bỏ qua. */
    private static final class MoveRecordingSink implements WorldOutboundSink {
        private final List<Integer> acknowledged;

        MoveRecordingSink(List<Integer> acknowledged) {
            this.acknowledged = acknowledged;
        }

        @Override
        public void onMoveAccepted(WorldOutboundSink.MoveAcceptedSnapshot accepted) {
            acknowledged.add(accepted.sequenceId());
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
        public void onAttackResult(long targetCharacterId, WorldOutboundSink.AttackOutcome outcome) {}

        @Override
        public void onSkillResult(long targetCharacterId, WorldOutboundSink.SkillOutcome outcome) {}

        @Override
        public void onHealthChanged(long targetCharacterId, WorldOutboundSink.HealthSnapshot health) {}

        @Override
        public void onAreaChatMessage(long targetCharacterId, long senderCharacterId, String senderName, String body) {}

        @Override
        public void onCharacterAppearanceChanged(long targetCharacterId, long changedCharacterId) {}

        @Override
        public void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {}

        @Override
        public void onCharacterRespawned(WorldOutboundSink.RespawnSnapshot respawn) {}
    }
}
