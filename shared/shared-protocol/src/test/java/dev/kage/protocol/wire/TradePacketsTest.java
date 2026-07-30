package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.AcceptTradeRequestCommand;
import dev.kage.protocol.AcceptTradeRequestOutcome;
import dev.kage.protocol.AcceptTradeRequestResponse;
import dev.kage.protocol.CancelTradeCommand;
import dev.kage.protocol.CancelTradeOutcome;
import dev.kage.protocol.CancelTradeResponse;
import dev.kage.protocol.ConfirmTradeCommand;
import dev.kage.protocol.ConfirmTradeOutcome;
import dev.kage.protocol.ConfirmTradeResponse;
import dev.kage.protocol.GetTradeStatusResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RequestTradeCommand;
import dev.kage.protocol.RequestTradeOutcome;
import dev.kage.protocol.RequestTradeResponse;
import dev.kage.protocol.SetTradeOfferCommand;
import dev.kage.protocol.SetTradeOfferOutcome;
import dev.kage.protocol.SetTradeOfferResponse;
import dev.kage.protocol.TradeCloseReason;
import dev.kage.protocol.TradeClosedEvent;
import dev.kage.protocol.TradeOfferItemInput;
import dev.kage.protocol.TradeOfferItemView;
import dev.kage.protocol.TradePhase;
import dev.kage.protocol.TradeRequestedEvent;
import dev.kage.protocol.TradeUpdatedEvent;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Trade (1400-1499, NETWORK.md §4).
 */
class TradePacketsTest {

