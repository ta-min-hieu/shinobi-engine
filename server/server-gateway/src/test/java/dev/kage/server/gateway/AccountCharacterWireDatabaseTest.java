package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.ListCharactersRequest;
import dev.kage.protocol.ListCharactersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.DbMetricsPort;
import dev.kage.server.database.ServerDatabaseBootstrap;
import dev.kage.server.database.session.RedisConfig;
import dev.kage.server.world.TickMetricsPort;
import dev.kage.server.world.WorldContentCatalog;
import dev.kage.server.world.WorldFacade;

/**
 * Đăng nhập + tạo nhân vật QUA DÂY, trên gateway lắp bằng chính composition root của server với
 * Postgres + Redis thật — tầng duy nhất thấy được cả hai nửa của hợp đồng: lỗi nghiệp vụ đúng VÀ
 * {@code error_key} đúng mà client hiển thị (race không được lọt ra thành 500).
 *
 * <p>Mỗi kết nối là một {@link EmbeddedChannel} với executor ĐỒNG BỘ, nên một luồng test gửi lệnh
 * là luồng đó chạy luôn phần chạm DB: N luồng gửi cùng lúc = N lượt đua thật trên Postgres.
 */
@Testcontainers
@Timeout(240)
class AccountCharacterWireDatabaseTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int REDIS_PORT = 6379;
    private static final String REDIS_PASSWORD = "wire-redis-password";
    private static final Duration POSITION_SAVE_INTERVAL = Duration.ofMinutes(5);
    private static final int CONTENDERS = 8;
    private static final int ROUNDS = 3;
    private static final int CHARACTER_LIMIT = 4;

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

    @BeforeAll
    static void wireServer() {
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
        WorldFacade firstChannelFacade =
                new WorldFacade(characterRegistry, true, TickMetricsPort.NO_OP, WorldContentCatalog.EMPTY);
        mainWorldChannels = new MainWorldChannelRegistry(
                characterRegistry, firstChannelFacade, meterRegistry, WorldContentCatalog.EMPTY);
        CharacterPositionPersister positionPersister =
                new CharacterPositionPersister(facades.characterFacade(), characterRegistry, POSITION_SAVE_INTERVAL);
        instanceRegistries = GatewayBootstrap.buildInstanceRegistries(
                characterRegistry, facades, mainWorldChannels, positionPersister);
        worldGateway = instanceRegistries.worldGateway();
    }

    @AfterAll
    static void stopServer() {
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

    /**
     * Tám người chơi (tám account, tám kết nối) bấm "tạo" cùng một tên đúng cùng lúc: đúng một
     * {@code CreateCharacterResponse}, bảy {@code error.duplicate_character_name} (409) — không một
     * {@code error.create_character_failed} (500) hay dòng log ERROR nào.
     */
    @Test
    void shouldAnswerDuplicateName_toEveryLoserOfASameNameRace() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONTENDERS);
        try (ErrorLogCapture errorLog = ErrorLogCapture.attach()) {
            for (int round = 0; round < ROUNDS; round++) {
                String name = "WireRace" + round;
                List<EmbeddedChannel> connections = new ArrayList<>();
                for (int i = 0; i < CONTENDERS; i++) {
                    EmbeddedChannel connection = newConnection();
                    login(connection, nextUsername());
                    connections.add(connection);
                }
                CyclicBarrier barrier = new CyclicBarrier(CONTENDERS);
                List<Future<PacketCodec.Frame>> replies = new ArrayList<>();
                for (EmbeddedChannel connection : connections) {
                    replies.add(executor.submit(() -> {
                        barrier.await(1, TimeUnit.MINUTES);
                        return sendCreate(connection, name, UUID.randomUUID().toString());
                    }));
                }
                int created = 0;
                List<String> errorKeys = new ArrayList<>();
                for (Future<PacketCodec.Frame> reply : replies) {
                    PacketCodec.Frame frame = reply.get(1, TimeUnit.MINUTES);
                    if (frame.header().opcode() == Opcode.OPCODE_CREATE_CHARACTER_RESPONSE_VALUE) {
                        assertThat(CreateCharacterResponse.parseFrom(frame.payload())
                                        .getSuccess())
                                .isTrue();
                        created++;
                    } else {
                        errorKeys.add(errorKeyOf(frame));
                    }
                }
                connections.forEach(EmbeddedChannel::finishAndReleaseAll);

                assertThat(created).as("vòng %d: số lượt tạo thành công", round).isEqualTo(1);
                assertThat(errorKeys).hasSize(CONTENDERS - 1).containsOnly("error.duplicate_character_name");
                assertThat(facades.characterFacade().findByName(name)).isPresent();
            }
            assertThat(errorLog.errorsSince(0))
                    .as("không lượt đua nào được rơi xuống nhánh 500")
                    .isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Hai kết nối cùng đăng nhập MỘT account rồi cùng tạo nhân vật dồn dập: account dừng đúng ở
     * giới hạn, mọi lượt vượt nhận {@code error.character_limit_exceeded}, và hai kết nối thấy cùng
     * một danh sách.
     */
    @Test
    void shouldHoldTheCharacterLimit_whenTwoConnectionsOfOneAccountCreateConcurrently() throws Exception {
        String username = nextUsername();
        EmbeddedChannel first = newConnection();
        EmbeddedChannel second = newConnection();
        long firstAccount = login(first, username);
        long secondAccount = login(second, username);
        assertThat(secondAccount).isEqualTo(firstAccount);

        int attemptsPerConnection = CHARACTER_LIMIT;
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (ErrorLogCapture errorLog = ErrorLogCapture.attach()) {
            Future<List<String>> firstOutcomes = executor.submit(
                    () -> createMany(first, barrier, "Dual" + firstAccount + "a", attemptsPerConnection));
            Future<List<String>> secondOutcomes = executor.submit(
                    () -> createMany(second, barrier, "Dual" + firstAccount + "b", attemptsPerConnection));
            List<String> outcomes = new ArrayList<>(firstOutcomes.get(1, TimeUnit.MINUTES));
            outcomes.addAll(secondOutcomes.get(1, TimeUnit.MINUTES));

            assertThat(outcomes.stream().filter("created"::equals).count()).isEqualTo(CHARACTER_LIMIT);
            assertThat(outcomes.stream().filter(outcome -> !"created".equals(outcome)))
                    .containsOnly("error.character_limit_exceeded");
            assertThat(listCharacterIds(first)).hasSize(CHARACTER_LIMIT).isEqualTo(listCharacterIds(second));
            assertThat(errorLog.errorsSince(0)).isEmpty();
        } finally {
            executor.shutdownNow();
            first.finishAndReleaseAll();
            second.finishAndReleaseAll();
        }
    }

    /**
     * Đầu vào độc qua dây: username độc ⇒ 400 {@code error.invalid_username} và kết nối VẪN chưa
     * đăng nhập (lệnh sau đó 401); tên nhân vật độc ⇒ 400 {@code error.invalid_character_name}, không
     * nhân vật nào được tạo. Không cái nào được rơi xuống 500.
     */
    @Test
    void shouldAnswerBadRequest_toHostileCredentialsAndNames_andCreateNothing() throws Exception {
        try (ErrorLogCapture errorLog = ErrorLogCapture.attach()) {
            for (String hostile : HostileInputs.usernames()) {
                EmbeddedChannel connection = newConnection();
                PacketCodec.Frame reply = send(
                        connection,
                        Opcode.OPCODE_CONNECT_REQUEST_VALUE,
                        ConnectRequest.newBuilder().setUsername(hostile).build().toByteArray());
                assertThat(errorKeyOf(reply))
                        .as(HostileInputs.describe(hostile))
                        .isEqualTo("error.invalid_username");
                PacketCodec.Frame list = send(
                        connection,
                        Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE,
                        ListCharactersRequest.getDefaultInstance().toByteArray());
                assertThat(errorKeyOf(list)).isEqualTo("error.not_authenticated");
                connection.finishAndReleaseAll();
            }

            EmbeddedChannel connection = newConnection();
            login(connection, nextUsername());
            for (String hostile : HostileInputs.characterNames()) {
                PacketCodec.Frame reply =
                        sendCreate(connection, hostile, UUID.randomUUID().toString());
                assertThat(errorKeyOf(reply))
                        .as(HostileInputs.describe(hostile))
                        .isEqualTo("error.invalid_character_name");
            }
            assertThat(listCharacterIds(connection)).isEmpty();
            connection.finishAndReleaseAll();
            assertThat(errorLog.errorsSince(0)).isEmpty();
        }
    }

    private static List<String> createMany(EmbeddedChannel connection, CyclicBarrier barrier, String prefix, int count)
            throws Exception {
        barrier.await(1, TimeUnit.MINUTES);
        List<String> outcomes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PacketCodec.Frame frame =
                    sendCreate(connection, prefix + i, UUID.randomUUID().toString());
            outcomes.add(
                    frame.header().opcode() == Opcode.OPCODE_CREATE_CHARACTER_RESPONSE_VALUE
                            ? "created"
                            : errorKeyOf(frame));
        }
        return outcomes;
    }

    private static String nextUsername() {
        return "wire" + NEXT_ACCOUNT.incrementAndGet();
    }

    private static long login(EmbeddedChannel connection, String username) throws InvalidProtocolBufferException {
        PacketCodec.Frame reply = send(
                connection,
                Opcode.OPCODE_CONNECT_REQUEST_VALUE,
                ConnectRequest.newBuilder().setUsername(username).build().toByteArray());
        assertThat(reply.header().opcode()).isEqualTo(Opcode.OPCODE_CONNECT_RESPONSE_VALUE);
        return ConnectResponse.parseFrom(reply.payload()).getAccountId();
    }

    private static List<Long> listCharacterIds(EmbeddedChannel connection) throws InvalidProtocolBufferException {
        PacketCodec.Frame reply = send(
                connection,
                Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE,
                ListCharactersRequest.getDefaultInstance().toByteArray());
        assertThat(reply.header().opcode()).isEqualTo(Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE);
        return ListCharactersResponse.parseFrom(reply.payload()).getCharactersList().stream()
                .map(character -> character.getId())
                .sorted()
                .toList();
    }

    private static PacketCodec.Frame sendCreate(EmbeddedChannel connection, String name, String idempotencyKey) {
        CreateCharacterRequest request = CreateCharacterRequest.newBuilder()
                .setName(name)
                .setIdempotencyKey(idempotencyKey)
                .build();
        return send(connection, Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, request.toByteArray());
    }

    /** Gửi một lệnh và trả về gói ĐẦU TIÊN server trả lời (executor đồng bộ ⇒ đã trả lời xong). */
    private static PacketCodec.Frame send(EmbeddedChannel connection, int opcode, byte[] payload) {
        GatewayPipelineTest.writeInboundFrame(
                connection, new PacketHeader(opcode, ProtocolVersion.CURRENT, 1), payload);
        List<PacketCodec.Frame> frames = GatewayPipelineTest.readOutboundFrames(connection);
        assertThat(frames).as("server phải trả lời opcode %d", opcode).isNotEmpty();
        return frames.get(0);
    }

    private static String errorKeyOf(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        assertThat(frame.header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
        return ErrorResponse.parseFrom(frame.payload()).getErrorKey();
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
