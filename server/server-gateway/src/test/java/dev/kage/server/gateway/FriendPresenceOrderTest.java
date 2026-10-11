package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
import dev.kage.friend.api.FriendFacade;
import dev.kage.friend.application.AcceptFriendRequestUseCase;
import dev.kage.friend.application.FriendUseCases;
import dev.kage.friend.application.ListFriendsUseCase;
import dev.kage.friend.application.ListPendingFriendRequestsUseCase;
import dev.kage.friend.application.RejectFriendRequestUseCase;
import dev.kage.friend.application.RemoveFriendUseCase;
import dev.kage.friend.application.SendFriendRequestUseCase;
import dev.kage.friend.domain.FriendPort;
import dev.kage.friend.domain.Friendship;
import dev.kage.friend.domain.FriendshipStatus;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.FriendOfflineEvent;
import dev.kage.protocol.FriendOnlineEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.server.world.WorldContentCatalog;
import dev.kage.server.world.WorldFacade;

/**
 * Trạng thái online mà bạn bè nhìn thấy phải khớp sự thật SAU CÙNG — không được kẹt "online" cho một
 * nhân vật đã rớt mạng.
 *
 * <p>Bug tìm được 2026-10-02 khi rà vòng đời bạn bè: lời báo "online" gửi SAU khi vào world, còn giữa
 * hai mốc đó có vài lần đọc DB (EXP, vàng, danh sách bạn). Rớt mạng đúng trong khe ấy thì
 * {@code channelInactive} báo "offline" trên luồng khác — và có thể tới TRƯỚC lời "online" đang trên
 * đường. Bạn bè nhận offline rồi online, tức thấy người đã thoát vẫn online cho tới lần họ đăng nhập
 * lại. Khe được giữ mở cố định ở đây: lần đọc danh sách bạn của lời báo "online" tự rút dây của chính
 * người đó và chờ lời báo "offline" đi qua trước.
 *
 * <p>{@link LocalChannel} vì cùng lý do {@code CharacterSessionTakeoverTest}: cần kết nối thật đóng
 * được, event loop thật, Virtual Thread thật.
 */
@Timeout(60)
class FriendPresenceOrderTest {

    private static final String USERNAME = "presence";
    private static final String FRIEND_USERNAME = "presencefriend";
    private static final Duration WAIT = Duration.ofSeconds(10);

    private final List<Channel> serverSideChannels = new CopyOnWriteArrayList<>();
    private final AtomicBoolean pullPlugOnNextFriendLookup = new AtomicBoolean();
    private final CountDownLatch friendSawOffline = new CountDownLatch(1);
    private EventLoopGroup eventLoops;
    private ExecutorService authExecutor;
    private GatewayPipelineTest.FakeCharacterPort characterPort;
    private CharacterFacade characterFacade;
    private AccountFacade accountFacade;
    private WorldGateway worldGateway;
    private GatewayObservability observability;
    private LocalAddress address;
    private Channel serverChannel;
    private volatile long accountId;
    private volatile long walkerId;
    private volatile long friendId;
    private volatile TestClient walker;

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
        walkerId = createCharacter("Walker");
        // Người bạn thuộc TÀI KHOẢN KHÁC: hai nhân vật của cùng tài khoản không online cùng lúc được
        // (quyết định chủ dự án 2026-10-11).
        friendId = createCharacter(accountFacade.login(FRIEND_USERNAME).accountId(), "Friend");

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        GatewayNetworkMetrics networkMetrics = new GatewayNetworkMetrics(meterRegistry);
        observability = new GatewayObservability(networkMetrics, GatewayTracing.disabled());
        ConnectedCharacterRegistry registry = new ConnectedCharacterRegistry(
                GatewayPipelineTest.fakeInventoryFacade(),
                GatewayPipelineTest.fakeQuestFacade(),
                characterFacade::grantExperience,
                networkMetrics);
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

        address = new LocalAddress("presence-" + UUID.randomUUID());
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
    void friendShouldEndUpSeeingOffline_whenTheCharacterDisconnectsWhileAnnouncingOnline() throws Exception {
        TestClient friend = login(FRIEND_USERNAME);
        select(friend, friendId);

        walker = login();
        pullPlugOnNextFriendLookup.set(true);
        // Không chờ response: walker tự rút dây ngay sau khi vào world.
        sendSelect(walker, walkerId);

        assertThat(friend.awaitPresence(Opcode.OPCODE_FRIEND_ONLINE_EVENT_VALUE))
                .as("lời báo online (muộn) vẫn tới — đó chính là khe")
                .isTrue();
        assertThat(worldGateway.registry().isOnline(walkerId)).isFalse();
        assertThat(friend.awaitLastPresence(Opcode.OPCODE_FRIEND_OFFLINE_EVENT_VALUE))
                .as("trạng thái CUỐI bạn bè thấy phải là offline, thứ tự nhận: %s", friend.presence)
                .isTrue();
    }

    // ------------------------------------------------------------------------------ dựng + client giả

