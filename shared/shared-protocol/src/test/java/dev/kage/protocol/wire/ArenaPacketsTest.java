package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.AcceptDuelCommand;
import dev.kage.protocol.AcceptDuelOutcome;
import dev.kage.protocol.AcceptDuelResponse;
import dev.kage.protocol.ChallengeDuelCommand;
import dev.kage.protocol.ChallengeDuelOutcome;
import dev.kage.protocol.ChallengeDuelResponse;
import dev.kage.protocol.DuelChallengeReceivedEvent;
import dev.kage.protocol.DuelResultEvent;
import dev.kage.protocol.DuelStartedEvent;
import dev.kage.protocol.GetPvpRatingRequest;
import dev.kage.protocol.GetPvpRatingResponse;
import dev.kage.protocol.Opcode;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Arena (1600-1699, NETWORK.md §4) + GetPvpRating (1200-1299, Ranking).
 */
class ArenaPacketsTest {

    @Test
    void shouldRoundTripChallengeDuelCommandAndResponse() throws InvalidProtocolBufferException {
        ChallengeDuelCommand command =
                ChallengeDuelCommand.newBuilder().setTargetName("shinobi_b").build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_CHALLENGE_DUEL_COMMAND_VALUE, ProtocolVersion.CURRENT, 1);
        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        ChallengeDuelCommand parsedCommand = ChallengeDuelCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());
        assertThat(parsedCommand.getTargetName()).isEqualTo("shinobi_b");

        ChallengeDuelResponse response = ChallengeDuelResponse.newBuilder()
                .setOutcome(ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_OK)
                .setChallengeId(42L)
                .build();
        PacketHeader responseHeader =
                new PacketHeader(Opcode.OPCODE_CHALLENGE_DUEL_RESPONSE_VALUE, ProtocolVersion.CURRENT, 1);
        byte[] responseBytes = PacketCodec.encodeFrame(responseHeader, response.toByteArray());
        ChallengeDuelResponse parsedResponse = ChallengeDuelResponse.parseFrom(
                PacketCodec.decodeFrame(responseBytes).payload());
        assertThat(parsedResponse.getOutcome()).isEqualTo(ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_OK);
        assertThat(parsedResponse.getChallengeId()).isEqualTo(42L);
    }

    @Test
    void shouldRoundTripAcceptDuelCommandAndResponse() throws InvalidProtocolBufferException {
        AcceptDuelCommand command =
                AcceptDuelCommand.newBuilder().setChallengeId(7L).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_ACCEPT_DUEL_COMMAND_VALUE, ProtocolVersion.CURRENT, 2);
        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        assertThat(AcceptDuelCommand.parseFrom(
                        PacketCodec.decodeFrame(frameBytes).payload()))
                .isEqualTo(command);

        AcceptDuelResponse response = AcceptDuelResponse.newBuilder()
                .setOutcome(AcceptDuelOutcome.ACCEPT_DUEL_OUTCOME_NOT_FOUND)
                .build();
        PacketHeader responseHeader =
                new PacketHeader(Opcode.OPCODE_ACCEPT_DUEL_RESPONSE_VALUE, ProtocolVersion.CURRENT, 2);
        byte[] responseBytes = PacketCodec.encodeFrame(responseHeader, response.toByteArray());
        AcceptDuelResponse parsedResponse = AcceptDuelResponse.parseFrom(
                PacketCodec.decodeFrame(responseBytes).payload());
        assertThat(parsedResponse.getOutcome()).isEqualTo(AcceptDuelOutcome.ACCEPT_DUEL_OUTCOME_NOT_FOUND);
    }

    @Test
    void shouldRoundTripDuelChallengeReceivedEvent() throws InvalidProtocolBufferException {
        DuelChallengeReceivedEvent event = DuelChallengeReceivedEvent.newBuilder()
                .setChallengeId(7L)
                .setChallengerCharacterId(1L)
                .setChallengerName("shinobi_a")
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_DUEL_CHALLENGE_RECEIVED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);
        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        DuelChallengeReceivedEvent parsed = DuelChallengeReceivedEvent.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());
        assertThat(parsed.getChallengerCharacterId()).isEqualTo(1L);
        assertThat(parsed.getChallengerName()).isEqualTo("shinobi_a");
    }

    @Test
    void shouldRoundTripDuelStartedEvent() throws InvalidProtocolBufferException {
        DuelStartedEvent event = DuelStartedEvent.newBuilder()
                .setOpponentCharacterId(2L)
                .setOpponentName("shinobi_b")
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_DUEL_STARTED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);
        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        DuelStartedEvent parsed =
                DuelStartedEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());
        assertThat(parsed.getOpponentCharacterId()).isEqualTo(2L);
        assertThat(parsed.getOpponentName()).isEqualTo("shinobi_b");
    }

    @Test
    void shouldRoundTripDuelResultEvent() throws InvalidProtocolBufferException {
        DuelResultEvent event = DuelResultEvent.newBuilder()
                .setWinnerCharacterId(1L)
                .setLoserCharacterId(2L)
                .setWinnerNewRating(1016)
                .setLoserNewRating(984)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_DUEL_RESULT_EVENT_VALUE, ProtocolVersion.CURRENT, 0);
        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        DuelResultEvent parsed =
                DuelResultEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());
        assertThat(parsed.getWinnerCharacterId()).isEqualTo(1L);
        assertThat(parsed.getLoserCharacterId()).isEqualTo(2L);
        assertThat(parsed.getWinnerNewRating()).isEqualTo(1016);
        assertThat(parsed.getLoserNewRating()).isEqualTo(984);
    }

    @Test
    void shouldRoundTripGetPvpRatingRequestAndResponse() throws InvalidProtocolBufferException {
        GetPvpRatingRequest request = GetPvpRatingRequest.newBuilder().build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_GET_PVP_RATING_REQUEST_VALUE, ProtocolVersion.CURRENT, 3);
        byte[] frameBytes = PacketCodec.encodeFrame(header, request.toByteArray());
        assertThat(GetPvpRatingRequest.parseFrom(
                        PacketCodec.decodeFrame(frameBytes).payload()))
                .isEqualTo(request);

        GetPvpRatingResponse response = GetPvpRatingResponse.newBuilder()
                .setRating(1000)
                .setWins(3)
                .setLosses(1)
                .build();
        PacketHeader responseHeader =
                new PacketHeader(Opcode.OPCODE_GET_PVP_RATING_RESPONSE_VALUE, ProtocolVersion.CURRENT, 3);
        byte[] responseBytes = PacketCodec.encodeFrame(responseHeader, response.toByteArray());
        GetPvpRatingResponse parsedResponse = GetPvpRatingResponse.parseFrom(
                PacketCodec.decodeFrame(responseBytes).payload());
        assertThat(parsedResponse.getRating()).isEqualTo(1000);
        assertThat(parsedResponse.getWins()).isEqualTo(3);
        assertThat(parsedResponse.getLosses()).isEqualTo(1);
    }
}
