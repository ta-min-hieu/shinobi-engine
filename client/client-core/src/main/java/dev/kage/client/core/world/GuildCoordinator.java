package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.GuildPanel;
import dev.kage.client.core.ui.hud.ScrollingList;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ApproveGuildJoinRequestResponse;
import dev.kage.protocol.CreateGuildResponse;
import dev.kage.protocol.DisbandGuildResponse;
import dev.kage.protocol.GetMyGuildResponse;
import dev.kage.protocol.KickGuildMemberResponse;
import dev.kage.protocol.LeaveGuildResponse;
import dev.kage.protocol.ListGuildMembersResponse;
import dev.kage.protocol.ListPendingGuildJoinRequestsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RejectGuildJoinRequestResponse;
import dev.kage.protocol.SendGuildJoinRequestResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nối {@link GuildController} (trạng thái), {@link GuildPanel} (hiển thị) và
 * {@link WorldCommandSender} (mạng) thành khung bang hội chạy được (2026-08-25) — cùng hình dạng
 * {@link ChatCoordinator}, và cùng lý do tồn tại: {@code WorldScreen} đang ở ĐÚNG trần
 * {@code CyclomaticComplexity} của PMD.
 *
 * <p>Toàn bộ dải opcode 700-719 chưa từng được client dùng trước hôm nay, dù {@code feature-guild}
 * phía server đã xong từ Phase 7.
 *
 * <p><b>Luật một dòng của lớp này: hỏi lại server sau MỌI lệnh đổi trạng thái</b> ({@link #settle}).
 * guild.proto không có event đẩy nào (xem javadoc {@link GuildController}), nên "lệnh vừa trả OK"
 * KHÔNG được dùng để suy ra trạng thái mới — trạng thái mới chỉ đến từ ba response tra cứu
 * ({@code GetMyGuild}, {@code ListGuildMembers}, {@code ListPendingGuildJoinRequests}), là những thứ
 * server nói ra, không phải client tự tính (Điều 15).
 *
 * <p>Hệ quả còn lại mà cách này KHÔNG bù được: giữa hai lần hỏi, màn hình vẫn có thể sai. Bị đuổi
 * khỏi bang trong lúc khung đang mở thì phải bấm Làm mới (hoặc đóng/mở lại) mới biết. Đóng lỗ đó
 * thật sự cần một event đẩy phía server, tức cần sửa .proto — nằm ngoài quyền của client.
 */
final class GuildCoordinator implements WindowFlow {

    private final GuildController controller = new GuildController();
    private final GuildPanel panel;
    private final WorldCommandSender sender;
    private final I18NBundle i18n = KageI18n.load();

    /** Đệm dựng sẵn cho {@link GuildPanel#setMembers} — chỉ đổi khi server vừa trả lời, không mỗi khung. */
    private final List<GuildPanel.MemberRow> memberRows = new ArrayList<>();

    private final List<GuildPanel.RequestRow> requestRows = new ArrayList<>();

    /**
     * @param canOpen khung chat có đang mở ô nhập không. Chữ "g" người chơi gõ trong ô chat
     *     cũng rơi tới {@code Stage} của {@link GuildPanel} và sẽ mở khung bang hội nếu không ai
     *     được phép mở — xem {@link GuildPanel#setCanOpen}. Nhận qua tham số chứ không tự hỏi
     *     {@code ChatCoordinator}: hai luồng này ngang hàng nhau, không cái nào sở hữu cái nào.
     */
    GuildCoordinator(GuildPanel panel, WorldCommandSender sender, BooleanSupplier canOpen) {
        this.panel = panel;
        this.sender = sender;
        panel.setCanOpen(canOpen);
        panel.setOnRefresh(this::refresh);
        panel.setOnCreate(typed -> submitName(typed, sender::createGuild));
        panel.setOnJoin(typed -> submitName(typed, sender::sendGuildJoinRequest));
        panel.setOnKick(sender::kickGuildMember);
        panel.setOnApprove(sender::approveGuildJoinRequest);
        panel.setOnReject(sender::rejectGuildJoinRequest);
        panel.setOnLeave(sender::leaveGuild);
        panel.setOnDisband(sender::disbandGuild);
        panel.setStatus(text("guild.hint.open"));
        refreshView();
    }

    /**
     * Khung có đang mở không — {@code WorldScreen} phải chặn MỌI phím gameplay khi câu này đúng, xem
     * {@link GuildPanel#isOpen()}.
     */
    @Override
    public boolean isOpen() {
        return panel.isOpen();
    }

    /** Đóng khung (mở hội thoại NPC, rời màn hình) — trạng thái đã tải vẫn giữ nguyên. */
    @Override
    public void close() {
        panel.close();
    }

    /**
     * Định tuyến một gói có thể thuộc luồng bang hội.
     *
     * <p>Tách làm hai nhóm THUẦN để hạ cyclomatic complexity dưới ngưỡng PMD (10 opcode trong một
     * chuỗi if là vượt ngưỡng) — cùng cách {@code WorldScreen.dispatchMovement}/
     * {@code dispatchCombat} đã làm, không phải đổi hành vi.
     *
     * @return {@code true} nếu gói đã được xử lý ở đây.
     */
    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        return dispatch(frame.header().opcode(), frame.payload());
    }

    private boolean dispatch(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        return dispatchQueryResponse(opcode, payload) || dispatchCommandResponse(opcode, payload);
    }

    /** Ba response TRA CỨU — nguồn DUY NHẤT làm đổi trạng thái hiển thị, xem javadoc lớp. */
    private boolean dispatchQueryResponse(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_GET_MY_GUILD_RESPONSE_VALUE) {
            controller.onMyGuild(GetMyGuildResponse.parseFrom(payload));
            refreshView();
            return true;
        }
        if (opcode == Opcode.OPCODE_LIST_GUILD_MEMBERS_RESPONSE_VALUE) {
            controller.onMembers(ListGuildMembersResponse.parseFrom(payload));
            refreshView();
            return true;
        }
        if (opcode == Opcode.OPCODE_LIST_PENDING_GUILD_JOIN_REQUESTS_RESPONSE_VALUE) {
            controller.onPendingRequests(ListPendingGuildJoinRequestsResponse.parseFrom(payload));
            refreshView();
            return true;
        }
        return false;
    }

    /**
     * Bảy response KẾT QUẢ LỆNH. Tất cả đi qua {@link #settle}: nói ra kết quả rồi hỏi lại server —
     * không cái nào tự sửa trạng thái, kể cả khi outcome là {@code OK}.
     */
    private boolean dispatchCommandResponse(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_CREATE_GUILD_RESPONSE_VALUE) {
            settle(GuildController.createMessageKey(
                    CreateGuildResponse.parseFrom(payload).getOutcome()));
        } else if (opcode == Opcode.OPCODE_SEND_GUILD_JOIN_REQUEST_RESPONSE_VALUE) {
            settle(GuildController.joinMessageKey(
                    SendGuildJoinRequestResponse.parseFrom(payload).getOutcome()));
        } else if (opcode == Opcode.OPCODE_APPROVE_GUILD_JOIN_REQUEST_RESPONSE_VALUE) {
            settle(GuildController.approveMessageKey(
                    ApproveGuildJoinRequestResponse.parseFrom(payload).getOutcome()));
        } else if (opcode == Opcode.OPCODE_REJECT_GUILD_JOIN_REQUEST_RESPONSE_VALUE) {
            settle(GuildController.rejectMessageKey(
                    RejectGuildJoinRequestResponse.parseFrom(payload).getOutcome()));
        } else if (opcode == Opcode.OPCODE_LEAVE_GUILD_RESPONSE_VALUE) {
            settle(GuildController.leaveMessageKey(
                    LeaveGuildResponse.parseFrom(payload).getOutcome()));
        } else if (opcode == Opcode.OPCODE_DISBAND_GUILD_RESPONSE_VALUE) {
            settle(GuildController.disbandMessageKey(
                    DisbandGuildResponse.parseFrom(payload).getOutcome()));
        } else if (opcode == Opcode.OPCODE_KICK_GUILD_MEMBER_RESPONSE_VALUE) {
            settle(GuildController.kickMessageKey(
                    KickGuildMemberResponse.parseFrom(payload).getOutcome()));
        } else {
            return false;
        }
        return true;
    }

    /**
     * Nói ra kết quả một lệnh, rồi hỏi lại server.
     *
     * <p>Hỏi lại KỂ CẢ khi lệnh hỏng: một lệnh hỏng thường có nghĩa là client đang hiểu sai trạng
     * thái (VD bấm Giải tán mà nhận {@code NOT_LEADER} — mình đã bị mất quyền trưởng bang lúc nào
     * không hay), và đó chính là lúc cần dữ liệu mới nhất nhất.
     *
     * @param messageKey rỗng khi client không nhận ra outcome (server MỚI HƠN client) — nói thẳng ra
     *     là không hiểu thay vì im lặng hoặc đoán bừa.
     */
    private void settle(Optional<String> messageKey) {
        panel.setStatus(text(messageKey.orElse("guild.error.unknown")));
        refresh();
    }

    /**
     * Hỏi lại cả ba thứ. Gửi cả ba mà không tự lọc trước ("mình đâu phải leader, hỏi đơn làm gì") là
     * CÓ CHỦ ĐÍCH: chính cái "mình là gì" mới là thứ đang đi hỏi. Hai request thừa trả về danh sách
     * rỗng, rẻ hơn nhiều so với một client tự tin sai.
     */
    private void refresh() {
        sender.requestMyGuild();
        sender.requestGuildMembers();
        sender.requestPendingGuildJoinRequests();
    }

    private void submitName(String typed, Consumer<String> send) {
        GuildController.ComposeResult result = controller.composeGuildName(typed);
        switch (result) {
            case GuildController.ComposeResult.Send valid -> send.accept(valid.guildName());
            case GuildController.ComposeResult.Reject reject -> panel.setStatus(text(reject.messageKey()));
            case GuildController.ComposeResult.Nothing ignored -> {
                // Ô nhập rỗng — không gửi, không mắng.
            }
        }
    }

    /** Đẩy toàn bộ trạng thái hiện có xuống panel. Gọi sau MỖI response tra cứu, không phải mỗi khung. */
    private void refreshView() {
        controller.guildName().ifPresentOrElse(this::showGuild, this::showNoGuild);
    }

    private void showNoGuild() {
        panel.showNoGuild(text("guild.header.none"));
    }

    private void showGuild(String guildName) {
        boolean leader = controller.isLeader();
        panel.showGuild(guildName + " — " + text(leader ? "guild.label.leader" : "guild.label.member"), leader);
        refreshMembers(leader);
        refreshPendingRequests();
    }

    /**
     * @param leader chỉ trưởng bang mới có nút Đuổi, và KHÔNG có ở dòng của chính mình — server trả
     *     {@code CANNOT_KICK_SELF} cho trường hợp đó (guild.proto), tức bày nút ra là bày một nút
     *     chắc chắn hỏng.
     */
    private void refreshMembers(boolean leader) {
        List<GuildController.Member> all = controller.members();
        int shown = Math.min(all.size(), ScrollingList.MAX_BUILT_ROWS);
        memberRows.clear();
        for (int index = 0; index < shown; index++) {
            GuildController.Member member = all.get(index);
            memberRows.add(new GuildPanel.MemberRow(memberText(member), member.name(), leader && !member.leader()));
        }
        panel.setMembers(memberRows, overflowNotice(all.size() - shown));
    }

    private void refreshPendingRequests() {
        List<GuildController.PendingRequest> all = controller.pendingRequests();
        int shown = Math.min(all.size(), ScrollingList.MAX_BUILT_ROWS);
        requestRows.clear();
        for (int index = 0; index < shown; index++) {
            GuildController.PendingRequest request = all.get(index);
            requestRows.add(new GuildPanel.RequestRow(request.requestId(), request.characterName()));
        }
        panel.setPendingRequests(requestRows, overflowNotice(all.size() - shown));
    }

    /** Ghép một dòng thành viên: {@code Tên — Vai trò · Trạng thái}. */
    private String memberText(GuildController.Member member) {
        String role = text(member.leader() ? "guild.label.leader" : "guild.label.member");
        String presence = text(member.online() ? "guild.label.online" : "guild.label.offline");
        return member.name() + " — " + role + " · " + presence;
    }

    /**
     * Câu "còn N dòng nữa không hiện", hoặc rỗng khi không cắt gì. Nói ra thay vì im lặng cắt: một
     * danh sách bị cắt âm thầm trông y hệt một danh sách ngắn thật.
     */
    private String overflowNotice(int hidden) {
        return hidden <= 0 ? "" : format("guild.list.truncated", hidden);
    }

    /** Tra bản dịch, thiếu thì hiện thẳng khoá — cùng lý do {@code DialoguePanel#text}. */
    private String text(String key) {
        try {
            return i18n.get(key);
        } catch (java.util.MissingResourceException e) {
            return key;
        }
    }

    private String format(String key, Object argument) {
        try {
            return i18n.format(key, argument);
        } catch (java.util.MissingResourceException e) {
            return key;
        }
    }

    /** Rời màn hình/mất kết nối — trạng thái bang của một phiên chơi không được sống sót sang phiên sau. */
    @Override
    public void reset() {
        controller.clear();
        panel.close();
        panel.setStatus(text("guild.hint.open"));
        refreshView();
    }
}
