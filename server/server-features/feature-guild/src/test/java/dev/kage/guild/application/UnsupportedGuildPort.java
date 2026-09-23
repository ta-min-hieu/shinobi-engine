package dev.kage.guild.application;

import java.util.List;
import java.util.Optional;

import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildJoinRequest;
import dev.kage.guild.domain.GuildMember;
import dev.kage.guild.domain.GuildPort;

/**
 * {@link GuildPort} nền cho test 1 UseCase cụ thể chỉ cần mô phỏng ĐÚNG 1-2 method mà UseCase đó
 * gọi tới (VD kịch bản race chỉ chạm {@code findMembershipOf}/{@code createGuild} hoặc
 * {@code findMembershipOf}/{@code leaveGuild}) — override đúng method cần, 11 method còn lại thừa
 * kế mặc định "chưa từng được gọi tới" ở đây thay vì lặp lại {@code throw
 * UnsupportedOperationException} 11 lần/file. Khác {@link FakeGuildPort} (mô phỏng ĐẦY ĐỦ ngữ
 * nghĩa DB dùng chung cho test hạnh phúc) — lớp này CỐ Ý không mô phỏng gì, chỉ dựng riêng cho 1
 * kịch bản race mà state nội bộ nhất quán của {@link FakeGuildPort} không thể ép ra được (VD
 * {@code findMembershipOf} phải thấy CÒN trong khi {@code leaveGuild} phải trả {@code false}).
 */
abstract class UnsupportedGuildPort implements GuildPort {

    @Override
    public Optional<Guild> findByName(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Guild> findById(long guildId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<GuildMember> findMembershipOf(long characterId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<GuildMember> listMembers(long guildId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Guild createGuild(String name, long leaderCharacterId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean disbandGuild(long leaderCharacterId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean leaveGuild(long characterId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean kickMember(long leaderCharacterId, long targetCharacterId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<GuildJoinRequest> findPendingRequestOfCharacter(long characterId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<GuildJoinRequest> listPendingRequests(long guildId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GuildJoinRequest sendJoinRequest(long guildId, long characterId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ApproveResult approveJoinRequest(long requestId, long leaderCharacterId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean rejectJoinRequest(long requestId, long leaderCharacterId) {
        throw new UnsupportedOperationException();
    }
}
