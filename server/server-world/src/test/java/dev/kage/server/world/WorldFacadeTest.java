package dev.kage.server.world;

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
 * vì đây chính là hành vi cần test (ROADMAP Phase 3: "2 client thấy nhau di chuyển").
 */
@Timeout(10)
class WorldFacadeTest {

    private record Spawned(long target, long spawned, float x, float y) {
    }

    private record Moved(long target, long moved, float x, float y) {
    }

    private record Despawned(long target, long despawned) {
    }

    private record Accepted(long characterId, int sequenceId, float x, float y) {
    }

    private static final class RecordingSink implements WorldOutboundSink {
        final List<Spawned> spawned = new CopyOnWriteArrayList<>();
        final List<Moved> moved = new CopyOnWriteArrayList<>();
        final List<Despawned> despawned = new CopyOnWriteArrayList<>();
        final List<Accepted> accepted = new CopyOnWriteArrayList<>();

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

        world.attachCharacter(1L);
        world.attachCharacter(2L);

        awaitTrue(() -> sink.spawned.stream().anyMatch(s -> s.target() == 1L && s.spawned() == 2L));
        awaitTrue(() -> sink.spawned.stream().anyMatch(s -> s.target() == 2L && s.spawned() == 1L));
    }

    @Test
    void shouldAcceptMoveAndBroadcastPositionToNearbyCharacter() {
        world.start();
        world.attachCharacter(1L);
        world.attachCharacter(2L);
        awaitTrue(() -> sink.spawned.size() >= 2);

        world.enqueueMoveCommand(1L, 42, 1f, 0f);

        awaitTrue(() -> sink.accepted.stream().anyMatch(a -> a.characterId() == 1L && a.sequenceId() == 42));
        awaitTrue(() -> sink.moved.stream().anyMatch(m -> m.target() == 2L && m.moved() == 1L));
    }

    @Test
    void shouldDespawnForRemainingCharacter_whenOneDetaches() {
        world.start();
        world.attachCharacter(1L);
        world.attachCharacter(2L);
        awaitTrue(() -> sink.spawned.size() >= 2);

        world.detachCharacter(1L);

        awaitTrue(() -> sink.despawned.stream().anyMatch(d -> d.target() == 2L && d.despawned() == 1L));
    }

    /** Poll với timeout thay vì {@code Thread.sleep} cố định — tick loop chạy real-time 60Hz. */
    private static void awaitTrue(Supplier<Boolean> condition) {
        var deadline = Instant.now().plus(Duration.ofSeconds(5));
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
        throw new AssertionError("Điều kiện không đúng sau 5s chờ tick loop");
    }
}
