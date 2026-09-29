package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.application.CharacterUseCases;
import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.application.ListCharactersUseCase;
import dev.kage.character.application.SelectCharacterUseCase;
import dev.kage.character.application.SelectFactionUseCase;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.character.domain.Transaction;
import dev.kage.server.world.TickMetricsPort;
import dev.kage.server.world.WorldFacade;

/**
 * Một nhân vật KHÔNG BAO GIỜ được nằm trong hai trận Arena cùng lúc. Đo GIÁ TRỊ CUỐI: nhân vật
 * đang ở World nào, không phải "lệnh có bị chặn không".
 *
 * <p>Không có test chốt trận (settle) ở đây: đường đó đi qua {@code PvpFacade} (DB) — các ô dưới
 * đây chỉ dựng trận rồi {@code stopAll}.
 */
@Timeout(60)
class ArenaInstanceRegistryConcurrencyTest {

    private static final long CHALLENGER_ID = 201L;
    private static final long TARGET_ONE_ID = 202L;
    private static final long TARGET_TWO_ID = 203L;

    private ConnectedCharacterRegistry characterRegistry;
    private MainWorldChannelRegistry mainWorldChannels;
    private ArenaInstanceRegistry arena;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    // Rào trong loadStats — lời gọi DB đầu tiên khi dựng trận. Mặc định rào 1 bên = không chờ ai.
    private volatile CyclicBarrier loadStatsBarrier = new CyclicBarrier(1);

