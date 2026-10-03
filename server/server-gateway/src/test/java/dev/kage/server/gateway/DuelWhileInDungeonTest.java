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
 * Chiều ngược của {@link DungeonPullWhileDuelingTest}: người đang ở HẦM NGỤC thách đấu, hoặc chấp
 * nhận một lời thách (bug tìm được 2026-10-03). Đấu trường chỉ hỏi "đang đấu chưa", không hỏi "đang
 * ở hầm ngục không", nên dời người đó vào World đấu trường trong khi thực thể của họ vẫn sống trong
 * World hầm ngục. Định tuyến ưu tiên hầm ngục ({@code currentWorldFacadeIfPresent}) ⇒ thực thể trong
 * trận đứng im chịu đòn; hết trận đấu trường gắn họ về world chính ⇒ một nhân vật, hai thực thể sống
 * (hầm ngục + world chính), và lệnh của họ vẫn đi vào hầm ngục.
 *
 * <p>Đo GIÁ TRỊ CUỐI: người ở hầm ngục không bao giờ có định tuyến đấu trường.
 */
@Timeout(60)
class DuelWhileInDungeonTest {

    private static final long LEADER_ID = DungeonInstanceRegistryConcurrencyTest.LEADER_ID;
    private static final long MEMBER_ID = DungeonInstanceRegistryConcurrencyTest.MEMBER_ID;
    /** Người ở world chính, ngoài nhóm. */
    private static final long OUTSIDER_ID = 103L;

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
        for (long id : new long[] {LEADER_ID, MEMBER_ID, OUTSIDER_ID}) {
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
        // Cùng cách GatewayBootstrap nối hai registry.
        arena.refuseCharactersIn(dungeons::isInDungeon);
    }

    @AfterEach
    void tearDown() {
        dungeons.stopAll();
        arena.stopAll();
        mainWorldChannels.stopAll();
        characterRegistry.stop();
    }

    @Test
    void shouldRefuseTheChallenge_whenTheChallengerIsInADungeon() {
        enterDungeon();

        assertThat(arena.challengeDuel(LEADER_ID, OUTSIDER_ID).outcome())
                .isEqualTo(ArenaInstanceRegistry.ChallengeOutcome.ALREADY_IN_DUEL);
    }

    @Test
    void shouldRefuseTheChallenge_whenTheTargetIsInADungeon() {
        enterDungeon();

        assertThat(arena.challengeDuel(OUTSIDER_ID, LEADER_ID).outcome())
                .isEqualTo(ArenaInstanceRegistry.ChallengeOutcome.TARGET_ALREADY_IN_DUEL);
    }

    /** Lời thách gửi khi cả hai còn ở world chính, người THÁCH vào hầm ngục trước khi được chấp nhận. */
    @Test
    void shouldNotStartTheDuel_whenTheChallengerEnteredADungeonBeforeTheAccept() {
        long challengeId = arena.challengeDuel(LEADER_ID, OUTSIDER_ID).challengeId();
        enterDungeon();

        assertThat(arena.acceptDuel(OUTSIDER_ID, challengeId).outcome())
                .isEqualTo(ArenaInstanceRegistry.AcceptOutcome.NOT_FOUND);

        assertThat(arena.worldFacadeFor(LEADER_ID)).as("người ở hầm ngục KHÔNG được gắn thêm vào đấu trường").isEmpty();
        assertThat(arena.worldFacadeFor(OUTSIDER_ID)).as("không có trận nào được dựng").isEmpty();
        assertThat(dungeons.worldFacadeFor(LEADER_ID)).as("vẫn ở nguyên hầm ngục").isPresent();
        assertThat(arena.isInDuel(OUTSIDER_ID)).as("chỗ giữ của người chấp nhận được nhả").isFalse();
    }

    /** Người CHẤP NHẬN đang ở hầm ngục (bị đồng đội kéo vào sau khi nhận lời thách). */
    @Test
    void shouldNotStartTheDuel_whenTheAccepterIsInADungeon() {
        long challengeId = arena.challengeDuel(OUTSIDER_ID, MEMBER_ID).challengeId();
        enterDungeon(); // LEADER bấm, kéo theo MEMBER

        assertThat(arena.acceptDuel(MEMBER_ID, challengeId).outcome())
                .isEqualTo(ArenaInstanceRegistry.AcceptOutcome.NOT_FOUND);

        assertThat(arena.worldFacadeFor(MEMBER_ID)).isEmpty();
        assertThat(arena.worldFacadeFor(OUTSIDER_ID)).isEmpty();
        assertThat(dungeons.worldFacadeFor(MEMBER_ID)).isPresent();
        assertThat(arena.isInDuel(OUTSIDER_ID)).as("người thách được nhả, thách đấu lại được").isFalse();
    }

    private void enterDungeon() {
        assertThat(dungeons.enterDungeon(LEADER_ID, DUNGEON_CODE)).isEqualTo(DungeonInstanceRegistry.EnterOutcome.OK);
        assertThat(dungeons.worldFacadeFor(LEADER_ID)).isPresent();
        assertThat(dungeons.worldFacadeFor(MEMBER_ID)).isPresent();
    }

    /** Rating cố định — test này không đụng tới Elo. */
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
