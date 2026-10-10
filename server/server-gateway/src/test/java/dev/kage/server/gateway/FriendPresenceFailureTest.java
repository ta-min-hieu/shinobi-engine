package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.friend.api.FriendFacade;
import dev.kage.friend.application.AcceptFriendRequestUseCase;
import dev.kage.friend.application.FriendUseCases;
import dev.kage.friend.application.ListFriendsUseCase;
import dev.kage.friend.application.ListPendingFriendRequestsUseCase;
import dev.kage.friend.application.RejectFriendRequestUseCase;
import dev.kage.friend.application.RemoveFriendUseCase;
import dev.kage.friend.application.SendFriendRequestUseCase;
import dev.kage.friend.domain.FriendPort;
import dev.kage.friend.domain.Friendship;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SendChatMessageOutcome;
import dev.kage.protocol.SendChatMessageResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Báo "tôi vừa online" cho bạn bè là việc PHỤ của lần vào world — nó hỏng thì không được biến một
 * lần vào world ĐÃ THÀNH CÔNG thành lỗi (rà 2026-10-10).
 *
 * <p>Trước bản vá: {@code SelectCharacterResponse(success)} đã gửi xong, rồi lần đọc danh sách bạn
 * (bảng {@code friendship}) lỗi ⇒ handler gửi THÊM {@code error.select_character_failed} cho cùng
 * {@code sequenceId}. Người chơi đã đứng trong world mà màn hình báo "chọn nhân vật thất bại" — một
 * câu trả lời tự mâu thuẫn, chỉ vì một bảng không liên quan gì tới việc vào world trục trặc.
 */
@Timeout(30)
class FriendPresenceFailureTest {

    private final SharedWorldPipelines gateway = new SharedWorldPipelines(friendFacadeWithBrokenList());

    @AfterEach
    void stopGateway() {
        gateway.stop();
    }

    @Test
    void shouldStillEnterWorldCleanly_whenTheFriendListCannotBeRead() throws InvalidProtocolBufferException {
        EmbeddedChannel channel = gateway.newChannel();
        GatewayPipelineTest.connect(channel, "brokenfriends");
        long characterId = GatewayPipelineTest.createCharacter(channel, "Lonely", 2);

        GatewayPipelineTest.writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, ProtocolVersion.CURRENT, 3),
                SelectCharacterRequest.newBuilder()
                        .setCharacterId(characterId)
                        .build()
                        .toByteArray());
        List<PacketCodec.Frame> frames = GatewayPipelineTest.readOutboundFrames(channel);

        assertThat(SelectCharacterResponse.parseFrom(frames.getFirst().payload())
                        .getSuccess())
                .isTrue();
        assertThat(frames)
                .as("không được có gói lỗi theo sau một lần vào world đã thành công")
                .noneMatch(frame -> frame.header().opcode() == Opcode.OPCODE_ERROR_VALUE);

        // Và nhân vật thật sự dùng được: một lệnh cần đã-vào-world vẫn chạy.
        GatewayPipelineTest.writeInboundFrame(
                channel,
                new PacketHeader(Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE, ProtocolVersion.CURRENT, 4),
                SendChatMessageCommand.newBuilder()
                        .setChannel(ChatChannel.CHAT_CHANNEL_WORLD)
                        .setBody("vẫn vào được")
                        .build()
                        .toByteArray());
        List<PacketCodec.Frame> chatFrames = GatewayPipelineTest.readOutboundFrames(channel);
        assertThat(chatFrames)
                .filteredOn(frame -> frame.header().opcode() == Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE)
                .singleElement()
                .satisfies(frame -> assertThat(SendChatMessageResponse.parseFrom(frame.payload())
                                .getOutcome())
                        .isEqualTo(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_OK));
    }

    private static FriendFacade friendFacadeWithBrokenList() {
        FriendPort port = new FriendPort() {
            @Override
            public Optional<Friendship> findBetween(long characterIdA, long characterIdB) {
                return Optional.empty();
            }

            @Override
            public Friendship sendRequest(long requesterId, long addresseeId) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }

            @Override
            public boolean accept(long friendshipId, long addresseeId) {
                return false;
            }

            @Override
            public boolean reject(long friendshipId, long addresseeId) {
                return false;
            }

            @Override
            public boolean remove(long characterId, long friendCharacterId) {
                return false;
            }

            @Override
            public List<Friendship> listAccepted(long characterId) {
                throw new IllegalStateException("bảng friendship không đọc được");
            }

            @Override
            public List<Friendship> listPendingIncoming(long characterId) {
                return List.of();
            }
        };
        return new FriendFacade(new FriendUseCases(
                new SendFriendRequestUseCase(port),
                new AcceptFriendRequestUseCase(port),
                new RejectFriendRequestUseCase(port),
                new RemoveFriendUseCase(port),
                new ListFriendsUseCase(port),
                new ListPendingFriendRequestsUseCase(port)));
    }
}
