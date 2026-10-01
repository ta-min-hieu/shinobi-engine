package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.Faction;
import dev.kage.protocol.ListCharactersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Tự đăng nhập lại sau khi đứt mạng — quyết định chủ dự án 2026-09-29. Dựng thẳng bằng một
 * {@link SessionRecovery.Link} giả: không socket, không libGDX.
 */
class SessionRecoveryTest {

    private static final long CHARACTER_ID = 42L;
    private static final String USERNAME = "tester";
    private static final float TICK = 1f / 60f;
    private static final String KICKED = "error.logged_in_elsewhere";

    // ---------------------------------------------------------------------------------------
    // Có bắt đầu hay không
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("Bị đá (error.logged_in_elsewhere) thì KHÔNG BAO GIỜ tự vào lại, kể cả khi socket cũng đã đứt")
    void kickNeverTriggersRelog() {
        // Server gửi lời đá rồi đóng kết nối — nên lúc bị đá, socket CŨNG đứt. Nếu chỉ nhìn socket
        // thì hai client đá nhau mãi: A vào lại đá B, B vào lại đá A...
        assertThat(SessionRecovery.shouldStart(KICKED, 1, 1, false)).isFalse();
        assertThat(SessionRecovery.shouldStart(KICKED, 1, 2, true)).isFalse();
        assertThat(SessionRecovery.shouldStart(KICKED, 1, 2, false)).isFalse();
    }

    @Test
    @DisplayName("Mạng đứt thật (không bị đá) thì tự vào lại — socket đang đứt, hoặc đã được dựng lại")
    void realLossTriggersRelog() {
        assertThat(SessionRecovery.shouldStart(null, 1, 1, false)).isTrue();
        // Socket đã được GameConnection dựng lại trước nhịp lấy mẫu này: đang "sống" nhưng là socket
        // CHƯA đăng nhập — phiên cũ vẫn chết.
        assertThat(SessionRecovery.shouldStart(null, 1, 2, true)).isTrue();
    }

    @Test
    @DisplayName("Phiên còn sống trên đúng socket của nó thì không làm gì")
    void healthySessionDoesNothing() {
        assertThat(SessionRecovery.shouldStart(null, 3, 3, true)).isFalse();
    }

    // ---------------------------------------------------------------------------------------
    // Chuỗi bắt tay
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("Đứt mạng → chờ socket → Connect → List → Select ĐÚNG nhân vật cũ → vào lại world")
    void relogsSameCharacter() throws IOException {
        FakeLink link = new FakeLink();
        SessionRecovery recovery = new SessionRecovery(link, USERNAME, CHARACTER_ID);

        assertThat(recovery.tick(TICK)).isEqualTo(SessionRecovery.Phase.WAITING_FOR_SOCKET);
        assertThat(link.sent).isEmpty();

        link.reconnect();
        assertThat(recovery.tick(TICK)).isEqualTo(SessionRecovery.Phase.AWAITING_CONNECT);
        assertThat(ConnectRequest.parseFrom(link.lastPayload()).getUsername()).isEqualTo(USERNAME);

        link.reply(Opcode.OPCODE_CONNECT_RESPONSE_VALUE, connectOk());
        assertThat(recovery.tick(TICK)).isEqualTo(SessionRecovery.Phase.AWAITING_CHARACTER_LIST);

        CharacterSummary levelledUp = summary(CHARACTER_ID, 11, Faction.FACTION_HOA);
        link.reply(
                Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE,
                ListCharactersResponse.newBuilder()
                        .addCharacters(summary(7L, 3, Faction.FACTION_THUY))
                        .addCharacters(levelledUp)
                        .build()
                        .toByteArray());
        assertThat(recovery.tick(TICK)).isEqualTo(SessionRecovery.Phase.AWAITING_SELECT);
        assertThat(SelectCharacterRequest.parseFrom(link.lastPayload()).getCharacterId())
                .isEqualTo(CHARACTER_ID);

        link.reply(Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE, selectOk());
        assertThat(recovery.tick(TICK)).isEqualTo(SessionRecovery.Phase.RESUMED);
        assertThat(recovery.resumed()).hasValueSatisfying(resumed -> {
            // Bản MỚI NHẤT từ server, không phải bản lúc vào world lần đầu.
            assertThat(resumed.character()).isEqualTo(levelledUp);
            assertThat(resumed.enterWorld().getCurrentHp()).isEqualTo(80);
        });
    }

