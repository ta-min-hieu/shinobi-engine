package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.server.world.CharacterBaseStats;
import dev.kage.server.world.CharacterEntry;
import dev.kage.server.world.Element;
import dev.kage.server.world.WorldContentCatalog;
import dev.kage.server.world.WorldFacade;

/**
 * Một nhân vật chỉ được ở MỘT khu vực map chính (quyết định chủ dự án 2026-09-25: một nhân vật chỉ
 * một phiên sống).
 *
 * <p>Bug trước đó: {@code attachCharacter} không hỏi nhân vật đã ở khu vực nào chưa. Cùng khu vực
 * thì world tự bỏ qua lệnh trùng, nhưng khu vực đầu ĐẦY thì lần gắn thứ hai rơi sang khu vực khác —
 * cùng một nhân vật có HAI thực thể ở hai world (cày gấp đôi), và {@code channelByCharacterId} chỉ
 * còn nhớ một, nên lần detach sau bỏ sót thực thể kia mãi mãi.
 */
@Timeout(30)
class MainWorldChannelRegistryTest {

    private static final CharacterBaseStats STATS = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 1);
    private static final long CHARACTER_ID = 1_000L;

    private MainWorldChannelRegistry channels;

    @BeforeEach
    void setUp() {
        ConnectedCharacterRegistry registry = new ConnectedCharacterRegistry(
                GatewayPipelineTest.fakeInventoryFacade(),
                GatewayPipelineTest.fakeQuestFacade(),
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        channels = new MainWorldChannelRegistry(
                registry, new WorldFacade(registry), new SimpleMeterRegistry(), WorldContentCatalog.EMPTY);
    }

    @AfterEach
    void tearDown() {
        channels.stopAll();
    }

    @Test
    void shouldKeepOneMembership_whenSameCharacterIsAttachedAgainAfterFirstChannelFilledUp() {
        WorldFacade first = channels.attachCharacter(entry(CHARACTER_ID));
        for (long filler = 1; filler < MainWorldChannelRegistry.DEFAULT_CAPACITY; filler++) {
            channels.attachCharacter(entry(filler));
        }
        int before = channels.playerCount();

        WorldFacade second = channels.attachCharacter(entry(CHARACTER_ID));

        assertThat(second).isSameAs(first);
        assertThat(channels.playerCount()).isEqualTo(before);
        assertThat(channels.worldFacadeFor(CHARACTER_ID)).containsSame(first);
    }

    @Test
    void shouldAttachAgain_afterCharacterWasDetached() {
        channels.attachCharacter(entry(CHARACTER_ID));
        channels.detachCharacter(CHARACTER_ID);

        channels.attachCharacter(entry(CHARACTER_ID));

        assertThat(channels.playerCount()).isEqualTo(1);
        assertThat(channels.worldFacadeFor(CHARACTER_ID)).isPresent();
    }

    private static CharacterEntry entry(long characterId) {
        return CharacterEntry.withoutEquipment(characterId, 0f, 0f, STATS);
    }
}
