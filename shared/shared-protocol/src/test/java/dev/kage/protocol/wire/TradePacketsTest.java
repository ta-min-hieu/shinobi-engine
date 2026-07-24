package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.protocol.AcceptTradeRequestCommand;
import dev.kage.protocol.AcceptTradeRequestOutcome;
import dev.kage.protocol.AcceptTradeRequestResponse;
import dev.kage.protocol.CancelTradeCommand;
import dev.kage.protocol.CancelTradeOutcome;
import dev.kage.protocol.CancelTradeResponse;
import dev.kage.protocol.ConfirmTradeCommand;
import dev.kage.protocol.ConfirmTradeOutcome;
import dev.kage.protocol.ConfirmTradeResponse;
import dev.kage.protocol.GetTradeStatusRequest;
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
import org.junit.jupiter.api.Test;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Trade (1400-1499, NETWORK.md §4).
 */
class TradePacketsTest {

    @Test
    void shouldRoundTripRequestTradeCommand() throws InvalidProtocolBufferException {
        var command = RequestTradeCommand.newBuilder().setTargetName("naruto").build();
        var header = new PacketHeader(Opcode.OPCODE_REQUEST_TRADE_COMMAND_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        var parsed = RequestTradeCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getTargetName()).isEqualTo("naruto");
    }

    @Test
    void shouldRoundTripRequestTradeResponse() throws InvalidProtocolBufferException {
        var response = RequestTradeResponse.newBuilder()
                .setOutcome(RequestTradeOutcome.REQUEST_TRADE_OUTCOME_OK)
                .setSessionId(7L)
                .build();
        var header = new PacketHeader(Opcode.OPCODE_REQUEST_TRADE_RESPONSE_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = RequestTradeResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(RequestTradeOutcome.REQUEST_TRADE_OUTCOME_OK);
        assertThat(parsed.getSessionId()).isEqualTo(7L);
    }

    @Test
    void shouldRoundTripAcceptTradeRequestCommandAndResponse() throws InvalidProtocolBufferException {
        var command = AcceptTradeRequestCommand.newBuilder().setSessionId(9L).build();
        var header = new PacketHeader(Opcode.OPCODE_ACCEPT_TRADE_REQUEST_COMMAND_VALUE, ProtocolVersion.CURRENT, 2);
        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        assertThat(AcceptTradeRequestCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload()).getSessionId())
                .isEqualTo(9L);

        var response = AcceptTradeRequestResponse.newBuilder()
                .setOutcome(AcceptTradeRequestOutcome.ACCEPT_TRADE_REQUEST_OUTCOME_NOT_FOUND)
                .build();
        var responseHeader =
                new PacketHeader(Opcode.OPCODE_ACCEPT_TRADE_REQUEST_RESPONSE_VALUE, ProtocolVersion.CURRENT, 2);
        byte[] responseBytes = PacketCodec.encodeFrame(responseHeader, response.toByteArray());
        var parsedResponse = AcceptTradeRequestResponse.parseFrom(PacketCodec.decodeFrame(responseBytes).payload());
        assertThat(parsedResponse.getOutcome())
                .isEqualTo(AcceptTradeRequestOutcome.ACCEPT_TRADE_REQUEST_OUTCOME_NOT_FOUND);
    }

    @Test
    void shouldRoundTripCancelTradeCommandAndResponse() throws InvalidProtocolBufferException {
        var command = CancelTradeCommand.newBuilder().setSessionId(11L).build();
        var header = new PacketHeader(Opcode.OPCODE_CANCEL_TRADE_COMMAND_VALUE, ProtocolVersion.CURRENT, 3);
        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        assertThat(CancelTradeCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload()).getSessionId())
                .isEqualTo(11L);

