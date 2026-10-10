package dev.kage.engine.ecs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

/**
 * {@link EventBus} — lớp này trước 2026-10-10 chưa có test nào. Đối chiếu với mô hình ngây thơ (danh
 * sách listener theo loại sự kiện) dưới chuỗi subscribe/publish ngẫu nhiên có seed, cộng các ca
 * publish/subscribe LỒNG nhau mà vòng for-each cũ không chịu được.
 */
class EventBusTest {

    private record Spawned(int value) implements Event {}

    private record Killed(int value) implements Event {}

    private record Moved(int value) implements Event {}

    private final List<String> delivered = new ArrayList<>();

    @Test
    void shouldDeliverToExactlyTheSubscribersOfThatType_inSubscriptionOrder() {
        for (long seed = 0; seed < 200; seed++) {
            SplittableRandom random = new SplittableRandom(seed);
            EventBus bus = new EventBus();
            List<List<String>> model = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            for (int step = 0; step < 200; step++) {
                int type = random.nextInt(3);
                if (random.nextInt(4) == 0) {
                    String name = "listener-" + step;
                    subscribe(bus, type, name);
                    model.get(type).add(name);
                    continue;
                }
                delivered.clear();
                List<String> expected = new ArrayList<>();
                for (String name : model.get(type)) {
                    expected.add(name + ":" + type + ":" + step);
                }

                publish(bus, type, step);

                assertThat(delivered).as("seed=" + seed + " step=" + step).isEqualTo(expected);
            }
        }
    }

    @Test
    void shouldDeliverNestedEvent_whenListenerPublishesWhileHandling() {
        EventBus bus = new EventBus();
        bus.subscribe(Spawned.class, event -> {
            delivered.add("spawned:" + event.value());
            bus.publish(new Killed(event.value() + 1));
        });
        bus.subscribe(Spawned.class, event -> delivered.add("spawned-second:" + event.value()));
        bus.subscribe(Killed.class, event -> delivered.add("killed:" + event.value()));

        bus.publish(new Spawned(1));

        assertThat(delivered).containsExactly("spawned:1", "killed:2", "spawned-second:1");
    }

    /**
     * Trước 2026-10-10: {@code ConcurrentModificationException} bay ra giữa tick. Listener đăng ký
     * giữa lúc phát KHÔNG nhận sự kiện đang phát — nó đã xảy ra trước khi listener tồn tại.
     */
    @Test
    void shouldStartDeliveringFromTheNextEvent_whenListenerSubscribesWhileHandling() {
        EventBus bus = new EventBus();
        List<String> subscribedOnce = new ArrayList<>();
        bus.subscribe(Spawned.class, event -> {
            if (subscribedOnce.isEmpty()) {
                subscribedOnce.add("done");
                bus.subscribe(Spawned.class, late -> delivered.add("late:" + late.value()));
            }
        });

        bus.publish(new Spawned(1));
        bus.publish(new Spawned(2));

        assertThat(delivered).containsExactly("late:2");
    }

    @Test
    void shouldIgnoreEvent_whenNobodySubscribed() {
        EventBus bus = new EventBus();
        bus.subscribe(Spawned.class, event -> delivered.add("spawned"));

        bus.publish(new Killed(1));

        assertThat(delivered).isEmpty();
    }

    private void subscribe(EventBus bus, int type, String name) {
        switch (type) {
            case 0 -> bus.subscribe(Spawned.class, event -> delivered.add(name + ":0:" + event.value()));
            case 1 -> bus.subscribe(Killed.class, event -> delivered.add(name + ":1:" + event.value()));
            default -> bus.subscribe(Moved.class, event -> delivered.add(name + ":2:" + event.value()));
        }
    }

    private static void publish(EventBus bus, int type, int value) {
        switch (type) {
            case 0 -> bus.publish(new Spawned(value));
            case 1 -> bus.publish(new Killed(value));
            default -> bus.publish(new Moved(value));
        }
    }
}
