package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.guild.api.GuildFacade;
import dev.kage.protocol.GuildInviteEvent;
import dev.kage.protocol.InviteToGuildCommand;
import dev.kage.protocol.InviteToGuildOutcome;
import dev.kage.protocol.InviteToGuildResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RespondGuildInviteCommand;
import dev.kage.protocol.RespondGuildInviteOutcome;
import dev.kage.protocol.RespondGuildInviteResponse;

/**
 * Mời thẳng vào bang (ADR-219). Bang chủ / phó bang chủ mời một người đang online theo tên; lời mời giữ trong RAM
 * {@value #TTL_MINUTES} phút (mất khi server khởi động lại — chỉ là lời mời). Đồng ý = đi đúng đường "xin vào + được
 * duyệt" sẵn có, nhân danh người mời: mọi luật cũ (đã ở bang khác, bang đầy, người mời còn quyền duyệt không) giữ
 * nguyên và báo cả bang như khi duyệt đơn.
 */
final class GuildInviteCommands {

    static final int TTL_MINUTES = 5;

    /** Nguồn dữ liệu. */
    record Deps(CharacterFacade characters, GuildFacade guilds, ConnectedCharacterRegistry registry, Clock clock) {}

    /** Một lời mời còn hiệu lực. */
    private record Invite(long inviterId, String guildName, Instant expiresAt) {}

    /**
     * Lời mời treo theo người được mời — DÙNG CHUNG cả server: lớp lệnh này dựng theo từng kết nối (handler), mà người
     * mời và người được mời là hai kết nối khác nhau. Khoá theo registry (một server = một registry; hai server trong
     * cùng JVM ở test không thấy lời mời của nhau), tham chiếu yếu như {@code LeaderboardCache}.
     */
    private static final Map<ConnectedCharacterRegistry, Map<Long, Invite>> BY_REGISTRY =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private final Deps deps;
    private final Map<Long, Invite> pendingByTarget;

    GuildInviteCommands(Deps deps) {
        this.deps = deps;
        this.pendingByTarget = BY_REGISTRY.computeIfAbsent(deps.registry(), registry -> new ConcurrentHashMap<>());
    }

    /** Virtual Thread; {@code inviterId} mời người tên {@code command.target_name}. */
    InviteToGuildResponse invite(long inviterId, InviteToGuildCommand command) {
        Optional<GuildFacade.MyGuildView> mine = deps.guilds().findMyGuild(inviterId);
        if (mine.isEmpty() || !(mine.get().isLeader() || mine.get().isViceLeader())) {
            return inviteOutcome(InviteToGuildOutcome.INVITE_TO_GUILD_OUTCOME_NOT_AUTHORIZED);
        }
        Optional<Character> target =
                deps.characters().findByName(command.getTargetName().strip());
        if (target.isEmpty() || !deps.registry().isOnline(target.get().id())) {
            return inviteOutcome(InviteToGuildOutcome.INVITE_TO_GUILD_OUTCOME_TARGET_NOT_FOUND);
        }
        long targetId = target.get().id();
        if (targetId == inviterId || deps.guilds().findMyGuild(targetId).isPresent()) {
            return inviteOutcome(InviteToGuildOutcome.INVITE_TO_GUILD_OUTCOME_TARGET_IN_GUILD);
        }
        String guildName = mine.get().guildName();
        pendingByTarget.put(
                targetId,
                new Invite(inviterId, guildName, deps.clock().instant().plus(Duration.ofMinutes(TTL_MINUTES))));
        String inviterName =
                deps.characters().findById(inviterId).map(Character::name).orElse("");
        deps.registry()
                .sendToCharacterIfOnline(
                        targetId,
                        Opcode.OPCODE_GUILD_INVITE_EVENT_VALUE,
                        0,
                        GuildInviteEvent.newBuilder()
                                .setGuildName(guildName)
                                .setInviterName(inviterName)
                                .build()
                                .toByteArray());
        return inviteOutcome(InviteToGuildOutcome.INVITE_TO_GUILD_OUTCOME_OK);
    }

    /** Virtual Thread; người được mời đồng ý / từ chối lời mời đang treo. */
    RespondGuildInviteResponse respond(long targetId, RespondGuildInviteCommand command) {
        Invite invite = pendingByTarget.remove(targetId);
        if (invite == null || invite.expiresAt().isBefore(deps.clock().instant())) {
            return respondOutcome(RespondGuildInviteOutcome.RESPOND_GUILD_INVITE_OUTCOME_NO_INVITE, "");
        }
        if (!command.getAccept()) {
            return respondOutcome(RespondGuildInviteOutcome.RESPOND_GUILD_INVITE_OUTCOME_DECLINED, invite.guildName());
        }
        return respondOutcome(join(targetId, invite), invite.guildName());
    }

    private RespondGuildInviteOutcome join(long targetId, Invite invite) {
        GuildFacade.SendJoinRequestResult sent = deps.guilds().sendJoinRequest(targetId, invite.guildName());
        if (sent == GuildFacade.SendJoinRequestResult.ALREADY_IN_GUILD) {
            return RespondGuildInviteOutcome.RESPOND_GUILD_INVITE_OUTCOME_ALREADY_IN_GUILD;
        }
        if (sent == GuildFacade.SendJoinRequestResult.GUILD_NOT_FOUND) {
            return RespondGuildInviteOutcome.RESPOND_GUILD_INVITE_OUTCOME_NO_INVITE;
        }
        Optional<Long> requestId = deps.guilds().listPendingJoinRequests(invite.inviterId()).stream()
                .filter(request -> request.characterId() == targetId)
                .map(GuildFacade.PendingJoinRequestView::requestId)
                .findFirst();
        if (requestId.isEmpty()) {
            return RespondGuildInviteOutcome.RESPOND_GUILD_INVITE_OUTCOME_NO_INVITE; // người mời mất quyền duyệt
        }
        java.util.List<Long> before = MembershipNotices.ids(
                deps.guilds().listMembers(invite.inviterId()), GuildFacade.MemberView::characterId);
        GuildFacade.ApproveResult approved = deps.guilds().approveJoinRequest(requestId.get(), invite.inviterId());
        return switch (approved) {
            case OK -> {
                new MembershipNotices(deps.registry())
                        .guildMembershipChanged(
                                invite.inviterId(),
                                before,
                                MembershipNotices.ids(
                                        deps.guilds().listMembers(invite.inviterId()),
                                        GuildFacade.MemberView::characterId));
                yield RespondGuildInviteOutcome.RESPOND_GUILD_INVITE_OUTCOME_JOINED;
            }
            case GUILD_FULL -> RespondGuildInviteOutcome.RESPOND_GUILD_INVITE_OUTCOME_GUILD_FULL;
            case TARGET_ALREADY_IN_GUILD -> RespondGuildInviteOutcome.RESPOND_GUILD_INVITE_OUTCOME_ALREADY_IN_GUILD;
            case NOT_FOUND -> RespondGuildInviteOutcome.RESPOND_GUILD_INVITE_OUTCOME_NO_INVITE;
        };
    }

    private static InviteToGuildResponse inviteOutcome(InviteToGuildOutcome outcome) {
        return InviteToGuildResponse.newBuilder().setOutcome(outcome).build();
    }

    private static RespondGuildInviteResponse respondOutcome(RespondGuildInviteOutcome outcome, String guildName) {
        return RespondGuildInviteResponse.newBuilder()
                .setOutcome(outcome)
                .setGuildName(guildName)
                .build();
    }
}
