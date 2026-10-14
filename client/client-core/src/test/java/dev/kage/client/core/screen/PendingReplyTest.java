package dev.kage.client.core.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Màn đăng nhập / chọn nhân vật chỉ được nhận gói trả lời của ĐÚNG yêu cầu đang chờ.
 *
 * <p>Tái hiện lỗi thật: chờ {@code SelectCharacterResponse} quá 5 giây thì màn hình báo lỗi và mở
 * khoá danh sách. Câu trả lời muộn cho nhân vật A vẫn tới sau đó; người chơi bấm nhân vật B — bản
 * cũ chỉ so OPCODE nên lấy câu trả lời của A làm câu trả lời của B và dựng màn world với tên/hệ phái
 * của B nhưng vị trí/máu của A. Cùng kiểu đó: {@code ErrorResponse} muộn của một lần tạo nhân vật đã
 * hết giờ làm hỏng lần chọn nhân vật đang chờ. Server luôn trả lại {@code sequence_id} của yêu cầu
 * (đúng điều {@code SessionRecovery} đã dựa vào).
 */
class PendingReplyTest {

    private static final int SELECT = Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE;

    private final Deque<PacketCodec.Frame> inbound = new ArrayDeque<>();

    @Test
    void shouldSkipALateReplyToAnEarlierRequest() {
        PacketCodec.Frame lateReplyForA = frame(SELECT, 3);
        PacketCodec.Frame replyForB = frame(SELECT, 7);
        inbound.add(lateReplyForA);
        inbound.add(replyForB);

        Optional<PacketCodec.Frame> reply = PendingReply.poll(inbound::poll, 7, SELECT);

        assertThat(reply).containsSame(replyForB);
    }

    /**
     * README dòng 73(c), 2026-10-14: lúc chờ {@code SelectCharacterResponse}, gói không khớp được trao cho
     * người gọi (để giữ lại cho màn world) chứ không vứt — theo đúng thứ tự tới.
     */
    @Test
    void shouldHandSkippedFramesToTheCaller_inArrivalOrder() {
        PacketCodec.Frame spawn = frame(Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE, 0);
        PacketCodec.Frame lateReplyForA = frame(SELECT, 3);
        PacketCodec.Frame replyForB = frame(SELECT, 7);
        inbound.add(spawn);
        inbound.add(lateReplyForA);
        inbound.add(replyForB);
        List<PacketCodec.Frame> skipped = new ArrayList<>();

        Optional<PacketCodec.Frame> reply = PendingReply.poll(inbound::poll, 7, SELECT, skipped::add);

        assertThat(reply).containsSame(replyForB);
        assertThat(skipped).containsExactly(spawn, lateReplyForA);
    }

    @Test
    void shouldSkipALateErrorForAnEarlierRequest() {
        inbound.add(frame(Opcode.OPCODE_ERROR_VALUE, 3));

        assertThat(PendingReply.poll(inbound::poll, 7, SELECT)).isEmpty();
    }

    @Test
    void shouldReturnTheErrorForThePendingRequest() {
        PacketCodec.Frame error = frame(Opcode.OPCODE_ERROR_VALUE, 7);
        inbound.add(error);

        assertThat(PendingReply.poll(inbound::poll, 7, SELECT)).containsSame(error);
    }

    @Test
    void shouldLeaveFramesAfterTheReplyInTheQueue() {
        // Gói world tới ngay sau SelectCharacterResponse thuộc về màn world sắp dựng — không được nuốt.
        PacketCodec.Frame worldPacket = frame(Opcode.OPCODE_ERROR_VALUE + 1000, 0);
        inbound.add(frame(SELECT, 7));
        inbound.add(worldPacket);

        PendingReply.poll(inbound::poll, 7, SELECT);

        assertThat(inbound).containsExactly(worldPacket);
    }

    private static PacketCodec.Frame frame(int opcode, int sequenceId) {
        return new PacketCodec.Frame(new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId), new byte[0]);
    }
}
