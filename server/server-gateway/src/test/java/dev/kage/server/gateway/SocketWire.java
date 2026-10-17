package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import dev.kage.protocol.AcceptDuelCommand;
import dev.kage.protocol.AcceptDuelOutcome;
import dev.kage.protocol.AcceptDuelResponse;
import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.ChallengeDuelCommand;
import dev.kage.protocol.ChallengeDuelOutcome;
import dev.kage.protocol.ChallengeDuelResponse;
import dev.kage.protocol.CharacterDespawnedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.EnterDungeonCommand;
import dev.kage.protocol.EnterDungeonOutcome;
import dev.kage.protocol.EnterDungeonResponse;
import dev.kage.protocol.ExitDungeonCommand;
import dev.kage.protocol.ExitDungeonOutcome;
import dev.kage.protocol.ExitDungeonResponse;
import dev.kage.protocol.GetPvpRatingRequest;
import dev.kage.protocol.GetPvpRatingResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Đọc/ghi frame qua socket TCP thật cho test gateway (không TLS) — tách khỏi
 * {@code GatewayServerIntegrationTest} (README dòng 58(b), 2026-10-17): lớp đó chạm trần độ phức tạp PMD
 * của cả lớp, và đây là phần không thuộc riêng một kịch bản nào.
 */
final class SocketWire {

    private SocketWire() {}

    /** Socket loopback tới {@code port}, đọc chờ tối đa {@code timeoutMillis}. */
    static Socket openSocket(int port, int timeoutMillis) throws Exception {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        socket.setSoTimeout(timeoutMillis);
        return socket;
    }

    /** Thách đấu nhân vật tên {@code targetName}; khẳng định server nhận lời thách — trả id lời thách. */
    static long challengeDuel(Socket socket, DataInputStream in, String targetName, int sequenceId) throws Exception {
        ChallengeDuelCommand challenge =
                ChallengeDuelCommand.newBuilder().setTargetName(targetName).build();
        sendFrame(socket, Opcode.OPCODE_CHALLENGE_DUEL_COMMAND_VALUE, sequenceId, challenge.toByteArray());
        ChallengeDuelResponse response = ChallengeDuelResponse.parseFrom(
                readUntilOpcode(in, Opcode.OPCODE_CHALLENGE_DUEL_RESPONSE_VALUE).payload());
        assertThat(response.getOutcome()).isEqualTo(ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_OK);
        return response.getChallengeId();
    }

