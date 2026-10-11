package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.List;

import dev.kage.protocol.AcceptPartyInviteCommand;
import dev.kage.protocol.AcceptPartyInviteResponse;
import dev.kage.protocol.CreatePartyCommand;
import dev.kage.protocol.CreatePartyResponse;
import dev.kage.protocol.InvitePartyMemberCommand;
import dev.kage.protocol.InvitePartyMemberResponse;
import dev.kage.protocol.ListPartyMembersRequest;
import dev.kage.protocol.ListPartyMembersResponse;
import dev.kage.protocol.ListPendingPartyInvitesRequest;
import dev.kage.protocol.ListPendingPartyInvitesResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PartyMemberView;
import dev.kage.protocol.PendingPartyInviteView;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Lập nhóm HAI người — đường headless để kiểm các luật chỉ lộ ra khi có đồng đội: chia EXP + tiến độ
 * quest cho đồng đội cùng World (README dòng 35) và trưởng nhóm rớt mạng tự chuyển quyền (40i).
 *
 * <p>Chạy HAI probe trên HAI TÀI KHOẢN khác nhau (một tài khoản chỉ một nhân vật online — 68b): bên
 * {@code --partyAccept=true} chạy TRƯỚC và thăm dò lời mời; bên {@code --partyInvite=<tên>} lập nhóm
 * rồi mời. Lời mời không có event đẩy về, nên bên nhận phải HỎI ({@code ListPendingPartyInvites}).
 *
 * <p>{@code ALREADY_IN_PARTY} lúc lập nhóm KHÔNG phải lỗi — nhóm sống theo DB, không theo kết nối
 * (cùng lý do đã ghi ở {@link DungeonScenario}).
 */
final class PartyScenario {

    /** Ngân sách bỏ qua AoI event khi chờ 1 phản hồi cụ thể — cùng lý do đã ghi ở {@code QuestScenario}. */
    private static final int MAX_FRAMES_TO_SKIP = 2000;

    private static final long INVITE_POLL_INTERVAL_MILLIS = 500L;
    private static final long MILLIS_PER_SECOND = 1000L;

    private final GameConnection connection;
    private final ProbeSession session;

    PartyScenario(GameConnection connection, ProbeSession session) {
        this.connection = connection;
        this.session = session;
    }

    /** Vào nhóm theo {@code plan}: chấp nhận lời mời, hoặc lập nhóm rồi mời; không yêu cầu thì không làm gì. */
    void join(ProbeConfig.PartyPlan plan) throws IOException, InterruptedException {
        if (plan.acceptIncoming()) {
            acceptIncoming(plan.waitSeconds());
        } else if (!plan.inviteTargetName().isEmpty()) {
            invite(plan.inviteTargetName());
        }
    }

    /** In danh sách thành viên (ai là trưởng nhóm, ai online) nếu {@code plan} yêu cầu. */
    void reportIfRequested(ProbeConfig.PartyPlan plan) throws IOException {
        if (!plan.report()) {
            return;
        }
        connection.send(
                Opcode.OPCODE_LIST_PARTY_MEMBERS_REQUEST_VALUE,
                ListPartyMembersRequest.getDefaultInstance().toByteArray());
        ListPartyMembersResponse response = ListPartyMembersResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_LIST_PARTY_MEMBERS_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("nhóm có " + response.getMembersCount() + " thành viên");
        for (PartyMemberView member : response.getMembersList()) {
            session.note("  thành viên id=" + member.getCharacterId() + " tên=" + member.getName() + " trưởngNhóm="
                    + member.getIsLeader() + " online=" + member.getOnline());
        }
    }

    private void invite(String targetName) throws IOException {
        connection.send(
                Opcode.OPCODE_CREATE_PARTY_COMMAND_VALUE,
                CreatePartyCommand.getDefaultInstance().toByteArray());
        CreatePartyResponse created = CreatePartyResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_CREATE_PARTY_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        connection.send(
                Opcode.OPCODE_INVITE_PARTY_MEMBER_COMMAND_VALUE,
                InvitePartyMemberCommand.newBuilder()
                        .setTargetName(targetName)
                        .build()
                        .toByteArray());
        InvitePartyMemberResponse invited = InvitePartyMemberResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_INVITE_PARTY_MEMBER_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("lập nhóm: " + created.getOutcome().name() + " — mời " + targetName + ": "
                + invited.getOutcome().name());
    }

    private void acceptIncoming(int waitSeconds) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + waitSeconds * MILLIS_PER_SECOND;
        while (System.currentTimeMillis() < deadline) {
            List<PendingPartyInviteView> invites = pendingInvites();
            if (!invites.isEmpty()) {
                accept(invites.get(0).getInviteId());
                return;
            }
            Thread.sleep(INVITE_POLL_INTERVAL_MILLIS);
        }
        session.note("KHÔNG có lời mời nhóm nào trong " + waitSeconds + "s — đi tiếp một mình");
    }

    private List<PendingPartyInviteView> pendingInvites() throws IOException {
        connection.send(
                Opcode.OPCODE_LIST_PENDING_PARTY_INVITES_REQUEST_VALUE,
                ListPendingPartyInvitesRequest.getDefaultInstance().toByteArray());
        return ListPendingPartyInvitesResponse.parseFrom(connection
                        .readUntilOpcode(Opcode.OPCODE_LIST_PENDING_PARTY_INVITES_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                        .payload())
                .getInvitesList();
    }

    private void accept(long inviteId) throws IOException {
        connection.send(
                Opcode.OPCODE_ACCEPT_PARTY_INVITE_COMMAND_VALUE,
                AcceptPartyInviteCommand.newBuilder()
                        .setInviteId(inviteId)
                        .build()
                        .toByteArray());
        AcceptPartyInviteResponse accepted = AcceptPartyInviteResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_ACCEPT_PARTY_INVITE_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("chấp nhận lời mời nhóm inviteId=" + inviteId + ": "
                + accepted.getOutcome().name());
    }
}
