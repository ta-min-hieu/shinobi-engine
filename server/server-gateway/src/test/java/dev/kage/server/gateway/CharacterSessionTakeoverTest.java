package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
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

import com.google.protobuf.InvalidProtocolBufferException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
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
 * Một nhân vật chỉ một phiên sống — chọn nhân vật đang online từ kết nối khác thì ĐÁ PHIÊN CŨ
 * (quyết định chủ dự án 2026-09-25, SERVER.md §8).
 *
 * <p>Ba bug thật trước quyết định này: (1) {@code register} ghi đè kết nối cũ, và kết nối nào đóng
 * trước thì {@code unregister} luôn cả nhân vật của kết nối kia; (2) khu vực đầu đầy thì cùng một
 * nhân vật có hai thực thể ở hai khu vực; (3) gói xác nhận di chuyển muộn của phiên cũ rơi vào kết
 * nối mới.
 *
 * <p><b>Vì sao {@link LocalChannel} chứ không {@code EmbeddedChannel}</b>: phải có kết nối THẬT
 * đang sống ({@code isActive}), đóng được từ phía server, và nhiều kết nối chạy SONG SONG trên
 * event loop thật + Virtual Thread thật — đúng như production. {@code LocalChannel} chở thẳng
 * {@link PacketCodec.Frame} nên không cần lớp mã hoá độ dài. Lớp riêng vì
 * {@code GatewayServerIntegrationTest} đã chạm trần độ phức tạp PMD của cả lớp.
 */
@Timeout(60)
class CharacterSessionTakeoverTest {

    private static final String LOGGED_IN_ELSEWHERE = "error.logged_in_elsewhere";
    private static final String USERNAME = "dualwielder";
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final int RACE_ROUNDS = 15;

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

        address = new LocalAddress("takeover-" + UUID.randomUUID());
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
    void shouldKickOldConnection_andKeepTheNewOne_whenSameCharacterIsSelectedFromAnotherConnection()
            throws InterruptedException {
        long characterId = createCharacter("Takeover");
        TestClient old = login();
        assertThat(select(old, characterId).getSuccess()).isTrue();

        TestClient fresh = login();
        assertThat(select(fresh, characterId).getSuccess()).isTrue();

        assertThat(awaitError(old)).as("phiên cũ phải được báo lý do").hasValue(LOGGED_IN_ELSEWHERE);
        assertThat(old.channel.closeFuture().await(WAIT.toMillis())).isTrue();
        awaitServerSideClosed(old);

        // Kết nối cũ đóng hẳn rồi mà nhân vật VẪN online và gói vẫn đi đúng kết nối mới.
        assertThat(worldGateway.registry().isOnline(characterId)).isTrue();
        assertOnlyReceiverOfPushIs(characterId, fresh, List.of(old));
        assertThat(worldGateway.mainWorldChannels().playerCount()).isEqualTo(1);
        assertThat(fresh.channel.isActive()).isTrue();
        assertThat(errorKeys(fresh)).doesNotContain(LOGGED_IN_ELSEWHERE);
    }

    /**
     * Một tài khoản chỉ MỘT nhân vật online (quyết định chủ dự án 2026-10-11). Trước đó hai kết nối của
     * cùng tài khoản chọn hai nhân vật khác nhau là chơi song song được cả hai.
     */
    @Test
    void shouldKickTheOtherCharacterOfTheSameAccount_whenASecondConnectionEntersWithAnotherOne()
            throws InterruptedException {
        long first = createCharacter("AltOne");
        long second = createCharacter("AltTwo");
        TestClient old = login();
        assertThat(select(old, first).getSuccess()).isTrue();

        TestClient fresh = login();
        assertThat(select(fresh, second).getSuccess()).isTrue();

        assertThat(awaitError(old))
                .as("kết nối đang chơi nhân vật kia phải được báo lý do")
                .hasValue(LOGGED_IN_ELSEWHERE);
        assertThat(old.channel.closeFuture().await(WAIT.toMillis())).isTrue();
        awaitServerSideClosed(old);
        assertThat(worldGateway.registry().isOnline(first))
                .as("nhân vật cũ đã rời world")
                .isFalse();
        assertThat(worldGateway.registry().isOnline(second)).isTrue();
        assertThat(worldGateway.mainWorldChannels().playerCount()).isEqualTo(1);
        assertThat(fresh.channel.isActive()).isTrue();
        assertThat(errorKeys(fresh)).doesNotContain(LOGGED_IN_ELSEWHERE);
    }

