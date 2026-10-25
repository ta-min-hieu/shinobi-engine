package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Locale;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import dev.kage.protocol.AbandonBountyCommand;
import dev.kage.protocol.AbandonBountyResponse;
import dev.kage.protocol.AcceptBountyCommand;
import dev.kage.protocol.AcceptBountyResponse;
import dev.kage.protocol.AcceptDaoLuCommand;
import dev.kage.protocol.AcceptDaoLuResponse;
import dev.kage.protocol.AcceptDiscipleCommand;
import dev.kage.protocol.AcceptDiscipleResponse;
import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.BlockCharacterCommand;
import dev.kage.protocol.BlockCharacterResponse;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ClaimOnlineRewardCommand;
import dev.kage.protocol.ClaimOnlineRewardResponse;
import dev.kage.protocol.ClaimSeclusionCommand;
import dev.kage.protocol.ClaimSeclusionResponse;
import dev.kage.protocol.DeclareGuildWarCommand;
import dev.kage.protocol.DeclareGuildWarResponse;
import dev.kage.protocol.DismantleItemCommand;
import dev.kage.protocol.DismantleItemResponse;
import dev.kage.protocol.DissolveDaoLuCommand;
import dev.kage.protocol.DissolveDaoLuResponse;
import dev.kage.protocol.Emote;
import dev.kage.protocol.EnterDungeonCommand;
import dev.kage.protocol.EnterDungeonResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.GatherHerbCommand;
import dev.kage.protocol.GatherHerbResponse;
import dev.kage.protocol.GetBountyBoardRequest;
import dev.kage.protocol.GetBountyBoardResponse;
import dev.kage.protocol.GetGuildWarStatusRequest;
import dev.kage.protocol.GetGuildWarStatusResponse;
import dev.kage.protocol.GetOnlineRewardsRequest;
import dev.kage.protocol.GetOnlineRewardsResponse;
import dev.kage.protocol.GetRelationshipsRequest;
import dev.kage.protocol.GetRelationshipsResponse;
import dev.kage.protocol.GetSeclusionStatusRequest;
import dev.kage.protocol.GetSeclusionStatusResponse;
import dev.kage.protocol.HerbGatheredEvent;
import dev.kage.protocol.InvitePartyMemberCommand;
import dev.kage.protocol.InvitePartyMemberResponse;
import dev.kage.protocol.LeaveMentorshipCommand;
import dev.kage.protocol.LeaveMentorshipResponse;
import dev.kage.protocol.ListHerbNodesRequest;
import dev.kage.protocol.ListHerbNodesResponse;
import dev.kage.protocol.ListWaypointsRequest;
import dev.kage.protocol.ListWaypointsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.ProposeDaoLuCommand;
import dev.kage.protocol.ProposeDaoLuResponse;
import dev.kage.protocol.RelationMemberView;
import dev.kage.protocol.RequestMentorCommand;
import dev.kage.protocol.RequestMentorResponse;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SendChatMessageResponse;
import dev.kage.protocol.SendEmoteCommand;
import dev.kage.protocol.SendEmoteResponse;
import dev.kage.protocol.SendFriendRequestCommand;
import dev.kage.protocol.SendFriendRequestResponse;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.TeleportCommand;
import dev.kage.protocol.TeleportResponse;
import dev.kage.protocol.UnblockCharacterCommand;
import dev.kage.protocol.UnblockCharacterResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Các tính năng lô 21 phần 1 — chặn (ADR-091), biểu cảm (ADR-092), sư đồ (ADR-093), đạo lữ (ADR-094). Mỗi bước một
 * chuỗi: {@code block:<tên>}, {@code unblock:<tên>}, {@code relations}, {@code emote:<GREET|BOW|...>},
 * {@code requestMentor:<tên>}, {@code acceptDisciple:<tên>}, {@code leaveMentor:<tên>}, {@code proposeDaoLu:<tên>},
 * {@code acceptDaoLu:<tên>}, {@code dissolveDaoLu}; và ba bước kiểm chặn: {@code whisper:<tên>},
 * {@code friendRequest:<tên>}, {@code partyInvite:<tên>} (in ra lỗi {@code error.blocked} nếu bị chặn). Phần 4 — bang
 * chiến (ADR-102): {@code declareWar:<tên bang>}, {@code warStatus}, {@code hitPlayer:<characterId>} (3 đòn thường, đếm
 * đòn trúng).
 */
