package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
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
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.ListCharactersRequest;
import dev.kage.protocol.ListCharactersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.PongResponse;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Test TOÀN BỘ pipeline (framing + codec + handler) qua {@link EmbeddedChannel} — không cần
 * socket thật (TESTING.md §2: "server-gateway: Netty client giả kết nối thật qua Netty
 * embedded"). Cùng cấu trúc pipeline với {@link GatewayBootstrap} — nếu hai bên lệch nhau, đây
 * là chỗ phải đồng bộ lại.
 */
class GatewayPipelineTest {

    @Test
    void shouldReplyPongToPing_withSameSequenceId() throws InvalidProtocolBufferException {
        var channel = newPipeline();
        var ping = PingCommand.newBuilder().setClientTimestampMs(123L).build();
        var header = new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, 7);

        writeInboundFrame(channel, header, ping.toByteArray());
        var response = readOutboundFrame(channel);

        // Response PHẢI khớp sequenceId của Command (NETWORK.md §105 request/response correlation).
        assertThat(response.header().opcode()).isEqualTo(Opcode.OPCODE_PONG_VALUE);
        assertThat(response.header().sequenceId()).isEqualTo(7);

        var pong = PongResponse.parseFrom(response.payload());
        assertThat(pong.getClientTimestampMs()).isEqualTo(123L);
        assertThat(pong.getServerTimestampMs()).isGreaterThan(0);
    }

    @Test
    void shouldReplyConnectResponse_toConnectRequest() throws InvalidProtocolBufferException {
        var channel = newPipeline();
        var request = ConnectRequest.newBuilder().setUsername("kagehero").build();
        var header = new PacketHeader(Opcode.OPCODE_CONNECT_REQUEST_VALUE, ProtocolVersion.CURRENT, 1);

        writeInboundFrame(channel, header, request.toByteArray());
        var response = readOutboundFrame(channel);

        assertThat(response.header().opcode()).isEqualTo(Opcode.OPCODE_CONNECT_RESPONSE_VALUE);
        var connectResponse = ConnectResponse.parseFrom(response.payload());
        assertThat(connectResponse.getSuccess()).isTrue();
        assertThat(connectResponse.getSessionId()).isNotBlank();
        assertThat(connectResponse.getAccountId()).isPositive();
    }

    @Test
    void shouldReplyError_forUnknownOpcode() {
        var channel = newPipeline();
        var header = new PacketHeader(9999, ProtocolVersion.CURRENT, 5);

        writeInboundFrame(channel, header, new byte[0]);
        var response = readOutboundFrame(channel);

        assertThat(response.header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
    }

    @Test
    void shouldRejectListCharacters_whenNotConnectedYet() {
        var channel = newPipeline();
        var header = new PacketHeader(Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE, ProtocolVersion.CURRENT, 1);

        writeInboundFrame(channel, header, ListCharactersRequest.newBuilder().build().toByteArray());
        var response = readOutboundFrame(channel);

        // accountId gắn vào CONNECTION lúc Connect thành công (xem GatewayChannelHandler) — chưa
        // Connect thì chưa có accountId, phải từ chối chứ không đọc packet.
        assertThat(response.header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
    }

    @Test
    void shouldCreateThenListCharacter_afterConnect() throws InvalidProtocolBufferException {
        var channel = newPipeline();
        connect(channel, "kagehero2");

        var createRequest = CreateCharacterRequest.newBuilder()
                .setName("KageHero")
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, ProtocolVersion.CURRENT, 2),
                createRequest.toByteArray());
        var createResponseFrame = readOutboundFrame(channel);
        assertThat(createResponseFrame.header().opcode()).isEqualTo(Opcode.OPCODE_CREATE_CHARACTER_RESPONSE_VALUE);
        var createResponse = CreateCharacterResponse.parseFrom(createResponseFrame.payload());
        assertThat(createResponse.getSuccess()).isTrue();
        assertThat(createResponse.getCharacter().getName()).isEqualTo("KageHero");

        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE, ProtocolVersion.CURRENT, 3),
                ListCharactersRequest.newBuilder().build().toByteArray());
        var listResponseFrame = readOutboundFrame(channel);
        assertThat(listResponseFrame.header().opcode()).isEqualTo(Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE);
        var listResponse = ListCharactersResponse.parseFrom(listResponseFrame.payload());
        assertThat(listResponse.getCharactersList()).extracting(c -> c.getName()).containsExactly("KageHero");
    }

    @Test
    void shouldSelectCharacter_afterCreatingIt() throws InvalidProtocolBufferException {
        var channel = newPipeline();
        connect(channel, "selecttest1");
        var characterId = createCharacter(channel, "SelectHero", 2);

        var request = SelectCharacterRequest.newBuilder().setCharacterId(characterId).build();
        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, ProtocolVersion.CURRENT, 3),
                request.toByteArray());
        var responseFrame = readOutboundFrame(channel);

        assertThat(responseFrame.header().opcode()).isEqualTo(Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE);
        var response = SelectCharacterResponse.parseFrom(responseFrame.payload());
        assertThat(response.getSuccess()).isTrue();
    }

    @Test
    void shouldRejectSelectCharacter_whenBelongsToDifferentAccount() throws InvalidProtocolBufferException {
        var characterPort = new FakeCharacterPort();
        // Nhân vật thuộc account KHÁC — không tạo được qua chính kết nối test này (login luôn tạo
        // đúng account của username đang dùng) — seed thẳng vào Fake Port để mô phỏng đúng kịch
        // bản IDOR mà SECURITY.md §4 mô tả.
        var otherAccountCharacter =
                characterPort.create(null, new NewCharacter(999L, "OtherHero", Faction.HOA, "seed-idem"));

        var channel = newPipeline(characterPort);
        connect(channel, "selecttest2");

        var request = SelectCharacterRequest.newBuilder().setCharacterId(otherAccountCharacter.id()).build();
        writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, ProtocolVersion.CURRENT, 2),
                request.toByteArray());
        var responseFrame = readOutboundFrame(channel);

        assertThat(responseFrame.header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
    }

    private static void connect(EmbeddedChannel channel, String username) {
        var request = ConnectRequest.newBuilder().setUsername(username).build();
        var header = new PacketHeader(Opcode.OPCODE_CONNECT_REQUEST_VALUE, ProtocolVersion.CURRENT, 1);
        writeInboundFrame(channel, header, request.toByteArray());
        readOutboundFrame(channel); // tiêu thụ ConnectResponse — caller không kiểm tra lại nội dung
    }

    private static long createCharacter(EmbeddedChannel channel, String name, int sequenceId)
            throws InvalidProtocolBufferException {
        var request = CreateCharacterRequest.newBuilder()
                .setName(name)
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        var header =
                new PacketHeader(Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, ProtocolVersion.CURRENT, sequenceId);
        writeInboundFrame(channel, header, request.toByteArray());
        var response = CreateCharacterResponse.parseFrom(readOutboundFrame(channel).payload());
        return response.getCharacter().getId();
    }

    private static EmbeddedChannel newPipeline() {
        return newPipeline(new FakeCharacterPort());
    }

    private static EmbeddedChannel newPipeline(FakeCharacterPort characterPort) {
        // ⚠️ Phải khớp CHÍNH XÁC pipeline thật trong GatewayBootstrap — lệch ở đây là test giả.
        // AccountFacade/CharacterFacade THẬT (không mock), chỉ Port bên dưới là Fake (TESTING.md
        // §4) — cùng kiểu Fake với *UseCaseTest ở feature-account/feature-character. Executor
        // chạy ĐỒNG BỘ (Runnable::run) — handler thật đẩy việc chạm DB sang authExecutor (Virtual
        // Thread ở production, SERVER.md §2); test cần đồng bộ để readOutboundFrame không bị race.
        // characterPort truyền vào từ ngoài để test seed sẵn dữ liệu (VD nhân vật account khác,
        // xem shouldRejectSelectCharacter_whenBelongsToDifferentAccount).
        var loginUseCase = new LoginOrCreateAccountUseCase(
                new FakeAccountPort(), new FakeSessionPort(), action -> action.accept(null));
        var accountFacade = new AccountFacade(loginUseCase);

        var createCharacterUseCase = new CreateCharacterUseCase(characterPort, action -> action.accept(null));
        var listCharactersUseCase = new ListCharactersUseCase(characterPort);
        var selectCharacterUseCase = new SelectCharacterUseCase(characterPort);
        var characterFacade =
                new CharacterFacade(createCharacterUseCase, listCharactersUseCase, selectCharacterUseCase);

        return new EmbeddedChannel(
                new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 4),
                new LengthFieldPrepender(4),
                new PacketFrameDecoder(),
                new PacketFrameEncoder(),
                new GatewayChannelHandler(accountFacade, characterFacade, Runnable::run));
    }

    /** Fake = implementation in-memory THẬT (TESTING.md §4), không Mockito. */
    static final class FakeAccountPort implements AccountPort {
        private final Map<String, Account> byUsername = new HashMap<>();
        private final AtomicLong nextId = new AtomicLong(1);

        @Override
        public Account findOrCreate(Transaction tx, String username) {
            return byUsername.computeIfAbsent(username, u -> new Account(nextId.getAndIncrement(), u, Instant.now()));
        }
    }

    static final class FakeSessionPort implements SessionPort {
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
    }

    static final class FakeCharacterPort implements CharacterPort {
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
    }

    private static void writeInboundFrame(EmbeddedChannel channel, PacketHeader header, byte[] payload) {
        byte[] frameBytes = PacketCodec.encodeFrame(header, payload);
        ByteBuf buf = Unpooled.buffer(4 + frameBytes.length);
        buf.writeInt(frameBytes.length);
        buf.writeBytes(frameBytes);
        channel.writeInbound(buf);
    }

    private static PacketCodec.Frame readOutboundFrame(EmbeddedChannel channel) {
        // ⚠️ PHÁT HIỆN THẬT: LengthFieldPrepender là MessageToMessageEncoder<ByteBuf> — encode()
        // sinh ra HAI message riêng biệt (buffer length-prefix + buffer content gốc qua retain()),
        // không gộp thành 1 buffer. Qua socket TCP thật thì vô hình (TCP là stream, tự nối lại),
        // nhưng EmbeddedChannel.readOutbound() trả về từng message một lần gọi — phải gom đủ.
        ByteBuf combined = Unpooled.buffer();
        ByteBuf part;
        while ((part = channel.readOutbound()) != null) {
            combined.writeBytes(part);
            part.release();
        }
        assertThat(combined.readableBytes())
                .as("server phải trả lời — không có outbound message nào")
                .isGreaterThan(0);
        int length = combined.readInt();
        byte[] bytes = new byte[length];
        combined.readBytes(bytes);
        return PacketCodec.decodeFrame(bytes);
    }
}