    @BeforeEach
    void setUp() {
        // Inventory/quest/pvp KHÔNG nằm trên đường thách đấu/chấp nhận (chỉ chốt trận mới chạm tới) —
        // null thay vì dựng ba facade cồng kềnh chỉ để không bao giờ gọi tới.
        characterRegistry = new ConnectedCharacterRegistry(
                GatewayPipelineTest.fakeInventoryFacade(),
                null,
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        for (long id : new long[] {CHALLENGER_ID, TARGET_ONE_ID, TARGET_TWO_ID}) {
            characterRegistry.register(
                    id,
                    new EmbeddedChannel(new ChannelInboundHandlerAdapter())
                            .pipeline()
                            .firstContext());
        }
        WorldFacade firstChannel = new WorldFacade(
                characterRegistry, true, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());
        firstChannel.start();
        mainWorldChannels = new MainWorldChannelRegistry(
                characterRegistry,
                firstChannel,
                new SimpleMeterRegistry(),
                TestWorldContents.trainingGroundWithElder());
        arena = new ArenaInstanceRegistry(characterRegistry, fakeCharacterFacade(), null, mainWorldChannels);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        arena.stopAll();
        mainWorldChannels.stopAll();
        characterRegistry.stop();
    }

    /**
     * Tất định, không cần race: A thách T từ TRƯỚC khi T vào trận với C. Bản cũ chỉ kiểm NGƯỜI
     * THÁCH còn rảnh, không kiểm NGƯỜI CHẤP NHẬN — T đang đánh C vẫn chấp nhận được lời thách cũ
     * của A và bị gắn vào trận thứ hai, bảng định tuyến ghi đè sang trận mới.
     */
    @Test
    void shouldRefuseAccept_whenTheAccepterIsAlreadyInAnotherDuel() {
        long staleChallenge = arena.challengeDuel(CHALLENGER_ID, TARGET_ONE_ID).challengeId();
        long duelChallenge = arena.challengeDuel(TARGET_ONE_ID, TARGET_TWO_ID).challengeId();
        assertThat(arena.acceptDuel(TARGET_TWO_ID, duelChallenge).outcome())
                .isEqualTo(ArenaInstanceRegistry.AcceptOutcome.OK);
        WorldFacade firstDuel = arena.worldFacadeFor(TARGET_ONE_ID).orElseThrow();

        ArenaInstanceRegistry.AcceptResult result = arena.acceptDuel(TARGET_ONE_ID, staleChallenge);

        assertThat(result.outcome()).isEqualTo(ArenaInstanceRegistry.AcceptOutcome.NOT_FOUND);
        assertThat(arena.worldFacadeFor(TARGET_ONE_ID)).containsSame(firstDuel);
        assertThat(arena.worldFacadeFor(CHALLENGER_ID)).isEmpty();
    }

    /**
     * A thách cả T1 lẫn T2, hai người bấm chấp nhận CÙNG lúc. Bản cũ kiểm "A còn rảnh" trước, rồi
     * mới đọc DB (chỉ số, vị trí) và cuối cùng mới ghi A vào bảng — hai lượt cùng qua được phép
     * kiểm, và A bị gắn vào HAI trận. Rào đặt trong {@code loadStats} để hai lượt chắc chắn cùng
     * nằm trong khoảng hở đó; bản vá giữ chỗ A trước khi đọc DB nên lượt thua không bao giờ tới rào
     * (rào hết giờ thì lượt thắng tự đi tiếp — xem {@link #awaitLoadStatsBarrier}).
     */
    @Test
    void shouldPutTheChallengerIntoExactlyOneDuel_whenTwoTargetsAcceptAtTheSameInstant() throws Exception {
        long challengeOne = arena.challengeDuel(CHALLENGER_ID, TARGET_ONE_ID).challengeId();
        long challengeTwo = arena.challengeDuel(CHALLENGER_ID, TARGET_TWO_ID).challengeId();
        loadStatsBarrier = new CyclicBarrier(2);

        Future<ArenaInstanceRegistry.AcceptResult> first =
                executor.submit(() -> arena.acceptDuel(TARGET_ONE_ID, challengeOne));
        Future<ArenaInstanceRegistry.AcceptResult> second =
                executor.submit(() -> arena.acceptDuel(TARGET_TWO_ID, challengeTwo));
        ArenaInstanceRegistry.AcceptOutcome firstOutcome =
                first.get(30, TimeUnit.SECONDS).outcome();
        ArenaInstanceRegistry.AcceptOutcome secondOutcome =
                second.get(30, TimeUnit.SECONDS).outcome();

        assertThat(List.of(firstOutcome, secondOutcome))
                .containsExactlyInAnyOrder(
                        ArenaInstanceRegistry.AcceptOutcome.OK, ArenaInstanceRegistry.AcceptOutcome.NOT_FOUND);
        long winnerTarget = firstOutcome == ArenaInstanceRegistry.AcceptOutcome.OK ? TARGET_ONE_ID : TARGET_TWO_ID;
        long loserTarget = winnerTarget == TARGET_ONE_ID ? TARGET_TWO_ID : TARGET_ONE_ID;
        assertThat(arena.worldFacadeFor(CHALLENGER_ID))
                .containsSame(arena.worldFacadeFor(winnerTarget).orElseThrow());
        assertThat(arena.worldFacadeFor(loserTarget)).isEmpty();
    }

    private void awaitLoadStatsBarrier() {
        try {
            loadStatsBarrier.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("bị ngắt khi chờ rào", e);
        } catch (BrokenBarrierException | TimeoutException e) {
            // Chủ ý: bên kia không bao giờ tới (đã bị từ chối trước khi đọc DB) — đi tiếp một mình.
            loadStatsBarrier = new CyclicBarrier(1);
        }
    }

    private CharacterFacade fakeCharacterFacade() {
        CharacterPort port = new CharacterPort() {
            @Override
            public Optional<Character> findById(long characterId) {
                return Optional.of(
                        new Character(characterId, 1L, 1, "Hero" + characterId, Faction.HOA, 1, 0L, 0, Instant.EPOCH));
            }

            @Override
            public CharacterStats loadStats(long characterId) {
                awaitLoadStatsBarrier();
                return new CharacterStats(10, 10, 10, 10);
            }

            @Override
            public PositionSnapshot loadPosition(long characterId) {
                return new PositionSnapshot(0f, 0f);
            }

            @Override
            public List<Character> listByAccount(long accountId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public int countByAccount(long accountId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public int lockAccountAndCountCharacters(dev.kage.character.domain.Transaction tx, long accountId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public boolean existsByName(String name) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public Optional<Character> findByName(String name) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public Character create(Transaction tx, NewCharacter newCharacter) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public void savePosition(long characterId, float x, float y) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public long loadGold(long characterId) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public long addExperience(long characterId, long amount) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public boolean saveLevel(long characterId, int level, long derivedFromExp) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }

            @Override
            public void updateFaction(long characterId, Faction faction) {
                throw new UnsupportedOperationException("không dùng trong test này");
            }
        };
        CharacterUseCases useCases = new CharacterUseCases(
                new CreateCharacterUseCase(port, action -> action.accept(null)),
                new ListCharactersUseCase(port),
                new SelectCharacterUseCase(port),
                new SelectFactionUseCase(port),
                new GrantExperienceUseCase(port));
        return new CharacterFacade(useCases, port);
    }
}
