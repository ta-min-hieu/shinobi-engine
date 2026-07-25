package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.protocol.AcceptQuestOutcome;
import dev.kage.protocol.AcceptQuestResponse;
import dev.kage.protocol.ClaimQuestRewardCommand;
import dev.kage.protocol.ClaimQuestRewardOutcome;
import dev.kage.protocol.ClaimQuestRewardResponse;
import dev.kage.protocol.ItemRewardView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.QuestStatus;
import dev.kage.protocol.QuestStatusChangedEvent;
import org.junit.jupiter.api.Test;

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
        AcceptQuestResponse parsed = AcceptQuestResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

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
        QuestStatusChangedEvent parsed =
                QuestStatusChangedEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getQuestCode()).isEqualTo("kill_training_dummies");
        assertThat(parsed.getStatus()).isEqualTo(QuestStatus.QUEST_STATUS_COMPLETED);
    }

    @Test
    void shouldRoundTripClaimQuestRewardCommand() throws InvalidProtocolBufferException {
        ClaimQuestRewardCommand command =
                ClaimQuestRewardCommand.newBuilder().setQuestCode("kill_training_dummies").build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_CLAIM_QUEST_REWARD_COMMAND_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        ClaimQuestRewardCommand parsed =
                ClaimQuestRewardCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getQuestCode()).isEqualTo("kill_training_dummies");
    }

    @Test
    void shouldRoundTripClaimQuestRewardResponse() throws InvalidProtocolBufferException {
        ItemRewardView itemReward = ItemRewardView.newBuilder().setItemTemplateCode("kunai").setQuantity(5).build();
        ClaimQuestRewardResponse response = ClaimQuestRewardResponse.newBuilder()
                .setOutcome(ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_OK)
                .setExpGranted(500L)
                .addItemsGranted(itemReward)
                .build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_CLAIM_QUEST_REWARD_RESPONSE_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ClaimQuestRewardResponse parsed =
                ClaimQuestRewardResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_OK);
        assertThat(parsed.getExpGranted()).isEqualTo(500L);
        assertThat(parsed.getItemsGrantedList()).hasSize(1);
        assertThat(parsed.getItemsGranted(0).getItemTemplateCode()).isEqualTo("kunai");
        assertThat(parsed.getItemsGranted(0).getQuantity()).isEqualTo(5);
    }
}
