package dev.kage.client.core.world;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.client.core.net.GameConnection;
import dev.kage.protocol.AcceptPartyInviteCommand;
import dev.kage.protocol.AcceptFriendRequestCommand;
import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ClaimQuestRewardCommand;
import dev.kage.protocol.CreatePartyCommand;
import dev.kage.protocol.GetMyPartyRequest;
import dev.kage.protocol.InvitePartyMemberCommand;
import dev.kage.protocol.JumpCommand;
import dev.kage.protocol.KickPartyMemberCommand;
import dev.kage.protocol.LeavePartyCommand;
import dev.kage.protocol.ListPartyMembersRequest;
import dev.kage.protocol.ListPendingPartyInvitesRequest;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RejectPartyInviteCommand;
import dev.kage.protocol.ListFriendsRequest;
import dev.kage.protocol.ListPendingFriendRequestsRequest;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RejectFriendRequestCommand;
import dev.kage.protocol.RemoveFriendCommand;
import dev.kage.protocol.SelectDialogueOptionCommand;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SendFriendRequestCommand;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.TalkToNpcCommand;
import dev.kage.protocol.UseSkillCommand;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Gửi Ý ĐỊNH của người chơi lên server (CLIENT.md §1 — client không bao giờ gửi KẾT QUẢ). Gom mọi
 * lệnh gameplay về một chỗ vì chúng chia sẻ đúng một quyết định đáng nhớ: <b>mất kết nối tạm
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

    /**
     * Gửi một tin nhắn chat.
     *
     * <p>KHÔNG có {@code idempotency_key} và đó là đúng: gửi trùng một tin nhắn chỉ làm nó hiện hai
     * lần, không trừ vàng cũng không đổi trạng thái nào (khác {@code BuyItemCommand}/
     * {@code ClaimQuestRewardCommand}, ADR-025) — thêm khoá vào đây là trả giá cho một rủi ro không
     * tồn tại.
     *
     * @param targetName chỉ có nghĩa với {@link ChatChannel#CHAT_CHANNEL_PRIVATE}; chuỗi rỗng cho
     *     mọi kênh khác. Server tự tra nhân vật theo tên và trả
     *     {@code TARGET_NOT_FOUND}/{@code TARGET_OFFLINE} — client KHÔNG kiểm trước (Điều 24).
     */
    void sendChatMessage(ChatChannel channel, String targetName, String body) {
        SendChatMessageCommand command = SendChatMessageCommand.newBuilder()
                .setChannel(channel)
                .setTargetName(targetName)
                .setBody(body)
                .build();
        send(Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE, command.toByteArray(), "SendChatMessageCommand");
    }

    // --- Nhóm (party.proto, dải 1300-1399) ----------------------------------------------------
    // Ba lệnh HỎI ở cuối cụm không đổi gì cả — chúng tồn tại vì party.proto KHÔNG có event đẩy nào,
    // nên trạng thái nhóm chỉ đúng đúng lúc vừa hỏi xong (xem javadoc PartyController).

    /** Tạo nhóm mới, tự làm trưởng nhóm. Gói rỗng — server tự biết ai gửi (party.proto). */
    void createParty() {
        send(
                Opcode.OPCODE_CREATE_PARTY_COMMAND_VALUE,
                CreatePartyCommand.newBuilder().build().toByteArray(),
                "CreatePartyCommand");
    }

    /**
     * Mời một nhân vật vào nhóm THEO TÊN. Server tự tra tên và trả {@code TARGET_NOT_FOUND} nếu sai
     * — client KHÔNG kiểm trước (Điều 24), cùng cách {@code sendChatMessage} làm với kênh riêng.
     */
    void invitePartyMember(String targetName) {
        InvitePartyMemberCommand command =
                InvitePartyMemberCommand.newBuilder().setTargetName(targetName).build();
        send(Opcode.OPCODE_INVITE_PARTY_MEMBER_COMMAND_VALUE, command.toByteArray(), "InvitePartyMemberCommand");
    }

    /** @param inviteId lấy từ {@code PendingPartyInviteView} — client không tự sinh id nào. */
    void acceptPartyInvite(long inviteId) {
        AcceptPartyInviteCommand command =
                AcceptPartyInviteCommand.newBuilder().setInviteId(inviteId).build();
        send(Opcode.OPCODE_ACCEPT_PARTY_INVITE_COMMAND_VALUE, command.toByteArray(), "AcceptPartyInviteCommand");
    }

    /** @param inviteId xem {@link #acceptPartyInvite(long)}. */
    void rejectPartyInvite(long inviteId) {
        RejectPartyInviteCommand command =
                RejectPartyInviteCommand.newBuilder().setInviteId(inviteId).build();
        send(Opcode.OPCODE_REJECT_PARTY_INVITE_COMMAND_VALUE, command.toByteArray(), "RejectPartyInviteCommand");
    }

    /**
     * Rời nhóm. Dùng chung cho CẢ trưởng nhóm lẫn thành viên thường — trưởng rời thì server tự thăng
     * người vào sớm nhất, không có lệnh giải tán riêng (party.proto).
     */
    void leaveParty() {
        send(
                Opcode.OPCODE_LEAVE_PARTY_COMMAND_VALUE,
                LeavePartyCommand.newBuilder().build().toByteArray(),
                "LeavePartyCommand");
    }

    /** Đuổi một thành viên THEO TÊN — xem {@link #invitePartyMember(String)} về việc gửi tên. */
    void kickPartyMember(String targetName) {
        KickPartyMemberCommand command =
                KickPartyMemberCommand.newBuilder().setTargetName(targetName).build();
        send(Opcode.OPCODE_KICK_PARTY_MEMBER_COMMAND_VALUE, command.toByteArray(), "KickPartyMemberCommand");
    }

    void getMyParty() {
        send(
                Opcode.OPCODE_GET_MY_PARTY_REQUEST_VALUE,
                GetMyPartyRequest.newBuilder().build().toByteArray(),
                "GetMyPartyRequest");
    }

    void listPartyMembers() {
        send(
                Opcode.OPCODE_LIST_PARTY_MEMBERS_REQUEST_VALUE,
                ListPartyMembersRequest.newBuilder().build().toByteArray(),
                "ListPartyMembersRequest");
    }

    void listPendingPartyInvites() {
        send(
                Opcode.OPCODE_LIST_PENDING_PARTY_INVITES_REQUEST_VALUE,
                ListPendingPartyInvitesRequest.newBuilder().build().toByteArray(),
                "ListPendingPartyInvitesRequest");
    /**
     * Gửi lời mời kết bạn theo TÊN.
     *
     * <p>Tên chứ không phải id là do giao thức quy định (friend.proto): server-gateway tự tra
     * {@code CharacterFacade.findByName} trước khi gọi feature-friend, nên client không cần — và
     * không được — tự đoán id của người kia (Điều 24). Sai tên thì server trả
     * {@code TARGET_NOT_FOUND}.
     */
    void sendFriendRequest(String targetName) {
        SendFriendRequestCommand command =
                SendFriendRequestCommand.newBuilder().setTargetName(targetName).build();
        send(Opcode.OPCODE_SEND_FRIEND_REQUEST_COMMAND_VALUE, command.toByteArray(), "SendFriendRequestCommand");
    }

    /**
     * Chấp nhận một lời mời.
     *
     * @param friendshipId id của LỜI MỜI, lấy từ {@code ListPendingFriendRequestsResponse} — server
     *     tự kiểm người chấp nhận có đúng là người được mời hay không (chống IDOR), client chỉ vọng
     *     lại con số đã nhận.
     */
    void acceptFriendRequest(long friendshipId) {
        AcceptFriendRequestCommand command = AcceptFriendRequestCommand.newBuilder()
                .setFriendshipId(friendshipId)
                .build();
        send(Opcode.OPCODE_ACCEPT_FRIEND_REQUEST_COMMAND_VALUE, command.toByteArray(), "AcceptFriendRequestCommand");
    }

    /** Từ chối một lời mời — cùng quy ước {@code friendshipId} với {@link #acceptFriendRequest}. */
    void rejectFriendRequest(long friendshipId) {
        RejectFriendRequestCommand command = RejectFriendRequestCommand.newBuilder()
                .setFriendshipId(friendshipId)
                .build();
        send(Opcode.OPCODE_REJECT_FRIEND_REQUEST_COMMAND_VALUE, command.toByteArray(), "RejectFriendRequestCommand");
    }

    /** Xoá một người khỏi danh sách bạn — theo TÊN, cùng lý do {@link #sendFriendRequest}. */
    void removeFriend(String targetName) {
        RemoveFriendCommand command =
                RemoveFriendCommand.newBuilder().setTargetName(targetName).build();
        send(Opcode.OPCODE_REMOVE_FRIEND_COMMAND_VALUE, command.toByteArray(), "RemoveFriendCommand");
    }

    /** Hỏi trọn danh sách bạn bè. Gói rỗng — người hỏi lấy từ session phía server (Điều 24). */
    void listFriends() {
        send(
                Opcode.OPCODE_LIST_FRIENDS_REQUEST_VALUE,
                ListFriendsRequest.newBuilder().build().toByteArray(),
                "ListFriendsRequest");
    }

    /** Hỏi trọn danh sách lời mời đang chờ — cùng hình dạng {@link #listFriends}. */
    void listPendingFriendRequests() {
        send(
                Opcode.OPCODE_LIST_PENDING_FRIEND_REQUESTS_REQUEST_VALUE,
                ListPendingFriendRequestsRequest.newBuilder().build().toByteArray(),
                "ListPendingFriendRequestsRequest");
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
