package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ClaimMailCommand;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.ListCharactersRequest;
import dev.kage.protocol.ListCharactersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SendChatMessageOutcome;
import dev.kage.protocol.SendChatMessageResponse;
import dev.kage.protocol.SendMailCommand;
import dev.kage.protocol.SendMailOutcome;
import dev.kage.protocol.SendMailResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Dữ liệu client bẩn từng đi xuyên tới tầng DB và quay về thành một lỗi SAI (rà 2026-10-02): hoặc
 * 500 {@code error.server_internal}, hoặc một khoá lỗi dành cho chuyện khác hẳn. Mỗi test ở đây
 * chốt rằng nó dừng ở đúng tầng kiểm và về đúng câu trả lời CÓ SẴN — không thêm opcode/outcome.
 *
 * <p>Fake Port của {@code GatewayPipelineTest} bắt chước đúng một ràng buộc của Postgres thật: từ
 * chối {@code U+0000} trong tham số chuỗi (xem {@code FakeCharacterPort#findByName}).
 */
@Timeout(30)
class ClientInputReachingDatabaseTest {

    private static final String NOT_A_UUID = "not-a-uuid";

    @Test
    void shouldAnswerInvalidPayload_andCreateNothing_whenCreateCharacterKeyIsNotAUuid()
            throws InvalidProtocolBufferException {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();
        GatewayPipelineTest.connect(channel, "badkeyuser");

        CreateCharacterRequest request = CreateCharacterRequest.newBuilder()
                .setName("BadKeyHero")
                .setIdempotencyKey(NOT_A_UUID)
                .build();
        write(channel, Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, 2, request.toByteArray());

        // Trước bản vá: adapter nổ IllegalArgumentException và gateway báo "tên không hợp lệ".
        assertErrorKey(GatewayPipelineTest.readOutboundFrames(channel), 2, "error.invalid_payload");
        write(
                channel,
                Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE,
                3,
                ListCharactersRequest.getDefaultInstance().toByteArray());
        ListCharactersResponse list = ListCharactersResponse.parseFrom(
                GatewayPipelineTest.readOutboundFrames(channel).getFirst().payload());
        assertThat(list.getCharactersList()).isEmpty();
    }

    @Test
    void shouldAnswerInvalidPayload_whenClaimMailKeyIsNotAUuid() throws InvalidProtocolBufferException {
        EmbeddedChannel channel = inWorld("badkeymail", "BadKeyMail");

        ClaimMailCommand command = ClaimMailCommand.newBuilder()
                .setMailId(1)
                .setIdempotencyKey(NOT_A_UUID)
                .build();
        write(channel, Opcode.OPCODE_CLAIM_MAIL_COMMAND_VALUE, 10, command.toByteArray());

        // Trước bản vá: 500 error.server_internal.
        assertErrorKey(GatewayPipelineTest.readOutboundFrames(channel), 10, "error.invalid_payload");
    }

    @Test
    void shouldAnswerInvalidPayload_whenMailBodyContainsNul() throws InvalidProtocolBufferException {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();
        GatewayPipelineTest.connect(channel, "nulmailuser");
        GatewayPipelineTest.createCharacter(channel, "NulRecv", 2);
        long senderId = GatewayPipelineTest.createCharacter(channel, "NulSender", 3);
        GatewayPipelineTest.selectCharacter(channel, senderId, 4);
        GatewayPipelineTest.readOutboundFrames(channel);

        SendMailCommand command = SendMailCommand.newBuilder()
                .setRecipientName("NulRecv")
                .setSubject("hi")
                .setBody("a\u0000b")
                .build();
        write(channel, Opcode.OPCODE_SEND_MAIL_COMMAND_VALUE, 10, command.toByteArray());

        // Trước bản vá: U+0000 tới câu INSERT INTO mail, Postgres từ chối ⇒ 500 error.server_internal.
        assertErrorKey(GatewayPipelineTest.readOutboundFrames(channel), 10, "error.invalid_payload");
    }

    @Test
    void shouldReportRecipientNotFound_whenRecipientNameContainsNul() throws InvalidProtocolBufferException {
        EmbeddedChannel channel = inWorld("nulrecipient", "NulRecipSend");

        SendMailCommand command = SendMailCommand.newBuilder()
                .setRecipientName("Kage\u0000Hero")
                .setSubject("hi")
                .setBody("hello")
                .build();
        write(channel, Opcode.OPCODE_SEND_MAIL_COMMAND_VALUE, 10, command.toByteArray());

        List<PacketCodec.Frame> frames = GatewayPipelineTest.readOutboundFrames(channel);
        assertThat(frames).hasSize(1);
        assertThat(frames.getFirst().header().opcode()).isEqualTo(Opcode.OPCODE_SEND_MAIL_RESPONSE_VALUE);
        assertThat(SendMailResponse.parseFrom(frames.getFirst().payload()).getOutcome())
                .isEqualTo(SendMailOutcome.SEND_MAIL_OUTCOME_RECIPIENT_NOT_FOUND);
    }

    @Test
    void shouldReportTargetNotFound_whenPrivateChatTargetContainsNul() throws InvalidProtocolBufferException {
        EmbeddedChannel channel = inWorld("nulchatuser", "NulChatter");

        SendChatMessageCommand command = SendChatMessageCommand.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_PRIVATE)
                .setTargetName("Kage\u0000Hero")
                .setBody("hello")
                .build();
        write(channel, Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE, 10, command.toByteArray());

        List<PacketCodec.Frame> frames = GatewayPipelineTest.readOutboundFrames(channel);
        assertThat(frames).hasSize(1);
        assertThat(frames.getFirst().header().opcode()).isEqualTo(Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE);
        assertThat(SendChatMessageResponse.parseFrom(frames.getFirst().payload())
                        .getOutcome())
                .isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_TARGET_NOT_FOUND);
    }

    @Test
    void shouldAcceptOnlyUuidShapedKeys() {
        assertThat(IdempotencyKeyFormat.isWellFormed(UUID.randomUUID().toString()))
                .isTrue();
        assertThat(IdempotencyKeyFormat.isWellFormed("")).isFalse();
        assertThat(IdempotencyKeyFormat.isWellFormed(NOT_A_UUID)).isFalse();
    }

    private static EmbeddedChannel inWorld(String username, String characterName)
            throws InvalidProtocolBufferException {
        EmbeddedChannel channel = GatewayPipelineTest.newPipeline();
        GatewayPipelineTest.connect(channel, username);
        long characterId = GatewayPipelineTest.createCharacter(channel, characterName, 2);
        GatewayPipelineTest.selectCharacter(channel, characterId, 3);
        GatewayPipelineTest.readOutboundFrames(channel); // bỏ mọi event kèm theo lúc vào world
        return channel;
    }

    private static void write(EmbeddedChannel channel, int opcode, int sequenceId, byte[] payload) {
        GatewayPipelineTest.writeInboundFrame(
                channel, new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId), payload);
    }

    private static void assertErrorKey(List<PacketCodec.Frame> frames, int sequenceId, String errorKey)
            throws InvalidProtocolBufferException {
        assertThat(frames).hasSize(1);
        assertThat(frames.getFirst().header().opcode()).isEqualTo(Opcode.OPCODE_ERROR_VALUE);
        ErrorResponse error = ErrorResponse.parseFrom(frames.getFirst().payload());
        assertThat(error.getErrorKey()).isEqualTo(errorKey);
        assertThat(error.getRelatedSequenceId()).isEqualTo(sequenceId);
    }
}
