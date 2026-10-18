package dev.kage.server.gateway;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongConsumer;

import dev.kage.protocol.GuildChangeReason;
import dev.kage.protocol.GuildChangedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PartyChangeReason;
import dev.kage.protocol.PartyChangedEvent;

/**
 * ADR-059 — báo "nhóm/bang của bạn vừa đổi" cho thành viên ONLINE bị ảnh hưởng, SAU khi một lệnh đổi
 * thành viên thành công. Người ra lệnh không được báo (họ đã có Response). Offline thì bỏ qua: lần vào
 * world sau client tự hỏi lại.
 *
 * <p>Danh sách người nhận do gateway lấy TRƯỚC lệnh (rời/kick/giải tán) hoặc SAU lệnh (vào) — lớp này
 * chỉ tính hiệu hai tập và gửi. Chạy trên Virtual Thread (cùng chỗ với lệnh), không chạm DB.
 */
final class MembershipNotices {

    private final ConnectedCharacterRegistry registry;

    MembershipNotices(ConnectedCharacterRegistry registry) {
        this.registry = registry;
    }

    /** {@code before}/{@code after}: thành viên trước/sau lệnh. Ai mới vào được {@code joinedReason}. */
    void partyMembershipChanged(long actorId, List<Long> before, List<Long> after) {
        notifyDiff(actorId, before, after, Domain.PARTY);
    }

    void guildMembershipChanged(long actorId, List<Long> before, List<Long> after) {
        notifyDiff(actorId, before, after, Domain.GUILD);
    }

    /** Kick: người bị kick nhận YOU_WERE_KICKED, mọi người khác (trừ người kick) nhận MEMBER_KICKED. */
    void partyKick(long actorId, long targetId, List<Long> before) {
        sendParty(targetId, PartyChangeReason.PARTY_CHANGE_REASON_YOU_WERE_KICKED, targetId);
        for (long member : before) {
            if (member != actorId && member != targetId) {
                sendParty(member, PartyChangeReason.PARTY_CHANGE_REASON_MEMBER_KICKED, targetId);
            }
        }
    }

    void guildKick(long actorId, long targetId, List<Long> before) {
        sendGuild(targetId, GuildChangeReason.GUILD_CHANGE_REASON_YOU_WERE_KICKED, targetId);
        for (long member : before) {
            if (member != actorId && member != targetId) {
                sendGuild(member, GuildChangeReason.GUILD_CHANGE_REASON_MEMBER_KICKED, targetId);
            }
        }
    }

    void guildDisbanded(long actorId, List<Long> before) {
        for (long member : before) {
            if (member != actorId) {
                sendGuild(member, GuildChangeReason.GUILD_CHANGE_REASON_DISBANDED, actorId);
            }
        }
    }

    /** Trưởng nhóm cũ rớt mạng, {@code newLeaderId} lên thay (dòng 40(i)) — mọi người online được báo. */
    void partyLeaderChanged(long newLeaderId, List<Long> members) {
        for (long member : members) {
            sendParty(member, PartyChangeReason.PARTY_CHANGE_REASON_LEADER_CHANGED, newLeaderId);
        }
    }

    private enum Domain {
        PARTY,
        GUILD
    }

    private void notifyDiff(long actorId, List<Long> before, List<Long> after, Domain domain) {
        Set<Long> beforeSet = new HashSet<>(before);
        Set<Long> afterSet = new HashSet<>(after);
        for (long joined : difference(afterSet, beforeSet)) {
            if (domain == Domain.GUILD && joined != actorId) {
                sendGuild(joined, GuildChangeReason.GUILD_CHANGE_REASON_YOU_WERE_ACCEPTED, joined);
            }
            tellOthers(
                    actorId,
                    joined,
                    afterSet,
                    domain == Domain.PARTY
                            ? member -> sendParty(member, PartyChangeReason.PARTY_CHANGE_REASON_MEMBER_JOINED, joined)
                            : member -> sendGuild(member, GuildChangeReason.GUILD_CHANGE_REASON_MEMBER_JOINED, joined));
        }
        for (long left : difference(beforeSet, afterSet)) {
            tellOthers(
                    actorId,
                    left,
                    afterSet,
                    domain == Domain.PARTY
                            ? member -> sendParty(member, PartyChangeReason.PARTY_CHANGE_REASON_MEMBER_LEFT, left)
                            : member -> sendGuild(member, GuildChangeReason.GUILD_CHANGE_REASON_MEMBER_LEFT, left));
        }
    }

    /** Gửi cho mọi người trong {@code current} trừ người ra lệnh và chính người liên quan. */
    private static void tellOthers(long actorId, long subject, Set<Long> current, LongConsumer send) {
        for (long member : current) {
            if (member != actorId && member != subject) {
                send.accept(member);
            }
        }
    }

    private static Set<Long> difference(Set<Long> a, Set<Long> b) {
        Set<Long> out = new HashSet<>(a);
        out.removeAll(b);
        return out;
    }

    private void sendParty(long recipient, PartyChangeReason reason, long subject) {
        PartyChangedEvent event = PartyChangedEvent.newBuilder()
                .setReason(reason)
                .setSubjectCharacterId(subject)
                .build();
        registry.sendToCharacterIfOnline(recipient, Opcode.OPCODE_PARTY_CHANGED_EVENT_VALUE, 0, event.toByteArray());
    }

    private void sendGuild(long recipient, GuildChangeReason reason, long subject) {
        GuildChangedEvent event = GuildChangedEvent.newBuilder()
                .setReason(reason)
                .setSubjectCharacterId(subject)
                .build();
        registry.sendToCharacterIfOnline(recipient, Opcode.OPCODE_GUILD_CHANGED_EVENT_VALUE, 0, event.toByteArray());
    }

    /** {@code ids} bỏ {@code excluded}. */
    static List<Long> without(List<Long> ids, long excluded) {
        return ids.stream().filter(id -> id != excluded).toList();
    }

    /** Id thành viên từ một danh sách view bất kỳ. */
    static <T> List<Long> ids(List<T> views, Function<T, Long> id) {
        return views.stream().map(id).toList();
    }
}
