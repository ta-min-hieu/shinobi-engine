package dev.kage.client.core.world;

import java.io.IOException;
import java.util.List;

import com.google.protobuf.MessageLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.client.core.net.GameConnection;
import dev.kage.protocol.AbandonQuestCommand;
import dev.kage.protocol.AcceptDuelCommand;
import dev.kage.protocol.AcceptFriendRequestCommand;
import dev.kage.protocol.AcceptPartyInviteCommand;
import dev.kage.protocol.AcceptTradeRequestCommand;
import dev.kage.protocol.ApproveGuildJoinRequestCommand;
import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.BuyItemCommand;
import dev.kage.protocol.CancelTradeCommand;
import dev.kage.protocol.ChallengeDuelCommand;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ClaimMailCommand;
import dev.kage.protocol.ClaimQuestRewardCommand;
import dev.kage.protocol.ConfirmTradeCommand;
import dev.kage.protocol.CreateGuildCommand;
import dev.kage.protocol.CreatePartyCommand;
import dev.kage.protocol.DeclineDuelCommand;
import dev.kage.protocol.DeleteMailCommand;
import dev.kage.protocol.DisbandGuildCommand;
import dev.kage.protocol.EnhanceItemCommand;
import dev.kage.protocol.EnterDungeonCommand;
import dev.kage.protocol.EquipItemCommand;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.ExitDuelCommand;
import dev.kage.protocol.ExitDungeonCommand;
import dev.kage.protocol.Faction;
import dev.kage.protocol.GetItemCatalogRequest;
import dev.kage.protocol.GetMyGuildRequest;
import dev.kage.protocol.GetMyPartyRequest;
import dev.kage.protocol.GetPvpRatingRequest;
import dev.kage.protocol.GetTradeStatusRequest;
import dev.kage.protocol.InvitePartyMemberCommand;
import dev.kage.protocol.JumpCommand;
import dev.kage.protocol.KickGuildMemberCommand;
import dev.kage.protocol.KickPartyMemberCommand;
import dev.kage.protocol.LeaveGuildCommand;
import dev.kage.protocol.LeavePartyCommand;
import dev.kage.protocol.ListFriendsRequest;
import dev.kage.protocol.ListGuildMembersRequest;
import dev.kage.protocol.ListInventoryRequest;
import dev.kage.protocol.ListMailRequest;
import dev.kage.protocol.ListPartyMembersRequest;
import dev.kage.protocol.ListPendingFriendRequestsRequest;
import dev.kage.protocol.ListPendingGuildJoinRequestsRequest;
import dev.kage.protocol.ListPendingPartyInvitesRequest;
import dev.kage.protocol.ListQuestsRequest;
import dev.kage.protocol.ListShopItemsRequest;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RejectFriendRequestCommand;
import dev.kage.protocol.RejectGuildJoinRequestCommand;
import dev.kage.protocol.RejectPartyInviteCommand;
import dev.kage.protocol.RemoveFriendCommand;
import dev.kage.protocol.RequestTradeCommand;
import dev.kage.protocol.SelectDialogueOptionCommand;
import dev.kage.protocol.SelectFactionRequest;
import dev.kage.protocol.SellItemCommand;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SendFriendRequestCommand;
import dev.kage.protocol.SendGuildJoinRequestCommand;
import dev.kage.protocol.SendMailCommand;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.SetTradeOfferCommand;
import dev.kage.protocol.TalkToNpcCommand;
import dev.kage.protocol.TradeOfferItemInput;
import dev.kage.protocol.UnequipItemCommand;
import dev.kage.protocol.UseItemCommand;
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
final class WorldCommandSender implements SessionRecovery.Link {

    private static final Logger LOG = LoggerFactory.getLogger(WorldCommandSender.class);

    private final CommandLink connection;

    /** Lệnh đang chờ trả lời — gói trả lời không khớp lệnh nào bị bỏ ở {@link #pollInbound()}. */
    private final ReplyLedger replies = new ReplyLedger();

    WorldCommandSender(GameConnection connection) {
        this(CommandLink.of(connection));
    }

    /** Cho test — đường truyền giả, xem {@link CommandLink}. */
    WorldCommandSender(CommandLink connection) {
        this.connection = connection;
    }

    /**
     * @param dirX hướng đi ngang đã chuẩn hoá (-1/0/1). Trục dọc của lệnh luôn gửi 0 kể từ ADR-033 — field
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

    /**
     * Bắt chuyện với NPC — server luôn trả node {@code "start"} (dialogue.proto).
     *
     * @return {@code sequenceId} của gói — server từ chối (NPC không còn, đứng xa quá) bằng
     *     {@code ErrorResponse} mang đúng số này
     */
    int talkToNpc(String npcCode) {
        TalkToNpcCommand command =
                TalkToNpcCommand.newBuilder().setNpcCode(npcCode).build();
        int sequenceId = connection.nextSequenceId();
        send(Opcode.OPCODE_TALK_TO_NPC_COMMAND_VALUE, sequenceId, command.toByteArray(), "TalkToNpcCommand");
        return sequenceId;
    }

