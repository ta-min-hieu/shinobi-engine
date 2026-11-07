package dev.kage.server.gateway;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;

import dev.kage.monster.domain.MonsterSpawnPoint;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.DbMetricsPort;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.ServerDatabaseBootstrap;
import dev.kage.server.database.session.RedisConfig;
import dev.kage.server.world.TickMetricsPort;
import dev.kage.server.world.WorldContentCatalog;
import dev.kage.server.world.WorldFacade;

/**
 * Server thật thu nhỏ cho {@code DisconnectAnywhereFuzzDatabaseTest}: CHÍNH composition root của
 * production ({@link GatewayBootstrap#wireFacades}, {@link GatewayBootstrap#buildInstanceRegistries})
 * trên Postgres + Redis thật, world chính ĐANG tick, lệnh chạm DB chạy trên Virtual Thread, mỗi kết
 * nối một event loop thật ({@link LocalChannel}) — tức đủ cả bốn loại luồng mà lần dọn lúc ngắt kết
 * nối phải chạy đua với.
 *
 * <p>Lớp này còn giữ phép ĐO: {@link #residueOf} đọc trạng thái cuối trong bộ nhớ, {@link #economy}
 * đọc trạng thái cuối trong DB.
 */
final class DisconnectFuzzServer implements AutoCloseable {

    private static final Duration POSITION_SAVE_INTERVAL = Duration.ofMinutes(5);
    private static final Duration CLOSE_WAIT = Duration.ofSeconds(10);
    private static final int EVENT_LOOP_THREADS = 4;
    private static final int STABLE_COUNT_ATTEMPTS = 50;
    private static final long STABLE_COUNT_INTERVAL_MILLIS = 100L;

    private final ServerDatabaseBootstrap databaseBootstrap;
    private final DataSource probe;
    private final GatewayFacades facades;
    private final GatewayObservability observability;
    private final ConnectedCharacterRegistry characterRegistry;
    private final MainWorldChannelRegistry mainWorldChannels;
    private final CharacterPositionPersister positionPersister;
    private final GatewayBootstrap.InstanceRegistries instanceRegistries;
    private final EventLoopGroup eventLoops = new DefaultEventLoopGroup(EVENT_LOOP_THREADS);
    private final ExecutorService authExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<Channel> serverSideChannels = new CopyOnWriteArrayList<>();
    private final LocalAddress address = new LocalAddress("disconnect-fuzz-" + UUID.randomUUID());
    private final Channel serverChannel;
    private final AtomicReference<HeldCommand> nextHeldCommand = new AtomicReference<>();
    private volatile int mainWorldEntityCount;
    private final List<HeldCommand> heldCommands = new CopyOnWriteArrayList<>();