final class Lot21Scenario {

    private static final int MAX_FRAMES_TO_SKIP = 2000;
    private static final int HIT_ATTEMPTS = 3;
    // Hơn hồi chiêu đánh thường 1 giây — nhanh hơn thì nửa số nhát bị server bỏ (bẫy probe đã ghi sổ).
    private static final long HIT_SPACING_MILLIS = 1_300L;

    private final GameConnection connection;
    private final ProbeSession session;

    Lot21Scenario(GameConnection connection, ProbeSession session) {
        this.connection = connection;
        this.session = session;
    }

    void run(List<String> steps) throws IOException {
        for (String raw : steps) {
            String step = raw.strip();
            int colon = step.indexOf(':');
            String verb = colon < 0 ? step : step.substring(0, colon);
            String arg = colon < 0 ? "" : step.substring(colon + 1);
            String result =
                    switch (verb) {
                        case "block" ->
                            ask(
                                    Opcode.OPCODE_BLOCK_CHARACTER_COMMAND_VALUE,
                                    BlockCharacterCommand.newBuilder()
                                            .setTargetName(arg)
                                            .build(),
                                    Opcode.OPCODE_BLOCK_CHARACTER_RESPONSE_VALUE,
                                    p -> BlockCharacterResponse.parseFrom(p)
                                            .getOutcome()
                                            .name());
                        case "unblock" ->
                            ask(
                                    Opcode.OPCODE_UNBLOCK_CHARACTER_COMMAND_VALUE,
                                    UnblockCharacterCommand.newBuilder()
                                            .setTargetName(arg)
                                            .build(),
                                    Opcode.OPCODE_UNBLOCK_CHARACTER_RESPONSE_VALUE,
                                    p -> UnblockCharacterResponse.parseFrom(p)
                                            .getOutcome()
                                            .name());
                        case "relations" ->
                            ask(
                                    Opcode.OPCODE_GET_RELATIONSHIPS_REQUEST_VALUE,
                                    GetRelationshipsRequest.getDefaultInstance(),
                                    Opcode.OPCODE_GET_RELATIONSHIPS_RESPONSE_VALUE,
                                    p -> describe(GetRelationshipsResponse.parseFrom(p)));
                        case "emote" ->
                            ask(
                                    Opcode.OPCODE_SEND_EMOTE_COMMAND_VALUE,
                                    SendEmoteCommand.newBuilder()
                                            .setEmote(Emote.valueOf("EMOTE_" + arg.toUpperCase(Locale.ROOT)))
                                            .build(),
                                    Opcode.OPCODE_SEND_EMOTE_RESPONSE_VALUE,
                                    p -> SendEmoteResponse.parseFrom(p)
                                            .getOutcome()
                                            .name());
                        default -> mentorshipOrDaoLu(verb, arg);
                    };
            session.note("lô 21 " + step + " → " + result);
        }
    }