    /**
     * Chọn một lựa chọn trong node đang mở. Gửi CHỈ SỐ chứ không gửi {@code action} — server tự tra
     * lại action thật từ script theo chỉ số (Điều 24, xem {@link DialogueController}).
     *
     * @return {@code sequenceId} của gói, như {@link #talkToNpc}
     */
    int selectDialogueOption(String npcCode, String currentNodeId, int optionIndex) {
        SelectDialogueOptionCommand command = SelectDialogueOptionCommand.newBuilder()
                .setNpcCode(npcCode)
                .setCurrentNodeId(currentNodeId)
                .setOptionIndex(optionIndex)
                .build();
        int sequenceId = connection.nextSequenceId();
        send(
                Opcode.OPCODE_SELECT_DIALOGUE_OPTION_COMMAND_VALUE,
                sequenceId,
                command.toByteArray(),
                "SelectDialogueOptionCommand");
        return sequenceId;
    }

    /**
     * Gửi một yêu cầu CHỈ ĐỌC (bảng nhân vật 210 — ADR-056, Bia Anh Danh 1202 — ADR-057). Một hàm chung
     * thay vì một hàm mỗi gói: lớp này chạm trần độ phức tạp PMD tính cho CẢ LỚP (80), và hai gói này
     * không có gì riêng ngoài opcode + message.
     */
    void sendReadRequest(int opcode, MessageLite request) {
        send(opcode, request.toByteArray(), request.getClass().getSimpleName());
    }