        var response =
                CancelTradeResponse.newBuilder().setOutcome(CancelTradeOutcome.CANCEL_TRADE_OUTCOME_OK).build();
        var responseHeader = new PacketHeader(Opcode.OPCODE_CANCEL_TRADE_RESPONSE_VALUE, ProtocolVersion.CURRENT, 3);
        byte[] responseBytes = PacketCodec.encodeFrame(responseHeader, response.toByteArray());
        var parsedResponse = CancelTradeResponse.parseFrom(PacketCodec.decodeFrame(responseBytes).payload());
        assertThat(parsedResponse.getOutcome()).isEqualTo(CancelTradeOutcome.CANCEL_TRADE_OUTCOME_OK);
    }

    @Test
    void shouldRoundTripSetTradeOfferCommand() throws InvalidProtocolBufferException {
        var item = TradeOfferItemInput.newBuilder().setItemId(42L).setQuantity(3).build();
        var command = SetTradeOfferCommand.newBuilder()
                .setSessionId(1L)
                .setGoldAmount(100L)
                .addItems(item)
                .build();
        var header = new PacketHeader(Opcode.OPCODE_SET_TRADE_OFFER_COMMAND_VALUE, ProtocolVersion.CURRENT, 4);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        var parsed = SetTradeOfferCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSessionId()).isEqualTo(1L);
        assertThat(parsed.getGoldAmount()).isEqualTo(100L);
        assertThat(parsed.getItemsList()).hasSize(1);
        assertThat(parsed.getItems(0).getItemId()).isEqualTo(42L);
        assertThat(parsed.getItems(0).getQuantity()).isEqualTo(3);
    }

    @Test
    void shouldRoundTripSetTradeOfferResponse() throws InvalidProtocolBufferException {
        var response = SetTradeOfferResponse.newBuilder()
                .setOutcome(SetTradeOfferOutcome.SET_TRADE_OFFER_OUTCOME_NOT_FOUND)
                .build();
        var header = new PacketHeader(Opcode.OPCODE_SET_TRADE_OFFER_RESPONSE_VALUE, ProtocolVersion.CURRENT, 4);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = SetTradeOfferResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(SetTradeOfferOutcome.SET_TRADE_OFFER_OUTCOME_NOT_FOUND);
    }

    @Test
    void shouldRoundTripConfirmTradeCommand() throws InvalidProtocolBufferException {
        var command = ConfirmTradeCommand.newBuilder()
                .setSessionId(1L)
                .setIdempotencyKey("44444444-4444-4444-4444-444444444444")
                .build();
        var header = new PacketHeader(Opcode.OPCODE_CONFIRM_TRADE_COMMAND_VALUE, ProtocolVersion.CURRENT, 5);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        var parsed = ConfirmTradeCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSessionId()).isEqualTo(1L);
        assertThat(parsed.getIdempotencyKey()).isEqualTo("44444444-4444-4444-4444-444444444444");
    }

    @Test
    void shouldRoundTripConfirmTradeResponse() throws InvalidProtocolBufferException {
        var response = ConfirmTradeResponse.newBuilder()
                .setOutcome(ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_EXECUTED)
                .build();
        var header = new PacketHeader(Opcode.OPCODE_CONFIRM_TRADE_RESPONSE_VALUE, ProtocolVersion.CURRENT, 5);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = ConfirmTradeResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_EXECUTED);
    }

    @Test
    void shouldRoundTripGetTradeStatusResponse() throws InvalidProtocolBufferException {
        GetTradeStatusRequest.newBuilder().build();
        var item = TradeOfferItemView.newBuilder()
                .setItemId(1L)
                .setItemTemplateId(200L)
                .setQuantity(2)
                .setEnhanceLevel(3)
                .build();
        var response = GetTradeStatusResponse.newBuilder()
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
        var header = new PacketHeader(Opcode.OPCODE_GET_TRADE_STATUS_RESPONSE_VALUE, ProtocolVersion.CURRENT, 6);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        var parsed = GetTradeStatusResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

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
        var event = TradeRequestedEvent.newBuilder()
                .setSessionId(1L)
                .setFromCharacterId(5L)
                .setFromName("naruto")
                .build();
        var header = new PacketHeader(Opcode.OPCODE_TRADE_REQUESTED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        var parsed = TradeRequestedEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSessionId()).isEqualTo(1L);
        assertThat(parsed.getFromCharacterId()).isEqualTo(5L);
        assertThat(parsed.getFromName()).isEqualTo("naruto");
    }

    @Test
    void shouldRoundTripTradeUpdatedEvent() throws InvalidProtocolBufferException {
        var event = TradeUpdatedEvent.newBuilder().setSessionId(1L).build();
        var header = new PacketHeader(Opcode.OPCODE_TRADE_UPDATED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        var parsed = TradeUpdatedEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSessionId()).isEqualTo(1L);
    }

    @Test
    void shouldRoundTripTradeClosedEvent() throws InvalidProtocolBufferException {
        var event = TradeClosedEvent.newBuilder()
                .setSessionId(1L)
                .setReason(TradeCloseReason.TRADE_CLOSE_REASON_EXECUTED)
                .build();
        var header = new PacketHeader(Opcode.OPCODE_TRADE_CLOSED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        var parsed = TradeClosedEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSessionId()).isEqualTo(1L);
        assertThat(parsed.getReason()).isEqualTo(TradeCloseReason.TRADE_CLOSE_REASON_EXECUTED);
    }
}
