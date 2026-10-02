package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.account.api.AccountFacade;
import dev.kage.account.application.LoginOrCreateAccountUseCase;
import dev.kage.character.api.CharacterFacade;
import dev.kage.character.application.CharacterUseCases;
import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.application.ListCharactersUseCase;
import dev.kage.character.application.SelectCharacterUseCase;
import dev.kage.character.application.SelectFactionUseCase;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.quest.api.QuestFacade;
import dev.kage.server.world.WorldContentCatalog;
import dev.kage.server.world.WorldFacade;
import dev.kage.server.world.WorldOutboundSink;

/**
 * MỘT kết nối chọn nhân vật C rồi chọn nhân vật KHÁC D (cùng account) — C phải được dọn y như ngắt
 * kết nối thường trước khi D vào world (SERVER.md §5.1).
 *
 * <p>Bug trước bản vá: {@code boundCharacterId} bị ghi đè C→D, cờ dọn chỉ chạy một lần mỗi kết nối,
 * nên C ở lại world/registry/sổ phiên mãi mãi — thực thể bóng ma, vị trí vẫn được lưu định kỳ, bạn
 * bè thấy online, và lần đăng nhập C sau đó đi "đá" kết nối đang chơi D. Client hiện tại không bao
 * giờ làm vậy, nhưng server không được tin client.
 *
 * <p>Cùng khung {@link LocalChannel} + event loop thật + Virtual Thread thật như
 * {@link CharacterSessionTakeoverTest} (lớp riêng: {@code GatewayServerIntegrationTest} đã chạm trần
 * độ phức tạp PMD).
 */
@Timeout(60)
class CharacterSwitchOnSameConnectionTest {

    private static final String LOGGED_IN_ELSEWHERE = "error.logged_in_elsewhere";
    private static final String USERNAME = "altoholic";
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final int RACE_ROUNDS = 15;
    private static final float WALKED_TO_X = 13.5f;
    private static final Duration GHOST_GRACE = Duration.ofSeconds(1);

    private final List<Channel> serverSideChannels = new CopyOnWriteArrayList<>();
    private EventLoopGroup eventLoops;
    private ExecutorService authExecutor;
    private GatewayPipelineTest.FakeCharacterPort characterPort;
    private CharacterFacade characterFacade;
    private AccountFacade accountFacade;
    private WorldGateway worldGateway;
    private GatewayObservability observability;
    private LocalAddress address;
    private Channel serverChannel;
    private long accountId;