    /**
     * Một quan hệ bạn bè duy nhất {@code walker <-> friend}. Lần tra danh sách bạn ĐẦU TIÊN của
     * {@code walker} sau khi {@link #pullPlugOnNextFriendLookup} bật là lần của lời báo "online": nó đóng
     * kết nối của walker rồi chờ friend nhận "offline" (do lần dọn của kết nối, tra lần hai) mới trả về.
     */
    private FriendFacade friendFacade() {
        FriendPort port = new FriendPort() {
            @Override
            public List<Friendship> listAccepted(long characterId) {
                if (characterId == walkerId && pullPlugOnNextFriendLookup.compareAndSet(true, false)) {
                    walker.channel.close().syncUninterruptibly();
                    awaitLatch(friendSawOffline);
                }
                return List.of(new Friendship(
                        1L, walkerId, friendId, FriendshipStatus.ACCEPTED, Instant.EPOCH, Instant.EPOCH));
            }

            @Override
            public Optional<Friendship> findBetween(long characterIdA, long characterIdB) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public Friendship sendRequest(long requesterId, long addresseeId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public boolean accept(long friendshipId, long addresseeId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public boolean reject(long friendshipId, long addresseeId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public boolean remove(long characterId, long friendCharacterId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public List<Friendship> listPendingIncoming(long characterId) {
                return List.of();
            }
        };
        return new FriendFacade(new FriendUseCases(
                new SendFriendRequestUseCase(port),
                new AcceptFriendRequestUseCase(port),
                new RejectFriendRequestUseCase(port),
                new RemoveFriendUseCase(port),
                new ListFriendsUseCase(port),
                new ListPendingFriendRequestsUseCase(port)));
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            assertThat(latch.await(WAIT.toMillis(), TimeUnit.MILLISECONDS))
                    .as("lần dọn của kết nối phải báo offline")
                    .isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("bị ngắt khi chờ lời báo offline", e);
        }
    }

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
                                        friendFacade(),
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
        return createCharacter(accountId, name);
    }

    /** Nhân vật của MỘT TÀI KHOẢN KHÁC — hai người chơi cùng online phải là hai tài khoản (2026-10-11). */
    private long createCharacter(long ownerAccountId, String name) {
        return characterPort
                .create(
                        null,
                        new NewCharacter(
                                ownerAccountId,
                                name,
                                Faction.HOA,
                                UUID.randomUUID().toString()))
                .id();
    }

    private TestClient login() throws InterruptedException {
        return login(USERNAME);
    }

    private TestClient login(String username) throws InterruptedException {
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
                ConnectRequest.newBuilder().setUsername(username).build().toByteArray());
        assertThat(client.awaitOpcode(Opcode.OPCODE_CONNECT_RESPONSE_VALUE)).isTrue();
        return client;
    }

    private static void select(TestClient client, long characterId) throws InterruptedException {
        sendSelect(client, characterId);
        assertThat(client.awaitOpcode(Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE))
                .isTrue();
    }

    private static void sendSelect(TestClient client, long characterId) {
        send(
                client,
                Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE,
                SelectCharacterRequest.newBuilder()
                        .setCharacterId(characterId)
                        .build()
                        .toByteArray());
    }

    private static void send(TestClient client, int opcode, byte[] payload) {
        client.channel
                .writeAndFlush(new PacketCodec.Frame(new PacketHeader(opcode, ProtocolVersion.CURRENT, 1), payload))
                .syncUninterruptibly();
    }

    /** Client giả: gom mọi gói, ghi riêng thứ tự lời báo online/offline về walker. */
    private final class TestClient extends SimpleChannelInboundHandler<PacketCodec.Frame> {
        private final BlockingQueue<PacketCodec.Frame> inbound = new LinkedBlockingQueue<>();
        private final List<Integer> presence = new CopyOnWriteArrayList<>();
        private Channel channel;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, PacketCodec.Frame frame) throws Exception {
            int opcode = frame.header().opcode();
            if (opcode == Opcode.OPCODE_FRIEND_ONLINE_EVENT_VALUE
                    && FriendOnlineEvent.parseFrom(frame.payload()).getCharacterId() == walkerId) {
                presence.add(opcode);
            } else if (opcode == Opcode.OPCODE_FRIEND_OFFLINE_EVENT_VALUE
                    && FriendOfflineEvent.parseFrom(frame.payload()).getCharacterId() == walkerId) {
                presence.add(opcode);
                friendSawOffline.countDown();
            }
            inbound.add(frame);
        }

        boolean awaitOpcode(int opcode) throws InterruptedException {
            long deadline = System.nanoTime() + WAIT.toNanos();
            while (System.nanoTime() < deadline) {
                PacketCodec.Frame frame = inbound.poll(50, TimeUnit.MILLISECONDS);
                if (frame != null && frame.header().opcode() == opcode) {
                    return true;
                }
            }
            return false;
        }

        boolean awaitPresence(int opcode) throws InterruptedException {
            long deadline = System.nanoTime() + WAIT.toNanos();
            while (!presence.contains(opcode) && System.nanoTime() < deadline) {
                Thread.sleep(10L);
            }
            return presence.contains(opcode);
        }

        boolean awaitLastPresence(int opcode) throws InterruptedException {
            long deadline = System.nanoTime() + WAIT.toNanos();
            while (!isLast(opcode) && System.nanoTime() < deadline) {
                Thread.sleep(10L);
            }
            return isLast(opcode);
        }

        private boolean isLast(int opcode) {
            return !presence.isEmpty() && presence.get(presence.size() - 1) == opcode;
        }
    }
}
