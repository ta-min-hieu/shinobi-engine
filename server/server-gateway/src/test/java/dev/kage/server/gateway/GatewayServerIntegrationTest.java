package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.account.api.AccountFacade;
import dev.kage.account.application.LoginOrCreateAccountUseCase;
import dev.kage.account.domain.Account;
import dev.kage.account.domain.AccountPort;
import dev.kage.account.domain.SessionPort;
import dev.kage.account.domain.Transaction;
import dev.kage.character.api.CharacterFacade;
import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.ListCharactersUseCase;
import dev.kage.character.application.SelectCharacterUseCase;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.PongResponse;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.server.world.WorldFacade;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Test end-to-end bằng SOCKET TCP THẬT (không {@code EmbeddedChannel}) — verify đúng ROADMAP
 * Phase 2 Exit Criteria "Client kết nối server, ping/pong hoạt động" qua chính giao thức dây,
 * không chỉ pipeline nội bộ.
 *
 * <p>Dùng {@code java.net.Socket} thuần (không {@code GameConnection} của client-core) để tránh
 * server-gateway phụ thuộc ngược vào client — module server tự đủ để test chính nó.
 */
class GatewayServerIntegrationTest {

    private GatewayServer server;
    private WorldFacade worldFacade;
    private CharacterPort characterPort;

    @BeforeEach
    void startServer() throws InterruptedException {
        // port 0 = OS tự cấp cổng trống — tránh đụng cổng khi nhiều test chạy song song.
        // AccountFacade/CharacterFacade THẬT với Fake Port (không server-database thật) — test
        // này chỉ verify ping/pong qua socket thật, không chạm Connect/Character, nhưng
        // GatewayServer luôn cần cả 2 Facade để khởi tạo (xem GatewayServer).
        var characterFacade = fakeCharacterFacade();
        var characterRegistry = new ConnectedCharacterRegistry();
        worldFacade = new WorldFacade(characterRegistry);
        worldFacade.start();
        var positionPersister =
                new CharacterPositionPersister(characterFacade, characterRegistry, Duration.ofMinutes(5));
        var worldGateway = new WorldGateway(worldFacade, characterRegistry, positionPersister);
        server = new GatewayServer(0, fakeAccountFacade(), characterFacade, worldGateway);
        server.start();
    }

    private static AccountFacade fakeAccountFacade() {
        var accountPort = new AccountPort() {
            private final Map<String, Account> byUsername = new HashMap<>();
            private final AtomicLong nextId = new AtomicLong(1);

            @Override
            public Account findOrCreate(Transaction tx, String username) {
                return byUsername.computeIfAbsent(
                        username, u -> new Account(nextId.getAndIncrement(), u, Instant.now()));
            }
        };
        var sessionPort = new SessionPort() {
            private final Map<String, Long> tokens = new HashMap<>();

            @Override
            public String issue(long accountId) {
                var token = "fake-token-" + accountId;
                tokens.put(token, accountId);
                return token;
            }

            @Override
            public Optional<Long> validateAndRenew(String token) {
                return Optional.ofNullable(tokens.get(token));
            }

            @Override
            public void revoke(String token) {
                tokens.remove(token);
            }
        };
        var useCase = new LoginOrCreateAccountUseCase(accountPort, sessionPort, action -> action.accept(null));
        return new AccountFacade(useCase);
    }

