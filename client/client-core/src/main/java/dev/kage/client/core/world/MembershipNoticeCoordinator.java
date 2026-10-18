package dev.kage.client.core.world;

import java.util.Map;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GuildChangeReason;
import dev.kage.protocol.GuildChangedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PartyChangeReason;
import dev.kage.protocol.PartyChangedEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * ADR-059 — nhận "nhóm/bang của bạn vừa đổi": nói một câu vào nhật ký chat rồi bảo luồng Nhóm/Bang hỏi
 * lại server. Không suy danh sách thành viên từ event — server vẫn là nguồn duy nhất.
 */
final class MembershipNoticeCoordinator implements WindowFlow {

    private static final Map<PartyChangeReason, String> PARTY_KEYS = Map.of(
            PartyChangeReason.PARTY_CHANGE_REASON_MEMBER_JOINED, "party.notice.member_joined",
            PartyChangeReason.PARTY_CHANGE_REASON_MEMBER_LEFT, "party.notice.member_left",
            PartyChangeReason.PARTY_CHANGE_REASON_MEMBER_KICKED, "party.notice.member_kicked",
            PartyChangeReason.PARTY_CHANGE_REASON_YOU_WERE_KICKED, "party.notice.you_were_kicked",
            PartyChangeReason.PARTY_CHANGE_REASON_LEADER_CHANGED, "party.notice.leader_changed");

    private static final Map<GuildChangeReason, String> GUILD_KEYS = Map.of(
            GuildChangeReason.GUILD_CHANGE_REASON_MEMBER_JOINED, "guild.notice.member_joined",
            GuildChangeReason.GUILD_CHANGE_REASON_MEMBER_LEFT, "guild.notice.member_left",
            GuildChangeReason.GUILD_CHANGE_REASON_MEMBER_KICKED, "guild.notice.member_kicked",
            GuildChangeReason.GUILD_CHANGE_REASON_YOU_WERE_KICKED, "guild.notice.you_were_kicked",
            GuildChangeReason.GUILD_CHANGE_REASON_DISBANDED, "guild.notice.your_guild_disbanded",
            GuildChangeReason.GUILD_CHANGE_REASON_YOU_WERE_ACCEPTED, "guild.notice.you_were_accepted");

    private final ChatCoordinator chat;
    private final PartyCoordinator party;
    private final GuildCoordinator guild;
    private final I18NBundle i18n = KageI18n.load();

    MembershipNoticeCoordinator(ChatCoordinator chat, PartyCoordinator party, GuildCoordinator guild) {
        this.chat = chat;
        this.party = party;
        this.guild = guild;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_PARTY_CHANGED_EVENT_VALUE) {
            say(PARTY_KEYS.get(PartyChangedEvent.parseFrom(frame.payload()).getReason()), "party.notice.changed");
            party.refresh();
            return true;
        }
        if (opcode == Opcode.OPCODE_GUILD_CHANGED_EVENT_VALUE) {
            say(GUILD_KEYS.get(GuildChangedEvent.parseFrom(frame.payload()).getReason()), "guild.notice.changed");
            guild.refresh();
            return true;
        }
        return false;
    }

    /** Lý do lạ (server mới hơn client) ⇒ câu chung, không im lặng. */
    private void say(String key, String fallbackKey) {
        chat.showSystemNotice(KageI18n.textOrKey(i18n, key == null ? fallbackKey : key));
    }
}
