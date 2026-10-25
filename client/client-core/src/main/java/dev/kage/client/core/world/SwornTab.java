package dev.kage.client.core.world;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.AcceptSwornCommand;
import dev.kage.protocol.AcceptSwornResponse;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.GetRelationshipsResponse;
import dev.kage.protocol.InviteSwornCommand;
import dev.kage.protocol.InviteSwornResponse;
import dev.kage.protocol.LeaveSwornCommand;
import dev.kage.protocol.LeaveSwornResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RelationMemberView;
import dev.kage.protocol.SwornInviteEvent;
import dev.kage.protocol.SwornOutcome;

/**
 * Tab "Kết nghĩa" của cửa sổ Quan hệ (ADR-110 D6) — tách khỏi {@link RelationshipCoordinator} để lớp đó không vượt trần
 * độ phức tạp PMD. Huynh đệ hiện có, lời mời người khác gửi tới (giữ ở đây vì server chỉ báo một lần), nút mời người
 * trong ô tên và nút rời nhóm; {@code /kn <chữ>} gửi lên kênh chat kết nghĩa. Server quyết hết.
 */
final class SwornTab {

    /** Thêm một dòng có nút cuối dòng — {@link RelationshipCoordinator} giữ danh sách việc của nút. */
    @FunctionalInterface
    interface RowSink {
        void add(String label, String actionKey, Runnable action);
    }

    private static final String SWORN_CHAT = "/kn ";

    private final WorldCommandSender sender;
    private final I18NBundle i18n = KageI18n.load();
    private final Set<String> pendingInvites = new LinkedHashSet<>();

    SwornTab(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        if (!line.toLowerCase(Locale.ROOT).startsWith(SWORN_CHAT)) {
            return false;
        }
        String body = line.substring(SWORN_CHAT.length()).strip();
        if (!body.isEmpty()) {
            sender.sendChatMessage(ChatChannel.CHAT_CHANNEL_SWORN, "", body);
        }
        return true;
    }

    /** Dòng của tab. {@code typedName} = chữ trong ô tên lúc bấm. */
    void rows(GetRelationshipsResponse current, Supplier<String> typedName, RowSink sink) {
        for (RelationMemberView brother : current.getSwornList()) {
            sink.add(
                    KageI18n.formatOrKey(
                            i18n,
                            "relation.row.sworn",
                            brother.getName(),
                            brother.getLevel(),
                            text(brother.getOnline() ? "friend.status.online" : "friend.status.offline")),
                    "",
                    () -> {});
        }
        for (String inviter : pendingInvites) {
            sink.add(
                    KageI18n.formatOrKey(i18n, "relation.row.sworn_invite", inviter),
                    "relation.action.accept",
                    () -> accept(inviter));
        }
        sink.add(text("relation.row.sworn_invite_hint"), "relation.action.invite", () -> invite(typedName.get()));
        if (current.getSwornCount() > 0) {
            sink.add(text("relation.row.sworn_leave"), "relation.action.leave", this::leave);
        }
    }

    /**
     * Gói thuộc tab này.
     *
     * @return khoá câu báo trong chat; rỗng nếu gói không thuộc tab này
     */
    Optional<String> dispatch(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_SWORN_INVITE_EVENT_VALUE) {
            String inviter = SwornInviteEvent.parseFrom(payload).getInviterName();
            pendingInvites.add(inviter);
            return Optional.of(KageI18n.formatOrKey(i18n, "relation.notice.sworn_invite", inviter));
        }
        if (opcode == Opcode.OPCODE_INVITE_SWORN_RESPONSE_VALUE) {
            return Optional.of(outcome(InviteSwornResponse.parseFrom(payload).getOutcome(), "relation.sworn.invited"));
        }
        if (opcode == Opcode.OPCODE_ACCEPT_SWORN_RESPONSE_VALUE) {
            return Optional.of(outcome(AcceptSwornResponse.parseFrom(payload).getOutcome(), "relation.sworn.joined"));
        }
        if (opcode == Opcode.OPCODE_LEAVE_SWORN_RESPONSE_VALUE) {
            return Optional.of(outcome(LeaveSwornResponse.parseFrom(payload).getOutcome(), "relation.sworn.left"));
        }
        return Optional.empty();
    }

    private String outcome(SwornOutcome outcome, String okKey) {
        if (outcome == SwornOutcome.SWORN_OUTCOME_OK) {
            return text(okKey);
        }
        String name = outcome.name();
        return text(
                "relation.sworn." + name.substring("SWORN_OUTCOME_".length()).toLowerCase(Locale.ROOT));
    }

    private void invite(String name) {
        if (!name.isBlank()) {
            sender.sendReadRequest(
                    Opcode.OPCODE_INVITE_SWORN_COMMAND_VALUE,
                    InviteSwornCommand.newBuilder().setTargetName(name.strip()).build());
        }
    }

    private void accept(String inviter) {
        pendingInvites.remove(inviter);
        sender.sendReadRequest(
                Opcode.OPCODE_ACCEPT_SWORN_COMMAND_VALUE,
                AcceptSwornCommand.newBuilder().setInviterName(inviter).build());
    }

    private void leave() {
        sender.sendReadRequest(Opcode.OPCODE_LEAVE_SWORN_COMMAND_VALUE, LeaveSwornCommand.getDefaultInstance());
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    void reset() {
        pendingInvites.clear();
    }
}