    /** Bỏ nhiệm vụ đang làm (opcode 612, ADR-054) — chỉ ý định; server tự kiểm trạng thái. */
    void abandonQuest(String questCode) {
        send(
                Opcode.OPCODE_ABANDON_QUEST_COMMAND_VALUE,
                AbandonQuestCommand.newBuilder().setQuestCode(questCode).build().toByteArray(),
                "AbandonQuestCommand");
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
     * Hỏi trọn nhật ký nhiệm vụ (opcode 610, ADR-035). Gói rỗng — characterId lấy từ session
     * (quest.proto). Đây là cách DUY NHẤT client biết nhiệm vụ nào đã hoàn thành TRƯỚC phiên chơi
     * hiện tại — xem {@link QuestCoordinator} về việc hỏi lại lúc vào world/mở cửa sổ/sau mỗi lệnh
     * đổi trạng thái, đúng gói vá bug "nhận nhiệm vụ → relog → mất thưởng vĩnh viễn".
     */
    void listQuests() {
        send(
                Opcode.OPCODE_LIST_QUESTS_REQUEST_VALUE,
                ListQuestsRequest.newBuilder().build().toByteArray(),
                "ListQuestsRequest");
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
    }

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

    /**
     * Tạo bang mới — người tạo tự thành trưởng bang (guild.proto).
     *
     * <p>KHÔNG có {@code idempotency_key}, và khác {@code sendChatMessage} thì ở đây đó là một
     * KHOẢNG TRỐNG chứ không phải quyết định: gửi trùng lệnh này sẽ tạo bang thứ hai nếu lần đầu đã
     * kịp thực thi mà response mất trên đường về. Giao thức không cho client làm gì hơn — không tự
     * vá được ở phía này (xem {@link GuildController} về các khoảng trống của guild.proto).
     *
     * @return {@code sequenceId} của gói — tên sai luật bị server từ chối bằng {@code ErrorResponse}
     *     mang đúng số này (2026-09-29), không bằng {@code CreateGuildResponse}
     */
    int createGuild(String name) {
        CreateGuildCommand command =
                CreateGuildCommand.newBuilder().setName(name).build();
        int sequenceId = connection.nextSequenceId();
        send(Opcode.OPCODE_CREATE_GUILD_COMMAND_VALUE, sequenceId, command.toByteArray(), "CreateGuildCommand");
        return sequenceId;
    }

    /**
     * Xin gia nhập theo TÊN BANG (không phải id) — guild.proto cố ý như vậy, cùng lý do
     * {@code target_name} ở friend.proto. Server tự tra tên và trả {@code GUILD_NOT_FOUND} nếu sai,
     * client KHÔNG kiểm trước (Điều 24).
     */
    void sendGuildJoinRequest(String guildName) {
        SendGuildJoinRequestCommand command =
                SendGuildJoinRequestCommand.newBuilder().setGuildName(guildName).build();
        send(Opcode.OPCODE_SEND_GUILD_JOIN_REQUEST_COMMAND_VALUE, command.toByteArray(), "SendGuildJoinRequestCommand");
    }

    /** Duyệt một đơn xin gia nhập. Server tự kiểm mình có phải trưởng bang không (Điều 15). */
    void approveGuildJoinRequest(long requestId) {
        ApproveGuildJoinRequestCommand command = ApproveGuildJoinRequestCommand.newBuilder()
                .setRequestId(requestId)
                .build();
        send(
                Opcode.OPCODE_APPROVE_GUILD_JOIN_REQUEST_COMMAND_VALUE,
                command.toByteArray(),
                "ApproveGuildJoinRequestCommand");
    }

    /** Từ chối một đơn xin gia nhập — server XOÁ dòng đơn, không giữ lịch sử (V14). */
    void rejectGuildJoinRequest(long requestId) {
        RejectGuildJoinRequestCommand command = RejectGuildJoinRequestCommand.newBuilder()
                .setRequestId(requestId)
                .build();
        send(
                Opcode.OPCODE_REJECT_GUILD_JOIN_REQUEST_COMMAND_VALUE,
                command.toByteArray(),
                "RejectGuildJoinRequestCommand");
    }

    /** Rời bang — gói rỗng, server lấy nhân vật từ SESSION chứ không từ packet (guild.proto). */
    void leaveGuild() {
        send(
                Opcode.OPCODE_LEAVE_GUILD_COMMAND_VALUE,
                LeaveGuildCommand.newBuilder().build().toByteArray(),
                "LeaveGuildCommand");
    }

    /** Giải tán bang — gói rỗng như {@link #leaveGuild()}; chỉ trưởng bang làm được, server quyết. */
    void disbandGuild() {
        send(
                Opcode.OPCODE_DISBAND_GUILD_COMMAND_VALUE,
                DisbandGuildCommand.newBuilder().build().toByteArray(),
                "DisbandGuildCommand");
    }

    /** Đuổi một thành viên theo TÊN — guild.proto dùng {@code target_name}, xem {@link #sendGuildJoinRequest}. */
    void kickGuildMember(String targetName) {
        KickGuildMemberCommand command =
                KickGuildMemberCommand.newBuilder().setTargetName(targetName).build();
        send(Opcode.OPCODE_KICK_GUILD_MEMBER_COMMAND_VALUE, command.toByteArray(), "KickGuildMemberCommand");
    }

    /**
     * Hỏi bang của mình.
     *
     * <p>Ba hàm {@code requestGuild*} dưới đây là cách DUY NHẤT client biết trạng thái bang: guild.proto
     * không có event đẩy nào, nên bị đuổi/được duyệt/bang giải tán đều im lặng tuyệt đối. Xem
     * {@link GuildCoordinator} về việc hỏi lại sau MỌI lệnh đổi trạng thái.
     */
    void requestMyGuild() {
        send(
                Opcode.OPCODE_GET_MY_GUILD_REQUEST_VALUE,
                GetMyGuildRequest.newBuilder().build().toByteArray(),
                "GetMyGuildRequest");
    }

    /** Hỏi danh sách thành viên — server trả rỗng nếu mình không ở bang nào. */
    void requestGuildMembers() {
        send(
                Opcode.OPCODE_LIST_GUILD_MEMBERS_REQUEST_VALUE,
                ListGuildMembersRequest.newBuilder().build().toByteArray(),
                "ListGuildMembersRequest");
    }

    /** Hỏi đơn đang chờ duyệt — server trả rỗng nếu mình không phải trưởng bang. */
    void requestPendingGuildJoinRequests() {
        send(
                Opcode.OPCODE_LIST_PENDING_GUILD_JOIN_REQUESTS_REQUEST_VALUE,
                ListPendingGuildJoinRequestsRequest.newBuilder().build().toByteArray(),
                "ListPendingGuildJoinRequestsRequest");
    }

    // --- Đấu tay đôi + xếp hạng PvP (arena.proto, dải 1600-1611 và 1200-1201) -------------------

    /**
     * Thách đấu một nhân vật theo TÊN.
     *
     * <p>Tên chứ không phải id là do giao thức quy định (arena.proto: "Thách đấu bằng TÊN [...]
     * server tự tra CharacterFacade.findByName TRƯỚC khi gọi ArenaInstanceRegistry"), cùng quy ước
     * {@link #sendFriendRequest}/{@link #invitePartyMember}. Hệ quả với client: người trên màn hình
     * chỉ có {@code character_id} ({@code CharacterSpawnedEvent} không mang tên), nên phải tra tên
     * từ chỗ khác — xem {@link DuelController} về sổ tên và khoảng trống này.
     */
    void challengeDuel(String targetName) {
        ChallengeDuelCommand command =
                ChallengeDuelCommand.newBuilder().setTargetName(targetName).build();
        send(Opcode.OPCODE_CHALLENGE_DUEL_COMMAND_VALUE, command.toByteArray(), "ChallengeDuelCommand");
    }

    /** @param challengeId lấy từ {@code DuelChallengeReceivedEvent} — client không tự sinh id nào. */
    void acceptDuel(long challengeId) {
        AcceptDuelCommand command =
                AcceptDuelCommand.newBuilder().setChallengeId(challengeId).build();
        send(Opcode.OPCODE_ACCEPT_DUEL_COMMAND_VALUE, command.toByteArray(), "AcceptDuelCommand");
    }

    /** @param challengeId xem {@link #acceptDuel(long)}. */
    void declineDuel(long challengeId) {
        DeclineDuelCommand command =
                DeclineDuelCommand.newBuilder().setChallengeId(challengeId).build();
        send(Opcode.OPCODE_DECLINE_DUEL_COMMAND_VALUE, command.toByteArray(), "DeclineDuelCommand");
    }

    /**
     * Bỏ cuộc giữa chừng. Gói rỗng — server tự tra nhân vật đang ở trận nào (arena.proto).
     *
     * <p>KHÁC {@code ExitDungeonCommand}: đây tính THUA THẬT và mất điểm MMR, nên nút gọi tới nó
     * chỉ được bày ra khi đang thật sự ở trong một trận (xem {@code DuelPanel}).
     */
    void exitDuel() {
        send(
                Opcode.OPCODE_EXIT_DUEL_COMMAND_VALUE,
                ExitDuelCommand.newBuilder().build().toByteArray(),
                "ExitDuelCommand");
    }

    /**
     * Hỏi điểm xếp hạng PvP của CHÍNH mình. Gói rỗng — người hỏi lấy từ session phía server.
     *
     * <p>Đây là đường DUY NHẤT client biết điểm của mình: arena.proto không có event nào đẩy điểm
     * mới, {@code DuelResultEvent} chỉ mang điểm của đúng trận vừa xong. Vì vậy phải hỏi lại mỗi lần
     * mở cửa sổ và sau mỗi trận (xem {@link DuelCoordinator}).
     */
    void requestPvpRating() {
        send(
                Opcode.OPCODE_GET_PVP_RATING_REQUEST_VALUE,
                GetPvpRatingRequest.newBuilder().build().toByteArray(),
                "GetPvpRatingRequest");
    }

    // --- Hầm ngục (dungeon.proto, dải 1500-1503) ----------------------------------------------
    // Không có idempotencyKey ở cả hai lệnh, và đó là quyết định chứ không phải khoảng trống:
    // vào/ra hầm ngục không sinh hiệu ứng kinh tế trùng lặp được nên ADR-025 không áp (dungeon.proto
    // nói thẳng điều này). Gửi trùng EnterDungeonCommand chỉ nhận về ALREADY_IN_DUNGEON.

    /**
     * Kéo CẢ NHÓM vào một instance hầm ngục.
     *
     * <p>⚠️ Lệnh này đổi World của cả những người KHÁC trong nhóm, nhưng chỉ máy này nhận được
     * {@code EnterDungeonResponse} — xem {@link DungeonController} về khoảng trống đó.
     *
     * @param dungeonCode {@link DungeonController#DUNGEON_CODE}. Server tra lại và trả
     *     {@code UNKNOWN_DUNGEON} nếu sai — client KHÔNG kiểm trước (Điều 24).
     */
    void enterDungeon(String dungeonCode) {
        EnterDungeonCommand command =
                EnterDungeonCommand.newBuilder().setDungeonCode(dungeonCode).build();
        send(Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE, command.toByteArray(), "EnterDungeonCommand");
    }

    /** Rời hầm ngục hiện tại — gói RỖNG, server tự tra nhân vật này đang ở instance nào (dungeon.proto). */
    void exitDungeon() {
        send(
                Opcode.OPCODE_EXIT_DUNGEON_COMMAND_VALUE,
                ExitDungeonCommand.newBuilder().build().toByteArray(),
                "ExitDungeonCommand");
    }

    /**
     * Chọn/đổi hệ phái (character.proto, cần Lv10). Gói KHÔNG mang {@code character_id} — server lấy
     * từ session đã bound lúc {@code SelectCharacterRequest} (chống IDOR, Điều 24).
     *
     * <p><b>Hàm DUY NHẤT ở lớp này trả về {@code sequenceId}</b>, và nó bắt buộc phải trả: server
     * báo thất bại qua {@code ErrorResponse} — một gói dùng chung cho cả giao thức — nên
     * {@code related_sequence_id} là thứ duy nhất nối một lỗi về đúng yêu cầu đã gửi. Xem
     * {@link FactionCoordinator}.
     *
     * @return {@code sequenceId} đã dùng để gửi. Vẫn trả về bình thường khi mất kết nối (lệnh bị
     *     nuốt như mọi lệnh khác ở lớp này) — chỗ gọi sẽ đơn giản là không bao giờ nhận được gói
     *     trả lời nào mang số đó, đúng như khi gói đi lạc trên đường.
     */
    int selectFaction(Faction faction) {
        int sequenceId = connection.nextSequenceId();
        SelectFactionRequest request =
                SelectFactionRequest.newBuilder().setFaction(faction).build();
        send(Opcode.OPCODE_SELECT_FACTION_REQUEST_VALUE, sequenceId, request.toByteArray(), "SelectFactionRequest");
        return sequenceId;
    }

    // -------------------------------------------------------------------------------------------
    // Thư (mail.proto, dải 900-908) — xem MailCoordinator.
    // -------------------------------------------------------------------------------------------

    /**
     * Gửi một lá thư.
     *
     * <p>KHÔNG có {@code idempotency_key} vì mail.proto không có trường đó cho lệnh này. Hệ quả
     * thật: mất kết nối đúng lúc rồi bấm Gửi lại sẽ ra HAI lá thư — khó chịu, nhưng không đổi tài
     * sản của ai (khác {@code ClaimMailCommand}, thứ CÓ khoá). Thêm khoá vào đây là đổi protocol,
     * nằm ngoài quyền của client (/CLAUDE.md §4).
     *
     * @param draft đã qua {@code MailController.compose} — độ dài và số vàng đã hợp lệ ở mức client.
     */
    void sendMail(MailController.Draft draft, String idempotencyKey) {
        SendMailCommand command = SendMailCommand.newBuilder()
                .setIdempotencyKey(idempotencyKey)
                .setRecipientName(draft.recipientName())
                .setSubject(draft.subject())
                .setBody(draft.body())
                .setGoldAmount(draft.goldAmount())
                .build();
        send(Opcode.OPCODE_SEND_MAIL_COMMAND_VALUE, command.toByteArray(), "SendMailCommand");
    }

    /**
     * Hỏi trọn hòm thư. Đây là cách DUY NHẤT client biết trong hòm có gì: mail.proto chỉ đẩy
     * {@code NewMailEvent} (một cái id trần, chỉ cho người nhận đang online) — xem
     * {@link MailCoordinator} về việc hỏi lại sau MỌI lệnh đổi trạng thái.
     */
    void listMail() {
        send(
                Opcode.OPCODE_LIST_MAIL_REQUEST_VALUE,
                ListMailRequest.newBuilder().build().toByteArray(),
                "ListMailRequest");
    }

    /**
     * Nhận vàng/vật phẩm đính kèm của một lá thư.
     *
     * @param idempotencyKey UUID do client sinh, GIỮ NGUYÊN qua các lần thử lại cùng một lần nhận
     *     (ADR-025) — server enforce unique trong CÙNG transaction với hiệu ứng thật (cộng vàng +
     *     cấp vật phẩm + đánh dấu claimed), xem {@link MailController#beginClaim}.
     */
    void claimMail(long mailId, String idempotencyKey) {
        ClaimMailCommand command = ClaimMailCommand.newBuilder()
                .setMailId(mailId)
                .setIdempotencyKey(idempotencyKey)
                .build();
        send(Opcode.OPCODE_CLAIM_MAIL_COMMAND_VALUE, command.toByteArray(), "ClaimMailCommand");
    }

    /**
     * Xoá một lá thư. KHÔNG có {@code idempotency_key} và ở đây đó là đủ: xoá hai lần cùng một lá
     * chỉ cho ra {@code DELETE_MAIL_OUTCOME_NOT_FOUND} ở lần thứ hai, không mất mát gì.
     */
    void deleteMail(long mailId) {
        DeleteMailCommand command =
                DeleteMailCommand.newBuilder().setMailId(mailId).build();
        send(Opcode.OPCODE_DELETE_MAIL_COMMAND_VALUE, command.toByteArray(), "DeleteMailCommand");
    }

    /**
     * Gói tin đến từ server — {@code null} khi hàng đợi rỗng (xem {@link GameConnection}).
     *
     * <p>Gói trả lời không khớp lệnh nào đang chờ bị bỏ ngay ở đây (dòng 67(a), xem {@link ReplyLedger});
     * {@code ErrorResponse} gạch lệnh nó từ chối khỏi sổ — header của nó mang đúng {@code sequenceId} đó.
     */
    @Override
    public PacketCodec.Frame pollInbound() {
        PacketCodec.Frame frame = connection.pollInbound();
        while (frame != null) {
            int opcode = frame.header().opcode();
            if (opcode == Opcode.OPCODE_ERROR_VALUE) {
                replies.forget(frame.header().sequenceId());
                return frame;
            }
            if (replies.admit(opcode, frame.header().sequenceId())) {
                return frame;
            }
            frame = connection.pollInbound();
        }
        return null;
    }

    /** Uỷ nhiệm thẳng — xem {@code GameConnection#replayBeforeQueued} (2026-10-14, README dòng 73(c)). */
    @Override
    public void replayBeforeQueued(List<PacketCodec.Frame> frames) {
        connection.replayBeforeQueued(frames);
    }

    /** Uỷ nhiệm thẳng, cùng khuôn {@link #pollInbound()} — xem {@code ConnectionCoordinator#onTick}. */
    @Override
    public boolean isConnected() {
        return connection.isConnected();
    }

    /** Uỷ nhiệm thẳng — xem {@link GameConnection#connectionGeneration()} và {@link SessionRecovery}. */
    @Override
    public int connectionGeneration() {
        return connection.connectionGeneration();
    }

    /**
     * Gửi một yêu cầu đăng nhập lại và NÉM lỗi thay vì nuốt như các lệnh gameplay: {@link
     * SessionRecovery} cần biết lượt này hỏng để thử lượt khác.
     */
    @Override
    public int sendRequest(int opcode, byte[] payload) throws IOException {
        int sequenceId = connection.nextSequenceId();
        replies.expect(opcode, sequenceId);
        connection.send(opcode, sequenceId, payload);
        return sequenceId;
    }

    /**
     * Dừng HẲN kết nối, kể cả vòng tự kết nối lại — phiên đã bị server kết thúc (bị đá vì đăng nhập
     * nơi khác), nối lại chỉ đưa người chơi vào một phiên chết. Xem {@code WorldScreen#endSession}.
     */
    void stop() {
        connection.stop();
    }

    // -------------------------------------------------------------------------------------------
    // Catalog vật phẩm (inventory.proto, ADR-035 opcode 509/510) — xem ItemCatalogCoordinator.
    // -------------------------------------------------------------------------------------------

    /**
     * Hỏi catalog vật phẩm.
     *
     * @param knownVersion chuỗi version client đang cache, rỗng nếu chưa có gì. Server phán quyết:
     *     khớp thì trả {@code unchanged=true} với danh sách RỖNG, và client giữ nguyên bản cũ
     *     (phương án A′ của ADR-035).
     */
    void requestItemCatalog(String knownVersion) {
        GetItemCatalogRequest request =
                GetItemCatalogRequest.newBuilder().setKnownVersion(knownVersion).build();
        send(Opcode.OPCODE_GET_ITEM_CATALOG_REQUEST_VALUE, request.toByteArray(), "GetItemCatalogRequest");
    }

    // -------------------------------------------------------------------------------------------
    // Túi đồ + trang bị (inventory.proto, ADR-035 opcode 507/508 + opcode 501-504 có sẵn từ lâu
    // nhưng client chưa từng dùng) — xem InventoryCoordinator.
    // -------------------------------------------------------------------------------------------

    /**
     * Hỏi trọn ảnh chụp túi đồ + trang bị. Đây là cách DUY NHẤT client biết trong túi có gì:
     * inventory.proto không có event đẩy nào báo "túi vừa đổi" (khác {@code NewMailEvent}), nên phải
     * hỏi lại sau MỌI lệnh có thể đổi túi (nhặt đồ, mặc, gỡ) — xem {@link InventoryCoordinator}.
     */
    void listInventory() {
        send(
                Opcode.OPCODE_LIST_INVENTORY_REQUEST_VALUE,
                ListInventoryRequest.newBuilder().build().toByteArray(),
                "ListInventoryRequest");
    }

    /**
     * Mặc một item vào slot.
     *
     * @param itemId {@code inventory_item.id} THẬT, lấy từ {@code ListInventoryResponse} — KHÔNG
     *     phải vị trí trong túi.
     * @param slot suy từ catalog vật phẩm ({@code item_template.equip_slot}) phía client để gửi kèm
     *     lệnh; server tự kiểm lại item có đúng thuộc slot này không (Điều 24), không tin client.
     */
    void equipItem(long itemId, EquipmentSlot slot) {
        EquipItemCommand command =
                EquipItemCommand.newBuilder().setItemId(itemId).setSlot(slot).build();
        send(Opcode.OPCODE_EQUIP_ITEM_COMMAND_VALUE, command.toByteArray(), "EquipItemCommand");
    }

    /** Gỡ trang bị khỏi slot — item vẫn còn trong túi đồ, không bị xoá (inventory.proto). */
    void unequipItem(EquipmentSlot slot) {
        UnequipItemCommand command =
                UnequipItemCommand.newBuilder().setSlot(slot).build();
        send(Opcode.OPCODE_UNEQUIP_ITEM_COMMAND_VALUE, command.toByteArray(), "UnequipItemCommand");
    }

    /**
     * Cường hoá một item (GAMEPLAY.md §7.1). Client KHÔNG gửi chi phí, tỉ lệ hay cấp hiện tại: server
     * tự tra {@code enhance_level} thật rồi tính theo {@code EnhancementTable} (Điều 24,
     * inventory.proto) — client cũng vì thế không hiện trước con số nào, xem {@link InventoryPanel}.
     *
     * @param itemId {@code inventory_item.id} THẬT, lấy từ {@code ListInventoryResponse}.
     * @param useProtection người chơi CHỦ ĐỘNG chọn có tiêu "Bảo Vệ" hay không; server chỉ thật sự
     *     tiêu khi roll THẤT BẠI ở mức đủ cao VÀ trong túi còn bùa, không tiêu nếu thành công.
     * @param idempotencyKey xem {@link #buyItem} — cùng luật ADR-025, và cùng lý do: cường hoá TRỪ
     *     VÀNG THẬT nên một lần bấm không được biến thành hai lần trừ.
     * @return {@code sequenceId} của gói — server từ chối món đang chào trong giao dịch bằng
     *     {@code ErrorResponse} mang đúng số này (2026-09-29), không bằng {@code EnhanceItemResponse}
     */
    int enhanceItem(long itemId, boolean useProtection, String idempotencyKey) {
        EnhanceItemCommand command = EnhanceItemCommand.newBuilder()
                .setItemId(itemId)
                .setUseProtection(useProtection)
                .setIdempotencyKey(idempotencyKey)
                .build();
        int sequenceId = connection.nextSequenceId();
        send(Opcode.OPCODE_ENHANCE_ITEM_COMMAND_VALUE, sequenceId, command.toByteArray(), "EnhanceItemCommand");
        return sequenceId;
    }

    /**
     * Dùng MỘT món tiêu hao (ADR-046 D5 — đan dược hồi máu).
     *
     * <p>Không có {@code idempotencyKey} như {@link #enhanceItem}: lệnh này không trừ vàng, và một
     * lần retry chỉ tốn thêm một viên đan của chính người chơi — không đáng một bảng idempotency
     * ghi DB cho mọi trận đánh.
     *
     * <p>Máu mới KHÔNG về trong {@code UseItemResponse} mà qua {@code HealthChangedEvent} (opcode
     * 408): hàng đợi world phía server là bất đồng bộ — xem ADR-046 D5.
     *
     * @param inventoryItemId {@code inventory_item.id} THẬT, không phải vị trí trong túi
     */
    void useItem(long inventoryItemId) {
        UseItemCommand command =
                UseItemCommand.newBuilder().setInventoryItemId(inventoryItemId).build();
        send(Opcode.OPCODE_USE_ITEM_COMMAND_VALUE, command.toByteArray(), "UseItemCommand");
    }

    // -------------------------------------------------------------------------------------------
    // Cửa hàng (shop.proto, dải 1000-1005) — xem ShopCoordinator.
    // -------------------------------------------------------------------------------------------

    /**
     * Hỏi trọn danh mục cửa hàng. Gói RỖNG — shop.proto chưa gắn NPC/vị trí cụ thể (cửa hàng TOÀN
     * CỤC), server luôn trả toàn bộ danh mục.
     */
    void listShopItems() {
        send(
                Opcode.OPCODE_LIST_SHOP_ITEMS_REQUEST_VALUE,
                ListShopItemsRequest.newBuilder().build().toByteArray(),
                "ListShopItemsRequest");
    }

    /**
     * Mua vật phẩm. Server tự tra {@code buy_price} hiện tại theo {@code item_template_id} trong
     * {@code shop_item}, KHÔNG tin giá/tổng tiền từ client (Điều 24, shop.proto).
     *
     * @param idempotencyKey UUID do client sinh, GIỮ NGUYÊN qua các lần thử lại cùng một lần mua
     *     (ADR-025) — server enforce unique trong CÙNG transaction với hiệu ứng thật (trừ gold + cộng
     *     vật phẩm), xem {@link ShopController}.
     */
    void buyItem(long itemTemplateId, int quantity, String idempotencyKey) {
        BuyItemCommand command = BuyItemCommand.newBuilder()
                .setItemTemplateId(itemTemplateId)
                .setQuantity(quantity)
                .setIdempotencyKey(idempotencyKey)
                .build();
        send(Opcode.OPCODE_BUY_ITEM_COMMAND_VALUE, command.toByteArray(), "BuyItemCommand");
    }

    /**
     * Bán vật phẩm. {@code itemId} là {@code inventory_item.id} THẬT (không phải mã vật phẩm) — server
     * tự tính tiền theo {@code sell_price} hiện tại, KHÔNG tin số liệu từ client (Điều 24, shop.proto).
     *
     * @param idempotencyKey xem {@link #buyItem} — cùng luật ADR-025.
     */
    void sellItem(long itemId, int quantity, String idempotencyKey) {
        SellItemCommand command = SellItemCommand.newBuilder()
                .setItemId(itemId)
                .setQuantity(quantity)
                .setIdempotencyKey(idempotencyKey)
                .build();
        send(Opcode.OPCODE_SELL_ITEM_COMMAND_VALUE, command.toByteArray(), "SellItemCommand");
    }

    // -------------------------------------------------------------------------------------------
    // Giao dịch (trade.proto, dải 1400-1414) — xem TradeCoordinator. "Hệ thống nguy hiểm nhất"
    // (GAMEPLAY.md §8): mọi lệnh dưới đây chỉ là Ý ĐỊNH, server xác thực lại TOÀN BỘ lúc thi hành.
    // -------------------------------------------------------------------------------------------

    /**
     * Mời một người chơi giao dịch, theo TÊN.
     *
     * @param targetName server tự tra {@code CharacterFacade.findByName} rồi trả
     *     {@code TARGET_NOT_FOUND} nếu sai — client KHÔNG kiểm trước (Điều 24), cùng khuôn
     *     {@code sendFriendRequest}.
     */
    void requestTrade(String targetName) {
        RequestTradeCommand command =
                RequestTradeCommand.newBuilder().setTargetName(targetName).build();
        send(Opcode.OPCODE_REQUEST_TRADE_COMMAND_VALUE, command.toByteArray(), "RequestTradeCommand");
    }

    /** Chấp nhận một lời mời giao dịch. */
    void acceptTradeRequest(long sessionId) {
        AcceptTradeRequestCommand command =
                AcceptTradeRequestCommand.newBuilder().setSessionId(sessionId).build();
        send(Opcode.OPCODE_ACCEPT_TRADE_REQUEST_COMMAND_VALUE, command.toByteArray(), "AcceptTradeRequestCommand");
    }

    /**
     * Từ chối một lời mời, HOẶC huỷ một phiên đang mở — trade.proto dùng CHUNG một lệnh cho cả hai
     * (giao dịch luôn đúng hai người nên không có khái niệm đuổi ai ra).
     */
    void cancelTrade(long sessionId) {
        CancelTradeCommand command =
                CancelTradeCommand.newBuilder().setSessionId(sessionId).build();
        send(Opcode.OPCODE_CANCEL_TRADE_COMMAND_VALUE, command.toByteArray(), "CancelTradeCommand");
    }

    /**
     * Đặt lại TOÀN BỘ lời chào của mình — thay hẳn lời chào cũ, KHÔNG cộng dồn (trade.proto). Gửi
     * lệnh này SAU KHI đối phương đã xác nhận sẽ reset CẢ HAI cờ confirmed; đó là hàng rào chống
     * lừa đảo của GAMEPLAY.md §8 và nó nằm ở SERVER, client không làm gì để có nó.
     *
     * @param items {@code inventory_item.id} THẬT + số lượng. Server KHÔNG khoá item lúc chào và
     *     xác thực lại quyền sở hữu lúc THI HÀNH — món bán mất giữa chừng sẽ làm
     *     {@code ConfirmTradeCommand} trả {@code OFFER_INVALID}, không phải lệnh này.
     */
    void setTradeOffer(long sessionId, long goldAmount, List<TradeController.OfferItem> items) {
        SetTradeOfferCommand.Builder command =
                SetTradeOfferCommand.newBuilder().setSessionId(sessionId).setGoldAmount(goldAmount);
        for (TradeController.OfferItem item : items) {
            command.addItems(TradeOfferItemInput.newBuilder()
                    .setItemId(item.itemId())
                    .setQuantity(item.quantity())
                    .build());
        }
        send(Opcode.OPCODE_SET_TRADE_OFFER_COMMAND_VALUE, command.build().toByteArray(), "SetTradeOfferCommand");
    }

    /**
     * Xác nhận giao dịch — lệnh DUY NHẤT ở feature-trade có thể chuyển vàng/vật phẩm thật, và vì
     * vậy là lệnh duy nhất mang {@code idempotency_key} (ADR-025).
     *
     * @param idempotencyKey UUID do client sinh, GIỮ NGUYÊN qua các lần thử lại cùng một lần xác
     *     nhận — xem {@link TradeController#beginConfirm}.
     */
    int confirmTrade(long sessionId, String idempotencyKey) {
        ConfirmTradeCommand command = ConfirmTradeCommand.newBuilder()
                .setSessionId(sessionId)
                .setIdempotencyKey(idempotencyKey)
                .build();
        int sequenceId = connection.nextSequenceId();
        send(Opcode.OPCODE_CONFIRM_TRADE_COMMAND_VALUE, sequenceId, command.toByteArray(), "ConfirmTradeCommand");
        return sequenceId;
    }

    /**
     * Hỏi trọn trạng thái phiên giao dịch. Gói RỖNG — server tra theo nhân vật của session.
     *
     * <p>Đây là cách DUY NHẤT client biết nội dung một phiên: {@code TradeUpdatedEvent} cố ý không
     * mang trạng thái, nó chỉ nói "có gì đó đổi rồi" (trade.proto).
     */
    void getTradeStatus() {
        send(
                Opcode.OPCODE_GET_TRADE_STATUS_REQUEST_VALUE,
                GetTradeStatusRequest.newBuilder().build().toByteArray(),
                "GetTradeStatusRequest");
    }

    private void send(int opcode, byte[] payload, String commandName) {
        send(opcode, connection.nextSequenceId(), payload, commandName);
    }

    private void send(int opcode, int sequenceId, byte[] payload, String commandName) {
        replies.expect(opcode, sequenceId);
        try {
            connection.send(opcode, sequenceId, payload);
        } catch (IOException e) {
            LOG.debug("Gửi {} thất bại (mất kết nối tạm thời)", commandName, e);
        }
    }
}
