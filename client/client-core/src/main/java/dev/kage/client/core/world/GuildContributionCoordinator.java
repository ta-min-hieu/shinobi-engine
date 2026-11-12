package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GuildMemberView;
import dev.kage.protocol.ListGuildMembersRequest;
import dev.kage.protocol.ListGuildMembersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bảng cống hiến bang (ADR-287) — {@code /conghien} (hoặc {@code /contrib}): hỏi lại danh sách thành viên (đã mang điểm
 * cống hiến, ADR-068), xếp giảm dần, in {@value #TOP} người đầu và hạng của chính mình. Không thêm opcode nào.
 */
final class GuildContributionCoordinator implements WindowFlow {

    /** Số người in ra. */
    static final int TOP = 10;

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    /** Chỉ in khi CHÍNH lệnh này hỏi — cửa sổ bang cũng nhận cùng gói trả lời. */
    private boolean pending;

    GuildContributionCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/conghien".equals(command) && !"/contrib".equals(command)) {
            return false;
        }
        pending = true;
        sender.sendReadRequest(
                Opcode.OPCODE_LIST_GUILD_MEMBERS_REQUEST_VALUE, ListGuildMembersRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (!pending || frame.header().opcode() != Opcode.OPCODE_LIST_GUILD_MEMBERS_RESPONSE_VALUE) {
            return false;
        }
        pending = false;
        List<GuildMemberView> ranked =
                ranked(ListGuildMembersResponse.parseFrom(frame.payload()).getMembersList());
        if (ranked.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "contrib.none"));
            return false;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "contrib.header"));
        for (int index = 0; index < Math.min(TOP, ranked.size()); index++) {
            GuildMemberView member = ranked.get(index);
            chat.showSystemNotice(
                    KageI18n.formatOrKey(i18n, "contrib.line", index + 1, member.getName(), member.getContribution()));
        }
        for (int index = 0; index < ranked.size(); index++) {
            if (chat.isOwnName(ranked.get(index).getName())) {
                chat.showSystemNotice(KageI18n.formatOrKey(
                        i18n,
                        "contrib.mine",
                        index + 1,
                        ranked.size(),
                        ranked.get(index).getContribution()));
            }
        }
        return false; // để cửa sổ bang cũng cập nhật như thường
    }

    /** Xếp giảm dần theo cống hiến, hoà thì theo tên. Tĩnh để test. */
    static List<GuildMemberView> ranked(List<GuildMemberView> members) {
        List<GuildMemberView> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparingLong(GuildMemberView::getContribution)
                .reversed()
                .thenComparing(GuildMemberView::getName));
        return sorted;
    }
}