    DisconnectFuzzServer(DatabaseConfig databaseConfig, RedisConfig redisConfig) throws InterruptedException {
        databaseBootstrap = new ServerDatabaseBootstrap(databaseConfig, redisConfig, DbMetricsPort.NO_OP);
        probe = PostgresDataSourceFactory.create(databaseConfig);
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
        WorldFacade firstChannelFacade = new WorldFacade(characterRegistry, true, new EntityCountProbe(), worldContent);
        firstChannelFacade.start();
        mainWorldChannels =
                new MainWorldChannelRegistry(characterRegistry, firstChannelFacade, meterRegistry, worldContent);
        positionPersister =
                new CharacterPositionPersister(facades.characterFacade(), characterRegistry, POSITION_SAVE_INTERVAL);
        positionPersister.start();
        instanceRegistries = GatewayBootstrap.buildInstanceRegistries(
                characterRegistry, facades, mainWorldChannels, positionPersister);
        serverChannel = new ServerBootstrap()
                .group(eventLoops)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    protected void initChannel(LocalChannel channel) {
                        serverSideChannels.add(channel);
                        channel.pipeline()
                                .addLast(new GatewayChannelHandler(
                                        facades,
                                        instanceRegistries.worldGateway(),
                                        DisconnectFuzzServer.this::executeBlockingCommand,
                                        observability));
                    }
                })
                .bind(address)
                .sync()
                .channel();
    }

    /**
     * Lệnh chạm DB KẾ TIẾP (của bất kỳ kết nối nào) sẽ đứng chờ ngay trước khi chạy, tới khi
     * {@link HeldCommand#release()} — để test cắt kết nối và cho server dọn XONG trong lúc lệnh còn
     * "đang bay trên luồng khác", rồi mới cho nó chạy tiếp trên một phiên đã chết.
     */
    HeldCommand holdNextBlockingCommand() {
        HeldCommand held = new HeldCommand(new CountDownLatch(1), new CountDownLatch(1));
        nextHeldCommand.set(held);
        heldCommands.add(held);
        return held;
    }

    /** Một lệnh chạm DB đang bị giữ lại — xem {@link #holdNextBlockingCommand}. */
    record HeldCommand(CountDownLatch released, CountDownLatch finished) {

        void release() {
            released.countDown();
        }

        boolean awaitFinished(Duration timeout) throws InterruptedException {
            return finished.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void executeBlockingCommand(Runnable command) {
        HeldCommand held = nextHeldCommand.getAndSet(null);
        if (held == null) {
            authExecutor.execute(command);
            return;
        }
        authExecutor.execute(() -> {
            try {
                held.released().await();
                command.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                held.finished().countDown();
            }
        });
    }

    /**
     * Số thực thể ECS trong world chính ở tick gần nhất — quái + NPC + nhân vật. Bài fuzz không hạ con
     * quái nào, nên khi không ai online con số này phải bằng đúng lúc server vừa dựng xong: lớn hơn là
     * một thực thể nhân vật bị bỏ lại trong world mà sổ sách của gateway không còn biết tới.
     */
    int mainWorldEntityCount() {
        return mainWorldEntityCount;
    }

    /** Chờ world chính tick ra một số thực thể đứng yên (nội dung tĩnh đã spawn xong) rồi trả số đó. */
    int awaitStableMainWorldEntityCount() throws InterruptedException {
        int previous = -1;
        for (int attempt = 0; attempt < STABLE_COUNT_ATTEMPTS; attempt++) {
            Thread.sleep(STABLE_COUNT_INTERVAL_MILLIS);
            int current = mainWorldEntityCount;
            if (current > 0 && current == previous) {
                return current;
            }
            previous = current;
        }
        throw new IllegalStateException("số thực thể world chính không đứng yên: " + previous);
    }

    /** Chỉ nghe số thực thể mỗi tick — ghi một {@code int}, không cấp phát gì trên tick thread. */
    private final class EntityCountProbe implements TickMetricsPort {
        @Override
        public void recordTickDuration(long durationNanos) {}

        @Override
        public void recordTickOverrun() {}

        @Override
        public void recordSystemDuration(String systemName, long durationNanos) {}

        @Override
        public void recordEntityCount(int count) {
            mainWorldEntityCount = count;
        }
    }

    /** Mở một kết nối mới — chưa đăng nhập. */
    DisconnectFuzzClient open(DisconnectFuzzClient.Hints hints) throws InterruptedException {
        DisconnectFuzzClient client = new DisconnectFuzzClient(hints);
        client.bind(new Bootstrap()
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
                .channel());
        return client;
    }

    /**
     * Chờ MỌI kết nối đã mở chạy xong {@code channelInactive} phía server. Lần dọn còn phần bất đồng
     * bộ sau mốc này (ghi vị trí, chốt trận, dừng World) — đó là lý do {@link #residueOf} được hỏi
     * lặp lại có hạn chứ không hỏi một lần.
     */
    void awaitAllConnectionsClosed() throws InterruptedException {
        for (Channel serverSide : serverSideChannels) {
            if (!serverSide.closeFuture().await(CLOSE_WAIT.toMillis())) {
                throw new IllegalStateException("kết nối phía server không đóng: " + serverSide);
            }
            // closeFuture xong TRƯỚC khi channelInactive chạy xong — xếp một việc rỗng sau nó rồi chờ.
            serverSide.eventLoop().submit(() -> {}).await(CLOSE_WAIT.toMillis());
        }
        serverSideChannels.clear();
    }

    /**
     * Những gì server CÒN GIỮ về các nhân vật này trong bộ nhớ. Gọi khi mọi kết nối đã đóng: danh
     * sách phải rỗng — mỗi dòng là một thứ lần dọn lúc ngắt kết nối bỏ sót.
     */
    List<String> residueOf(List<Long> characterIds, int emptyWorldEntityCount) {
        List<String> residue = new ArrayList<>();
        addIfNonZero(
                residue, "thực thể ECS thừa trong world chính (bóng ma)", mainWorldEntityCount - emptyWorldEntityCount);
        addIfNonZero(
                residue,
                "nhân vật còn đăng ký online",
                characterRegistry.onlineCharacterIds().size());
        addIfNonZero(residue, "thực thể còn ở world chính", mainWorldChannels.playerCount());
        addIfNonZero(
                residue, "World hầm ngục còn chạy", instanceRegistries.dungeon().instanceCount());
        addIfNonZero(
                residue,
                "dòng sổ sách hầm ngục còn sót",
                instanceRegistries.dungeon().trackedEntryCount());
        addIfNonZero(
                residue,
                "dòng sổ sách đấu trường còn sót",
                instanceRegistries.arena().trackedEntryCount());
        addIfNonZero(
                residue,
                "vị trí còn theo dõi (sẽ bị ghi xuống DB mỗi lượt định kỳ)",
                characterRegistry.snapshotPositions().size());
        for (long characterId : characterIds) {
            addIf(residue, characterRegistry.sessions().isClaimed(characterId), "còn giữ phiên", characterId);
            addIf(
                    residue,
                    instanceRegistries.dungeon().isInDungeon(characterId),
                    "còn định tuyến hầm ngục",
                    characterId);
            addIf(residue, instanceRegistries.arena().isInDuel(characterId), "còn định tuyến đấu trường", characterId);
            addIf(
                    residue,
                    mainWorldChannels.worldFacadeFor(characterId).isPresent(),
                    "còn định tuyến world chính",
                    characterId);
        }
        return residue;
    }

    private static void addIfNonZero(List<String> residue, String what, int count) {
        if (count != 0) {
            residue.add(what + " = " + count);
        }
    }

    private static void addIf(List<String> residue, boolean present, String what, long characterId) {
        if (present) {
            residue.add(what + ": characterId=" + characterId);
        }
    }

    /** Thêm một món vào túi, trả {@code inventory_item.id} của nó. */
    long seedItem(long characterId, String templateCode, int slotIndex) {
        long templateId = queryLong("SELECT id FROM item_template WHERE code = ?", templateCode);
        update(
                "INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)"
                        + " VALUES (?, ?, 1, 0, ?)",
                characterId,
                templateId,
                slotIndex);
        return queryLong("SELECT max(id) FROM inventory_item WHERE character_id = ?", characterId);
    }

    /** Túi ban đầu: vàng + một chồng vật phẩm — thứ duy nhất đổi chủ trong bài fuzz là qua giao dịch. */
    long seedEconomy(long characterId, long gold, int quantity) {
        update("UPDATE character_stat SET gold = ? WHERE character_id = ?", gold, characterId);
        long templateId = queryLong("SELECT id FROM item_template WHERE code = 'iron_scrap'");
        update(
                "INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)"
                        + " VALUES (?, ?, ?, 0, 0)",
                characterId,
                templateId,
                quantity);
        return queryLong("SELECT max(id) FROM inventory_item WHERE character_id = ?", characterId);
    }

    /** Ảnh chụp kinh tế + xếp hạng của TOÀN server (DB riêng của bài fuzz nên "toàn server" = các nhân vật này). */
    Economy economy() {
        Map<Long, Long> quantityByTemplate = new TreeMap<>();
        try (Connection connection = probe.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT item_template_id, sum(quantity) FROM inventory_item GROUP BY item_template_id");
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                quantityByTemplate.put(rows.getLong(1), rows.getLong(2));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("đọc túi đồ lỗi", e);
        }
        return new Economy(
                queryLong("SELECT coalesce(sum(gold), 0) FROM character_stat"),
                quantityByTemplate,
                queryLong("SELECT coalesce(sum(wins), 0) - coalesce(sum(losses), 0) FROM pvp_rating"),
                queryLong("SELECT count(*) FROM character_stat WHERE gold < 0")
                        + queryLong("SELECT count(*) FROM inventory_item WHERE quantity <= 0"));
    }

    /**
     * @param winsMinusLosses mỗi trận chốt đúng một thắng + một thua ⇒ luôn bằng 0
     * @param negativeRows số dòng vàng âm / vật phẩm số lượng không dương ⇒ luôn bằng 0
     */
    record Economy(long totalGold, Map<Long, Long> quantityByTemplate, long winsMinusLosses, long negativeRows) {}

    private long queryLong(String sql, Object... params) {
        try (Connection connection = probe.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("truy vấn test-only lỗi: " + sql, e);
        }
    }

    private void update(String sql, Object... params) {
        try (Connection connection = probe.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("ghi test-only lỗi: " + sql, e);
        }
    }

    @Override
    public void close() {
        // Test đỏ giữa chừng có thể bỏ quên một lệnh đang bị giữ — không thả thì authExecutor.close() treo.
        heldCommands.forEach(HeldCommand::release);
        serverChannel.close().syncUninterruptibly();
        eventLoops.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        authExecutor.close();
        mainWorldChannels.stopAll();
        instanceRegistries.dungeon().stopAll();
        instanceRegistries.arena().stopAll();
        characterRegistry.stop();
        positionPersister.stop();
        PostgresDataSourceFactory.close(probe);
        databaseBootstrap.shutdown();
    }
}
