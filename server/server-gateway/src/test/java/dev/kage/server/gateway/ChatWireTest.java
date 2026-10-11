package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SendChatMessageOutcome;
import dev.kage.protocol.SendChatMessageResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Chat QUA DÂY giữa nhiều người chơi trên cùng một gateway (rà 2026-10-10): tin tới ĐÚNG người, và
 * nội dung thù địch nhận một câu trả lời có nghĩa — không bao giờ là lỗi 500 hay một dòng log ERROR.
 *
 * <p>Lớp này xanh ngay từ lần chạy đầu: nó chốt hành vi đang đúng, không phải bản vá. Mọi ký tự vô
 * hình viết bằng escape để nhìn thấy được trong mã nguồn (cùng quy ước {@link HostileInputs}).
 */
@Timeout(30)
class ChatWireTest {

    private static final int MAX_BODY_LENGTH = 200;
    private static final SendChatMessageOutcome OK = SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_OK;
    private static final SendChatMessageOutcome BLANK = SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_MESSAGE_BLANK;
    private static final SendChatMessageOutcome TOO_LONG =
            SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_MESSAGE_TOO_LONG;

    private final SharedWorldPipelines gateway = new SharedWorldPipelines();
    private int nextSequenceId = 100;

    @AfterEach
    void stopGateway() {
        gateway.stop();
    }

    @Test
    void shouldDeliverAWhisperToItsTargetOnly() throws InvalidProtocolBufferException {
        EmbeddedChannel alice = enterWorld("whisperalice", "Alice");
        EmbeddedChannel bob = enterWorld("whisperbob", "Bob");
        EmbeddedChannel eve = enterWorld("whispereve", "Eve");

        List<PacketCodec.Frame> senderFrames = send(alice, ChatChannel.CHAT_CHANNEL_PRIVATE, "Bob", "chuyện riêng");

        assertThat(senderFrames).hasSize(1);
        assertThat(outcomeOf(senderFrames.getFirst())).isEqualTo(OK);
        List<PacketCodec.Frame> bobFrames = GatewayPipelineTest.readOutboundFrames(bob);
        assertThat(bobFrames).hasSize(1);
        ChatMessageEvent received =
                ChatMessageEvent.parseFrom(bobFrames.getFirst().payload());
        assertThat(received.getChannel()).isEqualTo(ChatChannel.CHAT_CHANNEL_PRIVATE);
        assertThat(received.getSenderName()).isEqualTo("Alice");
        assertThat(received.getBody()).isEqualTo("chuyện riêng");
        assertThat(GatewayPipelineTest.readOutboundFrames(eve))
                .as("người thứ ba không được thấy tin riêng")
                .isEmpty();
    }

