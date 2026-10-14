package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.pvp.api.PvpFacade;
import dev.kage.pvp.domain.EloRating;
import dev.kage.pvp.domain.PvpRating;
import dev.kage.pvp.domain.PvpRatingPort;
import dev.kage.server.world.TickMetricsPort;
import dev.kage.server.world.WorldFacade;

/**
 * Lượt "vào hầm ngục" kéo theo cả nhóm đang online (ADR-047) — nhưng một đồng đội có thể đang ĐẤU
 * TRƯỜNG. Bug tìm được 2026-10-02 khi rà vòng đời party: lượt kéo chỉ hỏi "đã ở hầm ngục chưa" và
 * "còn online không", nên người đang đấu bị gắn THÊM vào World hầm ngục trong khi thực thể của họ vẫn
 * nằm trong World đấu trường. Định tuyến ưu tiên hầm ngục ({@code currentWorldFacadeIfPresent}), nên
 * đối thủ đánh một thực thể không ai điều khiển; trận kết thúc thì đấu trường gắn họ về world chính —
 * một nhân vật, hai thực thể sống ở hai World.
 *
 * <p>Đo GIÁ TRỊ CUỐI: người đang đấu phải chỉ thuộc đúng World đấu trường.
 */
@Timeout(60)
class DungeonPullWhileDuelingTest {

    private static final long LEADER_ID = DungeonInstanceRegistryConcurrencyTest.LEADER_ID;
    private static final long MEMBER_ID = DungeonInstanceRegistryConcurrencyTest.MEMBER_ID;
    /** Đối thủ đấu trường của {@link #MEMBER_ID} — không thuộc nhóm. */
    private static final long OPPONENT_ID = 103L;

    private static final String DUNGEON_CODE = "sample_dungeon";

    private ConnectedCharacterRegistry characterRegistry;
    private MainWorldChannelRegistry mainWorldChannels;
    private ArenaInstanceRegistry arena;
    private DungeonInstanceRegistry dungeons;

    @BeforeEach
    void setUp() {
        characterRegistry = new ConnectedCharacterRegistry(
                GatewayPipelineTest.fakeInventoryFacade(),
                null,
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        for (long id : new long[] {LEADER_ID, MEMBER_ID, OPPONENT_ID}) {
            characterRegistry.register(
                    id,
                    new EmbeddedChannel(new ChannelInboundHandlerAdapter())
                            .pipeline()
                            .firstContext());
        }
        WorldFacade firstChannel = new WorldFacade(
                characterRegistry, false, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());
        firstChannel.start();
        mainWorldChannels = new MainWorldChannelRegistry(
                characterRegistry,
                firstChannel,
                new SimpleMeterRegistry(),
                TestWorldContents.trainingGroundWithElder());
        arena = new ArenaInstanceRegistry(
                characterRegistry,
                DungeonInstanceRegistryConcurrencyTest.fakeCharacterFacade(),
                new PvpFacade(new FixedRatingPort()),
                mainWorldChannels);
        dungeons = new DungeonInstanceRegistry(
                characterRegistry,
                new DungeonInstanceRegistry.DungeonFacades(
                        DungeonInstanceRegistryConcurrencyTest.fakeCharacterFacade(),
                        DungeonInstanceRegistryConcurrencyTest.fakePartyFacade(() -> {}),
                        arena::isInDuel),
                mainWorldChannels,
                TestWorldContents.withBoss());
    }

    @AfterEach
    void tearDown() {
        dungeons.stopAll();
        arena.stopAll();
        mainWorldChannels.stopAll();
        characterRegistry.stop();
    }

    @Test
    void shouldNotPullAPartyMemberWhoIsInADuel_intoTheDungeon() {
        startDuel(OPPONENT_ID, MEMBER_ID);

        assertThat(dungeons.enterDungeon(LEADER_ID, DUNGEON_CODE)).isEqualTo(DungeonInstanceRegistry.EnterOutcome.OK);

        assertThat(dungeons.worldFacadeFor(LEADER_ID)).as("người bấm vẫn vào").isPresent();
        assertThat(dungeons.worldFacadeFor(MEMBER_ID))
                .as("người đang đấu KHÔNG được gắn thêm vào hầm ngục")
                .isEmpty();
        assertThat(arena.worldFacadeFor(MEMBER_ID)).as("vẫn ở nguyên trận đấu").isPresent();
    }

    @Test
    void shouldRefuseTheDungeon_whenTheRequesterIsInADuel() {
        startDuel(OPPONENT_ID, LEADER_ID);

        assertThat(dungeons.enterDungeon(LEADER_ID, DUNGEON_CODE))
                .isEqualTo(DungeonInstanceRegistry.EnterOutcome.IN_DUEL);

        assertThat(dungeons.worldFacadeFor(LEADER_ID)).isEmpty();
        assertThat(dungeons.worldFacadeFor(MEMBER_ID))
                .as("không ai bị kéo khi lượt vào bị từ chối")
                .isEmpty();
        assertThat(dungeons.instanceCount()).isZero();
    }

    /** Sau trận, đồng đội tự vào được — gia nhập ĐÚNG instance nhóm đang có, không dựng cái thứ hai. */
    @Test
    void shouldLetTheMemberJoinThePartyInstanceLater_afterTheDuel() {
        startDuel(OPPONENT_ID, MEMBER_ID);
        dungeons.enterDungeon(LEADER_ID, DUNGEON_CODE);

        arena.forfeitDuel(MEMBER_ID);

        assertThat(dungeons.enterDungeon(MEMBER_ID, DUNGEON_CODE)).isEqualTo(DungeonInstanceRegistry.EnterOutcome.OK);
        assertThat(dungeons.worldFacadeFor(MEMBER_ID))
                .containsSame(dungeons.worldFacadeFor(LEADER_ID).orElseThrow());
        assertThat(dungeons.instanceCount()).isEqualTo(1);
    }

    private void startDuel(long challengerId, long targetId) {
        long challengeId = arena.challengeDuel(challengerId, targetId).challengeId();
        assertThat(arena.acceptDuel(targetId, challengeId).outcome()).isEqualTo(ArenaInstanceRegistry.AcceptOutcome.OK);
        assertThat(arena.worldFacadeFor(targetId)).isPresent();
    }

    /** Rating cố định — test này không đụng tới Elo, chỉ cần bỏ cuộc chốt được trận. */
    private static final class FixedRatingPort implements PvpRatingPort {

        @Override
        public Optional<PvpRating> findByCharacterId(long characterId) {
            return Optional.empty();
        }

        @Override
        public EloRating.Result recordDuelResult(
                long winnerCharacterId, long loserCharacterId, PvpRatingPort.EloComputer computer) {
            return new EloRating.Result(1016, 984);
        }
    }
}
