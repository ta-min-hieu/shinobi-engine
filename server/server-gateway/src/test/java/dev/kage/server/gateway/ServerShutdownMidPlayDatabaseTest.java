package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.protocol.AcceptTradeRequestCommand;
import dev.kage.protocol.BuyItemCommand;
import dev.kage.protocol.CancelTradeCommand;
import dev.kage.protocol.CancelTradeOutcome;
import dev.kage.protocol.CancelTradeResponse;
import dev.kage.protocol.ConfirmTradeCommand;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.GetTradeStatusRequest;
import dev.kage.protocol.GetTradeStatusResponse;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RequestTradeCommand;
import dev.kage.protocol.RequestTradeOutcome;
import dev.kage.protocol.RequestTradeResponse;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.SetTradeOfferCommand;
import dev.kage.protocol.TradeOfferItemInput;
import dev.kage.protocol.TradeRequestedEvent;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.DbMetricsPort;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.ServerDatabaseBootstrap;
import dev.kage.server.database.session.RedisConfig;

/**
 * Tắt server GIỮA LÚC ĐANG CHƠI rồi khởi động lại (SERVER.md §10) — trên Postgres + Redis thật, bằng
 * CHÍNH phép lắp và trình tự tắt của production ({@link GatewayBootstrap#assemble}), qua socket TCP
 * thật.
 *
 * <p>Mọi khẳng định đo GIÁ TRỊ CUỐI trong DB sau khi trình tự tắt trả về (pool DB đã đóng — đọc bằng
 * một kết nối riêng), rồi dựng server thứ hai trên cùng DB và cho người chơi đăng nhập lại. "Trình tự
 * tắt không ném gì" không phải bằng chứng là đã lưu.
 */
