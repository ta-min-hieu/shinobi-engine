package dev.kage.client.core.world;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.client.core.net.GameConnection;
import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.ClaimQuestRewardCommand;
import dev.kage.protocol.JumpCommand;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectDialogueOptionCommand;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.TalkToNpcCommand;
import dev.kage.protocol.UseSkillCommand;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Gửi Ý ĐỊNH của người chơi lên server (CLIENT.md §1 — client không bao giờ gửi KẾT QUẢ). Gom cả
 * năm lệnh gameplay về một chỗ vì chúng chia sẻ đúng một quyết định đáng nhớ: <b>mất kết nối tạm
 * thời KHÔNG phải lỗi</b>.
 *
 * <p>{@link GameConnection} tự kết nối lại; ý định mới nhất sẽ được gửi ở lần người chơi thao tác
 * tiếp theo, nên retry thủ công ở đây chỉ tạo ra hàng đợi lệnh cũ mèm đổ ập lên server ngay khi nối
 * lại. Vì vậy mọi hàm ở đây nuốt {@link IOException} xuống mức {@code debug} — đây là ngoại lệ CÓ
 * CHỦ ĐÍCH của "cấm nuốt exception" (/CLAUDE.md §11), không phải quên xử lý.
 */
final class WorldCommandSender {

    private static final Logger LOG = LoggerFactory.getLogger(WorldCommandSender.class);

    private final GameConnection connection;

    WorldCommandSender(GameConnection connection) {
        this.connection = connection;
    }

    /**
     * @param dirX hướng đi ngang đã chuẩn hoá (-1/0/1). {@code dirY} luôn 0 kể từ ADR-033 — field
     *     còn trên dây nhưng server-world bỏ qua, xem {@code WorldScreen}.
     */
    void moveDirection(float dirX) {
        MoveCommand command = MoveCommand.newBuilder().setDirX(dirX).setDirY(0f).build();
        send(Opcode.OPCODE_MOVE_COMMAND_VALUE, command.toByteArray(), "MoveCommand");
    }

    void jump() {
        send(Opcode.OPCODE_JUMP_COMMAND_VALUE, JumpCommand.newBuilder().build().toByteArray(), "JumpCommand");
    }

    /** Đánh thường một đòn — mục tiêu lấy từ phía server, gói này rỗng (combat.proto). */
    void attack() {
        send(
                Opcode.OPCODE_ATTACK_COMMAND_VALUE,
                AttackCommand.newBuilder().build().toByteArray(),
                "AttackCommand");
    }

    void setTarget(long targetEntityId) {
        SetTargetCommand command =
                SetTargetCommand.newBuilder().setTargetEntityId(targetEntityId).build();
        send(Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, command.toByteArray(), "SetTargetCommand");
    }

    void useSkill(String skillCode, long targetEntityId) {
        UseSkillCommand command = UseSkillCommand.newBuilder()
                .setSkillCode(skillCode)
                .setTargetEntityId(targetEntityId)
                .build();
        send(Opcode.OPCODE_USE_SKILL_COMMAND_VALUE, command.toByteArray(), "UseSkillCommand");
    }

    /** Bắt chuyện với NPC — server luôn trả node {@code "start"} (dialogue.proto). */
    void talkToNpc(String npcCode) {
        TalkToNpcCommand command =
                TalkToNpcCommand.newBuilder().setNpcCode(npcCode).build();
        send(Opcode.OPCODE_TALK_TO_NPC_COMMAND_VALUE, command.toByteArray(), "TalkToNpcCommand");
    }

    /**
     * Chọn một lựa chọn trong node đang mở. Gửi CHỈ SỐ chứ không gửi {@code action} — server tự tra
     * lại action thật từ script theo chỉ số (Điều 24, xem {@link DialogueController}).
     */
    void selectDialogueOption(String npcCode, String currentNodeId, int optionIndex) {
        SelectDialogueOptionCommand command = SelectDialogueOptionCommand.newBuilder()
                .setNpcCode(npcCode)
                .setCurrentNodeId(currentNodeId)
                .setOptionIndex(optionIndex)
                .build();
        send(Opcode.OPCODE_SELECT_DIALOGUE_OPTION_COMMAND_VALUE, command.toByteArray(), "SelectDialogueOptionCommand");
    }

    /**
     * Lĩnh thưởng nhiệm vụ.
     *
     * @param idempotencyKey UUID do client sinh, GIỮ NGUYÊN qua các lần thử lại cùng một lần lĩnh
     *     (ADR-025) — server enforce unique trong CÙNG transaction với hiệu ứng thật (EXP + vật phẩm
     *     + đổi status), xem {@link QuestTracker#claimKey()}.
     */
    void claimQuestReward(String questCode, String idempotencyKey) {
        ClaimQuestRewardCommand command = ClaimQuestRewardCommand.newBuilder()
                .setQuestCode(questCode)
                .setIdempotencyKey(idempotencyKey)
                .build();
        send(Opcode.OPCODE_CLAIM_QUEST_REWARD_COMMAND_VALUE, command.toByteArray(), "ClaimQuestRewardCommand");
    }

    /** Gói tin đến từ server — {@code null} khi hàng đợi rỗng (xem {@link GameConnection}). */
    PacketCodec.Frame pollInbound() {
        return connection.pollInbound();
    }

    private void send(int opcode, byte[] payload, String commandName) {
        try {
            connection.send(opcode, connection.nextSequenceId(), payload);
        } catch (IOException e) {
            LOG.debug("Gửi {} thất bại (mất kết nối tạm thời)", commandName, e);
        }
    }
}