    private CharacterFacade fakeCharacterFacade() {
        characterPort = new CharacterPort() {
            private final List<Character> characters = new ArrayList<>();
            private final AtomicLong nextId = new AtomicLong(1);
            private final Map<Long, PositionSnapshot> positions = new HashMap<>();

            @Override
            public PositionSnapshot loadPosition(long characterId) {
                return positions.getOrDefault(characterId, new PositionSnapshot(0f, 0f));
            }

            @Override
            public void savePosition(long characterId, float x, float y) {
                positions.put(characterId, new PositionSnapshot(x, y));
            }

            // Số liệu khởi điểm PLACEHOLDER (khớp PostgresCharacterAdapter.STARTING_STAT) — test
            // socket thật ở file này verify pipeline combat, không verify số liệu chỉ số cụ thể.
            @Override
            public CharacterStats loadStats(long characterId) {
                return new CharacterStats(10, 10, 10, 10);
            }

            @Override
            public List<Character> listByAccount(long accountId) {
                return characters.stream().filter(c -> c.accountId() == accountId).toList();
            }

            @Override
            public int countByAccount(long accountId) {
                return listByAccount(accountId).size();
            }

            @Override
            public boolean existsByName(String name) {
                return characters.stream().anyMatch(c -> c.name().equals(name));
            }

            @Override
            public Character create(dev.kage.character.domain.Transaction tx, NewCharacter newCharacter) {
                var character = new Character(
                        nextId.getAndIncrement(),
                        newCharacter.accountId(),
                        1,
                        newCharacter.name(),
                        newCharacter.faction(),
                        1,
                        0,
                        0,
                        Instant.now());
                characters.add(character);
                return character;
            }
        };
        var createUseCase = new CreateCharacterUseCase(characterPort, action -> action.accept(null));
        var listUseCase = new ListCharactersUseCase(characterPort);
        var selectUseCase = new SelectCharacterUseCase(characterPort);
        return new CharacterFacade(createUseCase, listUseCase, selectUseCase, characterPort);
    }

    @AfterEach
    void stopServer() {
        server.stop();
        worldFacade.stop();
    }