    @BeforeEach
    void startGateway() throws InterruptedException {
        eventLoops = new DefaultEventLoopGroup(4);
        authExecutor = Executors.newVirtualThreadPerTaskExecutor();
        characterPort = new GatewayPipelineTest.FakeCharacterPort();
        characterFacade = new CharacterFacade(
                new CharacterUseCases(
                        new CreateCharacterUseCase(characterPort, action -> action.accept(null)),
                        new ListCharactersUseCase(characterPort),
                        new SelectCharacterUseCase(characterPort),
                        new SelectFactionUseCase(characterPort),
                        new GrantExperienceUseCase(characterPort)),
                characterPort);
        accountFacade = new AccountFacade(new LoginOrCreateAccountUseCase(
                new GatewayPipelineTest.FakeAccountPort(),
                new GatewayPipelineTest.FakeSessionPort(),
                action -> action.accept(null)));
        accountId = accountFacade.login(USERNAME).accountId();

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        GatewayNetworkMetrics networkMetrics = new GatewayNetworkMetrics(meterRegistry);
        observability = new GatewayObservability(networkMetrics, GatewayTracing.disabled());
        InventoryFacade inventoryFacade = GatewayPipelineTest.fakeInventoryFacade();
        QuestFacade questFacade = GatewayPipelineTest.fakeQuestFacade();
        ConnectedCharacterRegistry registry = new ConnectedCharacterRegistry(
                inventoryFacade, questFacade, characterFacade::grantExperience, networkMetrics);
        MainWorldChannelRegistry mainWorldChannels = new MainWorldChannelRegistry(
                registry, new WorldFacade(registry), meterRegistry, WorldContentCatalog.EMPTY);
        worldGateway = new WorldGateway(
                mainWorldChannels,
                registry,
                new CharacterPositionPersister(characterFacade, registry, Duration.ofMinutes(5)),
                new DungeonInstanceRegistry(
                        registry,
                        new DungeonInstanceRegistry.DungeonFacades(
                                characterFacade, GatewayPipelineTest.fakePartyFacade()),
                        mainWorldChannels,
                        TestWorldContents.withBoss()),
                new ArenaInstanceRegistry(
                        registry, characterFacade, GatewayPipelineTest.fakePvpFacade(), mainWorldChannels));

        address = new LocalAddress("switch-" + UUID.randomUUID());
        serverChannel = new ServerBootstrap()
                .group(eventLoops)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    protected void initChannel(LocalChannel channel) {
                        serverSideChannels.add(channel);
                        channel.pipeline().addLast(newHandler());
                    }
                })
                .bind(address)
                .sync()
                .channel();
    }

    @AfterEach
    void stopGateway() {
        serverChannel.close().syncUninterruptibly();
        worldGateway.mainWorldChannels().stopAll();
        worldGateway.registry().stop();
        eventLoops.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        authExecutor.close();
    }

    @Test
    void shouldReleaseFirstCharacterCompletely_whenSameConnectionSelectsAnotherCharacter() throws InterruptedException {
        long first = createCharacter("First");
        long second = createCharacter("Second");
        TestClient client = login();
        assertThat(select(client, first).getSuccess()).isTrue();
        awaitTrue(() -> isInWorld(first));
        worldGateway
                .registry()
                .onMoveAccepted(new WorldOutboundSink.MoveAcceptedSnapshot(first, 7, WALKED_TO_X, 0f, 0f, true));

        assertThat(select(client, second).getSuccess()).isTrue();

        awaitTrue(() -> isInWorld(second) && isGone(first));
        assertThat(worldGateway.mainWorldChannels().playerCount()).isEqualTo(1);
        assertThat(isUnclaimed(first))
                .as("sổ phiên không được giữ nhân vật đã rời")
                .isTrue();
        awaitTrue(() -> Float.compare(characterPort.loadPosition(first).x(), WALKED_TO_X) == 0);
        assertThat(client.channel.isActive()).isTrue();
        assertThat(errorKeys(client)).doesNotContain(LOGGED_IN_ELSEWHERE);

        // Ngắt kết nối vẫn phải dọn nhân vật ĐANG gắn (D) — cờ dọn không được "đã dùng hết" cho C.
        client.channel.close().syncUninterruptibly();
        awaitServerSideClosed(client);
        awaitTrue(() -> isGone(second) && isUnclaimed(second));
        assertThat(worldGateway.mainWorldChannels().playerCount()).isZero();
    }

    /** Sau khi rời C, kết nối khác đăng nhập C thì KHÔNG được đá kết nối đang chơi D. */
    @Test
    void shouldNotKickTheConnection_whenAnotherConnectionLaterSelectsTheCharacterItLeft() throws InterruptedException {
        long first = createCharacter("Left");
        long second = createCharacter("Kept");
        TestClient client = login();
        assertThat(select(client, first).getSuccess()).isTrue();
        assertThat(select(client, second).getSuccess()).isTrue();
        awaitTrue(() -> isInWorld(second) && isGone(first));

        TestClient other = login();
        assertThat(select(other, first).getSuccess()).isTrue();

        awaitTrue(() -> isInWorld(first));
        assertThat(client.channel.isActive()).isTrue();
        assertThat(errorKeys(client)).doesNotContain(LOGGED_IN_ELSEWHERE);
        assertThat(worldGateway.registry().isOnline(second)).isTrue();
        assertThat(worldGateway.mainWorldChannels().playerCount()).isEqualTo(2);
    }

    /** Hai lượt chọn KHÁC nhân vật trên cùng kết nối chạy song song — cuối cùng chỉ một nhân vật gắn. */
    @Test
    void shouldLeaveExactlyOneCharacterBound_whenOneConnectionSelectsTwoCharactersAtOnce() throws InterruptedException {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            long first = createCharacter("RaceA" + round);
            long second = createCharacter("RaceB" + round);
            TestClient client = login();

            sendSelect(client, first, 2);
            sendSelect(client, second, 3);
            assertThat(awaitSelectResponse(client).getSuccess()).isTrue();
            assertThat(awaitSelectResponse(client).getSuccess()).isTrue();

            awaitTrue(() -> isInWorld(first) ^ isInWorld(second));
            long loser = isInWorld(first) ? second : first;
            awaitTrue(() -> isGone(loser) && isUnclaimed(loser));
            assertThat(worldGateway.mainWorldChannels().playerCount()).isEqualTo(1);

            client.channel.close().syncUninterruptibly();
            awaitServerSideClosed(client);
            awaitTrue(() -> isGone(first) && isGone(second) && isUnclaimed(first) && isUnclaimed(second));
            assertThat(worldGateway.mainWorldChannels().playerCount()).isZero();
        }
    }

    /**
     * Chọn LẠI đúng nhân vật đang chơi trong lúc nó đang ở trận Arena (cùng kết nối). Bug trước bản
     * vá: world chính không còn giữ nhân vật (nó đã được dời sang trận) nên lần gắn "vô hại" của lượt
     * chọn lại tạo một thực thể THỨ HAI ở world chính — và lần dọn lúc ngắt kết nối chỉ dọn trận, để
     * thực thể đó đứng mãi trong world chính.
     */
    @Test
    void shouldNotCreateASecondEntity_whenReselectingTheCharacterWhileItIsInADuel() throws InterruptedException {
        long duelist = createCharacter("Duelist");
        long opponent = createCharacter("Opponent");
        TestClient client = login();
        TestClient other = login();
        assertThat(select(client, duelist).getSuccess()).isTrue();
        assertThat(select(other, opponent).getSuccess()).isTrue();
        awaitTrue(() -> isInWorld(duelist) && isInWorld(opponent));

        ArenaInstanceRegistry arena = worldGateway.arenaInstanceRegistry();
        long challengeId = arena.challengeDuel(duelist, opponent).challengeId();
        assertThat(arena.acceptDuel(opponent, challengeId).outcome()).isEqualTo(ArenaInstanceRegistry.AcceptOutcome.OK);
        awaitTrue(() -> worldGateway.mainWorldChannels().worldFacadeFor(duelist).isEmpty());

        sendSelect(client, duelist, 3);
        awaitSelectResponse(client);
        // Response đi TRƯỚC lần gắn (xem enterWorld) — cho lần gắn sai (nếu có) đủ thời gian xảy ra.
        long deadline = System.nanoTime() + GHOST_GRACE.toNanos();
        while (worldGateway.mainWorldChannels().worldFacadeFor(duelist).isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }

        assertThat(worldGateway.mainWorldChannels().worldFacadeFor(duelist))
                .as("nhân vật đang đấu không được có thực thể thứ hai ở world chính")
                .isEmpty();
        assertThat(arena.worldFacadeFor(duelist)).isPresent();

        client.channel.close().syncUninterruptibly();
        awaitServerSideClosed(client);
        awaitTrue(() -> isGone(duelist) && arena.worldFacadeFor(duelist).isEmpty() && isUnclaimed(duelist));
    }

    // ------------------------------------------------------------------------------ kiểm trạng thái

    private boolean isInWorld(long characterId) {
        return worldGateway.registry().isOnline(characterId)
                && worldGateway.mainWorldChannels().worldFacadeFor(characterId).isPresent();
    }

    private boolean isGone(long characterId) {
        return !worldGateway.registry().isOnline(characterId)
                && worldGateway.mainWorldChannels().worldFacadeFor(characterId).isEmpty();
    }

    private boolean isUnclaimed(long characterId) {
        return !worldGateway.registry().sessions().isClaimed(characterId);
    }

    // ------------------------------------------------------------------------------ dựng + client giả

    private GatewayChannelHandler newHandler() {
        return new GatewayChannelHandler(
                new GatewayFacades(
                        accountFacade,
                        characterFacade,
                        GatewayPipelineTest.fakeInventoryFacade(),
                        new ContentFacades(
                                GatewayPipelineTest.fakeShopFacade(),
                                GatewayPipelineTest.fakeDialogueFacade(),
                                GatewayPipelineTest.fakeQuestFacade(),
                                new SocialFacades(
                                        GatewayPipelineTest.fakeFriendFacade(),
                                        GatewayPipelineTest.fakeMailFacade(),
                                        GatewayPipelineTest.fakeChatFacade(),
                                        GatewayPipelineTest.fakeGuildFacade(),
                                        GatewayPipelineTest.fakePartyFacade(),
                                        GatewayPipelineTest.fakeTradeFacade(),
                                        GatewayPipelineTest.fakePvpFacade()))),
                worldGateway,
                authExecutor,
                observability);
    }

    private long createCharacter(String name) {
        return characterPort
                .create(
                        null,
                        new NewCharacter(
                                accountId, name, Faction.HOA, UUID.randomUUID().toString()))
                .id();
    }

    private TestClient login() throws InterruptedException {
        TestClient client = new TestClient();
        client.channel = new Bootstrap()
                .group(eventLoops)
                .channel(LocalChannel.class)
                .handler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    protected void initChannel(LocalChannel channel) {
                        channel.pipeline().addLast(client);
                    }
                })
                .connect(address)
                .sync()
                .channel();
        send(
                client,
                Opcode.OPCODE_CONNECT_REQUEST_VALUE,
                1,
                ConnectRequest.newBuilder().setUsername(USERNAME).build().toByteArray());
        assertThat(awaitFrame(client, Opcode.OPCODE_CONNECT_RESPONSE_VALUE)).isPresent();
        return client;
    }

    private SelectCharacterResponse select(TestClient client, long characterId) throws InterruptedException {
        sendSelect(client, characterId, 2);
        return awaitSelectResponse(client);
    }

    private static void sendSelect(TestClient client, long characterId, int sequenceId) {
        send(
                client,
                Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE,
                sequenceId,
                SelectCharacterRequest.newBuilder()
                        .setCharacterId(characterId)
                        .build()
                        .toByteArray());
    }

    private static SelectCharacterResponse awaitSelectResponse(TestClient client) throws InterruptedException {
        PacketCodec.Frame frame = awaitFrame(client, Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE)
                .orElseThrow(() -> new AssertionError("không nhận được SelectCharacterResponse"));
        return parse(() -> SelectCharacterResponse.parseFrom(frame.payload()));
    }

    private static void send(TestClient client, int opcode, int sequenceId, byte[] payload) {
        client.channel
                .writeAndFlush(
                        new PacketCodec.Frame(new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId), payload))
                .syncUninterruptibly();
    }

    /** Gói đầu tiên mang {@code opcode} trong những gói CHƯA đọc; gói khác opcode được giữ lại để đọc sau. */
    private static Optional<PacketCodec.Frame> awaitFrame(TestClient client, int opcode) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            PacketCodec.Frame frame = client.inbound.poll(50, TimeUnit.MILLISECONDS);
            if (frame == null) {
                continue;
            }
            client.seen.add(frame);
            if (frame.header().opcode() == opcode) {
                return Optional.of(frame);
            }
        }
        return Optional.empty();
    }

    /** Mọi {@code error_key} client này đã nhận — kể cả gói chưa đọc tới. */
    private static List<String> errorKeys(TestClient client) {
        client.inbound.drainTo(client.seen);
        return client.seen.stream()
                .filter(frame -> frame.header().opcode() == Opcode.OPCODE_ERROR_VALUE)
                .map(frame ->
                        parse(() -> ErrorResponse.parseFrom(frame.payload())).getErrorKey())
                .toList();
    }

    /** Phía SERVER của kết nối đã chạy xong {@code channelInactive} — tức lần dọn của nó đã xảy ra. */
    private void awaitServerSideClosed(TestClient client) throws InterruptedException {
        Channel serverSide = serverSideChannels.stream()
                .filter(channel -> client.channel.localAddress().equals(channel.remoteAddress()))
                .findFirst()
                .orElseThrow();
        assertThat(serverSide.closeFuture().await(WAIT.toMillis())).isTrue();
        serverSide.eventLoop().submit(() -> {}).await(WAIT.toMillis());
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static <T> T parse(ProtoParser<T> parser) {
        try {
            return parser.parse();
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalStateException("gói hỏng", e);
        }
    }

    @FunctionalInterface
    private interface ProtoParser<T> {
        T parse() throws com.google.protobuf.InvalidProtocolBufferException;
    }

    /** Một client giả: gom mọi gói server gửi tới. */
    private static final class TestClient extends SimpleChannelInboundHandler<PacketCodec.Frame> {
        private final BlockingQueue<PacketCodec.Frame> inbound = new LinkedBlockingQueue<>();
        private final List<PacketCodec.Frame> seen = new CopyOnWriteArrayList<>();
        private Channel channel;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, PacketCodec.Frame frame) {
            inbound.add(frame);
        }
    }
}