    @Test
    @DisplayName("Gói tới SAU SelectCharacterResponse (spawn của World) được để lại cho màn world mới")
    void leavesWorldPacketsForTheNewScreen() {
        FakeLink link = new FakeLink();
        SessionRecovery recovery = handshakeUpToSelect(link);

        link.reply(Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE, selectOk());
        link.push(Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE, 0, new byte[0]);
        assertThat(recovery.tick(TICK)).isEqualTo(SessionRecovery.Phase.RESUMED);

        assertThat(link.inbound).hasSize(1);
        assertThat(link.inbound.peek().header().opcode()).isEqualTo(Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE);
    }

    @Test
    @DisplayName("Lỗi của một LỆNH KHÁC (not_authenticated cho lệnh gameplay lỡ gửi) không làm hỏng việc đăng nhập lại")
    void ignoresErrorsOfOtherCommands() {
        FakeLink link = new FakeLink();
        link.reconnect();
        SessionRecovery recovery = new SessionRecovery(link, USERNAME, CHARACTER_ID);
        recovery.tick(TICK);

        link.push(Opcode.OPCODE_ERROR_VALUE, link.lastSequenceId + 100, error("error.not_authenticated"));
        assertThat(recovery.tick(TICK)).isEqualTo(SessionRecovery.Phase.AWAITING_CONNECT);
    }

    @Test
    @DisplayName("Server từ chối đăng nhập ⇒ dừng ngay, nói đúng lý do của server, không thử lại")
    void authRejectedFailsWithServerReason() {
        FakeLink link = new FakeLink();
        link.reconnect();
        SessionRecovery recovery = new SessionRecovery(link, USERNAME, CHARACTER_ID);
        recovery.tick(TICK);

        link.reply(Opcode.OPCODE_ERROR_VALUE, error("error.invalid_username"));
        assertThat(recovery.tick(TICK)).isEqualTo(SessionRecovery.Phase.FAILED);
        assertThat(recovery.failureKey()).isEqualTo("error.invalid_username");
        assertThat(link.sent).hasSize(1);
    }

    @Test
    @DisplayName("Bị đá GIỮA lúc đang vào lại ⇒ dừng hẳn, không thử lại (không đá nhau mãi)")
    void kickDuringRecoveryStopsForGood() {
        FakeLink link = new FakeLink();
        SessionRecovery recovery = handshakeUpToSelect(link);
        int sentBefore = link.sent.size();

        // Lời đá không trả lời lệnh nào — sequenceId 0, không khớp yêu cầu đang chờ.
        link.push(Opcode.OPCODE_ERROR_VALUE, 0, error(KICKED));
        assertThat(recovery.tick(TICK)).isEqualTo(SessionRecovery.Phase.FAILED);
        assertThat(recovery.failureKey()).isEqualTo(KICKED);

        for (int i = 0; i < 60 * 120; i++) {
            recovery.tick(TICK);
        }
        assertThat(link.sent).hasSize(sentBefore);
    }

    @Test
    @DisplayName("Nhân vật không còn trong danh sách ⇒ dừng, không bao giờ chọn nhân vật khác")
    void neverSelectsAnotherCharacter() {
        FakeLink link = new FakeLink();
        link.reconnect();
        SessionRecovery recovery = new SessionRecovery(link, USERNAME, CHARACTER_ID);
        recovery.tick(TICK);
        link.reply(Opcode.OPCODE_CONNECT_RESPONSE_VALUE, connectOk());
        recovery.tick(TICK);

        link.reply(
                Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE,
                ListCharactersResponse.newBuilder()
                        .addCharacters(summary(7L, 3, Faction.FACTION_THUY))
                        .build()
                        .toByteArray());
        assertThat(recovery.tick(TICK)).isEqualTo(SessionRecovery.Phase.FAILED);
        assertThat(recovery.failureKey()).isEqualTo(SessionRecovery.RELOG_FAILED_KEY);
        assertThat(link.sentOpcodes()).doesNotContain(Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE);
    }