    /** Sau khi bị đá, tài khoản vẫn vào lại được bằng nhân vật cũ — sổ "đang chơi ai" không kẹt. */
    @Test
    void shouldLetTheAccountComeBackWithTheFirstCharacter_afterBeingKickedByItsOwnOtherCharacter()
            throws InterruptedException {
        long first = createCharacter("AltBackA");
        long second = createCharacter("AltBackB");
        TestClient a = login();
        assertThat(select(a, first).getSuccess()).isTrue();
        TestClient b = login();
        assertThat(select(b, second).getSuccess()).isTrue();
        awaitServerSideClosed(a);

        TestClient c = login();
        assertThat(select(c, first).getSuccess()).isTrue();

        awaitServerSideClosed(b);
        assertThat(worldGateway.registry().isOnline(first)).isTrue();
        assertThat(worldGateway.registry().isOnline(second)).isFalse();
        assertThat(worldGateway.mainWorldChannels().playerCount()).isEqualTo(1);
    }

    @Test
    void shouldNotKickItself_whenSameConnectionSelectsTheSameCharacterTwice() throws InterruptedException {
        long characterId = createCharacter("DoubleClick");
        TestClient client = login();

        assertThat(select(client, characterId).getSuccess()).isTrue();
        assertThat(select(client, characterId).getSuccess()).isTrue();

        assertThat(client.channel.isActive()).isTrue();
        assertThat(errorKeys(client)).doesNotContain(LOGGED_IN_ELSEWHERE);
        assertThat(worldGateway.registry().isOnline(characterId)).isTrue();
        assertThat(worldGateway.mainWorldChannels().playerCount()).isEqualTo(1);
    }

    /**
     * Vị trí lúc thoát của phiên cũ phải được GHI trước khi phiên mới đọc vị trí vào world — không
     * thì người chơi đăng nhập lại bị lùi về chỗ đã lưu lần trước.
     */
    @Test
    void shouldEnterTheWorldWhereTheKickedSessionLeftOff() throws InterruptedException {
        long characterId = createCharacter("Wanderer");
        TestClient old = login();
        assertThat(select(old, characterId).getSuccess()).isTrue();
        float walkedToX = 42.5f;
        worldGateway
                .registry()
                .onMoveAccepted(new WorldOutboundSink.MoveAcceptedSnapshot(characterId, 7, walkedToX, 0f, 0f, true));

        SelectCharacterResponse response = select(login(), characterId);

        assertThat(response.getSpawnX()).isEqualTo(walkedToX);
    }

