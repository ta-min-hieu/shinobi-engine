package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.sql.DataSource;

import com.google.protobuf.InvalidProtocolBufferException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.inventory.api.EquipmentUseCases;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.api.InventoryQueryUseCases;
import dev.kage.inventory.api.ItemAcquisitionUseCases;
import dev.kage.inventory.application.AddItemUseCase;
import dev.kage.inventory.application.ComputeEquipmentBonusUseCase;
import dev.kage.inventory.application.EnhanceItemUseCase;
import dev.kage.inventory.application.EnhancePorts;
import dev.kage.inventory.application.EquipItemUseCase;
import dev.kage.inventory.application.GetItemCatalogUseCase;
import dev.kage.inventory.application.GrantLootUseCase;
import dev.kage.inventory.application.ListInventoryUseCase;
import dev.kage.inventory.application.UnequipItemUseCase;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.protocol.ItemGrantedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.quest.api.QuestFacade;
import dev.kage.quest.application.AcceptQuestUseCase;
import dev.kage.quest.application.ClaimQuestRewardUseCase;
import dev.kage.quest.application.ListQuestsUseCase;
import dev.kage.quest.application.ProcessMonsterKilledUseCase;
import dev.kage.quest.application.QuestRewardPorts;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.inventory.JdbcInventoryTransactionRunner;
import dev.kage.server.database.inventory.PostgresAuditAdapter;
import dev.kage.server.database.inventory.PostgresDropTableAdapter;
import dev.kage.server.database.inventory.PostgresEquipmentAdapter;
import dev.kage.server.database.inventory.PostgresGoldAdapter;
import dev.kage.server.database.inventory.PostgresInventoryAdapter;
import dev.kage.server.database.inventory.PostgresInventoryIdempotencyAdapter;
import dev.kage.server.database.inventory.PostgresItemTemplateAdapter;
import dev.kage.server.database.quest.JdbcQuestTransactionRunner;
import dev.kage.server.database.quest.PostgresQuestAuditAdapter;
import dev.kage.server.database.quest.PostgresQuestExpAdapter;
import dev.kage.server.database.quest.PostgresQuestIdempotencyAdapter;
import dev.kage.server.database.quest.PostgresQuestInventoryGrantAdapter;
import dev.kage.server.database.quest.PostgresQuestProgressAdapter;
import dev.kage.server.database.quest.PostgresQuestTemplateAdapter;
import dev.kage.server.world.CharacterBaseStats;
import dev.kage.server.world.Element;
import dev.kage.server.world.TickMetricsPort;
import dev.kage.server.world.WorldFacade;

/**
 * Vòng đời rơi đồ TỪ QUÁI, nối liền một mạch: quái chết THẬT trong {@link WorldFacade} → ĐÚNG
 * người hạ gục → {@code GrantLootUseCase} → dòng thật trong {@code inventory_item} (PostgreSQL
 * thật qua Testcontainers) → {@code ItemGrantedEvent} mang {@code item_id} THẬT tới ĐÚNG kết nối.
 *
 * <p><b>Vì sao cần file này dù từng mảnh đều đã có test.</b> Trước đó chuỗi bị đứt làm ba đoạn
 * không ai nối: {@code WorldFacadeTest} dừng ở "sink được báo đúng người hạ gục" (sink giả),
 * {@code ConnectedCharacterRegistryTest} dùng {@code InventoryPort} giả mà {@code save()} trả
 * nguyên object truyền vào — nên {@code ItemGrantedEvent.item_id} ở đó LUÔN là 0 và không hề được
 * assert, {@code MvpLoopIntegrationTest} gọi thẳng {@code GrantLootUseCase} với Postgres thật
 * nhưng không đi qua world lẫn gateway. Không test nào chứng minh "đánh chết một con quái thì
 * nhặt được đồ thật".
 *
 * <p><b>Chốt tính ngẫu nhiên bằng DỮ LIỆU, không mock.</b> {@code LootRoller} gọi thẳng
 * {@link java.util.concurrent.ThreadLocalRandom} (cố ý, ENGINE.md §10 — random gameplay chỉ sinh ở
 * server), không có chỗ bơm nguồn ngẫu nhiên. Nên {@link #forceDeterministicTrainingDummyDrop()}
 * sửa {@code drop_table_entry} của container test thành 100% / đúng 1 cái: cả "có rơi hay không"
 * lẫn "rơi mấy cái" đều tất định, không cần lặp cho tới khi may. Đây chỉ là dữ liệu trong container
 * dùng một lần — KHÔNG đổi migration.
 *
 * <p><b>Vì sao {@link LocalChannel} chứ không {@code EmbeddedChannel}.</b> Một cú giết quái sinh ra
 * ba Virtual Thread ghi song song (rơi đồ, quest, EXP) cộng với thread tick ghi sự kiện AoI —
 * {@code EmbeddedEventLoop} chạy inline trên thread gọi nên gói bị nuốt mất
 * ({@code ConnectedCharacterRegistryTest} phải né bằng cách chọn quái không có EXP). {@code
 * LocalChannel} đăng ký vào {@link EventLoopGroup} thật nên mọi write được event loop tuần tự hoá
 * đúng như Netty thật.
 */
