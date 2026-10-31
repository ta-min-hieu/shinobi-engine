package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

/** ADR-131 — NPC động (thương nhân du hành): đặt lặp vô hại, gỡ thì người đang thấy nhận despawn. */
class DynamicNpcTest {

    private static final long CHARACTER_ID = 5L;
    private static final CharacterBaseStats STATS = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 10);
    private static final String MERCHANT = "traveling_merchant";

    @SuppressWarnings({"ReferenceEquality", "PMD.CompareObjectsWithEquals"})
    private static WorldOutboundSink recording(List<String> spawned, List<Long> despawned) {
        return (WorldOutboundSink) Proxy.newProxyInstance(
                WorldOutboundSink.class.getClassLoader(),
                new Class<?>[] {WorldOutboundSink.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "onNpcSpawned" -> {
                        spawned.add(((WorldOutboundSink.NpcSnapshot) args[1]).npcCode());
                        yield null;
                    }
                    case "onNpcDespawned" -> {
                        despawned.add((Long) args[1]);
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "RecordingSink";
                    default -> null;
                });
    }

    @Test
    void shouldShowPlacedNpcOnceAndDespawnItWhenRemoved() {
        List<String> spawned = new CopyOnWriteArrayList<>();
        List<Long> despawned = new CopyOnWriteArrayList<>();
        WorldFacade world = new WorldFacade(recording(spawned, despawned), false);
        world.attachCharacter(CHARACTER_ID, 0f, 0f, STATS);
        world.runOneTickOnCallerThread();

        world.placeNpc(MERCHANT, 2f, 0f);
        world.runOneTickOnCallerThread();
        world.placeNpc(MERCHANT, 2f, 0f);
        world.runOneTickOnCallerThread();

        assertThat(spawned).as("đặt lặp cùng chỗ không sinh thêm").containsExactly(MERCHANT);
        assertThat(despawned).isEmpty();

        world.removeNpc(MERCHANT);
        world.runOneTickOnCallerThread();
        world.removeNpc(MERCHANT);
        world.runOneTickOnCallerThread();

        assertThat(despawned).as("gỡ lặp vô hại").hasSize(1);
    }

    @Test
    void shouldMoveTheNpcWhenPlacedSomewhereElse() {
        List<String> spawned = new CopyOnWriteArrayList<>();
        List<Long> despawned = new CopyOnWriteArrayList<>();
        WorldFacade world = new WorldFacade(recording(spawned, despawned), false);
        world.attachCharacter(CHARACTER_ID, 0f, 0f, STATS);
        world.runOneTickOnCallerThread();

        world.placeNpc(MERCHANT, 2f, 0f);
        world.runOneTickOnCallerThread();
        world.placeNpc(MERCHANT, 4f, 0f);
        world.runOneTickOnCallerThread();

        assertThat(spawned).containsExactly(MERCHANT, MERCHANT);
        assertThat(despawned).as("chỗ cũ gỡ trước khi đặt chỗ mới").hasSize(1);
    }
}
