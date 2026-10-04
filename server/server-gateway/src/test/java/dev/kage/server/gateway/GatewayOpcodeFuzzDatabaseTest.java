package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.protobuf.InvalidProtocolBufferException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.monster.domain.MonsterSpawnPoint;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.DbMetricsPort;
import dev.kage.server.database.ServerDatabaseBootstrap;
import dev.kage.server.database.session.RedisConfig;
import dev.kage.server.world.TickMetricsPort;
import dev.kage.server.world.WorldContentCatalog;
import dev.kage.server.world.WorldFacade;

/**
 * Cổng build: fuzz MỌI opcode client→server qua gateway lắp bằng CHÍNH composition root của server
 * ({@link GatewayBootstrap#wireFacades}, {@link ServerDatabaseBootstrap}) trên Postgres + Redis thật
 * (Testcontainers) — xem {@link OpcodeFuzzer} cho bất biến và cách chạy lại một ca đỏ.
 *
 * <p>Đây là tầng bắt được thứ {@link GatewayOpcodeFuzzTest} (Port giả) không thấy: chuỗi Postgres từ
 * chối (U+0000), số tràn kiểu cột, id âm/0 tới câu SQL, lời gọi mà Port giả không cài.
 *
 * <p>Kết nối là {@link EmbeddedChannel} với executor ĐỒNG BỘ (cùng cách {@code GatewayPipelineTest}),
 * nên mỗi payload chạy xong — kể cả phần chạm DB — trước khi bộ fuzz đọc câu trả lời; không có
 * khoảng chờ hay race nào trong phép đo. World chính KHÔNG tick (thứ cần ở đây là đường lệnh, không
 * phải mô phỏng). Chạy tay nhiều vòng hơn: {@code -Dkage.fuzz.dbIterations=200}.
 */
@Testcontainers
@Timeout(240)
class GatewayOpcodeFuzzDatabaseTest {

    private static final int DEFAULT_ITERATIONS = 40;
    private static final int POSTGRES_PORT = 5432;
    private static final int REDIS_PORT = 6379;
    private static final String REDIS_PASSWORD = "fuzz-redis-password";
    private static final Duration POSITION_SAVE_INTERVAL = Duration.ofMinutes(5);

    /** Một nhân vật CÓ THẬT mà payload độc nhắm tới bằng tên (thư, kết bạn, mời nhóm, giao dịch...). */
    static final String PEER_CHARACTER_NAME = "FuzzPeer";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8"))
            .withExposedPorts(REDIS_PORT)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    private static final AtomicInteger NEXT_ACCOUNT = new AtomicInteger();

    private static ServerDatabaseBootstrap databaseBootstrap;
    private static GatewayFacades facades;
    private static WorldGateway worldGateway;
    private static GatewayObservability observability;
    private static ConnectedCharacterRegistry characterRegistry;
    private static MainWorldChannelRegistry mainWorldChannels;
    private static GatewayBootstrap.InstanceRegistries instanceRegistries;
    private static EmbeddedChannel peer;

    @BeforeAll
    static void wireServer() throws InvalidProtocolBufferException {
        databaseBootstrap = new ServerDatabaseBootstrap(
                new DatabaseConfig(
                        POSTGRES.getHost(),
                        POSTGRES.getMappedPort(POSTGRES_PORT),
                        POSTGRES.getDatabaseName(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()),
                new RedisConfig(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT), REDIS_PASSWORD),
                DbMetricsPort.NO_OP);
        facades = GatewayBootstrap.wireFacades(databaseBootstrap);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        GatewayNetworkMetrics networkMetrics = new GatewayNetworkMetrics(meterRegistry);
        observability = new GatewayObservability(networkMetrics, GatewayTracing.disabled());
        characterRegistry = new ConnectedCharacterRegistry(
                facades.inventoryFacade(),
                facades.contentFacades().questFacade(),
                facades.characterFacade()::grantExperience,
                networkMetrics);
        GatewayBootstrap.wireGoldChangeNotifications(databaseBootstrap, characterRegistry);
        WorldContentCatalog worldContent = WorldContentLoader.load(
                databaseBootstrap.wireMonsterFacade(),
                databaseBootstrap.wireNpcPlacementFacade(),
                databaseBootstrap.wireWorldGeometryFacade(),
                MonsterSpawnPoint.MAIN_WORLD_CODE);
        WorldFacade firstChannelFacade = new WorldFacade(characterRegistry, true, TickMetricsPort.NO_OP, worldContent);
        mainWorldChannels =
                new MainWorldChannelRegistry(characterRegistry, firstChannelFacade, meterRegistry, worldContent);
        CharacterPositionPersister positionPersister =
                new CharacterPositionPersister(facades.characterFacade(), characterRegistry, POSITION_SAVE_INTERVAL);
        instanceRegistries = GatewayBootstrap.buildInstanceRegistries(
                characterRegistry, facades, mainWorldChannels, positionPersister);
        worldGateway = instanceRegistries.worldGateway();

        // Nhân vật đích ĐANG ONLINE suốt buổi fuzz: thách đấu, giao dịch, mời nhóm, chat riêng nhắm
        // vào nó đi hết đường "người kia có mặt" thay vì dừng ở "không tìm thấy".
        peer = newConnection();
        GatewayPipelineTest.connect(peer, "fuzzpeer");
        long peerId = GatewayPipelineTest.createCharacter(peer, PEER_CHARACTER_NAME, 2);
        GatewayPipelineTest.selectCharacter(peer, peerId, 3);
        GatewayPipelineTest.readOutboundFrames(peer);
    }

