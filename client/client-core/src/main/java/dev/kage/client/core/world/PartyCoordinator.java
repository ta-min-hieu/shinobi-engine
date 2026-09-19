package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.PartyPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.AcceptPartyInviteResponse;
import dev.kage.protocol.CreatePartyResponse;
import dev.kage.protocol.GetMyPartyResponse;
import dev.kage.protocol.InvitePartyMemberResponse;
import dev.kage.protocol.KickPartyMemberResponse;
import dev.kage.protocol.LeavePartyResponse;
import dev.kage.protocol.ListPartyMembersResponse;
import dev.kage.protocol.ListPendingPartyInvitesResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RejectPartyInviteResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nối {@link PartyController} (trạng thái), {@link PartyPanel} (hiển thị) và
 * {@link WorldCommandSender} (mạng) thành cửa sổ Nhóm chạy được (2026-08-25) — cùng hình dạng
 * {@link ChatCoordinator}/{@link DialogueCoordinator}.
 *
 * <p><b>Không lớp nào tự suy ra trạng thái mới từ một lệnh vừa trả OK.</b> Mọi lệnh đổi nhóm đều
 * kết thúc bằng {@link #refresh()} — hỏi lại server ba câu và vẽ theo câu trả lời. Đắt hơn việc tự
 * cập nhật cục bộ, nhưng đúng Điều 15, và là cách DUY NHẤT đúng ở đây: server không đẩy gì cả (xem
 * javadoc {@link PartyController}), nên trạng thái cục bộ không có nguồn đính chính nào khác.
 *
 * <p>Lý do lỗi được báo qua {@code ChatCoordinator} chứ không phải một dòng riêng trong cửa sổ: chat
 * đã là nơi người chơi đọc mọi phản hồi bằng chữ, và câu trả lời cho "sao mời không được" thường
 * nằm cạnh chính cuộc trò chuyện dẫn tới lời mời đó.
 */
final class PartyCoordinator implements WindowFlow {

    private final PartyController controller = new PartyController();
    private final PartyPanel panel;
    private final WorldCommandSender sender;
    private final ChatCoordinator notices;
    private final I18NBundle i18n = KageI18n.load();

    /** Đệm dựng sẵn cho {@link PartyPanel#setContent} — chỉ đổi khi có gói mới, không phải mỗi khung. */
    private final List<PartyPanel.MemberRow> memberRows = new ArrayList<>();

    private final List<PartyPanel.InviteRow> inviteRows = new ArrayList<>();

    /**
     * @param notices kênh báo lý do thất bại — xem javadoc lớp về việc dùng nhật ký chat thay vì một
     *     dòng lỗi riêng trong cửa sổ.
     */
    PartyCoordinator(PartyPanel panel, WorldCommandSender sender, ChatCoordinator notices) {
        this.panel = panel;
        this.sender = sender;
        this.notices = notices;
        panel.setOnOpened(this::refresh);
        panel.setOnCreate(sender::createParty);
        panel.setOnInvite(sender::invitePartyMember);
        panel.setOnLeave(sender::leaveParty);
        panel.setOnKick(sender::kickPartyMember);
        panel.setOnAcceptInvite(sender::acceptPartyInvite);
        panel.setOnRejectInvite(sender::rejectPartyInvite);
        redraw();
    }

    /** Cửa sổ đang mở hay không — {@code WorldScreen} chặn phím gameplay khi đúng. */
    @Override
    public boolean isOpen() {
        return panel.isOpen();
    }

    @Override
    public void close() {
        panel.close();
    }

    /** Hỏi lại server toàn bộ trạng thái nhóm. Ba câu vì party.proto tách làm ba gói. */
    void refresh() {
        sender.getMyParty();
        sender.listPartyMembers();
        sender.listPendingPartyInvites();
    }

    /**
     * Định tuyến một gói có thể thuộc luồng nhóm.
     *
     * @return {@code true} nếu gói đã được xử lý ở đây.
     */
    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        return dispatch(frame.header().opcode(), frame.payload());
    }

    private boolean dispatch(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        return dispatchState(opcode, payload) || dispatchOutcome(opcode, payload);
    }

    /**
     * Ba gói TRẠNG THÁI (trả lời cho {@link #refresh()}). Tách khỏi nhóm gói KẾT QUẢ bên dưới thuần
     * để giữ độ phức tạp method dưới ngưỡng PMD — cùng cách {@code WorldScreen.dispatch} đã tách.
     */
    private boolean dispatchState(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_GET_MY_PARTY_RESPONSE_VALUE) {
            controller.onMyParty(GetMyPartyResponse.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_LIST_PARTY_MEMBERS_RESPONSE_VALUE) {
            controller.onMembers(ListPartyMembersResponse.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_LIST_PENDING_PARTY_INVITES_RESPONSE_VALUE) {
            controller.onInvites(ListPendingPartyInvitesResponse.parseFrom(payload));
        } else {
            return false;
        }
        redraw();
        return true;
    }

    /**
     * Sáu gói KẾT QUẢ lệnh. Mỗi cái chỉ mang một {@code outcome} — không mang trạng thái nhóm mới,
     * nên xử lý luôn là: nói ra lý do nếu hỏng, rồi hỏi lại server bất kể thành công hay không.
     */
    private boolean dispatchOutcome(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        Optional<String> messageKey;
        if (opcode == Opcode.OPCODE_CREATE_PARTY_RESPONSE_VALUE) {
            messageKey = PartyController.createOutcomeKey(
                    CreatePartyResponse.parseFrom(payload).getOutcome());
        } else if (opcode == Opcode.OPCODE_INVITE_PARTY_MEMBER_RESPONSE_VALUE) {
            messageKey = PartyController.inviteOutcomeKey(
                    InvitePartyMemberResponse.parseFrom(payload).getOutcome());
        } else if (opcode == Opcode.OPCODE_ACCEPT_PARTY_INVITE_RESPONSE_VALUE) {
            messageKey = PartyController.acceptOutcomeKey(
                    AcceptPartyInviteResponse.parseFrom(payload).getOutcome());
        } else if (opcode == Opcode.OPCODE_REJECT_PARTY_INVITE_RESPONSE_VALUE) {
            messageKey = PartyController.rejectOutcomeKey(
                    RejectPartyInviteResponse.parseFrom(payload).getOutcome());
        } else if (opcode == Opcode.OPCODE_LEAVE_PARTY_RESPONSE_VALUE) {
            messageKey = PartyController.leaveOutcomeKey(
                    LeavePartyResponse.parseFrom(payload).getOutcome());
        } else if (opcode == Opcode.OPCODE_KICK_PARTY_MEMBER_RESPONSE_VALUE) {
            messageKey = PartyController.kickOutcomeKey(
                    KickPartyMemberResponse.parseFrom(payload).getOutcome());
        } else {
            return false;
        }
        messageKey.ifPresent(key -> notices.showSystemNotice(text(key)));
        // Hỏi lại KỂ CẢ khi lệnh hỏng: một lệnh hỏng vì "đã ở trong nhóm rồi" là bằng chứng ảnh chụp
        // cục bộ đang SAI, đúng lúc đáng đồng bộ lại nhất.
        refresh();
        return true;
    }

    private void redraw() {
        memberRows.clear();
        for (PartyController.Member member : controller.members()) {
            memberRows.add(new PartyPanel.MemberRow(displayName(member), member.name(), member.online()));
        }
        inviteRows.clear();
        for (PartyController.Invite invite : controller.invites()) {
            inviteRows.add(
                    new PartyPanel.InviteRow(invite.inviteId(), i18n.format("party.invite.row", invite.partyId())));
        }
        panel.setContent(statusText(), memberRows, controller.isLeader(), inviteRows);
        panel.setActionsEnabled(controller.inParty(), controller.isLeader());
        // Kênh chat Nhóm chỉ có nghĩa khi đang ở trong nhóm — đây là nơi DUY NHẤT biết điều đó.
        notices.setPartyAvailable(controller.inParty());
    }

    private String statusText() {
        if (!controller.inParty()) {
            return text("party.status.none");
        }
        return i18n.format(
                controller.isLeader() ? "party.status.leader" : "party.status.member",
                controller.members().size());
    }

    private String displayName(PartyController.Member member) {
        return member.leader() ? i18n.format("party.member.leader", member.name()) : member.name();
    }

    /** Tra bản dịch, thiếu thì hiện thẳng khoá — cùng lý do {@code DialoguePanel#text}. */
    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    /** Rời màn hình/mất kết nối — ảnh chụp của một phiên chơi không sống sót sang phiên sau. */
    @Override
    public void reset() {
        controller.clear();
        panel.close();
        redraw();
    }
}