    @Test
    void shouldReportOfflineOrUnknownTargets_withoutDeliveringAnything() throws InvalidProtocolBufferException {
        EmbeddedChannel alice = enterWorld("offalice", "Alice");
        EmbeddedChannel bob = enterWorld("offbob", "Bob");
        bob.close().syncUninterruptibly();

        assertThat(outcomeOf(send(alice, ChatChannel.CHAT_CHANNEL_PRIVATE, "Bob", "còn đó không")
                        .getFirst()))
                .isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_TARGET_OFFLINE);
        assertThat(outcomeOf(send(alice, ChatChannel.CHAT_CHANNEL_PRIVATE, "Nobody", "ai đó")
                        .getFirst()))
                .isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_TARGET_NOT_FOUND);
        assertThat(outcomeOf(send(alice, ChatChannel.CHAT_CHANNEL_PRIVATE, "", "không tên")
                        .getFirst()))
                .isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_TARGET_NOT_FOUND);
    }

    // Nhóm/Bang: không ở trong thì không gửi được, và không ai nhận được gì.
    @Test
    void shouldRefuseGroupChannels_whenTheSenderBelongsToNoGroup() throws InvalidProtocolBufferException {
        EmbeddedChannel alice = enterWorld("groupalice", "Alice");
        EmbeddedChannel bob = enterWorld("groupbob", "Bob");

        assertThat(outcomeOf(send(alice, ChatChannel.CHAT_CHANNEL_GUILD, "", "bang ơi")
                        .getFirst()))
                .isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_NOT_IN_GUILD);
        assertThat(outcomeOf(send(alice, ChatChannel.CHAT_CHANNEL_PARTY, "", "nhóm ơi")
                        .getFirst()))
                .isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_NOT_IN_PARTY);
        assertThat(GatewayPipelineTest.readOutboundFrames(bob)).isEmpty();
    }

    @Test
    void shouldAnswerInvalidChannel_forChannelValuesOutsideTheEnum() throws InvalidProtocolBufferException {
        EmbeddedChannel alice = enterWorld("chanalice", "Alice");
        EmbeddedChannel bob = enterWorld("chanbob", "Bob");

        for (int rawChannel : new int[] {0, 99, -1}) {
            SendChatMessageCommand command = SendChatMessageCommand.newBuilder()
                    .setChannelValue(rawChannel)
                    .setBody("lọt không")
                    .build();
            List<PacketCodec.Frame> frames = write(alice, command.toByteArray());

            assertThat(frames).as("kênh %d", rawChannel).hasSize(1);
            assertThat(frames.getFirst().header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
            assertThat(ErrorResponse.parseFrom(frames.getFirst().payload()).getErrorKey())
                    .isEqualTo("error.invalid_chat_channel");
        }
        assertThat(GatewayPipelineTest.readOutboundFrames(bob)).isEmpty();
    }

    /**
     * Mỗi nội dung thù địch nhận ĐÚNG MỘT câu trả lời có nghĩa và không sinh log ERROR. Mỗi nội dung
     * một tài khoản riêng — trần kênh Thế giới là 5 tin/10 giây mỗi tài khoản.
     *
     * <p>Các nội dung "lạ nhưng nhìn thấy được" (ký hiệu format/SQL/HTML) được phát NGUYÊN VĂN: client
     * vẽ chat dạng chữ thuần. Riêng xuống dòng và ký tự điều khiển thì từ 2026-10-11 server đổi thành
     * dấu cách trước khi phát ({@code ChatMessage#singleLine}, xem
     * {@link #shouldDeliverASingleLine_whenTheBodyContainsLineBreaks}).
     */
    @Test
    void shouldClassifyHostileBodies_withoutServerErrors() throws InvalidProtocolBufferException {
        Map<String, SendChatMessageOutcome> expectedByBody = Map.ofEntries(
                Map.entry("", BLANK),
                Map.entry("   ", BLANK),
                Map.entry("\t\r\n", BLANK),
                Map.entry("\u0000\u0007\u001B", BLANK),
                Map.entry("\u200B\u200D\uFEFF", BLANK),
                Map.entry("\u00A0\u2007\u202F\u3000", BLANK),
                Map.entry("\u202E\u202D\u2066", BLANK),
                Map.entry("a".repeat(MAX_BODY_LENGTH + 1), TOO_LONG),
                // 30.000 ký tự: xa trên trần tin nhắn nhưng vẫn lọt khung 64 KiB (trần khung từ 2026-10-11;
                // khung lớn hơn bị cắt ngay ở tầng khung, không tới được phép kiểm này).
                Map.entry("a".repeat(30_000), TOO_LONG),
                // 101 emoji = 202 đơn vị UTF-16: trần đếm theo đơn vị UTF-16.
                Map.entry("\uD83D\uDE00".repeat(MAX_BODY_LENGTH / 2 + 1), TOO_LONG),
                Map.entry("a".repeat(MAX_BODY_LENGTH), OK),
                Map.entry("NGU", SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_CONTAINS_BANNED_WORD),
                Map.entry("Nguyên Anh", OK),
                Map.entry("chào\n[Hệ thống] giả mạo", OK),
                Map.entry("%s%n{}${jndi:x}", OK),
                Map.entry("<b>x</b>'; DROP TABLE audit_log; --", OK));
        EmbeddedChannel listener = enterWorld("hostilelistener", "Listener");
        int index = 0;
        try (ErrorLogCapture errors = ErrorLogCapture.attach()) {
            for (Map.Entry<String, SendChatMessageOutcome> expected : expectedByBody.entrySet()) {
                String body = expected.getKey();
                EmbeddedChannel sender = enterWorld("hostilebody" + index, "Sender" + (char) ('A' + index));
                index++;
                GatewayPipelineTest.readOutboundFrames(listener);

                List<PacketCodec.Frame> frames = send(sender, ChatChannel.CHAT_CHANNEL_WORLD, "", body);

                assertThat(outcomeOf(responseIn(frames, body)))
                        .as("nội dung %s", HostileInputs.describe(body))
                        .isEqualTo(expected.getValue());
                assertThat(GatewayPipelineTest.readOutboundFrames(listener))
                        .as("tin bị từ chối không được tới ai: %s", HostileInputs.describe(body))
                        .hasSize(expected.getValue() == OK ? 1 : 0);
            }
            assertThat(errors.errorsSince(0)).isEmpty();
        }
    }

    /**
     * Tin có xuống dòng từng giả được tin hệ thống trên máy người nhận (quyết định chủ dự án 2026-10-11).
     * Đo thứ NGƯỜI NHẬN thấy: một dòng duy nhất.
     */
    @Test
    void shouldDeliverASingleLine_whenTheBodyContainsLineBreaks() throws InvalidProtocolBufferException {
        EmbeddedChannel listener = enterWorld("linelistener", "LineListener");
        EmbeddedChannel sender = enterWorld("linesender", "LineSender");
        GatewayPipelineTest.readOutboundFrames(listener);
        String lineFeed = Character.toString(0x0A);

        send(sender, ChatChannel.CHAT_CHANNEL_WORLD, "", "chào" + lineFeed + "[Hệ thống] giả mạo");

        List<PacketCodec.Frame> received = GatewayPipelineTest.readOutboundFrames(listener);
        assertThat(received).hasSize(1);
        assertThat(dev.kage.protocol.ChatMessageEvent.parseFrom(received.get(0).payload())
                        .getBody())
                .isEqualTo("chào [Hệ thống] giả mạo");
    }

    // Chuỗi trên dây không phải UTF-8 hợp lệ (nửa cặp surrogate mã hoá thẳng): protobuf từ chối ngay
    // lúc parse — không bao giờ thành một String chứa surrogate lẻ đi tiếp vào server.
    @Test
    void shouldAnswerInvalidPayload_whenBodyIsNotValidUtf8() throws InvalidProtocolBufferException {
        EmbeddedChannel alice = enterWorld("utfalice", "Alice");
        EmbeddedChannel bob = enterWorld("utfbob", "Bob");
        byte[] payload = {
            0x08,
            0x01, // channel = WORLD
            0x1A,
            0x03,
            (byte) 0xED,
            (byte) 0xA0,
            (byte) 0x80 // body = U+D800 mã hoá thẳng
        };

        List<PacketCodec.Frame> frames = write(alice, payload);

        assertThat(frames).hasSize(1);
        assertThat(frames.getFirst().header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
        assertThat(ErrorResponse.parseFrom(frames.getFirst().payload()).getErrorKey())
                .isEqualTo("error.invalid_payload");
        assertThat(GatewayPipelineTest.readOutboundFrames(bob)).isEmpty();
    }

    private EmbeddedChannel enterWorld(String username, String characterName) throws InvalidProtocolBufferException {
        EmbeddedChannel channel = gateway.newChannel();
        GatewayPipelineTest.connect(channel, username);
        long characterId = GatewayPipelineTest.createCharacter(channel, characterName, 2);
        GatewayPipelineTest.selectCharacter(channel, characterId, 3);
        GatewayPipelineTest.readOutboundFrames(channel);
        return channel;
    }

    private List<PacketCodec.Frame> send(EmbeddedChannel channel, ChatChannel chatChannel, String target, String body) {
        SendChatMessageCommand command = SendChatMessageCommand.newBuilder()
                .setChannel(chatChannel)
                .setTargetName(target)
                .setBody(body)
                .build();
        return write(channel, command.toByteArray());
    }

    private List<PacketCodec.Frame> write(EmbeddedChannel channel, byte[] payload) {
        GatewayPipelineTest.writeInboundFrame(
                channel,
                new PacketHeader(
                        Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE, ProtocolVersion.CURRENT, nextSequenceId++),
                payload);
        return GatewayPipelineTest.readOutboundFrames(channel);
    }

    private static PacketCodec.Frame responseIn(List<PacketCodec.Frame> frames, String body) {
        return frames.stream()
                .filter(frame -> frame.header().opcode() == Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("không có phản hồi cho " + HostileInputs.describe(body)));
    }

    private static SendChatMessageOutcome outcomeOf(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        assertThat(frame.header().opcode()).isEqualTo(Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE);
        return SendChatMessageResponse.parseFrom(frame.payload()).getOutcome();
    }
}
