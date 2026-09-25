package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.character.domain.PositionSnapshot;
import dev.kage.server.world.WorldOutboundSink;

/**
 * Vị trí ghi lúc logout là vị trí CUỐI CÙNG nằm trong DB — một lượt lưu định kỳ đã chụp vị trí CŨ
 * và đang ghi dở không được phép đè lên nó sau đó (người chơi đăng nhập lại bị lùi vài bước).
 */
@Timeout(10)
class CharacterPositionPersisterTest {

    private static final long CHARACTER_ID = 42L;
    private static final PositionSnapshot SNAPSHOT_POSITION = new PositionSnapshot(1f, 1f);
    private static final PositionSnapshot LOGOUT_POSITION = new PositionSnapshot(2f, 2f);
    private static final long WAIT_SECONDS = 5L;

    private final ConnectedCharacterRegistry registry = new ConnectedCharacterRegistry(null, null, null, null);
    private CharacterPositionPersister persister;

    @AfterEach
    void tearDown() {
        if (persister != null) {
            persister.stop();
        }
        registry.stop();
    }

    @Test
    void logoutPositionIsFinal_whenPeriodicSnapshotOfOlderPositionIsStillWriting() throws InterruptedException {
        CountDownLatch periodicWriteEntered = new CountDownLatch(1);
        CountDownLatch releasePeriodicWrite = new CountDownLatch(1);
        AtomicBoolean firstWrite = new AtomicBoolean(true);
        List<PositionSnapshot> writes = new CopyOnWriteArrayList<PositionSnapshot>();
        CharacterPositionPersister.PositionWriter writer = (characterId, x, y) -> {
            if (firstWrite.compareAndSet(true, false)) {
                // Lượt định kỳ đầu tiên: đã chụp SNAPSHOT_POSITION, "DB chậm" giữ nó ở đây.
                periodicWriteEntered.countDown();
                awaitQuietly(releasePeriodicWrite);
            }
            writes.add(new PositionSnapshot(x, y));
        };
        persister = new CharacterPositionPersister(writer, registry, Duration.ofMillis(10));
        moveTo(SNAPSHOT_POSITION);
        persister.start();
        assertThat(periodicWriteEntered.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        // Nhân vật đi tiếp rồi thoát — đúng trình tự GatewayChannelHandler.releaseCharacter: đọc vị
        // trí cuối, unregister, rồi lưu trên thread khác (authExecutor).
        moveTo(LOGOUT_POSITION);
        PositionSnapshot lastPosition = registry.lastKnownPosition(CHARACTER_ID);
        registry.unregister(CHARACTER_ID);
        Thread logout =
                Thread.ofVirtual().start(() -> persister.save(CHARACTER_ID, lastPosition.x(), lastPosition.y()));
        logout.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));

        releasePeriodicWrite.countDown();
        awaitWriteCount(writes, 2);

        assertThat(writes).last().isEqualTo(LOGOUT_POSITION);
    }

    private void moveTo(PositionSnapshot position) {
        registry.onMoveAccepted(
                new WorldOutboundSink.MoveAcceptedSnapshot(CHARACTER_ID, 0, position.x(), position.y(), 0f, true));
    }

    private static void awaitWriteCount(List<PositionSnapshot> writes, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (writes.size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertThat(writes).hasSize(expected);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