@Testcontainers
@Timeout(180)
class MonsterLootChainIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final String TRAINING_DUMMY_CODE = "training_dummy";
    private static final String KUNAI_CODE = "kunai";
    /** {@code PostgresInventoryAdapter.DEFAULT_CAPACITY} — private bên đó, lặp lại để test nhánh túi đầy. */
    private static final int INVENTORY_CAPACITY = 20;
    /** Nhân vật đứng cách quái mẫu (5,5) đúng 1m — trong tầm đánh 2m, y hệt {@code WorldFacadeTest}. */
    private static final float SPAWN_X = 5f;

    private static final float SPAWN_Y = 6f;

    private static final CharacterBaseStats DEFAULT_STATS = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 1);

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;
    static long kunaiTemplateId;

    private EventLoopGroup eventLoopGroup;
    private ConnectedCharacterRegistry registry;
    private WorldFacade world;

    @BeforeAll
    static void migrateAndPinDropRate() {
        DatabaseConfig config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        dataSource = PostgresDataSourceFactory.create(config);
        FlywayMigrator.migrate(dataSource);

        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] accountHolder = new Account[1];
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "lootchaintester"));
        accountId = accountHolder[0].id();
        kunaiTemplateId = findItemTemplateIdByCode(KUNAI_CODE);
        forceDeterministicTrainingDummyDrop();
    }

    @BeforeEach
    void startRegistry() {
        eventLoopGroup = new DefaultEventLoopGroup(1);
        registry = new ConnectedCharacterRegistry(
                realInventoryFacade(),
                realQuestFacade(),
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
    }

    @AfterEach
    void stopEverything() {
        if (world != null) {
            world.stop();
            world = null;
        }
        registry.stop();
        eventLoopGroup.shutdownGracefully();
    }

    /**
     * CHUỖI ĐẦY ĐỦ, không bỏ mắt xích nào: bấm đánh thật cho tới khi quái chết trong world tick
     * 60Hz → rơi đồ vào DB của ĐÚNG người hạ gục → gói {@code ItemGrantedEvent} mang {@code item_id}
     * khớp đúng {@code inventory_item.id} vừa sinh ra, và người ngoài cuộc KHÔNG nhận được gì.
     */
    @Test
    void shouldPersistLootAndNotifyKiller_whenMonsterActuallyDiesInWorld() throws InvalidProtocolBufferException {
        long killerId = createFreshCharacter("LootKiller");
        long witnessId = createFreshCharacter("LootWitness");
        OutboundFrameCollector killerFrames = registerCharacter(killerId);
        OutboundFrameCollector witnessFrames = registerCharacter(witnessId);

        world = new WorldFacade(registry, true, TickMetricsPort.NO_OP, TestMonsterCatalogs.trainingGround());
        world.start();
        world.attachCharacter(killerId, SPAWN_X, SPAWN_Y, DEFAULT_STATS);

        PacketCodec.Frame spawnFrame =
                awaitFrame(killerFrames, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE, Duration.ofSeconds(10));
        MonsterSpawnedEvent spawn = MonsterSpawnedEvent.parseFrom(spawnFrame.payload());
        assertThat(spawn.getMonsterCode()).isEqualTo(TRAINING_DUMMY_CODE);

        attackUntilLooted(killerId, spawn.getMonsterId(), killerFrames);

        PacketCodec.Frame lootFrame =
                awaitFrame(killerFrames, Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE, Duration.ofSeconds(1));
        ItemGrantedEvent granted = ItemGrantedEvent.parseFrom(lootFrame.payload());
        assertThat(granted.getItemTemplateId()).isEqualTo(kunaiTemplateId);
        assertThat(granted.getQuantity()).isEqualTo(1);
        // ĐÂY là mắt xích chưa ai chứng minh: id THẬT do Postgres cấp, không phải 0 (giá trị mà
        // ItemStack.NEW_ITEM_ID mang trước khi save). Client cần đúng số này để mặc/bán món vừa rơi.
        assertThat(granted.getItemId()).isPositive();

        List<InventoryRow> rows = readInventoryRows(killerId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).id()).isEqualTo(granted.getItemId());
        assertThat(rows.get(0).itemTemplateId()).isEqualTo(kunaiTemplateId);
        assertThat(rows.get(0).quantity()).isEqualTo(granted.getQuantity());

        // Rơi đồ là chuyện riêng của người hạ gục — người ngoài cuộc không nhận gói, không có dòng DB.
        assertThat(opcodesOf(witnessFrames)).doesNotContain(Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE);
        assertThat(readInventoryRows(witnessId)).isEmpty();
    }

    /**
     * Túi đầy: {@code GrantLootUseCase} trả danh sách RỖNG (món rơi ra bị MẤT — hạn chế đã ghi rõ ở
     * feature-inventory/CLAUDE.md, chưa có đồ rơi xuống đất), nên gateway không phát gói nào và
     * không dòng nào được thêm. Verify cả hai tầng: giá trị trả về (đồng bộ, tất định) và hành vi
     * qua {@code onMonsterKilled} thật.
     */
    @Test
    void shouldGrantNothingAndSendNoEvent_whenInventoryAlreadyFull() {
        long characterId = createFreshCharacter("LootBagFull");
        InventoryFacade inventoryFacade = realInventoryFacade();
        for (int i = 0; i < INVENTORY_CAPACITY; i++) {
            assertThat(inventoryFacade.addItem(characterId, ItemStack.newItem(kunaiTemplateId, 1, 0)))
                    .isEqualTo(InventoryFacade.Result.OK);
        }
        assertThat(readInventoryRows(characterId)).hasSize(INVENTORY_CAPACITY);
        OutboundFrameCollector frames = registerCharacter(characterId);

        assertThat(inventoryFacade.grantLoot(characterId, TRAINING_DUMMY_CODE)).isEmpty();
        registry.onMonsterKilled(characterId, TRAINING_DUMMY_CODE, 0L);

        // Không có tín hiệu "đã chạy xong" nào để chờ khi kết quả đúng là KHÔNG có gì xảy ra —
        // chờ đủ lâu cho Virtual Thread chạy hết rồi mới khẳng định, cùng cách ConnectedCharacter
        // RegistryTest kiểm các nhánh "không được gửi gì".
        sleepQuietly(Duration.ofMillis(1500));
        assertThat(opcodesOf(frames)).doesNotContain(Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE);
        assertThat(readInventoryRows(characterId)).hasSize(INVENTORY_CAPACITY);
    }

    /**
     * Ngắt kết nối ngay trước lúc roll xong: item vẫn PHẢI vào DB thật (chỉ mất thông báo) — đúng
     * lời hứa ghi trong {@code grantLootBlocking}. Nếu sai, người chơi mất đồ vĩnh viễn vì rớt mạng
     * đúng lúc giết quái.
     */
    @Test
    void shouldStillPersistLoot_whenKillerDisconnectedBeforeEventIsSent() {
        long characterId = createFreshCharacter("LootOffline");
        OutboundFrameCollector frames = registerCharacter(characterId);
        registry.unregister(characterId);

        registry.onMonsterKilled(characterId, TRAINING_DUMMY_CODE, 0L);

        awaitInventoryRowCount(characterId, 1, Duration.ofSeconds(5));
        assertThat(opcodesOf(frames)).doesNotContain(Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE);
    }

    // ---------------------------------------------------------------- world / kênh giả lập client

    /**
     * Bấm đánh dày hơn cooldown thật (1 đòn/giây) cho tới khi gói rơi đồ tới — dư lệnh là no-op,
     * cùng nhịp {@code targetAndAttackUntil} của {@code WorldFacadeTest}. Quái bù nhìn 50 HP nên
     * vài đòn là chết; timeout rộng vì tick loop chạy real-time.
     */
    private void attackUntilLooted(long killerId, long monsterId, OutboundFrameCollector frames) {
        world.setTarget(killerId, monsterId);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            if (findFrame(frames, Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE).isPresent()) {
                return;
            }
            world.attack(killerId);
            sleepQuietly(Duration.ofMillis(10));
        }
        throw new AssertionError("Quái không chết / không rơi đồ sau 30s vừa bấm đánh vừa chờ");
    }

    /**
     * Một "client" giả lập: {@link LocalChannel} đăng ký vào event loop thật, mọi packet mà
     * {@code ConnectedCharacterRegistry} ghi ra bị chặn lại và gom vào {@link OutboundFrameCollector}
     * thay vì đi tiếp ra dây (kênh này cố ý không nối tới đâu — test chỉ cần biết server GỬI GÌ).
     */
    private OutboundFrameCollector registerCharacter(long characterId) {
        OutboundFrameCollector collector = new OutboundFrameCollector();
        LocalChannel channel = new LocalChannel();
        channel.pipeline().addLast(collector).addLast(new ChannelInboundHandlerAdapter());
        eventLoopGroup.register(channel).syncUninterruptibly();
        // Ghi từ context CUỐI pipeline, đúng chỗ GatewayChannelHandler đứng ở server thật.
        ChannelHandlerContext ctx = channel.pipeline().lastContext();
        registry.register(characterId, ctx);
        return collector;
    }

    private static final class OutboundFrameCollector extends ChannelOutboundHandlerAdapter {

        private final Queue<PacketCodec.Frame> pending = new ConcurrentLinkedQueue<>();
        private final List<PacketCodec.Frame> received = new ArrayList<>();

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof PacketCodec.Frame frame) {
                pending.add(frame);
            }
            promise.setSuccess();
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            // Nuốt luôn: đã giữ lại toàn bộ message ở write(), không có gì thật để đẩy ra dây.
        }

        /** Chỉ luồng test gọi — dồn hàng đợi (event loop ghi vào) sang danh sách tích luỹ. */
        List<PacketCodec.Frame> drain() {
            PacketCodec.Frame frame = pending.poll();
            while (frame != null) {
                received.add(frame);
                frame = pending.poll();
            }
            return received;
        }
    }

    private static Optional<PacketCodec.Frame> findFrame(OutboundFrameCollector collector, int opcode) {
        for (PacketCodec.Frame frame : collector.drain()) {
            if (frame.header().opcode() == opcode) {
                return Optional.of(frame);
            }
        }
        return Optional.empty();
    }

    private static List<Integer> opcodesOf(OutboundFrameCollector collector) {
        List<Integer> opcodes = new ArrayList<>();
        for (PacketCodec.Frame frame : collector.drain()) {
            opcodes.add(frame.header().opcode());
        }
        return opcodes;
    }

    private static PacketCodec.Frame awaitFrame(OutboundFrameCollector collector, int opcode, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            Optional<PacketCodec.Frame> frame = findFrame(collector, opcode);
            if (frame.isPresent()) {
                return frame.get();
            }
            sleepQuietly(Duration.ofMillis(10));
        }
        throw new AssertionError("Không nhận được gói opcode=" + opcode + " sau " + timeout);
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bị ngắt khi chờ chuỗi rơi đồ chạy thật", e);
        }
    }

    // ----------------------------------------------------------------------- lắp Facade y production

    /** Bản sao 1:1 của {@code ServerDatabaseBootstrap.wireInventoryFacade()} — cùng Adapter thật, cùng thứ tự. */
    private static InventoryFacade realInventoryFacade() {
        PostgresInventoryAdapter inventoryPort = new PostgresInventoryAdapter(dataSource);
        PostgresItemTemplateAdapter itemTemplatePort = new PostgresItemTemplateAdapter(dataSource);
        PostgresEquipmentAdapter equipmentPort = new PostgresEquipmentAdapter(dataSource);
        JdbcInventoryTransactionRunner transactionRunner = new JdbcInventoryTransactionRunner(dataSource);
        EquipmentUseCases equipmentUseCases = new EquipmentUseCases(
                new EquipItemUseCase(inventoryPort, itemTemplatePort, equipmentPort, transactionRunner),
                new UnequipItemUseCase(equipmentPort, transactionRunner),
                new ComputeEquipmentBonusUseCase(equipmentPort, inventoryPort, itemTemplatePort));
        EnhancePorts enhancePorts = new EnhancePorts(
                inventoryPort,
                new PostgresGoldAdapter(),
                new PostgresAuditAdapter(),
                itemTemplatePort,
                new PostgresInventoryIdempotencyAdapter());
        return new InventoryFacade(
                new ItemAcquisitionUseCases(
                        new AddItemUseCase(inventoryPort, transactionRunner),
                        new GrantLootUseCase(
                                new PostgresDropTableAdapter(dataSource), inventoryPort, transactionRunner)),
                equipmentUseCases,
                new EnhanceItemUseCase(enhancePorts, transactionRunner),
                new InventoryQueryUseCases(
                        new ListInventoryUseCase(inventoryPort, equipmentPort),
                        new GetItemCatalogUseCase(itemTemplatePort)));
    }

    /**
     * Bản sao 1:1 của {@code ServerDatabaseBootstrap.wireQuestFacade()} — dùng đồ thật thay vì
     * Facade giả để chuỗi "quái chết" chạy ĐỦ ba nhánh song song như server thật (rơi đồ, quest,
     * EXP); nhân vật test không nhận quest nào nên nhánh quest không đổi gì.
     */
    private static QuestFacade realQuestFacade() {
        PostgresQuestProgressAdapter progressPort = new PostgresQuestProgressAdapter(dataSource);
        PostgresQuestTemplateAdapter templatePort = new PostgresQuestTemplateAdapter(dataSource);
        JdbcQuestTransactionRunner transactionRunner = new JdbcQuestTransactionRunner(dataSource);
        QuestRewardPorts rewardPorts = new QuestRewardPorts(
                new PostgresQuestExpAdapter(),
                new PostgresQuestInventoryGrantAdapter(),
                new PostgresQuestAuditAdapter(),
                new PostgresQuestIdempotencyAdapter());
        return new QuestFacade(
                new AcceptQuestUseCase(progressPort, templatePort, transactionRunner),
                new ProcessMonsterKilledUseCase(progressPort, templatePort, transactionRunner),
                new ClaimQuestRewardUseCase(progressPort, templatePort, rewardPorts, transactionRunner),
                new ListQuestsUseCase(progressPort));
    }

    // -------------------------------------------------------------------------- truy vấn test-only

    private record InventoryRow(long id, long itemTemplateId, int quantity) {}

    private static long createFreshCharacter(String name) {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner characterTx = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter =
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        characterTx.inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        return holder[0].id();
    }

    /**
     * Ghim bảng rơi của {@code training_dummy} trong container test thành ĐÚNG MỘT dòng (kunai) ở
     * 100% / đúng 1 cái — xem javadoc lớp về lý do phải chốt tính ngẫu nhiên bằng dữ liệu. Migration
     * V4 (50%, 1-3 cái) giữ nguyên, không đụng tới.
     *
     * <p><b>Phải XOÁ các dòng khác, không chỉ sửa tỉ lệ.</b> Bản đầu chỉ {@code UPDATE ... SET
     * drop_chance_percent = 100} cho cả bảng; hôm sau V21 thêm hai dòng rơi nữa cho cùng con quái và
     * test lập tức đỏ ("Expected size: 1 but was: 3") — vì cả ba dòng đều bị đẩy lên 100%. Xoá hết
     * trừ một dòng làm test miễn nhiễm với mọi lần thêm nội dung sau này.
     */
    private static void forceDeterministicTrainingDummyDrop() {
        String deleteOthers = """
                DELETE FROM drop_table_entry
                 WHERE drop_table_id = (SELECT id FROM drop_table WHERE code = ?)
                   AND item_template_id <> (SELECT id FROM item_template WHERE code = ?)
                """;
        String pinRemaining = """
                UPDATE drop_table_entry
                   SET drop_chance_percent = 100, min_quantity = 1, max_quantity = 1
                 WHERE drop_table_id = (SELECT id FROM drop_table WHERE code = ?)
                """;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(deleteOthers)) {
                statement.setString(1, TRAINING_DUMMY_CODE);
                statement.setString(2, KUNAI_CODE);
                statement.executeUpdate(); // 0 dòng bị xoá là hợp lệ: bảng có thể chỉ có mỗi kunai
            }
            try (PreparedStatement statement = connection.prepareStatement(pinRemaining)) {
                statement.setString(1, TRAINING_DUMMY_CODE);
                assertThat(statement.executeUpdate()).isPositive();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only ghim tỉ lệ rơi đồ", e);
        }
    }

    private static long findItemTemplateIdByCode(String code) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT id FROM item_template WHERE code = ?")) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only lookup item_template: code=" + code, e);
        }
    }

    private static List<InventoryRow> readInventoryRows(long characterId) {
        String sql = "SELECT id, item_template_id, quantity FROM inventory_item WHERE character_id = ? ORDER BY id";
        List<InventoryRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new InventoryRow(rs.getLong(1), rs.getLong(2), rs.getInt(3)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc inventory_item: characterId=" + characterId, e);
        }
        return rows;
    }

    private static void awaitInventoryRowCount(long characterId, int expectedCount, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (readInventoryRows(characterId).size() == expectedCount) {
                return;
            }
            sleepQuietly(Duration.ofMillis(20));
        }
        throw new AssertionError("inventory_item của characterId=" + characterId + " không đạt " + expectedCount
                + " dòng sau " + timeout);
    }
}
