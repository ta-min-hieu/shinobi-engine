package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.AcceptQuestOutcome;
import dev.kage.protocol.AcceptQuestResponse;
import dev.kage.protocol.ClaimQuestRewardCommand;
import dev.kage.protocol.ClaimQuestRewardOutcome;
import dev.kage.protocol.ClaimQuestRewardResponse;
import dev.kage.protocol.ItemRewardView;
import dev.kage.protocol.ListQuestsRequest;
import dev.kage.protocol.ListQuestsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.QuestLogEntry;
import dev.kage.protocol.QuestStatus;
import dev.kage.protocol.QuestStatusChangedEvent;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Quest (600-699 dùng chung NPC/Dialogue, NETWORK.md §4).
 */
class QuestPacketsTest {

    @Test
    void shouldRoundTripAcceptQuestResponse() throws InvalidProtocolBufferException {
        AcceptQuestResponse response = AcceptQuestResponse.newBuilder()
                .setOutcome(AcceptQuestOutcome.ACCEPT_QUEST_OUTCOME_ALREADY_ACCEPTED)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_ACCEPT_QUEST_RESPONSE_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        AcceptQuestResponse parsed = AcceptQuestResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(AcceptQuestOutcome.ACCEPT_QUEST_OUTCOME_ALREADY_ACCEPTED);
    }

    @Test
    void shouldRoundTripQuestStatusChangedEvent() throws InvalidProtocolBufferException {
        QuestStatusChangedEvent event = QuestStatusChangedEvent.newBuilder()
                .setQuestCode("kill_training_dummies")
                .setStatus(QuestStatus.QUEST_STATUS_COMPLETED)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_QUEST_STATUS_CHANGED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        QuestStatusChangedEvent parsed = QuestStatusChangedEvent.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getQuestCode()).isEqualTo("kill_training_dummies");
        assertThat(parsed.getStatus()).isEqualTo(QuestStatus.QUEST_STATUS_COMPLETED);
    }

    @Test
    void shouldRoundTripClaimQuestRewardCommand() throws InvalidProtocolBufferException {
        ClaimQuestRewardCommand command = ClaimQuestRewardCommand.newBuilder()
                .setQuestCode("kill_training_dummies")
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_CLAIM_QUEST_REWARD_COMMAND_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        ClaimQuestRewardCommand parsed = ClaimQuestRewardCommand.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getQuestCode()).isEqualTo("kill_training_dummies");
    }

    @Test
    void shouldRoundTripClaimQuestRewardResponse() throws InvalidProtocolBufferException {
        ItemRewardView itemReward = ItemRewardView.newBuilder()
                .setItemTemplateCode("kunai")
                .setQuantity(5)
                .build();
        ClaimQuestRewardResponse response = ClaimQuestRewardResponse.newBuilder()
                .setOutcome(ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_OK)
                .setExpGranted(500L)
                .addItemsGranted(itemReward)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_CLAIM_QUEST_REWARD_RESPONSE_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ClaimQuestRewardResponse parsed = ClaimQuestRewardResponse.parseFrom(
                PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_OK);
        assertThat(parsed.getExpGranted()).isEqualTo(500L);
        assertThat(parsed.getItemsGrantedList()).hasSize(1);
        assertThat(parsed.getItemsGranted(0).getItemTemplateCode()).isEqualTo("kunai");
        assertThat(parsed.getItemsGranted(0).getQuantity()).isEqualTo(5);
    }

    // --- ADR-035 (Accepted 2026-08-27): nhật ký nhiệm vụ 610/611 ---

    @Test
    void shouldRoundTripListQuestsRequest() throws InvalidProtocolBufferException {
        ListQuestsRequest request = ListQuestsRequest.newBuilder().build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_LIST_QUESTS_REQUEST_VALUE, ProtocolVersion.CURRENT, 6);

        byte[] frameBytes = PacketCodec.encodeFrame(header, request.toByteArray());
        PacketCodec.Frame decoded = PacketCodec.decodeFrame(frameBytes);

        // Không field nào (characterId từ session) — round-trip vẫn phải giữ nguyên header.
        assertThat(decoded.header()).isEqualTo(header);
        assertThat(ListQuestsRequest.parseFrom(decoded.payload())).isEqualTo(request);
    }

    @Test
    void shouldRoundTripQuestLogEntry() throws InvalidProtocolBufferException {
        QuestLogEntry entry = QuestLogEntry.newBuilder()
                .setQuestCode("kill_training_dummies")
                .setStatus(QuestStatus.QUEST_STATUS_IN_PROGRESS)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_LIST_QUESTS_RESPONSE_VALUE, ProtocolVersion.CURRENT, 6);

        byte[] frameBytes = PacketCodec.encodeFrame(header, entry.toByteArray());
        QuestLogEntry parsed =
                QuestLogEntry.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getQuestCode()).isEqualTo("kill_training_dummies");
        assertThat(parsed.getStatus()).isEqualTo(QuestStatus.QUEST_STATUS_IN_PROGRESS);
    }

    @Test
    void shouldRoundTripListQuestsResponse_includingClaimedQuests() throws InvalidProtocolBufferException {
        ListQuestsResponse response = ListQuestsResponse.newBuilder()
                .addQuests(QuestLogEntry.newBuilder()
                        .setQuestCode("kill_training_dummies")
                        .setStatus(QuestStatus.QUEST_STATUS_COMPLETED)
                        .build())
                // CLAIMED phải nằm trong response (ADR-035 Decision-4) — lọc phía server thì client
                // không phân biệt được "chưa từng nhận" với "đã trả".
                .addQuests(QuestLogEntry.newBuilder()
                        .setQuestCode("talk_to_town_elder")
                        .setStatus(QuestStatus.QUEST_STATUS_CLAIMED)
                        .build())
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_LIST_QUESTS_RESPONSE_VALUE, ProtocolVersion.CURRENT, 6);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ListQuestsResponse parsed =
                ListQuestsResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getQuestsList()).hasSize(2);
        assertThat(parsed.getQuests(0).getQuestCode()).isEqualTo("kill_training_dummies");
        assertThat(parsed.getQuests(0).getStatus()).isEqualTo(QuestStatus.QUEST_STATUS_COMPLETED);
        assertThat(parsed.getQuests(1).getStatus()).isEqualTo(QuestStatus.QUEST_STATUS_CLAIMED);
    }
}