    // ---------------------------------------------------------------------------------------
    // Thử lại có giới hạn
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("Server im lặng ⇒ thử lại có nghỉ tăng dần, hết MAX_ATTEMPTS lượt thì bỏ cuộc với lý do chung")
    void boundedRetriesOnTimeout() {
        FakeLink link = new FakeLink();
        link.reconnect();
        SessionRecovery recovery = new SessionRecovery(link, USERNAME, CHARACTER_ID);

        SessionRecovery.Phase phase = recovery.phase();
        for (int i = 0; i < 60 * 120 && phase != SessionRecovery.Phase.FAILED; i++) {
            phase = recovery.tick(TICK);
        }
        assertThat(phase).isEqualTo(SessionRecovery.Phase.FAILED);
        assertThat(recovery.failureKey()).isEqualTo(SessionRecovery.RELOG_FAILED_KEY);
        assertThat(link.sentOpcodes())
                .containsOnly(Opcode.OPCODE_CONNECT_REQUEST_VALUE)
                .hasSize(SessionRecovery.MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("Socket không bao giờ dựng lại ⇒ bỏ cuộc sau hạn chờ, không treo vô hạn")
    void givesUpWhenSocketNeverReturns() {
        FakeLink link = new FakeLink();
        SessionRecovery recovery = new SessionRecovery(link, USERNAME, CHARACTER_ID);

        SessionRecovery.Phase phase = recovery.phase();
        int ticks = 0;
        while (phase != SessionRecovery.Phase.FAILED && ticks < 60 * 600) {
            phase = recovery.tick(TICK);
            ticks++;
        }
        assertThat(phase).isEqualTo(SessionRecovery.Phase.FAILED);
        assertThat(link.sent).isEmpty();
        assertThat(ticks * TICK).isLessThanOrEqualTo(SessionRecovery.TOTAL_DEADLINE_SECONDS + 2 * TICK);
    }

    /**
     * Quyết định chủ dự án 2026-10-01: một hạn TỔNG. Bản cũ cho mỗi lượt 45 giây chờ socket riêng —
     * socket chập chờn (về rồi đứt giữa bắt tay, lặp lại) kéo dài 4 × 45 giây = 3 phút đứng im.
     */
    @Test
    @DisplayName("Socket chập chờn qua nhiều lượt ⇒ vẫn bỏ cuộc trong hạn tổng, không cộng dồn hạn mỗi lượt")
    void flappingSocketStillFailsWithinTheTotalDeadline() {
        FakeLink link = new FakeLink();
        SessionRecovery recovery = new SessionRecovery(link, USERNAME, CHARACTER_ID);

        SessionRecovery.Phase phase = recovery.phase();
        int ticks = 0;
        while (phase != SessionRecovery.Phase.FAILED && ticks < 60 * 600) {
            // Socket về sau 40 giây chờ mỗi lượt, rồi đứt ngay giữa bắt tay.
            if (phase == SessionRecovery.Phase.WAITING_FOR_SOCKET && ticks % (60 * 40) == 60 * 40 - 1) {
                link.reconnect();
            } else if (phase == SessionRecovery.Phase.AWAITING_CONNECT) {
                link.connected = false;
            }
            phase = recovery.tick(TICK);
            ticks++;
        }
        assertThat(phase).isEqualTo(SessionRecovery.Phase.FAILED);
        assertThat(recovery.failureKey()).isEqualTo(SessionRecovery.RELOG_FAILED_KEY);
        assertThat(ticks * TICK).isLessThanOrEqualTo(SessionRecovery.TOTAL_DEADLINE_SECONDS + 2 * TICK);
    }

    @Test
    @DisplayName("Socket đứt lần nữa giữa lúc bắt tay ⇒ lượt đó hỏng, lượt sau chạy trên socket mới")
    void socketDropMidHandshakeRetriesOnNewSocket() throws IOException {
        FakeLink link = new FakeLink();
        link.reconnect();
        SessionRecovery recovery = new SessionRecovery(link, USERNAME, CHARACTER_ID);
        recovery.tick(TICK);
        assertThat(recovery.phase()).isEqualTo(SessionRecovery.Phase.AWAITING_CONNECT);

        link.connected = false;
        assertThat(recovery.tick(TICK)).isEqualTo(SessionRecovery.Phase.BACKING_OFF);
        assertThat(recovery.attemptNumber()).isEqualTo(2);

        link.reconnect();
        SessionRecovery.Phase phase = recovery.phase();
        for (int i = 0; i < 60 * 3 && phase != SessionRecovery.Phase.AWAITING_CONNECT; i++) {
            phase = recovery.tick(TICK);
        }
        assertThat(phase).isEqualTo(SessionRecovery.Phase.AWAITING_CONNECT);
        assertThat(link.sentOpcodes())
                .containsExactly(Opcode.OPCODE_CONNECT_REQUEST_VALUE, Opcode.OPCODE_CONNECT_REQUEST_VALUE);
        assertThat(ConnectRequest.parseFrom(link.lastPayload()).getUsername()).isEqualTo(USERNAME);
    }

    // ---------------------------------------------------------------------------------------

    private static SessionRecovery handshakeUpToSelect(FakeLink link) {
        link.reconnect();
        SessionRecovery recovery = new SessionRecovery(link, USERNAME, CHARACTER_ID);
        recovery.tick(TICK);
        link.reply(Opcode.OPCODE_CONNECT_RESPONSE_VALUE, connectOk());
        recovery.tick(TICK);
        link.reply(
                Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE,
                ListCharactersResponse.newBuilder()
                        .addCharacters(summary(CHARACTER_ID, 5, Faction.FACTION_LOI))
                        .build()
                        .toByteArray());
        recovery.tick(TICK);
        assertThat(recovery.phase()).isEqualTo(SessionRecovery.Phase.AWAITING_SELECT);
        return recovery;
    }

    private static byte[] connectOk() {
        return ConnectResponse.newBuilder()
                .setSuccess(true)
                .setSessionId("s")
                .setAccountId(1L)
                .build()
                .toByteArray();
    }

    private static byte[] selectOk() {
        return SelectCharacterResponse.newBuilder()
                .setSuccess(true)
                .setCurrentHp(80)
                .setMaxHp(100)
                .build()
                .toByteArray();
    }

    private static byte[] error(String key) {
        return ErrorResponse.newBuilder().setErrorKey(key).build().toByteArray();
    }

    private static CharacterSummary summary(long id, int level, Faction faction) {
        return CharacterSummary.newBuilder()
                .setId(id)
                .setName("c" + id)
                .setLevel(level)
                .setFaction(faction)
                .build();
    }

    /** Đường truyền giả: bật/tắt socket, đếm thế hệ, ghi lại mọi yêu cầu, hàng đợi gói tự nhét. */
    private static final class FakeLink implements SessionRecovery.Link {
        private final Queue<PacketCodec.Frame> inbound = new ArrayDeque<>();
        private final List<PacketCodec.Frame> sent = new ArrayList<>();
        private boolean connected;
        private int generation = 1;
        private int nextSequenceId = 100;
        private int lastSequenceId;

        void reconnect() {
            connected = true;
            generation++;
        }

        /** Response cho yêu cầu GẦN NHẤT — mang đúng sequenceId của nó như server thật. */
        void reply(int opcode, byte[] payload) {
            push(opcode, lastSequenceId, payload);
        }

        void push(int opcode, int sequenceId, byte[] payload) {
            inbound.add(new PacketCodec.Frame(new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId), payload));
        }

        byte[] lastPayload() {
            return sent.get(sent.size() - 1).payload();
        }

        List<Integer> sentOpcodes() {
            return sent.stream().map(frame -> frame.header().opcode()).toList();
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public int connectionGeneration() {
            return generation;
        }

        @Override
        public int sendRequest(int opcode, byte[] payload) throws IOException {
            if (!connected) {
                throw new IOException("đứt");
            }
            lastSequenceId = nextSequenceId++;
            sent.add(new PacketCodec.Frame(new PacketHeader(opcode, ProtocolVersion.CURRENT, lastSequenceId), payload));
            return lastSequenceId;
        }

        @Override
        public PacketCodec.Frame pollInbound() {
            return inbound.poll();
        }
    }
}
