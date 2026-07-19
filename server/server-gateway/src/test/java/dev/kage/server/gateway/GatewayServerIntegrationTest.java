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
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.NewCharacter;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.PongResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @BeforeEach
    void startServer() throws InterruptedException {
        // port 0 = OS tự cấp cổng trống — tránh đụng cổng khi nhiều test chạy song song.
        // AccountFacade/CharacterFacade THẬT với Fake Port (không server-database thật) — test
        // này chỉ verify ping/pong qua socket thật, không chạm Connect/Character, nhưng
        // GatewayServer luôn cần cả 2 Facade để khởi tạo (xem GatewayServer).
        server = new GatewayServer(0, fakeAccountFacade(), fakeCharacterFacade());
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

    private static CharacterFacade fakeCharacterFacade() {
        var characterPort = new CharacterPort() {
            private final List<Character> characters = new ArrayList<>();
            private final AtomicLong nextId = new AtomicLong(1);

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
        return new CharacterFacade(createUseCase, listUseCase);
    }

    @AfterEach
    void stopServer() {
        server.stop();
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
