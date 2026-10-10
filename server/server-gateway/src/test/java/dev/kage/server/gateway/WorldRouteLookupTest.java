package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongFunction;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.server.world.WorldFacade;

/**
 * {@link WorldRouteLookup} — một người đang được DỜI World không bao giờ được coi là "không thuộc World
 * nào". Lượt dời được mô phỏng tất định: nó "chạy" đúng lúc bảng world chính được hỏi, tức sau khi bảng
 * hầm ngục đã trả "không" — đúng khe hở giữa hai khoá riêng của production.
 */
class WorldRouteLookupTest {

    private static final long CHARACTER_ID = 7L;

    private static WorldFacade dungeonWorld;
    private static WorldFacade mainWorld;

    @BeforeAll
    static void buildWorlds() {
        ConnectedCharacterRegistry registry = new ConnectedCharacterRegistry(
                GatewayPipelineTest.fakeInventoryFacade(),
                GatewayPipelineTest.fakeQuestFacade(),
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        // Không start() — chỉ cần hai đối tượng phân biệt được.
        dungeonWorld = new WorldFacade(registry);
        mainWorld = new WorldFacade(registry);
    }

    @Test
    void shouldFindTheNewWorld_whenTheTransferLandsBetweenTheTwoQuestions() {
        AtomicBoolean movedIntoDungeon = new AtomicBoolean();
        LongFunction<Optional<WorldFacade>> dungeonRoute =
                id -> movedIntoDungeon.get() ? Optional.of(dungeonWorld) : Optional.empty();
        // Lượt dời chạy trọn NGAY TRƯỚC câu hỏi này: định tuyến hầm ngục đã ghi, định tuyến world chính đã gỡ.
        LongFunction<Optional<WorldFacade>> mainRoute = id -> {
            movedIntoDungeon.set(true);
            return Optional.empty();
        };

        Optional<WorldFacade> found = new WorldRouteLookup(List.of(dungeonRoute, mainRoute)).find(CHARACTER_ID);

        assertThat(found).containsSame(dungeonWorld);
    }

    @Test
    void shouldPreferTheInstance_whenBothRoutesExistDuringATransferBackToTheMainWorld() {
        WorldRouteLookup lookup =
                new WorldRouteLookup(List.of(id -> Optional.of(dungeonWorld), id -> Optional.of(mainWorld)));

        assertThat(lookup.find(CHARACTER_ID)).containsSame(dungeonWorld);
    }

    @Test
    void shouldBeEmpty_whenTheCharacterIsInNoWorldAtAll() {
        WorldRouteLookup lookup = new WorldRouteLookup(List.of(id -> Optional.empty(), id -> Optional.empty()));

        assertThat(lookup.find(CHARACTER_ID)).isEmpty();
    }
}
