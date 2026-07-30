package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ClaimMailCommand;
import dev.kage.protocol.ClaimMailOutcome;
import dev.kage.protocol.ClaimMailResponse;
import dev.kage.protocol.DeleteMailCommand;
import dev.kage.protocol.DeleteMailOutcome;
import dev.kage.protocol.DeleteMailResponse;
import dev.kage.protocol.ListMailRequest;
import dev.kage.protocol.ListMailResponse;
import dev.kage.protocol.MailAttachmentView;
import dev.kage.protocol.MailView;
import dev.kage.protocol.NewMailEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendMailCommand;
import dev.kage.protocol.SendMailOutcome;
import dev.kage.protocol.SendMailResponse;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Mail (900-999, NETWORK.md §4).
 */
class MailPacketsTest {

    @Test
    void shouldRoundTripSendMailCommand() throws InvalidProtocolBufferException {
        MailAttachmentView attachment = MailAttachmentView.newBuilder()
                .setItemTemplateId(1L)
                .setQuantity(3)
                .build();
        SendMailCommand command = SendMailCommand.newBuilder()
                .setRecipientName("naruto")
                .setSubject("Chào")
                .setBody("Nội dung")
                .setGoldAmount(100)
                .addAttachments(attachment)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_SEND_MAIL_COMMAND_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        SendMailCommand parsed =
                SendMailCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getRecipientName()).isEqualTo("naruto");
        assertThat(parsed.getGoldAmount()).isEqualTo(100);
        assertThat(parsed.getAttachmentsList()).hasSize(1);
        assertThat(parsed.getAttachments(0).getItemTemplateId()).isEqualTo(1L);
    }

    @Test
    void shouldRoundTripSendMailResponse() throws InvalidProtocolBufferException {
        SendMailResponse response = SendMailResponse.newBuilder()
                .setOutcome(SendMailOutcome.SEND_MAIL_OUTCOME_RECIPIENT_NOT_FOUND)
                .setMailId(0)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_SEND_MAIL_RESPONSE_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        SendMailResponse parsed =
                SendMailResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(SendMailOutcome.SEND_MAIL_OUTCOME_RECIPIENT_NOT_FOUND);
    }

    @Test
    void shouldRoundTripListMailRequest() throws InvalidProtocolBufferException {
        ListMailRequest request = ListMailRequest.newBuilder().build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_LIST_MAIL_REQUEST_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, request.toByteArray());
        ListMailRequest parsed =
                ListMailRequest.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed).isEqualTo(request);
    }

    @Test
    void shouldRoundTripListMailResponse() throws InvalidProtocolBufferException {
        MailAttachmentView attachment = MailAttachmentView.newBuilder()
                .setItemTemplateId(2L)
                .setQuantity(1)
                .build();
        MailView mailView = MailView.newBuilder()
                .setMailId(5L)
                .setHasSender(true)
                .setSenderId(7L)
                .setSubject("s")
                .setBody("b")
                .setGoldAmount(10)
                .addAttachments(attachment)
                .setClaimed(false)
                .build();
        ListMailResponse response =
                ListMailResponse.newBuilder().addMails(mailView).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_LIST_MAIL_RESPONSE_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ListMailResponse parsed =
                ListMailResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getMailsList()).hasSize(1);
        assertThat(parsed.getMails(0).getMailId()).isEqualTo(5L);
        assertThat(parsed.getMails(0).getHasSender()).isTrue();
        assertThat(parsed.getMails(0).getSenderId()).isEqualTo(7L);
        assertThat(parsed.getMails(0).getAttachmentsList()).hasSize(1);
    }

    @Test
    void shouldRoundTripClaimMailCommand() throws InvalidProtocolBufferException {
        ClaimMailCommand command = ClaimMailCommand.newBuilder()
                .setMailId(9L)
                .setIdempotencyKey("33333333-3333-3333-3333-333333333333")
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_CLAIM_MAIL_COMMAND_VALUE, ProtocolVersion.CURRENT, 3);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        ClaimMailCommand parsed =
                ClaimMailCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getMailId()).isEqualTo(9L);
        assertThat(parsed.getIdempotencyKey()).isEqualTo("33333333-3333-3333-3333-333333333333");
    }

    @Test
    void shouldRoundTripClaimMailResponse() throws InvalidProtocolBufferException {
        ClaimMailResponse response = ClaimMailResponse.newBuilder()
                .setOutcome(ClaimMailOutcome.CLAIM_MAIL_OUTCOME_INVENTORY_FULL)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_CLAIM_MAIL_RESPONSE_VALUE, ProtocolVersion.CURRENT, 3);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ClaimMailResponse parsed =
                ClaimMailResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(ClaimMailOutcome.CLAIM_MAIL_OUTCOME_INVENTORY_FULL);
    }

    @Test
    void shouldRoundTripDeleteMailCommand() throws InvalidProtocolBufferException {
        DeleteMailCommand command =
                DeleteMailCommand.newBuilder().setMailId(11L).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_DELETE_MAIL_COMMAND_VALUE, ProtocolVersion.CURRENT, 4);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        DeleteMailCommand parsed =
                DeleteMailCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getMailId()).isEqualTo(11L);
    }

    @Test
    void shouldRoundTripDeleteMailResponse() throws InvalidProtocolBufferException {
        DeleteMailResponse response = DeleteMailResponse.newBuilder()
                .setOutcome(DeleteMailOutcome.DELETE_MAIL_OUTCOME_MUST_CLAIM_FIRST)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_DELETE_MAIL_RESPONSE_VALUE, ProtocolVersion.CURRENT, 4);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        DeleteMailResponse parsed =
                DeleteMailResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(DeleteMailOutcome.DELETE_MAIL_OUTCOME_MUST_CLAIM_FIRST);
    }

    @Test
    void shouldRoundTripNewMailEvent() throws InvalidProtocolBufferException {
        NewMailEvent event = NewMailEvent.newBuilder().setMailId(13L).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_NEW_MAIL_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        NewMailEvent parsed =
                NewMailEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getMailId()).isEqualTo(13L);
    }
}