@Testcontainers
@Timeout(240)
class ServerShutdownMidPlayDatabaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(ServerShutdownMidPlayDatabaseTest.class);

    private static final int POSTGRES_PORT = 5432;
    private static final int REDIS_PORT = 6379;
    private static final String REDIS_PASSWORD = "shutdown-audit-redis-password";
    private static final int SOCKET_TIMEOUT_MILLIS = 10_000;
    private static final int FRAMES_BEFORE_GIVING_UP = 5_000;

    /** 5 m/s × 2,5 s ≈ 12,5 m thật; vị trí lấy mẫu mỗi giây ⇒ chỗ đã lưu cách điểm xuất phát hơn 5 m. */
    private static final long WALK_MILLIS = 2_500L;

    private static final float MINIMUM_SAVED_DISTANCE = 5f;
    private static final float POSITION_TOLERANCE = 0.001f;

    private static final long START_GOLD = 100_000L;
    private static final int PURCHASES_IN_FLIGHT = 30;
    private static final long TRADE_GOLD = 100L;
    private static final int TRADE_ITEM_QUANTITY = 2;
    private static final int START_ITEM_QUANTITY = 5;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8"))
            .withExposedPorts(REDIS_PORT)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    private static DataSource probe;

    private final List<GatewayBootstrap.Assembly> running = new ArrayList<>();

    @BeforeAll
    static void openProbe() {
        probe = PostgresDataSourceFactory.create(databaseConfig());
    }

    @AfterAll
    static void closeProbe() {
        PostgresDataSourceFactory.close(probe);
    }

    /** Test đỏ giữa chừng có thể bỏ lại một server chưa tắt — tắt nốt, không thì JVM test không thoát. */
    @AfterEach
    void shutDownLeftovers() {
        running.forEach(server -> server.shutdown().run());
        running.clear();
    }

    @Test
    void walkingCharacterShouldComeBackWhereItReallyWas_afterRestart() throws Exception {
        GatewayBootstrap.Assembly first = startServer();
        String username = "walk" + suffix();
        long characterId;
        float startX;
        List<Integer> lastOpcodes;
        try (Player player = Player.connect(first)) {
            player.logIn(username);
            characterId = player.createCharacter();
            startX = player.select(characterId).getSpawnX();
            player.send(
                    Opcode.OPCODE_MOVE_COMMAND_VALUE,
                    MoveCommand.newBuilder().setDirX(1f).build().toByteArray());
            player.await(Opcode.OPCODE_MOVE_RESPONSE_VALUE);
            Thread.sleep(WALK_MILLIS); // phím vẫn giữ — không có lệnh nào nữa

            shutDown(first);

            lastOpcodes = player.readUntilClosed();
        }

        assertThat(lastOpcodes)
                .as("người chơi phải được báo là server tắt có chủ đích trước khi mất kết nối")
                .contains(Opcode.OPCODE_SERVER_SHUTTING_DOWN_EVENT_VALUE);
        float savedX = queryFloat("SELECT pos_x FROM character WHERE id = ?", characterId);
        assertThat(savedX - startX)
                .as("quãng đường đã đi mà pos_x trong DB còn giữ sau khi tắt")
                .isGreaterThan(MINIMUM_SAVED_DISTANCE);

        GatewayBootstrap.Assembly second = startServer();
        try (Player player = Player.connect(second)) {
            player.logIn(username);
            SelectCharacterResponse selected = player.select(characterId);
            assertThat(selected.getSuccess()).isTrue();
            assertThat(selected.getSpawnX()).isCloseTo(savedX, org.assertj.core.data.Offset.offset(POSITION_TOLERANCE));
            // Còn điều khiển được: không khoá phiên, không định tuyến mồ côi nào sống qua lần khởi động lại.
            player.send(
                    Opcode.OPCODE_MOVE_COMMAND_VALUE,
                    MoveCommand.newBuilder().setDirX(0f).build().toByteArray());
            MoveResponse moved = MoveResponse.parseFrom(
                    player.await(Opcode.OPCODE_MOVE_RESPONSE_VALUE).payload());
            assertThat(moved.getX()).isCloseTo(savedX, org.assertj.core.data.Offset.offset(POSITION_TOLERANCE));
        }
        shutDown(second);
    }

    /**
     * Một loạt lệnh mua còn đang bay lúc server tắt: mỗi lệnh hoặc chạy trọn (trừ vàng VÀ có hàng VÀ có
     * dòng audit) hoặc không chạy gì — không có lệnh nào dừng nửa chừng vì pool DB đóng trước mặt nó.
     */
    @Test
    void purchasesInFlightAtShutdownShouldBeAllOrNothing() throws Exception {
        GatewayBootstrap.Assembly server = startServer();
        long templateId = queryLong("SELECT id FROM item_template WHERE code = 'iron_scrap'");
        long price = queryLong("SELECT buy_price FROM shop_item WHERE item_template_id = ?", templateId);
        long characterId;
        try (ErrorLogCapture errorLog = ErrorLogCapture.attach();
                Player player = Player.connect(server)) {
            player.logIn("buy" + suffix());
            characterId = player.createCharacter();
            update("UPDATE character_stat SET gold = ? WHERE character_id = ?", START_GOLD, characterId);
            player.select(characterId);

            for (int i = 0; i < PURCHASES_IN_FLIGHT; i++) {
                player.send(
                        Opcode.OPCODE_BUY_ITEM_COMMAND_VALUE,
                        BuyItemCommand.newBuilder()
                                .setItemTemplateId(templateId)
                                .setQuantity(1)
                                .setIdempotencyKey(UUID.randomUUID().toString())
                                .build()
                                .toByteArray());
            }
            shutDown(server); // không chờ câu trả lời nào

            assertThat(errorLog.errorsSince(0))
                    .as("tắt server giữa loạt lệnh mua không được sinh log ERROR")
                    .isEmpty();
        }

        long bought = queryLong(
                "SELECT coalesce(sum(quantity), 0) FROM inventory_item WHERE character_id = ? AND item_template_id = ?",
                characterId,
                templateId);
        long gold = queryLong("SELECT gold FROM character_stat WHERE character_id = ?", characterId);
        LOG.info("Tắt giữa {} lệnh mua: {} lệnh đã chạy trọn", PURCHASES_IN_FLIGHT, bought);
        assertThat(START_GOLD - gold)
                .as("vàng đã trừ phải đúng bằng giá × số món thật sự có trong túi (mua được %s món)", bought)
                .isEqualTo(bought * price);
        assertThat(queryLong(
                        "SELECT count(*) FROM audit_log WHERE character_id = ? AND action = 'SHOP_BUY'", characterId))
                .as("mỗi món mua được có đúng một dòng audit")
                .isEqualTo(bought);
    }

    /**
     * Server tắt đúng lúc lệnh xác nhận THỨ HAI của một phiên giao dịch đang bay. Giao dịch hoặc xảy ra
     * trọn (vàng + món đổi chủ, phiên biến mất) hoặc không xảy ra gì (phiên còn nguyên) — và sau khi
     * khởi động lại, phiên còn sót không được khoá hai người chơi mãi mãi: huỷ được, mời lại được.
     */
    @Test
    void tradeConfirmedDuringShutdownShouldBeAllOrNothing_andNeverLockPlayersOutAfterRestart() throws Exception {
        GatewayBootstrap.Assembly first = startServer();
        String buyerName = "tb" + suffix();
        String sellerName = "ts" + suffix();
        long templateId = queryLong("SELECT id FROM item_template WHERE code = 'iron_scrap'");
        long buyerId;
        long sellerId;
        String sellerCharacterName;
        long sessionId;
        try (Player buyer = Player.connect(first);
                Player seller = Player.connect(first)) {
            buyer.logIn(buyerName);
            buyerId = buyer.createCharacter();
            seller.logIn(sellerName);
            sellerId = seller.createCharacter();
            sellerCharacterName = queryString("SELECT name FROM character WHERE id = ?", sellerId);
            update("UPDATE character_stat SET gold = ? WHERE character_id = ?", START_GOLD, buyerId);
            update(
                    "INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)"
                            + " VALUES (?, ?, ?, 0, 0)",
                    sellerId,
                    templateId,
                    START_ITEM_QUANTITY);
            long sellerItemId = queryLong("SELECT max(id) FROM inventory_item WHERE character_id = ?", sellerId);
            buyer.select(buyerId);
            seller.select(sellerId);

            buyer.send(
                    Opcode.OPCODE_REQUEST_TRADE_COMMAND_VALUE,
                    RequestTradeCommand.newBuilder()
                            .setTargetName(sellerCharacterName)
                            .build()
                            .toByteArray());
            RequestTradeResponse requested = RequestTradeResponse.parseFrom(
                    buyer.await(Opcode.OPCODE_REQUEST_TRADE_RESPONSE_VALUE).payload());
            assertThat(requested.getOutcome()).isEqualTo(RequestTradeOutcome.REQUEST_TRADE_OUTCOME_OK);
            sessionId = TradeRequestedEvent.parseFrom(seller.await(Opcode.OPCODE_TRADE_REQUESTED_EVENT_VALUE)
                            .payload())
                    .getSessionId();
            seller.send(
                    Opcode.OPCODE_ACCEPT_TRADE_REQUEST_COMMAND_VALUE,
                    AcceptTradeRequestCommand.newBuilder()
                            .setSessionId(sessionId)
                            .build()
                            .toByteArray());
            seller.await(Opcode.OPCODE_ACCEPT_TRADE_REQUEST_RESPONSE_VALUE);
            buyer.send(
                    Opcode.OPCODE_SET_TRADE_OFFER_COMMAND_VALUE,
                    SetTradeOfferCommand.newBuilder()
                            .setSessionId(sessionId)
                            .setGoldAmount(TRADE_GOLD)
                            .build()
                            .toByteArray());
            buyer.await(Opcode.OPCODE_SET_TRADE_OFFER_RESPONSE_VALUE);
            seller.send(
                    Opcode.OPCODE_SET_TRADE_OFFER_COMMAND_VALUE,
                    SetTradeOfferCommand.newBuilder()
                            .setSessionId(sessionId)
                            .addItems(TradeOfferItemInput.newBuilder()
                                    .setItemId(sellerItemId)
                                    .setQuantity(TRADE_ITEM_QUANTITY))
                            .build()
                            .toByteArray());
            seller.await(Opcode.OPCODE_SET_TRADE_OFFER_RESPONSE_VALUE);
            buyer.send(Opcode.OPCODE_CONFIRM_TRADE_COMMAND_VALUE, confirm(sessionId));
            buyer.await(Opcode.OPCODE_CONFIRM_TRADE_RESPONSE_VALUE);

            seller.send(Opcode.OPCODE_CONFIRM_TRADE_COMMAND_VALUE, confirm(sessionId));
            shutDown(first); // lệnh xác nhận thứ hai còn đang bay
        }

        long buyerGold = queryLong("SELECT gold FROM character_stat WHERE character_id = ?", buyerId);
        long sellerGold = queryLong("SELECT gold FROM character_stat WHERE character_id = ?", sellerId);
        long buyerItems = itemQuantity(buyerId, templateId);
        long sellerItems = itemQuantity(sellerId, templateId);
        boolean sessionLeft = queryLong("SELECT count(*) FROM trade_session WHERE id = ?", sessionId) > 0;
        LOG.info("Tắt giữa lệnh xác nhận giao dịch thứ hai: phiên còn lại trong DB = {}", sessionLeft);
        assertThat(buyerGold + sellerGold).as("tổng vàng bảo toàn").isEqualTo(START_GOLD);
        assertThat(buyerItems + sellerItems).as("tổng vật phẩm bảo toàn").isEqualTo(START_ITEM_QUANTITY);
        if (sessionLeft) {
            assertThat(new long[] {buyerGold, sellerGold, buyerItems, sellerItems})
                    .as("phiên còn ⇒ chưa có gì đổi chủ")
                    .containsExactly(START_GOLD, 0L, 0L, START_ITEM_QUANTITY);
        } else {
            assertThat(new long[] {buyerGold, sellerGold, buyerItems, sellerItems})
                    .as("phiên đã xoá ⇒ giao dịch xảy ra trọn")
                    .containsExactly(
                            START_GOLD - TRADE_GOLD,
                            TRADE_GOLD,
                            TRADE_ITEM_QUANTITY,
                            START_ITEM_QUANTITY - TRADE_ITEM_QUANTITY);
        }

        GatewayBootstrap.Assembly second = startServer();
        try (Player buyer = Player.connect(second)) {
            buyer.logIn(buyerName);
            buyer.select(buyerId);
            buyer.send(
                    Opcode.OPCODE_GET_TRADE_STATUS_REQUEST_VALUE,
                    GetTradeStatusRequest.getDefaultInstance().toByteArray());
            GetTradeStatusResponse status = GetTradeStatusResponse.parseFrom(
                    buyer.await(Opcode.OPCODE_GET_TRADE_STATUS_RESPONSE_VALUE).payload());
            assertThat(status.getInTrade())
                    .as("server mới phải thấy đúng thứ DB còn giữ về phiên giao dịch")
                    .isEqualTo(sessionLeft);
            if (sessionLeft) {
                buyer.send(
                        Opcode.OPCODE_CANCEL_TRADE_COMMAND_VALUE,
                        CancelTradeCommand.newBuilder()
                                .setSessionId(sessionId)
                                .build()
                                .toByteArray());
                assertThat(CancelTradeResponse.parseFrom(buyer.await(Opcode.OPCODE_CANCEL_TRADE_RESPONSE_VALUE)
                                        .payload())
                                .getOutcome())
                        .isEqualTo(CancelTradeOutcome.CANCEL_TRADE_OUTCOME_OK);
            }
            // Không còn kẹt: mời lại được (đối phương offline vẫn là một phiên chờ hợp lệ).
            buyer.send(
                    Opcode.OPCODE_REQUEST_TRADE_COMMAND_VALUE,
                    RequestTradeCommand.newBuilder()
                            .setTargetName(sellerCharacterName)
                            .build()
                            .toByteArray());
            assertThat(RequestTradeResponse.parseFrom(buyer.await(Opcode.OPCODE_REQUEST_TRADE_RESPONSE_VALUE)
                                    .payload())
                            .getOutcome())
                    .isEqualTo(RequestTradeOutcome.REQUEST_TRADE_OUTCOME_OK);
        }
        shutDown(second);
    }

    // ---------------------------------------------------------------------------------------------

    private GatewayBootstrap.Assembly startServer() throws Exception {
        ServerDatabaseBootstrap databaseBootstrap = new ServerDatabaseBootstrap(
                databaseConfig(),
                new RedisConfig(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT), REDIS_PASSWORD),
                DbMetricsPort.NO_OP);
        // Cổng 0 cho cả hai: hệ điều hành tự cấp, không đụng server dev đang chạy trên 7777/9404.
        GatewayBootstrap.Assembly server = GatewayBootstrap.assemble(
                databaseBootstrap,
                GatewayBootstrap.buildMetrics(),
                GatewayTracing.disabled(),
                new GatewayBootstrap.Endpoints(0, 0, TlsConfig.disabled()));
        running.add(server);
        GatewayBootstrap.startOrShutDown(server.server(), server.shutdown());
        return server;
    }

    private void shutDown(GatewayBootstrap.Assembly server) {
        running.remove(server);
        server.shutdown().run();
    }

    private static DatabaseConfig databaseConfig() {
        return new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static byte[] confirm(long sessionId) {
        return ConfirmTradeCommand.newBuilder()
                .setSessionId(sessionId)
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build()
                .toByteArray();
    }

    private static long itemQuantity(long characterId, long templateId) {
        return queryLong(
                "SELECT coalesce(sum(quantity), 0) FROM inventory_item WHERE character_id = ? AND item_template_id = ?",
                characterId,
                templateId);
    }

    private static long queryLong(String sql, Object... params) {
        return query(sql, rows -> rows.getLong(1), params);
    }

    private static float queryFloat(String sql, Object... params) {
        return query(sql, rows -> rows.getFloat(1), params);
    }

    private static String queryString(String sql, Object... params) {
        return query(sql, rows -> rows.getString(1), params);
    }

    @FunctionalInterface
    private interface RowReader<T> {
        T read(ResultSet rows) throws SQLException;
    }

    private static <T> T query(String sql, RowReader<T> reader, Object... params) {
        try (Connection connection = probe.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("truy vấn test-only không trả dòng nào: " + sql);
                }
                return reader.read(rows);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("truy vấn test-only lỗi: " + sql, e);
        }
    }

    private static void update(String sql, Object... params) {
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

    /** Một client qua socket TCP thật — đọc đồng bộ, bỏ qua gói không hỏi tới. */
    private static final class Player implements AutoCloseable {

        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private int nextSequenceId = 1;

        private Player(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
        }

        static Player connect(GatewayBootstrap.Assembly server) throws IOException {
            Socket socket =
                    new Socket(InetAddress.getLoopbackAddress(), server.server().port());
            socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
            return new Player(socket);
        }

        void logIn(String username) throws IOException {
            send(
                    Opcode.OPCODE_CONNECT_REQUEST_VALUE,
                    ConnectRequest.newBuilder().setUsername(username).build().toByteArray());
            assertThat(ConnectResponse.parseFrom(
                                    await(Opcode.OPCODE_CONNECT_RESPONSE_VALUE).payload())
                            .getSuccess())
                    .isTrue();
        }

        long createCharacter() throws IOException {
            send(
                    Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE,
                    CreateCharacterRequest.newBuilder()
                            .setName("Hero" + suffix())
                            .setIdempotencyKey(UUID.randomUUID().toString())
                            .build()
                            .toByteArray());
            CreateCharacterResponse created = CreateCharacterResponse.parseFrom(
                    await(Opcode.OPCODE_CREATE_CHARACTER_RESPONSE_VALUE).payload());
            assertThat(created.getSuccess()).isTrue();
            return created.getCharacter().getId();
        }

        SelectCharacterResponse select(long characterId) throws IOException {
            send(
                    Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE,
                    SelectCharacterRequest.newBuilder()
                            .setCharacterId(characterId)
                            .build()
                            .toByteArray());
            SelectCharacterResponse selected = SelectCharacterResponse.parseFrom(
                    await(Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE).payload());
            assertThat(selected.getSuccess()).isTrue();
            return selected;
        }

        void send(int opcode, byte[] payload) throws IOException {
            PacketHeader header = new PacketHeader(opcode, ProtocolVersion.CURRENT, nextSequenceId++);
            byte[] frame = PacketCodec.encodeFrame(header, payload);
            out.writeInt(frame.length);
            out.write(frame);
            out.flush();
        }

        PacketCodec.Frame await(int opcode) throws IOException {
            for (int i = 0; i < FRAMES_BEFORE_GIVING_UP; i++) {
                PacketCodec.Frame frame = readFrame();
                if (frame.header().opcode() == opcode) {
                    return frame;
                }
            }
            throw new AssertionError("không thấy opcode " + opcode + " sau " + FRAMES_BEFORE_GIVING_UP + " gói");
        }

        /** Mọi opcode còn tới cho đến khi server đóng kết nối. */
        List<Integer> readUntilClosed() throws IOException {
            List<Integer> opcodes = new ArrayList<>();
            try {
                for (int i = 0; i < FRAMES_BEFORE_GIVING_UP; i++) {
                    opcodes.add(readFrame().header().opcode());
                }
            } catch (EOFException closed) {
                return opcodes;
            }
            throw new AssertionError("server không đóng kết nối sau " + FRAMES_BEFORE_GIVING_UP + " gói");
        }

        private PacketCodec.Frame readFrame() throws IOException {
            int length = in.readInt();
            byte[] bytes = new byte[length];
            in.readFully(bytes);
            return PacketCodec.decodeFrame(bytes);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
