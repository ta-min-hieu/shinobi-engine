package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
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

    /**
     * {@code stop()} chạy trên luồng khác (gateway, 2026-10-02) và đang chờ một lần tick dở dang — lần
     * tick đó VẪN có thể phát gói. Mốc lấy đúng lúc này không được xong trước khi {@code stop()} xong.
     * Bản cũ thấy {@code running == false} và xong mốc ngay.
     */
    @Test
    void shouldNotComplete_whileStopIsStillWaitingForTheTickInProgress() throws Exception {
        CountDownLatch tickBlocked = new CountDownLatch(1);
        CountDownLatch releaseTick = new CountDownLatch(1);
        WorldFacade world = new WorldFacade(new MoveRecordingSink(new CopyOnWriteArrayList<>(), () -> {
            tickBlocked.countDown();
            awaitQuietly(releaseTick);
        }));
        world.start();
        world.attachCharacter(CHARACTER_ID, 0f, 0f, DEFAULT_STATS);
        world.enqueueMoveCommand(CHARACTER_ID, 1, 1f, 0f);
        assertThat(tickBlocked.await(5, TimeUnit.SECONDS)).isTrue();

        Thread stopper = Thread.ofPlatform().start(world::stop);
        try {
            waitUntilParked(stopper);
            CompletableFuture<Void> barrier = world.inboxBarrier();
            assertThat(barrier).as("lần tick dở dang còn đang phát gói").isNotDone();

            releaseTick.countDown();
            stopper.join(TimeUnit.SECONDS.toMillis(5));
            barrier.get(5, TimeUnit.SECONDS);
        } finally {
            releaseTick.countDown();
            stopper.join(TimeUnit.SECONDS.toMillis(5));
        }
    }

    /**
     * Dòng 56 (2026-10-17): một tick treo không được kéo cả trình tự tắt treo theo — quá trần chờ thì
     * {@code stop()} đi tiếp, và mọi mốc chặn đang chờ vẫn xong.
     */
    @Test
    void stopShouldGiveUpWaitingForAHungTick_afterItsLimit() throws Exception {
        CountDownLatch tickBlocked = new CountDownLatch(1);
        CountDownLatch releaseTick = new CountDownLatch(1);
        WorldFacade world = new WorldFacade(new MoveRecordingSink(new CopyOnWriteArrayList<>(), () -> {
            tickBlocked.countDown();
            awaitQuietly(releaseTick);
        }));
        world.stopTickWait(java.time.Duration.ofMillis(200));
        world.start();
        world.attachCharacter(CHARACTER_ID, 0f, 0f, DEFAULT_STATS);
        world.enqueueMoveCommand(CHARACTER_ID, 1, 1f, 0f);
        assertThat(tickBlocked.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            long start = System.nanoTime();
            world.stop();
            long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(waitedMillis).as("chờ đúng trần rồi đi tiếp").isBetween(150L, 3_000L);
            assertThat(world.inboxBarrier()).isDone();
        } finally {
            releaseTick.countDown();
        }
    }

    /**
     * Dòng 76(a) (2026-10-17): lệnh gỡ báo vị trí THẬT lúc gỡ — gateway lưu nó thay cho mẫu 1 Hz, nên
     * rớt mạng giữa lúc chạy không còn lùi tới ~5 m.
     */
    @Test
    void detachShouldReportTheCharactersActualPositionAtThatTick() throws Exception {
        WorldFacade world = new WorldFacade(new MoveRecordingSink(new CopyOnWriteArrayList<>()));
        world.start();
        try {
            world.attachCharacter(CHARACTER_ID, 3f, 0f, DEFAULT_STATS);
            world.enqueueMoveCommand(CHARACTER_ID, 1, 1f, 0f);
            Thread.sleep(300);
            float[] reported = {Float.NaN, Float.NaN};
            int[] calls = {0};
            world.detachCharacter(CHARACTER_ID, (x, y) -> {
                reported[0] = x;
                reported[1] = y;
                calls[0]++;
            });
            world.inboxBarrier().get(5, TimeUnit.SECONDS);

            assertThat(calls[0]).isEqualTo(1);
            assertThat(reported[0]).as("đã chạy sang phải từ x=3").isGreaterThan(3.5f);
            assertThat(reported[1]).isEqualTo(0f);

            world.detachCharacter(CHARACTER_ID, (x, y) -> calls[0]++);
            world.inboxBarrier().get(5, TimeUnit.SECONDS);
            assertThat(calls[0]).as("không còn trong World thì không báo gì").isEqualTo(1);
        } finally {
            world.stop();
        }
    }

    private static void waitUntilParked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        // TIMED_WAITING từ 2026-10-17: stop() chờ tickLock có trần (dòng 56).
        while (thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TIMED_WAITING) {
            assertThat(System.nanoTime()).as("stop() phải đang chờ tickLock").isLessThan(deadline);
            Thread.sleep(1);
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("bị ngắt khi giữ tick", e);
        }
    }

    /** Chỉ ghi lại {@code sequence_id} của các gói xác nhận di chuyển; mọi tín hiệu khác bỏ qua. */
    private static final class MoveRecordingSink implements WorldOutboundSink {
        private final List<Integer> acknowledged;
        private final Runnable onAccepted;

        MoveRecordingSink(List<Integer> acknowledged) {
            this(acknowledged, () -> {});
        }

        MoveRecordingSink(List<Integer> acknowledged, Runnable onAccepted) {
            this.acknowledged = acknowledged;
            this.onAccepted = onAccepted;
        }

        @Override
        public void onMoveAccepted(WorldOutboundSink.MoveAcceptedSnapshot accepted) {
            acknowledged.add(accepted.sequenceId());
            onAccepted.run();
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
