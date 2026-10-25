package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.AcceptDaoLuCommand;
import dev.kage.protocol.AcceptDaoLuResponse;
import dev.kage.protocol.AcceptDiscipleCommand;
import dev.kage.protocol.AcceptDiscipleResponse;
import dev.kage.protocol.BlockCharacterCommand;
import dev.kage.protocol.BlockCharacterResponse;
import dev.kage.protocol.DaoLuOutcome;
import dev.kage.protocol.DaoLuProposalEvent;
import dev.kage.protocol.DissolveDaoLuCommand;
import dev.kage.protocol.DissolveDaoLuResponse;
import dev.kage.protocol.GetRelationshipsRequest;
import dev.kage.protocol.GetRelationshipsResponse;
import dev.kage.protocol.LeaveMentorshipCommand;
import dev.kage.protocol.LeaveMentorshipResponse;
import dev.kage.protocol.MentorRequestReceivedEvent;
import dev.kage.protocol.MentorshipOutcome;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.ProposeDaoLuCommand;
import dev.kage.protocol.ProposeDaoLuResponse;
import dev.kage.protocol.RelationMemberView;
import dev.kage.protocol.RequestMentorCommand;
import dev.kage.protocol.RequestMentorResponse;
import dev.kage.protocol.UnblockCharacterCommand;
import dev.kage.protocol.UnblockCharacterResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Cửa sổ Quan hệ (phím Q, lô 21) — ba tab trên {@link ReportPanel}: Sư đồ (ADR-093), Đạo lữ (ADR-094), Chặn (ADR-091).
 * Ô nhập tên dùng chung cho mọi nút "với người trong ô". Lời bái sư / lời cầu đạo lữ NGƯỜI KHÁC gửi tới được giữ ở
 * đây (server chỉ báo một lần bằng sự kiện) để hiện thành dòng có nút nhận. Server quyết hết; client chỉ hiển thị.
 *
 * <p>Danh sách chặn còn được dùng để ẩn tin chat kênh chung của người bị chặn ({@link #isBlocked}).
 */
final class RelationshipCoordinator implements WindowFlow {

    static final List<String> TAB_KEYS =
            List.of("relation.tab.mentor", "relation.tab.daolu", "relation.tab.block", "relation.tab.sworn");

    private static final int TAB_MENTOR = 0;
    private static final int TAB_DAO_LU = 1;
    private static final int TAB_SWORN = 3;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    private final Set<Long> blockedIds = new HashSet<>();
    /** Lời bái sư đang chờ mình nhận: tên → cấp. */
    private final Map<String, Integer> pendingDisciples = new LinkedHashMap<>();
    /** Lời cầu đạo lữ đang chờ mình nhận lời (tên người cầu). */
    private final Set<String> pendingProposals = new HashSet<>();
    /** Việc của nút cuối dòng thứ i — dựng lại cùng lúc với dòng. */
    private final List<Runnable> rowActions = new ArrayList<>();

    private GetRelationshipsResponse current = GetRelationshipsResponse.getDefaultInstance();
    private int tab = TAB_MENTOR;
    private final SwornTab sworn;

    RelationshipCoordinator(ReportPanel panel, WorldCommandSender sender, ChatCoordinator chat) {
        this.panel = panel;
        this.sender = sender;
        this.chat = chat;
        this.sworn = new SwornTab(sender, chat);
        panel.enableInput("relation.input.name");
        panel.setOnRefresh(this::request);
        panel.setOnTabSelected(index -> {
            tab = index;
            render();
        });
        panel.setOnRowAction(index -> {
            if (index >= 0 && index < rowActions.size()) {
                rowActions.get(index).run();
            }
        });
        chat.hideSendersMatching(this::isBlocked);
        chat.addSlashCommands(this::onSlash);
        request(); // nạp danh sách chặn ngay khi vào world — bộ lọc chat cần nó
    }

    /** Người này đang bị mình chặn (theo lần đọc gần nhất). */
    boolean isBlocked(long characterId) {
        return blockedIds.contains(characterId);
    }

    private boolean onSlash(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        if ("/quanhe".equals(lower)) {
            panel.open();
            return true;
        }
        if (lower.startsWith("/chan ")) {
            block(line.substring("/chan ".length()).strip());
            return true;
        }
        return false;
    }

    private void request() {
        sender.sendReadRequest(
                Opcode.OPCODE_GET_RELATIONSHIPS_REQUEST_VALUE, GetRelationshipsRequest.getDefaultInstance());
    }

    @Override
    public boolean isOpen() {
        return panel.isOpen();
    }

    @Override
    public void close() {
        panel.close();
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        byte[] payload = frame.payload();
        if (opcode == Opcode.OPCODE_GET_RELATIONSHIPS_RESPONSE_VALUE) {
            onRelationships(GetRelationshipsResponse.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_MENTOR_REQUEST_RECEIVED_EVENT_VALUE) {
            MentorRequestReceivedEvent event = MentorRequestReceivedEvent.parseFrom(payload);
            pendingDisciples.put(event.getDiscipleName(), event.getDiscipleLevel());
            notice(KageI18n.formatOrKey(
                    i18n, "relation.notice.mentor_request", event.getDiscipleName(), event.getDiscipleLevel()));
            render();
            return true;
        }
        if (opcode == Opcode.OPCODE_DAO_LU_PROPOSAL_EVENT_VALUE) {
            DaoLuProposalEvent event = DaoLuProposalEvent.parseFrom(payload);
            pendingProposals.add(event.getProposerName());
            notice(KageI18n.formatOrKey(i18n, "relation.notice.dao_lu_proposal", event.getProposerName()));
            render();
            return true;
        }
        Optional<String> swornLine = sworn.dispatch(opcode, payload);
        if (swornLine.isPresent()) {
            notice(swornLine.get());
            request();
            return true;
        }
        return dispatchOutcome(opcode, payload);
    }

    /** Trả lời các lệnh — mỗi lệnh một câu trong nhật ký chat, rồi đọc lại quan hệ. */
    private boolean dispatchOutcome(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        String key;
        if (opcode == Opcode.OPCODE_BLOCK_CHARACTER_RESPONSE_VALUE) {
            key = "relation.block."
                    + suffix(BlockCharacterResponse.parseFrom(payload)
                            .getOutcome()
                            .name());
        } else if (opcode == Opcode.OPCODE_UNBLOCK_CHARACTER_RESPONSE_VALUE) {
            key = "relation.unblock."
                    + suffix(UnblockCharacterResponse.parseFrom(payload)
                            .getOutcome()
                            .name());
        } else if (opcode == Opcode.OPCODE_REQUEST_MENTOR_RESPONSE_VALUE) {
            key = mentorKey(RequestMentorResponse.parseFrom(payload).getOutcome(), "relation.mentor.requested");
        } else if (opcode == Opcode.OPCODE_ACCEPT_DISCIPLE_RESPONSE_VALUE) {
            key = mentorKey(AcceptDiscipleResponse.parseFrom(payload).getOutcome(), "relation.mentor.accepted");
        } else if (opcode == Opcode.OPCODE_LEAVE_MENTORSHIP_RESPONSE_VALUE) {
            key = mentorKey(LeaveMentorshipResponse.parseFrom(payload).getOutcome(), "relation.mentor.left");
        } else if (opcode == Opcode.OPCODE_PROPOSE_DAO_LU_RESPONSE_VALUE) {
            key = daoLuKey(ProposeDaoLuResponse.parseFrom(payload).getOutcome(), "relation.daolu.proposed");
        } else if (opcode == Opcode.OPCODE_ACCEPT_DAO_LU_RESPONSE_VALUE) {
            key = daoLuKey(AcceptDaoLuResponse.parseFrom(payload).getOutcome(), "relation.daolu.bound");
        } else if (opcode == Opcode.OPCODE_DISSOLVE_DAO_LU_RESPONSE_VALUE) {
            key = daoLuKey(DissolveDaoLuResponse.parseFrom(payload).getOutcome(), "relation.daolu.dissolved");
        } else {
            return false;
        }
        notice(text(key));
        request();
        return true;
    }

    private void onRelationships(GetRelationshipsResponse response) {
        current = response;
        blockedIds.clear();
        for (RelationMemberView blocked : response.getBlockedList()) {
            blockedIds.add(blocked.getCharacterId());
        }
        render();
    }

    private void render() {
        List<ReportPanel.Row> rows = new ArrayList<>();
        rowActions.clear();
        if (tab == TAB_MENTOR) {
            panel.setStatus(text("relation.hint.mentor"));
            mentorRows(rows);
        } else if (tab == TAB_DAO_LU) {
            panel.setStatus(text("relation.hint.daolu"));
            daoLuRows(rows);
        } else if (tab == TAB_SWORN) {
            panel.setStatus(text("relation.hint.sworn"));
            sworn.rows(current, panel::inputText, (label, actionKey, action) -> add(rows, label, actionKey, action));
        } else {
            panel.setStatus(text("relation.hint.block"));
            blockRows(rows);
        }
        panel.setRows(rows);
    }

    private void mentorRows(List<ReportPanel.Row> rows) {
        if (current.hasMentor()) {
            RelationMemberView mentor = current.getMentor();
            add(rows, member("relation.row.mentor", mentor), "relation.action.leave", () -> leave(mentor.getName()));
        } else {
            add(rows, text("relation.row.no_mentor"), "", () -> {});
        }
        for (RelationMemberView disciple : current.getDisciplesList()) {
            add(
                    rows,
                    member("relation.row.disciple", disciple),
                    "relation.action.leave",
                    () -> leave(disciple.getName()));
        }
        for (Map.Entry<String, Integer> pending : pendingDisciples.entrySet()) {
            String name = pending.getKey();
            add(
                    rows,
                    KageI18n.formatOrKey(i18n, "relation.row.pending_disciple", name, pending.getValue()),
                    "relation.action.accept",
                    () -> acceptDisciple(name));
        }
        add(rows, text("relation.row.request_mentor"), "relation.action.request_mentor", this::requestMentor);
    }

    private void daoLuRows(List<ReportPanel.Row> rows) {
        if (current.hasDaoLu()) {
            add(rows, member("relation.row.dao_lu", current.getDaoLu()), "relation.action.dissolve", this::dissolve);
        } else {
            add(rows, text("relation.row.no_dao_lu"), "", () -> {});
        }
        for (String proposer : pendingProposals) {
            add(
                    rows,
                    KageI18n.formatOrKey(i18n, "relation.row.pending_proposal", proposer),
                    "relation.action.accept",
                    () -> acceptProposal(proposer));
        }
        add(rows, text("relation.row.propose"), "relation.action.propose", this::propose);
    }

    private void blockRows(List<ReportPanel.Row> rows) {
        for (RelationMemberView blocked : current.getBlockedList()) {
            add(rows, blocked.getName(), "relation.action.unblock", () -> unblock(blocked.getName()));
        }
        add(
                rows,
                text("relation.row.block"),
                "relation.action.block",
                () -> block(panel.inputText().strip()));
    }

    private void add(List<ReportPanel.Row> rows, String label, String actionKey, Runnable action) {
        rows.add(new ReportPanel.Row(label, false, actionKey));
        rowActions.add(action);
    }

    private String member(String key, RelationMemberView view) {
        return KageI18n.formatOrKey(
                i18n,
                key,
                view.getName(),
                view.getLevel(),
                text(view.getOnline() ? "friend.status.online" : "friend.status.offline"));
    }

    private void requestMentor() {
        withTypedName(name -> sender.sendReadRequest(
                Opcode.OPCODE_REQUEST_MENTOR_COMMAND_VALUE,
                RequestMentorCommand.newBuilder().setMentorName(name).build()));
    }

    private void acceptDisciple(String name) {
        pendingDisciples.remove(name);
        sender.sendReadRequest(
                Opcode.OPCODE_ACCEPT_DISCIPLE_COMMAND_VALUE,
                AcceptDiscipleCommand.newBuilder().setDiscipleName(name).build());
    }

    private void leave(String otherName) {
        sender.sendReadRequest(
                Opcode.OPCODE_LEAVE_MENTORSHIP_COMMAND_VALUE,
                LeaveMentorshipCommand.newBuilder().setOtherName(otherName).build());
    }

    private void propose() {
        withTypedName(name -> sender.sendReadRequest(
                Opcode.OPCODE_PROPOSE_DAO_LU_COMMAND_VALUE,
                ProposeDaoLuCommand.newBuilder().setTargetName(name).build()));
    }

    private void acceptProposal(String proposer) {
        pendingProposals.remove(proposer);
        sender.sendReadRequest(
                Opcode.OPCODE_ACCEPT_DAO_LU_COMMAND_VALUE,
                AcceptDaoLuCommand.newBuilder().setProposerName(proposer).build());
    }

    private void dissolve() {
        sender.sendReadRequest(Opcode.OPCODE_DISSOLVE_DAO_LU_COMMAND_VALUE, DissolveDaoLuCommand.getDefaultInstance());
    }

    private void block(String name) {
        if (name.isEmpty()) {
            notice(text("relation.error.name_required"));
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_BLOCK_CHARACTER_COMMAND_VALUE,
                BlockCharacterCommand.newBuilder().setTargetName(name).build());
    }

    private void unblock(String name) {
        sender.sendReadRequest(
                Opcode.OPCODE_UNBLOCK_CHARACTER_COMMAND_VALUE,
                UnblockCharacterCommand.newBuilder().setTargetName(name).build());
    }

    private void withTypedName(java.util.function.Consumer<String> action) {
        String name = panel.inputText().strip();
        if (name.isEmpty()) {
            notice(text("relation.error.name_required"));
            return;
        }
        action.accept(name);
    }

    private static String mentorKey(MentorshipOutcome outcome, String okKey) {
        return outcome == MentorshipOutcome.MENTORSHIP_OUTCOME_OK ? okKey : "relation.mentor." + suffix(outcome.name());
    }

    private static String daoLuKey(DaoLuOutcome outcome, String okKey) {
        return outcome == DaoLuOutcome.DAO_LU_OUTCOME_OK ? okKey : "relation.daolu." + suffix(outcome.name());
    }

    /** {@code MENTORSHIP_OUTCOME_MENTOR_FULL} → {@code mentor_full}: phần sau chữ {@code OUTCOME_}. */
    private static String suffix(String enumName) {
        int at = enumName.indexOf("OUTCOME_");
        return enumName.substring(at < 0 ? 0 : at + "OUTCOME_".length()).toLowerCase(Locale.ROOT);
    }

    private void notice(String line) {
        chat.showSystemNotice(line);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        pendingDisciples.clear();
        pendingProposals.clear();
        sworn.reset();
        current = GetRelationshipsResponse.getDefaultInstance();
    }
}