    @AfterAll
    static void stopServer() {
        if (peer != null) {
            peer.finishAndReleaseAll();
        }
        if (instanceRegistries != null) {
            instanceRegistries.dungeon().stopAll();
            instanceRegistries.arena().stopAll();
        }
        if (mainWorldChannels != null) {
            mainWorldChannels.stopAll();
        }
        if (characterRegistry != null) {
            characterRegistry.stop();
        }
        if (databaseBootstrap != null) {
            databaseBootstrap.shutdown();
        }
    }

    @Test
    void everyClientOpcodeShouldSurviveHostilePayloads_againstRealDatabase() throws InvalidProtocolBufferException {
        OpcodeFuzzer.Settings settings =
                OpcodeFuzzer.Settings.fromSystemProperties("kage.fuzz.dbIterations", DEFAULT_ITERATIONS);
        List<OpcodeFuzzer.Violation> violations;
        String report;
        long distinctReplies;
        try (ErrorLogCapture errorLog = ErrorLogCapture.attach()) {
            OpcodeFuzzer fuzzer = new OpcodeFuzzer(settings, GatewayOpcodeFuzzDatabaseTest::openAt, errorLog);
            violations = fuzzer.run();
            report = fuzzer.report(violations);
            distinctReplies = fuzzer.outcomes().keySet().stream()
                    .filter(outcome -> outcome.startsWith("OPCODE_"))
                    .count();
        }
        assertThat(violations).as(report).isEmpty();
        // Chống xanh RỖNG: thiết lập kết nối hỏng thì mọi payload chỉ nhận 401 và bộ fuzz "không thấy lỗi".
        assertThat(distinctReplies)
                .as("số loại phản hồi thành công khác nhau — bộ fuzz phải đi tới handler thật")
                .isGreaterThanOrEqualTo(OpcodeFuzzer.MIN_DISTINCT_REPLIES);
    }

    /** Mỗi kết nối một tài khoản riêng — tránh trần số nhân vật/tài khoản và trạng thái dính giữa các ca. */
    private static OpcodeFuzzer.FuzzConnection openAt(OpcodeFuzzer.Stage stage) throws InvalidProtocolBufferException {
        GatewayPipelineTest.readOutboundFrames(peer); // lời mời/thách đấu dồn về peer — xả cho khỏi phình
        EmbeddedChannel channel = newConnection();
        if (!stage.loggedIn()) {
            return new OpcodeFuzzer.FuzzConnection(channel, "", 0);
        }
        int account = NEXT_ACCOUNT.incrementAndGet();
        String characterName = "Fz" + account;
        GatewayPipelineTest.connect(channel, "fz" + account);
        long characterId = GatewayPipelineTest.createCharacter(channel, characterName, 2);
        if (stage.inWorld()) {
            GatewayPipelineTest.selectCharacter(channel, characterId, 3);
        }
        GatewayPipelineTest.readOutboundFrames(channel); // bỏ mọi gói phát kèm lúc thiết lập
        return new OpcodeFuzzer.FuzzConnection(channel, characterName, characterId);
    }

    private static EmbeddedChannel newConnection() {
        GatewayChannelHandler handler = new GatewayChannelHandler(facades, worldGateway, Runnable::run, observability);
        return new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                GatewayServer.addProtocolHandlers(ch.pipeline(), handler);
            }
        });
    }
}