    @Test
    void shouldRoundTripRequestTradeCommand() throws InvalidProtocolBufferException {
        RequestTradeCommand command =
                RequestTradeCommand.newBuilder().setTargetName("naruto").build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_REQUEST_TRADE_COMMAND_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        RequestTradeCommand parsed = RequestTradeCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getTargetName()).isEqualTo("naruto");
    }

    @Test
    void shouldRoundTripRequestTradeResponse() throws InvalidProtocolBufferException {
        RequestTradeResponse response = RequestTradeResponse.newBuilder()
                .setOutcome(RequestTradeOutcome.REQUEST_TRADE_OUTCOME_OK)
                .setSessionId(7L)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_REQUEST_TRADE_RESPONSE_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        RequestTradeResponse parsed = RequestTradeResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(RequestTradeOutcome.REQUEST_TRADE_OUTCOME_OK);
        assertThat(parsed.getSessionId()).isEqualTo(7L);
    }

    @Test
    void shouldRoundTripAcceptTradeRequestCommandAndResponse() throws InvalidProtocolBufferException {
        AcceptTradeRequestCommand command =
                AcceptTradeRequestCommand.newBuilder().setSessionId(9L).build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_ACCEPT_TRADE_REQUEST_COMMAND_VALUE, ProtocolVersion.CURRENT, 2);
        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        assertThat(AcceptTradeRequestCommand.parseFrom(
                                PacketCodec.decodeFrame(frameBytes).payload())
                        .getSessionId())
                .isEqualTo(9L);

        AcceptTradeRequestResponse response = AcceptTradeRequestResponse.newBuilder()
                .setOutcome(AcceptTradeRequestOutcome.ACCEPT_TRADE_REQUEST_OUTCOME_NOT_FOUND)
                .build();
        PacketHeader responseHeader =
                new PacketHeader(Opcode.OPCODE_ACCEPT_TRADE_REQUEST_RESPONSE_VALUE, ProtocolVersion.CURRENT, 2);
        byte[] responseBytes = PacketCodec.encodeFrame(responseHeader, response.toByteArray());
        AcceptTradeRequestResponse parsedResponse = AcceptTradeRequestResponse.parseFrom(
                PacketCodec.decodeFrame(responseBytes).payload());
        assertThat(parsedResponse.getOutcome())
                .isEqualTo(AcceptTradeRequestOutcome.ACCEPT_TRADE_REQUEST_OUTCOME_NOT_FOUND);
    }

    @Test
    void shouldRoundTripCancelTradeCommandAndResponse() throws InvalidProtocolBufferException {
        CancelTradeCommand command =
                CancelTradeCommand.newBuilder().setSessionId(11L).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_CANCEL_TRADE_COMMAND_VALUE, ProtocolVersion.CURRENT, 3);
        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        assertThat(CancelTradeCommand.parseFrom(
                                PacketCodec.decodeFrame(frameBytes).payload())
                        .getSessionId())
                .isEqualTo(11L);

        CancelTradeResponse response = CancelTradeResponse.newBuilder()
                .setOutcome(CancelTradeOutcome.CANCEL_TRADE_OUTCOME_OK)
                .build();
        PacketHeader responseHeader =
                new PacketHeader(Opcode.OPCODE_CANCEL_TRADE_RESPONSE_VALUE, ProtocolVersion.CURRENT, 3);
        byte[] responseBytes = PacketCodec.encodeFrame(responseHeader, response.toByteArray());
        CancelTradeResponse parsedResponse = CancelTradeResponse.parseFrom(
                PacketCodec.decodeFrame(responseBytes).payload());
        assertThat(parsedResponse.getOutcome()).isEqualTo(CancelTradeOutcome.CANCEL_TRADE_OUTCOME_OK);
    }

    @Test
    void shouldRoundTripSetTradeOfferCommand() throws InvalidProtocolBufferException {
        TradeOfferItemInput item =
                TradeOfferItemInput.newBuilder().setItemId(42L).setQuantity(3).build();
        SetTradeOfferCommand command = SetTradeOfferCommand.newBuilder()
                .setSessionId(1L)
                .setGoldAmount(100L)
                .addItems(item)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_SET_TRADE_OFFER_COMMAND_VALUE, ProtocolVersion.CURRENT, 4);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        SetTradeOfferCommand parsed = SetTradeOfferCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSessionId()).isEqualTo(1L);
        assertThat(parsed.getGoldAmount()).isEqualTo(100L);
        assertThat(parsed.getItemsList()).hasSize(1);
        assertThat(parsed.getItems(0).getItemId()).isEqualTo(42L);
        assertThat(parsed.getItems(0).getQuantity()).isEqualTo(3);
    }

    @Test
    void shouldRoundTripSetTradeOfferResponse() throws InvalidProtocolBufferException {
        SetTradeOfferResponse response = SetTradeOfferResponse.newBuilder()
                .setOutcome(SetTradeOfferOutcome.SET_TRADE_OFFER_OUTCOME_NOT_FOUND)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_SET_TRADE_OFFER_RESPONSE_VALUE, ProtocolVersion.CURRENT, 4);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        SetTradeOfferResponse parsed = SetTradeOfferResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(SetTradeOfferOutcome.SET_TRADE_OFFER_OUTCOME_NOT_FOUND);
    }

    @Test
    void shouldRoundTripConfirmTradeCommand() throws InvalidProtocolBufferException {
        ConfirmTradeCommand command = ConfirmTradeCommand.newBuilder()
                .setSessionId(1L)
                .setIdempotencyKey("44444444-4444-4444-4444-444444444444")
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_CONFIRM_TRADE_COMMAND_VALUE, ProtocolVersion.CURRENT, 5);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        ConfirmTradeCommand parsed = ConfirmTradeCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSessionId()).isEqualTo(1L);
        assertThat(parsed.getIdempotencyKey()).isEqualTo("44444444-4444-4444-4444-444444444444");
    }

    @Test
    void shouldRoundTripConfirmTradeResponse() throws InvalidProtocolBufferException {
        ConfirmTradeResponse response = ConfirmTradeResponse.newBuilder()
                .setOutcome(ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_EXECUTED)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_CONFIRM_TRADE_RESPONSE_VALUE, ProtocolVersion.CURRENT, 5);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ConfirmTradeResponse parsed = ConfirmTradeResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_EXECUTED);
    }

    @Test
    void shouldRoundTripGetTradeStatusResponse() throws InvalidProtocolBufferException {
        TradeOfferItemView item = TradeOfferItemView.newBuilder()
                .setItemId(1L)
                .setItemTemplateId(200L)
                .setQuantity(2)
                .setEnhanceLevel(3)
                .build();
        GetTradeStatusResponse response = GetTradeStatusResponse.newBuilder()
                .setInTrade(true)
                .setSessionId(5L)
                .setPhase(TradePhase.TRADE_PHASE_OPEN)
                .setCounterpartCharacterId(7L)
                .setCounterpartName("sasuke")
                .setIsRequester(true)
                .setMyGoldOffer(10L)
                .addMyItems(item)
                .setTheirGoldOffer(20L)
                .setMyConfirmed(true)
                .setTheirConfirmed(false)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_GET_TRADE_STATUS_RESPONSE_VALUE, ProtocolVersion.CURRENT, 6);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        GetTradeStatusResponse parsed = GetTradeStatusResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getInTrade()).isTrue();
        assertThat(parsed.getSessionId()).isEqualTo(5L);
        assertThat(parsed.getPhase()).isEqualTo(TradePhase.TRADE_PHASE_OPEN);
        assertThat(parsed.getCounterpartCharacterId()).isEqualTo(7L);
        assertThat(parsed.getCounterpartName()).isEqualTo("sasuke");
        assertThat(parsed.getIsRequester()).isTrue();
        assertThat(parsed.getMyGoldOffer()).isEqualTo(10L);
        assertThat(parsed.getMyItemsList()).hasSize(1);
        assertThat(parsed.getMyItems(0).getItemTemplateId()).isEqualTo(200L);
        assertThat(parsed.getTheirGoldOffer()).isEqualTo(20L);
        assertThat(parsed.getMyConfirmed()).isTrue();
        assertThat(parsed.getTheirConfirmed()).isFalse();
    }

    @Test
    void shouldRoundTripTradeRequestedEvent() throws InvalidProtocolBufferException {
        TradeRequestedEvent event = TradeRequestedEvent.newBuilder()
                .setSessionId(1L)
                .setFromCharacterId(5L)
                .setFromName("naruto")
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_TRADE_REQUESTED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        TradeRequestedEvent parsed = TradeRequestedEvent.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSessionId()).isEqualTo(1L);
        assertThat(parsed.getFromCharacterId()).isEqualTo(5L);
        assertThat(parsed.getFromName()).isEqualTo("naruto");
    }

    @Test
    void shouldRoundTripTradeUpdatedEvent() throws InvalidProtocolBufferException {
        TradeUpdatedEvent event =
                TradeUpdatedEvent.newBuilder().setSessionId(1L).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_TRADE_UPDATED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        TradeUpdatedEvent parsed =
                TradeUpdatedEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSessionId()).isEqualTo(1L);
    }

    @Test
    void shouldRoundTripTradeClosedEvent() throws InvalidProtocolBufferException {
        TradeClosedEvent event = TradeClosedEvent.newBuilder()
                .setSessionId(1L)
                .setReason(TradeCloseReason.TRADE_CLOSE_REASON_EXECUTED)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_TRADE_CLOSED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        TradeClosedEvent parsed =
                TradeClosedEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSessionId()).isEqualTo(1L);
        assertThat(parsed.getReason()).isEqualTo(TradeCloseReason.TRADE_CLOSE_REASON_EXECUTED);
    }
}