    /** Hai kết nối cùng chọn nhân vật đang online VÀO CÙNG LÚC — chỉ một người thắng, không ai "nửa vời". */
    @Test
    void shouldLeaveExactlyOneLiveSession_whenTwoConnectionsTakeOverAtTheSameTime() throws InterruptedException {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            long characterId = createCharacter("Racer" + round);
            TestClient old = login();
            assertThat(select(old, characterId).getSuccess()).isTrue();
            TestClient first = login();
            TestClient second = login();

            sendSelect(first, characterId, 2);
            sendSelect(second, characterId, 2);
            awaitSelectResponse(first);
            awaitSelectResponse(second);

            List<TestClient> contenders = List.of(old, first, second);
            awaitTrue(
                    () -> contenders.stream().filter(c -> c.channel.isActive()).count() == 1L);
            TestClient winner = contenders.stream()
                    .filter(c -> c.channel.isActive())
                    .findFirst()
                    .orElseThrow();
            List<TestClient> losers = new ArrayList<>(contenders);
            losers.remove(winner);
            for (TestClient loser : losers) {
                awaitServerSideClosed(loser);
                assertThat(errorKeys(loser)).contains(LOGGED_IN_ELSEWHERE);
            }
            assertThat(worldGateway.registry().isOnline(characterId)).isTrue();
            assertOnlyReceiverOfPushIs(characterId, winner, losers);
            assertThat(worldGateway.mainWorldChannels().playerCount()).isEqualTo(1);
            winner.channel.close().syncUninterruptibly();
            awaitServerSideClosed(winner);
        }
    }

    /** Kết nối cũ tự đóng ĐÚNG lúc kết nối mới chọn nhân vật — lần dọn của kết nối cũ không được xoá phiên mới. */
    @Test
    void shouldKeepTheNewSession_whenOldConnectionClosesWhileTheNewOneSelects() throws InterruptedException {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            long characterId = createCharacter("Closer" + round);
            TestClient old = login();
            assertThat(select(old, characterId).getSuccess()).isTrue();
            TestClient fresh = login();

            sendSelect(fresh, characterId, 2);
            // KHÔNG chờ ở đây: đóng phải chạy đua với lệnh chọn đang bay. Chỉ chờ sau, để một lần
            // đóng thất bại không bị nuốt im.
            ChannelFuture oldClosed = old.channel.close();
            assertThat(awaitSelectResponse(fresh).getSuccess()).isTrue();
            oldClosed.syncUninterruptibly();
            awaitServerSideClosed(old);

            assertThat(fresh.channel.isActive()).isTrue();
            assertThat(worldGateway.registry().isOnline(characterId)).isTrue();
            assertOnlyReceiverOfPushIs(characterId, fresh, List.of());
            assertThat(worldGateway.mainWorldChannels().playerCount()).isEqualTo(1);
            fresh.channel.close().syncUninterruptibly();
            awaitServerSideClosed(fresh);
        }
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

    private static Optional<String> awaitError(TestClient client) throws InterruptedException {
        return awaitFrame(client, Opcode.OPCODE_ERROR_VALUE)
                .map(frame ->
                        parse(() -> ErrorResponse.parseFrom(frame.payload())).getErrorKey());
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

    /** Đẩy một gói tới nhân vật qua registry — CHỈ kết nối đang giữ nhân vật được nhận. */
    private void assertOnlyReceiverOfPushIs(long characterId, TestClient expected, List<TestClient> others)
            throws InterruptedException {
        byte[] marker = ("push-" + UUID.randomUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        worldGateway.registry().sendToCharacterIfOnline(characterId, Opcode.OPCODE_PONG_VALUE, 0, marker);
        long deadline = System.nanoTime() + WAIT.toNanos();
        boolean received = false;
        while (!received && System.nanoTime() < deadline) {
            PacketCodec.Frame frame = expected.inbound.poll(50, TimeUnit.MILLISECONDS);
            if (frame != null) {
                expected.seen.add(frame);
                received = java.util.Arrays.equals(frame.payload(), marker);
            }
        }
        assertThat(received).as("kết nối đang giữ nhân vật phải nhận gói").isTrue();
        for (TestClient other : others) {
            other.inbound.drainTo(other.seen);
            assertThat(other.seen).noneMatch(frame -> java.util.Arrays.equals(frame.payload(), marker));
        }
    }

    /** Phía SERVER của kết nối đã chạy xong {@code channelInactive} — tức lần dọn của nó đã xảy ra. */
    private void awaitServerSideClosed(TestClient client) throws InterruptedException {
        Channel serverSide = serverSideChannels.stream()
                .filter(channel -> client.channel.localAddress().equals(channel.remoteAddress()))
                .findFirst()
                .orElseThrow();
        assertThat(serverSide.closeFuture().await(WAIT.toMillis())).isTrue();
        // closeFuture xong TRƯỚC khi channelInactive chạy xong trên event loop — xếp một việc rỗng sau
        // nó rồi chờ, để chắc lần dọn đã chạy hết.
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
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("gói hỏng", e);
        }
    }

    @FunctionalInterface
    private interface ProtoParser<T> {
        T parse() throws InvalidProtocolBufferException;
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
