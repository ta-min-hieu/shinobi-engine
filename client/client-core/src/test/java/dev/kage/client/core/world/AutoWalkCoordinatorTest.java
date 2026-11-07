package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.NpcLocation;

/** ADR-206 — khớp tên NPC không dấu, chọn chỗ đứng gần nhất, và vòng đời của đích tự đi. */
class AutoWalkCoordinatorTest {

    private static final Function<String, String> NAMES = Map.of(
            "town_elder", "Trưởng lão Chu Hoành",
            "waypost_attendant", "Đệ tử trực trạm")::get;

    private static final List<NpcLocation> LOCATIONS = List.of(
            location("town_elder", -5f), location("waypost_attendant", 100f), location("waypost_attendant", 300f));

    @Test
    void shouldMatchAccentlessPartialNames() {
        assertThat(AutoWalkCoordinator.nearestMatch(LOCATIONS, AutoWalkCoordinator.fold("chu  HOANH"), 0f, NAMES))
                .map(NpcLocation::getX)
                .contains(-5f);
        assertThat(AutoWalkCoordinator.fold("Đệ tử")).isEqualTo("de tu");
    }

    @Test
    void shouldPickTheNearestSpot_whenTheNpcStandsInSeveralPlaces() {
        assertThat(AutoWalkCoordinator.nearestMatch(LOCATIONS, "de tu", 250f, NAMES))
                .map(NpcLocation::getX)
                .contains(300f);
        assertThat(AutoWalkCoordinator.nearestMatch(LOCATIONS, "waypost_attendant", 0f, NAMES))
                .map(NpcLocation::getX)
                .contains(100f);
    }

    @Test
    void shouldFindNothing_forAnUnknownName() {
        assertThat(AutoWalkCoordinator.nearestMatch(LOCATIONS, "thuong nhan", 0f, NAMES))
                .isEmpty();
    }

    @Test
    void shouldRunExactlyOneCallback_andStopBeingActive() {
        AutoWalkTarget target = new AutoWalkTarget();
        AtomicInteger arrived = new AtomicInteger();
        AtomicInteger stopped = new AtomicInteger();

        target.start(10f, arrived::incrementAndGet, stopped::incrementAndGet);
        target.arrive();
        target.stop();

        assertThat(target.isActive()).isFalse();
        assertThat(arrived).hasValue(1);
        assertThat(stopped).hasValue(0);
    }

    private static NpcLocation location(String code, float x) {
        return NpcLocation.newBuilder().setNpcCode(code).setX(x).build();
    }
}