    private String mentorshipOrDaoLu(String verb, String arg) throws IOException {
        return switch (verb) {
            case "requestMentor" ->
                ask(
                        Opcode.OPCODE_REQUEST_MENTOR_COMMAND_VALUE,
                        RequestMentorCommand.newBuilder().setMentorName(arg).build(),
                        Opcode.OPCODE_REQUEST_MENTOR_RESPONSE_VALUE,
                        p -> RequestMentorResponse.parseFrom(p).getOutcome().name());
            case "acceptDisciple" ->
                ask(
                        Opcode.OPCODE_ACCEPT_DISCIPLE_COMMAND_VALUE,
                        AcceptDiscipleCommand.newBuilder().setDiscipleName(arg).build(),
                        Opcode.OPCODE_ACCEPT_DISCIPLE_RESPONSE_VALUE,
                        p -> AcceptDiscipleResponse.parseFrom(p).getOutcome().name());
            case "leaveMentor" ->
                ask(
                        Opcode.OPCODE_LEAVE_MENTORSHIP_COMMAND_VALUE,
                        LeaveMentorshipCommand.newBuilder().setOtherName(arg).build(),
                        Opcode.OPCODE_LEAVE_MENTORSHIP_RESPONSE_VALUE,
                        p -> LeaveMentorshipResponse.parseFrom(p).getOutcome().name());
            case "proposeDaoLu" ->
                ask(
                        Opcode.OPCODE_PROPOSE_DAO_LU_COMMAND_VALUE,
                        ProposeDaoLuCommand.newBuilder().setTargetName(arg).build(),
                        Opcode.OPCODE_PROPOSE_DAO_LU_RESPONSE_VALUE,
                        p -> ProposeDaoLuResponse.parseFrom(p).getOutcome().name());
            case "acceptDaoLu" ->
                ask(
                        Opcode.OPCODE_ACCEPT_DAO_LU_COMMAND_VALUE,
                        AcceptDaoLuCommand.newBuilder().setProposerName(arg).build(),
                        Opcode.OPCODE_ACCEPT_DAO_LU_RESPONSE_VALUE,
                        p -> AcceptDaoLuResponse.parseFrom(p).getOutcome().name());
            case "dissolveDaoLu" ->
                ask(
                        Opcode.OPCODE_DISSOLVE_DAO_LU_COMMAND_VALUE,
                        DissolveDaoLuCommand.getDefaultInstance(),
                        Opcode.OPCODE_DISSOLVE_DAO_LU_RESPONSE_VALUE,
                        p -> DissolveDaoLuResponse.parseFrom(p).getOutcome().name());
            default -> blockChecks(verb, arg);
        };
    }

    private String blockChecks(String verb, String arg) throws IOException {
        return switch (verb) {
            case "whisper" ->
                ask(
                        Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE,
                        SendChatMessageCommand.newBuilder()
                                .setChannel(ChatChannel.CHAT_CHANNEL_PRIVATE)
                                .setTargetName(arg)
                                .setBody("probe lô 21")
                                .build(),
                        Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE,
                        p -> SendChatMessageResponse.parseFrom(p).getOutcome().name());
            case "friendRequest" ->
                ask(
                        Opcode.OPCODE_SEND_FRIEND_REQUEST_COMMAND_VALUE,
                        SendFriendRequestCommand.newBuilder().setTargetName(arg).build(),
                        Opcode.OPCODE_SEND_FRIEND_REQUEST_RESPONSE_VALUE,
                        p -> SendFriendRequestResponse.parseFrom(p).getOutcome().name());
            case "partyInvite" ->
                ask(
                        Opcode.OPCODE_INVITE_PARTY_MEMBER_COMMAND_VALUE,
                        InvitePartyMemberCommand.newBuilder().setTargetName(arg).build(),
                        Opcode.OPCODE_INVITE_PARTY_MEMBER_RESPONSE_VALUE,
                        p -> InvitePartyMemberResponse.parseFrom(p).getOutcome().name());
            default -> daily(verb, arg);
        };
    }

    /** Lô 21 phần 2: bế quan, thưởng online, phân giải, linh thảo. */
    private String daily(String verb, String arg) throws IOException {
        return switch (verb) {
            case "seclusion" ->
                ask(
                        Opcode.OPCODE_GET_SECLUSION_STATUS_REQUEST_VALUE,
                        GetSeclusionStatusRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_SECLUSION_STATUS_RESPONSE_VALUE,
                        Lot21Scenario::describeSeclusion);
            case "claimSeclusion" ->
                ask(
                        Opcode.OPCODE_CLAIM_SECLUSION_COMMAND_VALUE,
                        ClaimSeclusionCommand.newBuilder()
                                .setDoubled("x2".equals(arg))
                                .build(),
                        Opcode.OPCODE_CLAIM_SECLUSION_RESPONSE_VALUE,
                        Lot21Scenario::describeSeclusionClaim);
            case "online" ->
                ask(
                        Opcode.OPCODE_GET_ONLINE_REWARDS_REQUEST_VALUE,
                        GetOnlineRewardsRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_ONLINE_REWARDS_RESPONSE_VALUE,
                        Lot21Scenario::describeOnline);
            case "claimOnline" ->
                ask(
                        Opcode.OPCODE_CLAIM_ONLINE_REWARD_COMMAND_VALUE,
                        ClaimOnlineRewardCommand.newBuilder()
                                .setMilestoneMinutes(Integer.parseInt(arg))
                                .build(),
                        Opcode.OPCODE_CLAIM_ONLINE_REWARD_RESPONSE_VALUE,
                        p -> ClaimOnlineRewardResponse.parseFrom(p).getOutcome().name());
            case "dismantle" ->
                ask(
                        Opcode.OPCODE_DISMANTLE_ITEM_COMMAND_VALUE,
                        DismantleItemCommand.newBuilder()
                                .setItemId(Long.parseLong(arg))
                                .build(),
                        Opcode.OPCODE_DISMANTLE_ITEM_RESPONSE_VALUE,
                        Lot21Scenario::describeDismantle);
            case "herbs" ->
                ask(
                        Opcode.OPCODE_LIST_HERB_NODES_REQUEST_VALUE,
                        ListHerbNodesRequest.getDefaultInstance(),
                        Opcode.OPCODE_LIST_HERB_NODES_RESPONSE_VALUE,
                        Lot21Scenario::describeHerbs);
            case "gather" -> gather(Long.parseLong(arg));
            case "wait" -> waitSeconds(Integer.parseInt(arg));
            default -> adventure(verb, arg);
        };
    }

