package dev.kage.guild.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import dev.kage.guild.domain.DuplicateGuildJoinRequestException;
import dev.kage.guild.domain.DuplicateGuildNameException;
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildJoinRequest;
import dev.kage.guild.domain.GuildMember;
import dev.kage.guild.domain.GuildPort;
import dev.kage.guild.domain.GuildRole;

/**
 * Fake in-memory THẬT (TESTING.md §4), không Mockito — DÙNG CHUNG cho toàn bộ test
 * {@code application/} thay vì lặp lại 1 nested Fake/file (khác feature-friend, xem
 * feature-guild/CLAUDE.md mục "Quyết định MVP" — {@link GuildPort} có 13 method, lặp lại quá
 * nhiều lần mới là trùng lặp thật). Logic mô phỏng đúng ràng buộc adapter Postgres thật: UNIQUE
 * tên bang, UNIQUE (guild_id, character_id) đơn xin gia nhập, UNIQUE character_id thành viên.
 */
final class FakeGuildPort implements GuildPort {

    private final Map<Long, Guild> guildsById = new LinkedHashMap<>();
    private final List<GuildMember> members = new ArrayList<>();
    private final List<GuildJoinRequest> joinRequests = new ArrayList<>();
    private final AtomicLong nextGuildId = new AtomicLong(1);
    private final AtomicLong nextRequestId = new AtomicLong(1);

    @Override
    public Optional<Guild> findByName(String name) {
        return guildsById.values().stream().filter(g -> g.name().equals(name)).findFirst();
    }

    @Override
    public Optional<Guild> findById(long guildId) {
        return Optional.ofNullable(guildsById.get(guildId));
    }

    @Override
    public Optional<GuildMember> findMembershipOf(long characterId) {
        return members.stream().filter(m -> m.characterId() == characterId).findFirst();
    }

    @Override
    public List<GuildMember> listMembers(long guildId) {
        return members.stream().filter(m -> m.guildId() == guildId).toList();
    }

    @Override
    public Guild createGuild(String name, long leaderCharacterId) {
        if (findByName(name).isPresent()) {
            throw new DuplicateGuildNameException(name);
        }
        Guild guild = new Guild(nextGuildId.getAndIncrement(), name, leaderCharacterId, Instant.now());
        guildsById.put(guild.id(), guild);
        members.add(new GuildMember(guild.id(), leaderCharacterId, GuildRole.LEADER, Instant.now()));
        return guild;
    }

    @Override
    public boolean disbandGuild(long leaderCharacterId) {
        Optional<Guild> guild = guildsById.values().stream()
                .filter(g -> g.leaderCharacterId() == leaderCharacterId)
                .findFirst();
        if (guild.isEmpty()) {
            return false;
        }
        long guildId = guild.get().id();
        guildsById.remove(guildId);
        members.removeIf(m -> m.guildId() == guildId);
        joinRequests.removeIf(r -> r.guildId() == guildId);
        return true;
    }

    @Override
    public boolean leaveGuild(long characterId) {
        return members.removeIf(m -> m.characterId() == characterId && m.role() == GuildRole.MEMBER);
    }

    @Override
    public boolean kickMember(long leaderCharacterId, long targetCharacterId) {
        Optional<GuildMember> leaderMembership = members.stream()
                .filter(m -> m.characterId() == leaderCharacterId && m.role() == GuildRole.LEADER)
                .findFirst();
        if (leaderMembership.isEmpty()) {
            return false;
        }
        long guildId = leaderMembership.get().guildId();
        return members.removeIf(
                m -> m.guildId() == guildId && m.characterId() == targetCharacterId && m.role() == GuildRole.MEMBER);
    }

    @Override
    public Optional<GuildJoinRequest> findPendingRequestOfCharacter(long characterId) {
        return joinRequests.stream().filter(r -> r.characterId() == characterId).findFirst();
    }

    @Override
    public List<GuildJoinRequest> listPendingRequests(long guildId) {
        return joinRequests.stream().filter(r -> r.guildId() == guildId).toList();
    }

    @Override
    public GuildJoinRequest sendJoinRequest(long guildId, long characterId) {
        if (joinRequests.stream().anyMatch(r -> r.guildId() == guildId && r.characterId() == characterId)) {
            throw new DuplicateGuildJoinRequestException(guildId, characterId);
        }
        GuildJoinRequest request =
                new GuildJoinRequest(nextRequestId.getAndIncrement(), guildId, characterId, Instant.now());
        joinRequests.add(request);
        return request;
    }

    @Override
    public ApproveResult approveJoinRequest(long requestId, long leaderCharacterId) {
        Optional<GuildJoinRequest> request =
                joinRequests.stream().filter(r -> r.id() == requestId).findFirst();
        if (request.isEmpty()) {
            return ApproveResult.NOT_FOUND;
        }
        Guild guild = guildsById.get(request.get().guildId());
        if (guild == null || guild.leaderCharacterId() != leaderCharacterId) {
            return ApproveResult.NOT_FOUND;
        }
        if (findMembershipOf(request.get().characterId()).isPresent()) {
            joinRequests.remove(request.get());
            return ApproveResult.TARGET_ALREADY_IN_GUILD;
        }
        members.add(
                new GuildMember(request.get().guildId(), request.get().characterId(), GuildRole.MEMBER, Instant.now()));
        joinRequests.remove(request.get());
        return ApproveResult.OK;
    }

    @Override
    public boolean rejectJoinRequest(long requestId, long leaderCharacterId) {
        Optional<GuildJoinRequest> request =
                joinRequests.stream().filter(r -> r.id() == requestId).findFirst();
        if (request.isEmpty()) {
            return false;
        }
        Guild guild = guildsById.get(request.get().guildId());
        if (guild == null || guild.leaderCharacterId() != leaderCharacterId) {
            return false;
        }
        joinRequests.remove(request.get());
        return true;
    }
}