    @Test
    @Timeout(15)
    void shouldSavePosition_whenServerShutsDownWhileClientConnected() throws Exception {
        // PHÁT HIỆN THẬT (xem javadoc GatewayServer#stop): bản trước tắt authExecutor TRƯỚC khi
        // đóng channel — channelInactive lúc shutdown bị RejectedExecutionException âm thầm, mất
        // vị trí cuối. Test này verify trực tiếp qua Fake Port, không chỉ đọc code.
        try (var socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            var in = new DataInputStream(socket.getInputStream());
            long characterId = connectCreateAndSelect(socket, in, "shutdowntest");

            var moveCommand = MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 10, moveCommand.toByteArray());
            readUntilOpcode(in, Opcode.OPCODE_MOVE_RESPONSE_VALUE); // xác nhận world đã bắt đầu di chuyển

            Thread.sleep(200); // để vài tick 60Hz chạy — x phải > 0 lúc này nếu đang di chuyển thật

            // MoveCommand thứ 2 (dừng lại) — MoveResponse trả về là vị trí HIỆN TẠI, dùng làm giá
            // trị kỳ vọng để so sánh với DB sau khi tắt server (không đoán khoảng cách đã đi).
            var stopCommand = MoveCommand.newBuilder().setDirX(0f).setDirY(0f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 11, stopCommand.toByteArray());
            var stopResponse =
                    MoveResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_MOVE_RESPONSE_VALUE).payload());
            assertThat(stopResponse.getX()).isGreaterThan(0f); // sanity: di chuyển thật đã xảy ra

            server.stop(); // KHÔNG đợi @AfterEach — test tự gọi để verify NGAY sau khi trả về

            var savedPosition = characterPort.loadPosition(characterId);
            assertThat(savedPosition.x()).isEqualTo(stopResponse.getX());
        }
    }

    @Test
    @Timeout(10)
    void clientShouldReceivePong_overRealSocket() throws Exception {
        try (var socket = new Socket("127.0.0.1", server.port())) {
            var out = new DataOutputStream(socket.getOutputStream());
            var in = new DataInputStream(socket.getInputStream());

            var ping = PingCommand.newBuilder().setClientTimestampMs(999L).build();
            var header = new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, 1);
            byte[] frame = PacketCodec.encodeFrame(header, ping.toByteArray());
            out.writeInt(frame.length);
            out.write(frame);
            out.flush();

            var decoded = readFrame(in);

            assertThat(decoded.header().opcode()).isEqualTo(Opcode.OPCODE_PONG_VALUE);
            assertThat(decoded.header().sequenceId()).isEqualTo(1);

            var pong = PongResponse.parseFrom(decoded.payload());
            assertThat(pong.getClientTimestampMs()).isEqualTo(999L);
        }
    }

    @Test
    @Timeout(10)
    void serverShouldAcceptMultipleConnectionsConcurrently() throws Exception {
        try (var socketA = new Socket("127.0.0.1", server.port());
                var socketB = new Socket("127.0.0.1", server.port())) {
            sendPing(socketA, 1, 111L);
            sendPing(socketB, 1, 222L);

            var pongA = PongResponse.parseFrom(
                    readFrame(new DataInputStream(socketA.getInputStream())).payload());
            var pongB = PongResponse.parseFrom(
                    readFrame(new DataInputStream(socketB.getInputStream())).payload());

            // Mỗi kết nối nhận ĐÚNG pong của mình — không lẫn giữa các connection (SERVER.md §1).
            assertThat(pongA.getClientTimestampMs()).isEqualTo(111L);
            assertThat(pongB.getClientTimestampMs()).isEqualTo(222L);
        }
    }

    @Test
    @Timeout(15)
    void twoClientsShouldSeeEachOtherMove_overRealSockets() throws Exception {
        // Verify đúng Phase 3 Exit Criteria (ROADMAP.md): "2 client kết nối cùng server, thấy
        // nhau di chuyển" — qua chính giao thức dây (socket thật), không chỉ nội bộ WorldFacade.
        try (var socketA = new Socket("127.0.0.1", server.port());
                var socketB = new Socket("127.0.0.1", server.port())) {
            socketA.setSoTimeout(5000);
            socketB.setSoTimeout(5000);
            var inA = new DataInputStream(socketA.getInputStream());
            var inB = new DataInputStream(socketB.getInputStream());

            // Account.username giới hạn 3-20 ký tự — không dùng UUID đầy đủ được.
            var suffix = UUID.randomUUID().toString().substring(0, 8);
            long characterA = connectCreateAndSelect(socketA, inA, "a" + suffix);
            long characterB = connectCreateAndSelect(socketB, inB, "b" + suffix);

            // Cả 2 spawn cùng điểm (0,0) — vào AoI của nhau ngay tick kế tiếp sau khi attach.
            var spawnSeenByA = CharacterSpawnedEvent.parseFrom(
                    readUntilOpcode(inA, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE).payload());
            assertThat(spawnSeenByA.getCharacterId()).isEqualTo(characterB);
            var spawnSeenByB = CharacterSpawnedEvent.parseFrom(
                    readUntilOpcode(inB, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE).payload());
            assertThat(spawnSeenByB.getCharacterId()).isEqualTo(characterA);

            // A di chuyển — chỉ gửi HƯỚNG (movement.proto), server tự nhân tốc độ.
            var moveCommand = MoveCommand.newBuilder().setDirX(1f).setDirY(0f).build();
            sendFrame(socketA, Opcode.OPCODE_MOVE_COMMAND_VALUE, 10, moveCommand.toByteArray());

            // A tự nhận MoveResponse (reconciliation, CLIENT.md §6) khớp sequenceId đã gửi.
            var moveResponseFrame = readUntilOpcode(inA, Opcode.OPCODE_MOVE_RESPONSE_VALUE);
            assertThat(moveResponseFrame.header().sequenceId()).isEqualTo(10);
            var moveResponse = MoveResponse.parseFrom(moveResponseFrame.payload());
            assertThat(moveResponse.getX()).isGreaterThanOrEqualTo(0f);

            // B nhận CharacterMovedEvent về A (AoI — SERVER.md §6), vị trí đã đổi khỏi (0,0).
            var movedSeenByB = CharacterMovedEvent.parseFrom(
                    readUntilOpcode(inB, Opcode.OPCODE_CHARACTER_MOVED_EVENT_VALUE).payload());
            assertThat(movedSeenByB.getCharacterId()).isEqualTo(characterA);
            assertThat(movedSeenByB.getX()).isGreaterThan(0f);
        }
    }

    @Test
    @Timeout(15)
    void clientShouldDamageMonster_overRealSockets() throws Exception {
        // Verify Phase 4 lát cắt 1 qua chính giao thức dây: di chuyển tới gần quái bù nhìn (spawn
        // cố định (5,5), WorldFacade) → SetTargetCommand → nhận AttackResultEvent thật.
        try (var socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            var in = new DataInputStream(socket.getInputStream());
            long characterId = connectCreateAndSelect(socket, in, "atk" + UUID.randomUUID().toString().substring(0, 6));

            // Nhân vật mới spawn (0,0) — quái ở (5,5), cách ~7.07m, đã trong AoI (15m) nên
            // MonsterSpawnedEvent tới ngay, nhưng CHƯA trong tầm đánh (2m) — phải di chuyển lại gần.
            var monsterSpawned = MonsterSpawnedEvent.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE).payload());
            long monsterId = monsterSpawned.getMonsterId();

            // Hướng chéo (0.7071, 0.7071) — chuẩn hoá, giống hệt client thật gửi (server tự nhân
            // tốc độ, movement.proto). ~1.2s ở 5 m/s đưa nhân vật vào trong tầm đánh (còn cách quái ~1m).
            var moveCommand = MoveCommand.newBuilder().setDirX(0.7071f).setDirY(0.7071f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 10, moveCommand.toByteArray());
            Thread.sleep(1200);
            var stopCommand = MoveCommand.newBuilder().setDirX(0f).setDirY(0f).build();
            sendFrame(socket, Opcode.OPCODE_MOVE_COMMAND_VALUE, 11, stopCommand.toByteArray());

            var setTarget = SetTargetCommand.newBuilder().setTargetEntityId(monsterId).build();
            sendFrame(socket, Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, 12, setTarget.toByteArray());

            var attackResult = AttackResultEvent.parseFrom(
                    readUntilOpcode(in, Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE).payload());
            assertThat(attackResult.getAttackerEntityId()).isEqualTo(characterId);
            assertThat(attackResult.getTargetEntityId()).isEqualTo(monsterId);
        }
    }

    private static long connectCreateAndSelect(Socket socket, DataInputStream in, String username)
            throws Exception {
        var connectRequest = ConnectRequest.newBuilder().setUsername(username).build();
        sendFrame(socket, Opcode.OPCODE_CONNECT_REQUEST_VALUE, 1, connectRequest.toByteArray());
        ConnectResponse.parseFrom(readFrame(in).payload());

        // Character.validateName: chỉ chữ/số, KHÔNG dấu gạch ngang (feature-character/CLAUDE.md).
        var createRequest = CreateCharacterRequest.newBuilder()
                .setName("Hero" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        sendFrame(socket, Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, 2, createRequest.toByteArray());
        var createResponse = CreateCharacterResponse.parseFrom(readFrame(in).payload());
        long characterId = createResponse.getCharacter().getId();

        var selectRequest = SelectCharacterRequest.newBuilder().setCharacterId(characterId).build();
        sendFrame(socket, Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, 3, selectRequest.toByteArray());
        readFrame(in); // SelectCharacterResponse — nội dung đã verify ở GatewayPipelineTest

        return characterId;
    }

    private static void sendFrame(Socket socket, int opcode, int sequenceId, byte[] payload) throws Exception {
        var out = new DataOutputStream(socket.getOutputStream());
        var header = new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId);
        byte[] frame = PacketCodec.encodeFrame(header, payload);
        out.writeInt(frame.length);
        out.write(frame);
        out.flush();
    }

    /** AoI Event tới bất kỳ lúc nào giữa các Response khác — đọc tới khi thấy đúng opcode cần. */
    private static PacketCodec.Frame readUntilOpcode(DataInputStream in, int expectedOpcode) throws Exception {
        for (int attempt = 0; attempt < 1000; attempt++) {
            var frame = readFrame(in);
            if (frame.header().opcode() == expectedOpcode) {
                return frame;
            }
        }
        throw new AssertionError("Không thấy opcode " + expectedOpcode + " sau 1000 frame");
    }

    private static void sendPing(Socket socket, int sequenceId, long clientTimestampMs) throws Exception {
        var out = new DataOutputStream(socket.getOutputStream());
        var ping = PingCommand.newBuilder().setClientTimestampMs(clientTimestampMs).build();
        var header = new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, sequenceId);
        byte[] frame = PacketCodec.encodeFrame(header, ping.toByteArray());
        out.writeInt(frame.length);
        out.write(frame);
        out.flush();
    }

    private static PacketCodec.Frame readFrame(DataInputStream in) throws Exception {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return PacketCodec.decodeFrame(bytes);
    }
}
