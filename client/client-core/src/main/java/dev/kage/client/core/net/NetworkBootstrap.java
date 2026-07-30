package dev.kage.client.core.net;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.ListCharactersRequest;
import dev.kage.protocol.ListCharactersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Chuỗi handshake login → chọn nhân vật, chạy MỘT LẦN lúc khởi động, TRƯỚC khi game loop bắt đầu
 * (không phải trong {@code fixedUpdate}/{@code render} — CLIENT.md §8 chỉ cấm xử lý packet GIỮA
 * lúc render, không cấm block lúc khởi động khi chưa có gì để render).
 *
 * <p>⚠️ Đơn giản hoá có chủ đích: KHÔNG có màn hình Login/CharacterSelect Scene2D thật
 * (UI.md — việc riêng, ngoài phạm vi lát cắt movement này) — tự động dùng nhân vật ĐẦU TIÊN có
 * sẵn, tự tạo nếu chưa có. Đủ để verify "2 client thấy nhau di chuyển" (ROADMAP Phase 3 Exit
 * Criteria); màn hình chọn nhân vật thật là việc tiếp theo, không phải của lát cắt này.
 */
public final class NetworkBootstrap {

    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private NetworkBootstrap() {}

    /** @return {@code characterId} đã chọn — sẵn sàng để gameplay Command dùng ngay. */
    public static long connectAndSelectCharacter(GameConnection connection, String username)
            throws IOException, TimeoutException {
        awaitConnected(connection);

        ConnectRequest connectRequest =
                ConnectRequest.newBuilder().setUsername(username).build();
        byte[] connectPayload = sendAndAwait(
                connection,
                Opcode.OPCODE_CONNECT_REQUEST_VALUE,
                Opcode.OPCODE_CONNECT_RESPONSE_VALUE,
                connectRequest.toByteArray());
        ConnectResponse connectResponse = ConnectResponse.parseFrom(connectPayload);
        if (!connectResponse.getSuccess()) {
            throw new IOException("Đăng nhập thất bại cho username: " + username);
        }

        long characterId = findOrCreateCharacter(connection, username);

        SelectCharacterRequest selectRequest =
                SelectCharacterRequest.newBuilder().setCharacterId(characterId).build();
        byte[] selectPayload = sendAndAwait(
                connection,
                Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE,
                Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE,
                selectRequest.toByteArray());
        SelectCharacterResponse selectResponse = SelectCharacterResponse.parseFrom(selectPayload);
        if (!selectResponse.getSuccess()) {
            throw new IOException("Chọn nhân vật thất bại: characterId=" + characterId);
        }
        return characterId;
    }

    private static long findOrCreateCharacter(GameConnection connection, String username) throws IOException {
        byte[] listPayload = sendAndAwait(
                connection,
                Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE,
                Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE,
                ListCharactersRequest.newBuilder().build().toByteArray());
        ListCharactersResponse listResponse = ListCharactersResponse.parseFrom(listPayload);
        if (listResponse.getCharactersCount() > 0) {
            return listResponse.getCharacters(0).getId();
        }

        // Character.validateName: chỉ chữ/số (feature-character/CLAUDE.md) — lọc username.
        String characterName = username.replaceAll("[^a-zA-Z0-9]", "");
        CreateCharacterRequest createRequest = CreateCharacterRequest.newBuilder()
                .setName(characterName)
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        byte[] createPayload = sendAndAwait(
                connection,
                Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE,
                Opcode.OPCODE_CREATE_CHARACTER_RESPONSE_VALUE,
                createRequest.toByteArray());
        CreateCharacterResponse createResponse = CreateCharacterResponse.parseFrom(createPayload);
        if (!createResponse.getSuccess()) {
            throw new IOException("Tạo nhân vật thất bại cho username: " + username);
        }
        return createResponse.getCharacter().getId();
    }

    private static void awaitConnected(GameConnection connection) throws TimeoutException {
        Instant deadline = Instant.now().plus(CONNECT_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (connection.isConnected()) {
                return;
            }
            sleepQuietly();
        }
        throw new TimeoutException("Không kết nối được tới server sau " + CONNECT_TIMEOUT);
    }

    private static byte[] sendAndAwait(
            GameConnection connection, int requestOpcode, int expectedResponseOpcode, byte[] payload)
            throws IOException {
        int sequenceId = connection.nextSequenceId();
        connection.send(requestOpcode, sequenceId, payload);

        Instant deadline = Instant.now().plus(STEP_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            PacketCodec.Frame frame = connection.pollInbound();
            if (frame == null) {
                sleepQuietly();
                continue;
            }
            if (frame.header().opcode() == expectedResponseOpcode) {
                return frame.payload();
            }
            if (frame.header().opcode() == Opcode.OPCODE_ERROR_VALUE) {
                throw new IOException("Server từ chối opcode=" + requestOpcode + " (ErrorResponse)");
            }
            // Opcode khác (VD PongResponse của heartbeat) — bỏ qua, không phải phần handshake này.
        }
        throw new IOException("Không nhận được response opcode=" + expectedResponseOpcode + " sau " + STEP_TIMEOUT);
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