    /** Lô 21 phần 3: truyền tống, truy nã, lượt Bí Cảnh. */
    private String adventure(String verb, String arg) throws IOException {
        return switch (verb) {
            case "waypoints" ->
                ask(
                        Opcode.OPCODE_LIST_WAYPOINTS_REQUEST_VALUE,
                        ListWaypointsRequest.getDefaultInstance(),
                        Opcode.OPCODE_LIST_WAYPOINTS_RESPONSE_VALUE,
                        Lot21Scenario::describeWaypoints);
            case "teleport" ->
                ask(
                        Opcode.OPCODE_TELEPORT_COMMAND_VALUE,
                        TeleportCommand.newBuilder().setWaypointCode(arg).build(),
                        Opcode.OPCODE_TELEPORT_RESPONSE_VALUE,
                        p -> TeleportResponse.parseFrom(p).getOutcome().name());
            case "bounty" ->
                ask(
                        Opcode.OPCODE_GET_BOUNTY_BOARD_REQUEST_VALUE,
                        GetBountyBoardRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_BOUNTY_BOARD_RESPONSE_VALUE,
                        Lot21Scenario::describeBounty);
            case "acceptBounty" ->
                ask(
                        Opcode.OPCODE_ACCEPT_BOUNTY_COMMAND_VALUE,
                        AcceptBountyCommand.newBuilder().setBountyCode(arg).build(),
                        Opcode.OPCODE_ACCEPT_BOUNTY_RESPONSE_VALUE,
                        p -> AcceptBountyResponse.parseFrom(p).getOutcome().name());
            case "abandonBounty" ->
                ask(
                        Opcode.OPCODE_ABANDON_BOUNTY_COMMAND_VALUE,
                        AbandonBountyCommand.getDefaultInstance(),
                        Opcode.OPCODE_ABANDON_BOUNTY_RESPONSE_VALUE,
                        p -> "bỏ=" + AbandonBountyResponse.parseFrom(p).getAbandoned());
            case "enterDungeon" ->
                ask(
                        Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE,
                        EnterDungeonCommand.newBuilder().setDungeonCode(arg).build(),
                        Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE,
                        p -> {
                            EnterDungeonResponse r = EnterDungeonResponse.parseFrom(p);
                            return r.getOutcome() + " cònLượt=" + r.getEntriesLeft();
                        });
            default -> guildWar(verb, arg);
        };
    }

    /** Lô 21 phần 4: bang chiến (ADR-102) — tuyên, xem tình hình, đánh thử một người chơi. */
    private String guildWar(String verb, String arg) throws IOException {
        return switch (verb) {
            case "declareWar" ->
                ask(
                        Opcode.OPCODE_DECLARE_GUILD_WAR_COMMAND_VALUE,
                        DeclareGuildWarCommand.newBuilder()
                                .setTargetGuildName(arg)
                                .build(),
                        Opcode.OPCODE_DECLARE_GUILD_WAR_RESPONSE_VALUE,
                        p -> DeclareGuildWarResponse.parseFrom(p).getOutcome().name());
            case "warStatus" ->
                ask(
                        Opcode.OPCODE_GET_GUILD_WAR_STATUS_REQUEST_VALUE,
                        GetGuildWarStatusRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_GUILD_WAR_STATUS_RESPONSE_VALUE,
                        Lot21Scenario::describeGuildWars);
            case "hitPlayer" -> hitPlayer(Long.parseLong(arg));
            default -> "bước không hiểu";
        };
    }

