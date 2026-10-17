package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.domain.ExperienceProgress;
import dev.kage.inventory.domain.DropTableEntry;
import dev.kage.server.world.WorldFacade;

/**
 * Chia phần thưởng hạ quái cho nhóm (quyết định chủ dự án 2026-10-11): đồng đội CÙNG World được EXP và
 * tiến độ nhiệm vụ; đồ rơi vẫn chỉ của người ra đòn cuối.
 *
 * <p>Đo thứ các cổng THẬT SỰ nhận được sau khi {@code stop()} đã chờ mọi lượt xong.
 */
@Timeout(10)
class KillRewardPartySharingTest {

    private static final long KILLER_ID = 7L;
    private static final long SAME_WORLD_MATE = 8L;
    private static final long OTHER_WORLD_MATE = 9L;
    private static final long OFFLINE_MATE = 10L;
    private static final long EXP_REWARD = 20L;
    private static final String MONSTER_CODE = "training_dummy";

    private final AtomicInteger lootRolls = new AtomicInteger();
    private final AtomicInteger questEvents = new AtomicInteger();
    private final List<Long> experienceReceivers = new CopyOnWriteArrayList<>();

    // Không start(): chỉ cần hai ĐỊNH DANH World khác nhau để so "cùng World".
    private WorldFacade killerWorld;
    private WorldFacade otherWorld;

    private ConnectedCharacterRegistry registry;

    @AfterEach
    void stopRegistry() {
        if (registry != null) {
            registry.stop();
        }
    }

    @Test
    void teammateInTheSameWorldShouldGetExperienceAndQuestProgress_butNoLoot() {
        registry = registryWith(sharingAmong(List.of(KILLER_ID, SAME_WORLD_MATE)));

        registry.onMonsterKilled(KILLER_ID, MONSTER_CODE, EXP_REWARD);
        registry.stop();

        assertThat(experienceReceivers).containsExactlyInAnyOrder(KILLER_ID, SAME_WORLD_MATE);
        assertThat(questEvents).as("người hạ + một đồng đội").hasValue(2);
        assertThat(lootRolls)
                .as("đồ rơi chỉ roll MỘT lần, cho người ra đòn cuối")
                .hasValue(1);
    }

    @Test
    void teammatesElsewhereOrOfflineShouldGetNothing() {
        registry = registryWith(sharingAmong(List.of(KILLER_ID, SAME_WORLD_MATE, OTHER_WORLD_MATE, OFFLINE_MATE)));

        registry.onMonsterKilled(KILLER_ID, MONSTER_CODE, EXP_REWARD);
        registry.stop();

        assertThat(experienceReceivers).containsExactlyInAnyOrder(KILLER_ID, SAME_WORLD_MATE);
        assertThat(questEvents).hasValue(2);
    }

    @Test
    void soloKillShouldRewardOnlyTheKiller() {
        registry = registryWith(sharingAmong(List.of()));

        registry.onMonsterKilled(KILLER_ID, MONSTER_CODE, EXP_REWARD);
        registry.stop();

        assertThat(experienceReceivers).containsExactly(KILLER_ID);
        assertThat(new int[] {lootRolls.get(), questEvents.get()}).containsExactly(1, 1);
    }

    /** Tra nhóm hỏng (DB chập chờn): người ra đòn cuối vẫn nhận ĐỦ ba phần, và lỗi có dấu vết. */
    @Test
    void killerShouldKeepEverything_whenThePartyLookupFails() {
        registry = registryWith(new KillRewardSharing(
                characterId -> {
                    throw new IllegalStateException("simulated database outage");
                },
                this::worldOf));

        try (ErrorLogCapture errorLog = ErrorLogCapture.attach()) {
            registry.onMonsterKilled(KILLER_ID, MONSTER_CODE, EXP_REWARD);
            registry.stop();

            assertThat(experienceReceivers).containsExactly(KILLER_ID);
            assertThat(new int[] {lootRolls.get(), questEvents.get()}).containsExactly(1, 1);
            // WARN từ 2026-10-17 (dòng 78(d)).
            assertThat(errorLog.errorsSince(0)).isEmpty();
            assertThat(errorLog.warningsSince(0))
                    .singleElement()
                    .asString()
                    .contains("step=PARTY_SHARE")
                    .contains("characterId=" + KILLER_ID);
        }
    }

    private KillRewardSharing sharingAmong(List<Long> partyMemberIds) {
        return new KillRewardSharing(characterId -> partyMemberIds, this::worldOf);
    }

    private Optional<WorldFacade> worldOf(long characterId) {
        Map<Long, WorldFacade> worlds =
                Map.of(KILLER_ID, killerWorld, SAME_WORLD_MATE, killerWorld, OTHER_WORLD_MATE, otherWorld);
        return Optional.ofNullable(worlds.get(characterId));
    }

    private ConnectedCharacterRegistry registryWith(KillRewardSharing sharing) {
        ConnectedCharacterRegistry built = new ConnectedCharacterRegistry(
                ConnectedCharacterRegistryTest.fakeInventoryFacade(dropTableCode -> {
                    lootRolls.incrementAndGet();
                    return List.<DropTableEntry>of();
                }),
                ConnectedCharacterRegistryTest.fakeQuestFacade(questEvents::incrementAndGet),
                (characterId, amount) -> {
                    experienceReceivers.add(characterId);
                    return new GrantExperienceUseCase.Result(new ExperienceProgress(1, amount, amount, 100L), false);
                },
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        killerWorld = new WorldFacade(built, false);
        otherWorld = new WorldFacade(built, false);
        built.shareKillRewardsThrough(sharing);
        return built;
    }
}