    /** Chấp nhận lời thách {@code challengeId} rồi đọc tới response của nó. */
    static AcceptDuelOutcome acceptDuel(Socket socket, DataInputStream in, long challengeId, int sequenceId)
            throws Exception {
        AcceptDuelCommand accept =
                AcceptDuelCommand.newBuilder().setChallengeId(challengeId).build();
        sendFrame(socket, Opcode.OPCODE_ACCEPT_DUEL_COMMAND_VALUE, sequenceId, accept.toByteArray());
        return AcceptDuelResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_ACCEPT_DUEL_RESPONSE_VALUE)
                        .payload())
                .getOutcome();
    }

    /** Hỏi rating đấu trường của chính nhân vật trên kết nối này. */
    static GetPvpRatingResponse requestPvpRating(Socket socket, DataInputStream in, int sequenceId) throws Exception {
        sendFrame(
                socket,
                Opcode.OPCODE_GET_PVP_RATING_REQUEST_VALUE,
                sequenceId,
                GetPvpRatingRequest.getDefaultInstance().toByteArray());
        return GetPvpRatingResponse.parseFrom(
                readUntilOpcode(in, Opcode.OPCODE_GET_PVP_RATING_RESPONSE_VALUE).payload());
    }

    /** Gửi {@code EnterDungeonCommand} vào hầm ngục mẫu rồi đọc tới response của nó. */
    static EnterDungeonOutcome enterDungeon(Socket socket, DataInputStream in, int sequenceId) throws Exception {
        EnterDungeonCommand command = EnterDungeonCommand.newBuilder()
                .setDungeonCode("sample_dungeon")
                .build();
        sendFrame(socket, Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE, sequenceId, command.toByteArray());
        return EnterDungeonResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE)
                        .payload())
                .getOutcome();
    }

    /** Gửi {@code ExitDungeonCommand} rồi đọc tới response của nó. */
    static ExitDungeonOutcome exitDungeon(Socket socket, DataInputStream in, int sequenceId) throws Exception {
        sendFrame(
                socket,
                Opcode.OPCODE_EXIT_DUNGEON_COMMAND_VALUE,
                sequenceId,
                ExitDungeonCommand.getDefaultInstance().toByteArray());
        return ExitDungeonResponse.parseFrom(readUntilOpcode(in, Opcode.OPCODE_EXIT_DUNGEON_RESPONSE_VALUE)
                        .payload())
                .getOutcome();
    }

    /** Id nhân vật của {@code count} gói {@code CharacterSpawnedEvent} kế tiếp (bỏ qua gói khác). */
    static Set<Long> readSpawnedCharacterIds(DataInputStream in, int count) throws Exception {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ids.add(CharacterSpawnedEvent.parseFrom(readUntilOpcode(in, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE)
                            .payload())
                    .getCharacterId());
        }
        return ids;
    }

    /** Id nhân vật của {@code count} gói {@code CharacterDespawnedEvent} kế tiếp (bỏ qua gói khác). */
    static Set<Long> readDespawnedCharacterIds(DataInputStream in, int count) throws Exception {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ids.add(CharacterDespawnedEvent.parseFrom(readUntilOpcode(in, Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE)
                            .payload())
                    .getCharacterId());
        }
        return ids;
    }

    /** Tên nhân vật THẬT (sinh ngẫu nhiên "Hero...") — KHÁC {@code username} tài khoản. */
    record CreatedCharacter(long characterId, String name) {}

    /**
     * Như {@link #connectCreateAndSelect} nhưng trả thêm tên nhân vật THẬT — cần cho
     * {@code ChallengeDuelCommand.target_name} (server tra theo tên nhân vật, không phải username
     * tài khoản, xem javadoc {@code CreatedCharacter}).
     */
    static CreatedCharacter connectCreateAndSelectNamed(Socket socket, DataInputStream in, String username)
            throws Exception {
        ConnectRequest connectRequest =
                ConnectRequest.newBuilder().setUsername(username).build();
        sendFrame(socket, Opcode.OPCODE_CONNECT_REQUEST_VALUE, 1, connectRequest.toByteArray());
        assertThat(ConnectResponse.parseFrom(readFrame(in).payload()).getSuccess())
                .isTrue();

        // Character.validateName: chỉ chữ/số, KHÔNG dấu gạch ngang (feature-character/CLAUDE.md).
        CreateCharacterRequest createRequest = CreateCharacterRequest.newBuilder()
                .setName("Hero" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        sendFrame(socket, Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, 2, createRequest.toByteArray());
        CreateCharacterResponse createResponse =
                CreateCharacterResponse.parseFrom(readFrame(in).payload());
        long characterId = createResponse.getCharacter().getId();
        String name = createResponse.getCharacter().getName();

        SelectCharacterRequest selectRequest =
                SelectCharacterRequest.newBuilder().setCharacterId(characterId).build();
        sendFrame(socket, Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, 3, selectRequest.toByteArray());
        readFrame(in); // SelectCharacterResponse — nội dung đã verify ở GatewayPipelineTest

        return new CreatedCharacter(characterId, name);
    }

    /** Đăng nhập (tự tạo tài khoản {@code username}), tạo một nhân vật rồi vào world — trả id nhân vật. */
    static long connectCreateAndSelect(Socket socket, DataInputStream in, String username) throws Exception {
        return connectCreateAndSelectNamed(socket, in, username).characterId();
    }

    // synchronized trên chính socket: từ 2026-08-09 có luồng nền bấm đánh (startAttackSpammer) ghi
    // song song với luồng test chính trên CÙNG socket — 2 lời gọi write() đan nhau sẽ cắt đôi một
    // frame và hỏng cả dòng dữ liệu. Khoá ở đây thay vì bắt mọi chỗ gọi phải tự nhớ.
    static void sendFrame(Socket socket, int opcode, int sequenceId, byte[] payload) throws Exception {
        synchronized (socket) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            PacketHeader header = new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId);
            byte[] frame = PacketCodec.encodeFrame(header, payload);
            out.writeInt(frame.length);
            out.write(frame);
            out.flush();
        }
    }

    /**
     * Bấm ĐÁNH nền — gửi {@code AttackCommand} đều đặn trên socket này tới khi bị {@code interrupt}
     * hoặc socket đóng.
     *
     * <p>Cần từ 2026-08-09: đánh thường không còn tự động (chủ dự án quyết sau playtest, xem
     * {@code CombatSystem}), mà các test này chờ ĐỌC frame nên không thể vừa đọc vừa bấm trên cùng
     * một luồng. Nhịp 200ms dày hơn cooldown thật (1 đòn/giây) — lệnh thừa là no-op ở server.
     */
    // catch (Exception) là CỐ Ý: sendFrame khai báo `throws Exception`, và mọi lỗi ở luồng nền này
    // đều có đúng một nghĩa — socket đã đóng vì test kết thúc. Không có gì để xử lý riêng, cũng
    // không được để nó nổi lên làm hỏng test khác (cùng lý do WorldFacade.tickTask bọc Throwable).
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    static Thread startAttackSpammer(Socket socket) {
        byte[] payload = AttackCommand.getDefaultInstance().toByteArray();
        Thread spammer = new Thread(
                () -> {
                    for (int sequenceId = 9000; !Thread.currentThread().isInterrupted(); sequenceId++) {
                        try {
                            sendFrame(socket, Opcode.OPCODE_ATTACK_COMMAND_VALUE, sequenceId, payload);
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return; // test đã xong — kết thúc bình thường, không phải lỗi
                        } catch (Exception e) {
                            return; // socket đóng lúc test kết thúc — cùng lý do trên
                        }
                    }
                },
                "attack-spammer");
        spammer.setDaemon(true);
        spammer.start();
        return spammer;
    }

    /** AoI Event tới bất kỳ lúc nào giữa các Response khác — đọc tới khi thấy đúng opcode cần. */
    static PacketCodec.Frame readUntilOpcode(DataInputStream in, int expectedOpcode) throws Exception {
        java.util.Map<Integer, Integer> seen = new java.util.HashMap<>();
        for (int attempt = 0; attempt < 1000; attempt++) {
            PacketCodec.Frame frame = readFrame(in);
            if (frame.header().opcode() == expectedOpcode) {
                return frame;
            }
            seen.merge(frame.header().opcode(), 1, Integer::sum);
        }
        throw new AssertionError("Không thấy opcode " + expectedOpcode + " sau 1000 frame, đã thấy: " + seen);
    }

    static void sendPing(Socket socket, int sequenceId, long clientTimestampMs) throws Exception {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        PingCommand ping =
                PingCommand.newBuilder().setClientTimestampMs(clientTimestampMs).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_PING_VALUE, ProtocolVersion.CURRENT, sequenceId);
        byte[] frame = PacketCodec.encodeFrame(header, ping.toByteArray());
        out.writeInt(frame.length);
        out.write(frame);
        out.flush();
    }

    static PacketCodec.Frame readFrame(DataInputStream in) throws Exception {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return PacketCodec.decodeFrame(bytes);
    }
}