    private static String describeGuildWars(byte[] payload) throws InvalidProtocolBufferException {
        GetGuildWarStatusResponse r = GetGuildWarStatusResponse.parseFrom(payload);
        StringBuilder line = new StringBuilder("giá=" + r.getDeclareCostGold() + " trận=" + r.getWarsCount());
        r.getWarsList().forEach(w -> line.append(" | ")
                .append(w.getAttackerGuildName())
                .append(' ')
                .append(w.getAttackerKills())
                .append(':')
                .append(w.getDefenderKills())
                .append(' ')
                .append(w.getDefenderGuildName())
                .append(" đangĐánh=")
                .append(w.getActive())
                .append(" còn=")
                .append(w.getSecondsUntilChange())
                .append("s củaMình=")
                .append(w.getMine()));
        return line.toString();
    }

    /** Nhắm một người chơi rồi ra {@link #HIT_ATTEMPTS} đòn thường cách nhau hơn hồi chiêu; đếm đòn TRÚNG họ. */
    private String hitPlayer(long targetId) throws IOException {
        connection.send(
                Opcode.OPCODE_SET_TARGET_COMMAND_VALUE,
                SetTargetCommand.newBuilder()
                        .setTargetEntityId(targetId)
                        .build()
                        .toByteArray());
        int landed = 0;
        boolean died = false;
        for (int attempt = 0; attempt < HIT_ATTEMPTS; attempt++) {
            connection.send(
                    Opcode.OPCODE_ATTACK_COMMAND_VALUE,
                    AttackCommand.getDefaultInstance().toByteArray());
            long until = System.currentTimeMillis() + HIT_SPACING_MILLIS;
            while (System.currentTimeMillis() < until) {
                PacketCodec.Frame frame;
                try {
                    frame = connection.readFrame();
                } catch (SocketTimeoutException quiet) {
                    break;
                }
                if (frame.header().opcode() == Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE) {
                    AttackResultEvent hit = AttackResultEvent.parseFrom(frame.payload());
                    if (hit.getTargetEntityId() == targetId
                            && hit.getAttackerEntityId() > 0) { // quái (id âm) không tính
                        landed++;
                        died |= hit.getTargetDied();
                    }
                }
            }
        }
        return "trúng=" + landed + "/" + HIT_ATTEMPTS + " hạ=" + died;
    }

    private static String describeWaypoints(byte[] payload) throws InvalidProtocolBufferException {
        ListWaypointsResponse r = ListWaypointsResponse.parseFrom(payload);
        StringBuilder line = new StringBuilder("phù=" + r.getTalismans());
        r.getWaypointsList()
                .forEach(w ->
                        line.append(" | ").append(w.getCode()).append(" mở=").append(w.getOpen()));
        return line.toString();
    }

    private static String describeBounty(byte[] payload) throws InvalidProtocolBufferException {
        GetBountyBoardResponse r = GetBountyBoardResponse.parseFrom(payload);
        StringBuilder line = new StringBuilder("xong=" + r.getCompletedToday() + "/" + r.getMaxPerDay() + " đangLàm="
                + r.getActiveCode() + " " + r.getActiveProgress() + "/" + r.getActiveTarget());
        r.getBountiesList().forEach(b -> line.append(" | ")
                .append(b.getCode())
                .append(' ')
                .append(b.getMonsterCode())
                .append(" x")
                .append(b.getKillCount()));
        return line.toString();
    }

    private static String describeSeclusion(byte[] payload) throws InvalidProtocolBufferException {
        GetSeclusionStatusResponse r = GetSeclusionStatusResponse.parseFrom(payload);
        return "quỹ=" + r.getBankedMinutes() + "phút expX1=" + r.getExpSingle() + " phíX2=" + r.getDoubleCost();
    }

    private static String describeSeclusionClaim(byte[] payload) throws InvalidProtocolBufferException {
        ClaimSeclusionResponse r = ClaimSeclusionResponse.parseFrom(payload);
        return r.getOutcome() + " exp=" + r.getExpGained();
    }

    private static String describeOnline(byte[] payload) throws InvalidProtocolBufferException {
        GetOnlineRewardsResponse r = GetOnlineRewardsResponse.parseFrom(payload);
        StringBuilder line = new StringBuilder("giâyHômNay=" + r.getSecondsToday());
        r.getMilestonesList().forEach(m -> line.append(" | ")
                .append(m.getMinutes())
                .append("p đạt=")
                .append(m.getReached())
                .append(" nhận=")
                .append(m.getClaimed()));
        return line.toString();
    }

    private static String describeDismantle(byte[] payload) throws InvalidProtocolBufferException {
        DismantleItemResponse r = DismantleItemResponse.parseFrom(payload);
        return r.getOutcome() + " vàng=" + r.getGold() + " thảo=" + r.getHerbQuantity();
    }

    private static String describeHerbs(byte[] payload) throws InvalidProtocolBufferException {
        StringBuilder line = new StringBuilder();
        ListHerbNodesResponse.parseFrom(payload)
                .getNodesList()
                .forEach(n -> line.append(String.format(
                        Locale.ROOT,
                        "| #%d x=%.0f cách=%.1f sẵn=%s chờ=%ds ",
                        n.getNodeId(),
                        n.getX(),
                        n.getDistance(),
                        n.getReady(),
                        n.getSecondsUntilReady())));
        return line.toString();
    }

    /** Chờ — để server kịp việc nền lúc vào world (quỹ bế quan, mẫu vị trí 1 Hz) trước bước sau. */
    private static String waitSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "bị ngắt";
        }
        return "đã chờ " + seconds + "s";
    }

    /** Hái rồi chờ {@code HerbGatheredEvent} (server đếm 3 giây). */
    private String gather(long nodeId) throws IOException {
        String started = ask(
                Opcode.OPCODE_GATHER_HERB_COMMAND_VALUE,
                GatherHerbCommand.newBuilder().setNodeId(nodeId).build(),
                Opcode.OPCODE_GATHER_HERB_RESPONSE_VALUE,
                p -> GatherHerbResponse.parseFrom(p).getOutcome().name());
        if (!started.endsWith("STARTED")) {
            return started;
        }
        HerbGatheredEvent event = HerbGatheredEvent.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_HERB_GATHERED_EVENT_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        return started + " → " + event.getOutcome() + " x" + event.getQuantity();
    }

    @FunctionalInterface
    private interface Reply {
        String describe(byte[] payload) throws InvalidProtocolBufferException;
    }

    /** Gửi một lệnh, chờ phản hồi của nó hoặc {@code ErrorResponse} của ĐÚNG lệnh đó. */
    private String ask(int opcode, MessageLite command, int responseOpcode, Reply reply) throws IOException {
        int sequenceId = connection.send(opcode, command.toByteArray());
        for (int skipped = 0; skipped < MAX_FRAMES_TO_SKIP; skipped++) {
            PacketCodec.Frame frame = connection.readFrame();
            int got = frame.header().opcode();
            if (got == responseOpcode) {
                return reply.describe(frame.payload());
            }
            if (got == Opcode.OPCODE_ERROR_VALUE) {
                ErrorResponse error = ErrorResponse.parseFrom(frame.payload());
                if (error.getRelatedSequenceId() == sequenceId) {
                    return "LỖI " + error.getErrorCode() + " " + error.getErrorKey();
                }
            }
        }
        throw new IOException("Không thấy opcode " + responseOpcode + " sau " + MAX_FRAMES_TO_SKIP + " frame");
    }

    private static String describe(GetRelationshipsResponse response) {
        StringBuilder line = new StringBuilder();
        line.append("sưPhụ=").append(response.hasMentor() ? brief(response.getMentor()) : "-");
        line.append(" đồĐệ=[");
        response.getDisciplesList().forEach(view -> line.append(brief(view)).append(' '));
        line.append("] đạoLữ=").append(response.hasDaoLu() ? brief(response.getDaoLu()) : "-");
        line.append(" chặn=[");
        response.getBlockedList().forEach(view -> line.append(view.getName()).append(' '));
        return line.append(']').toString();
    }

    private static String brief(RelationMemberView view) {
        return view.getName() + "/Lv" + view.getLevel();
    }
}
