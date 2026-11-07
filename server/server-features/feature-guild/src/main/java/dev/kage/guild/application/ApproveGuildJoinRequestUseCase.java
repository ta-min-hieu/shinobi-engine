package dev.kage.guild.application;

import java.util.Optional;

import dev.kage.guild.domain.GuildLevelTable;
import dev.kage.guild.domain.GuildMember;
import dev.kage.guild.domain.GuildPort;

/** Leader duyệt đơn xin gia nhập — thêm thành viên MỚI + xoá đơn, nguyên tử ở {@link GuildPort}. */
public final class ApproveGuildJoinRequestUseCase {

    private final GuildPort port;

    public ApproveGuildJoinRequestUseCase(GuildPort port) {
        this.port = port;
    }

    /**
     * Số thành viên tối đa của một bang CẤP 1, tính cả bang chủ (quyết định chủ dự án 2026-10-11). Từ
     * ADR-068 trần tăng theo cấp bang ({@link GuildLevelTable#maxMembers}). Kiểm ở đây, trước khi chạm đơn:
     * chỉ bang chủ duyệt được và họ duyệt từng đơn một, nên "đếm rồi mới ghi" không có ai để đua cùng ngoài
     * chính họ bấm hai lần.
     */
    public static final int MAX_MEMBERS = GuildLevelTable.BASE_MAX_MEMBERS;

    public Result execute(long requestId, long leaderCharacterId) {
        Optional<GuildMember> leader = port.findMembershipOf(leaderCharacterId);
        if (leader.isPresent()
                && leader.get().role().canManageMembership() // ADR-184 — phó bang chủ cũng duyệt
                && port.listMembers(leader.get().guildId()).size()
                        >= GuildLevelTable.maxMembers(port.levelOf(leader.get().guildId()))) {
            return Result.GUILD_FULL;
        }
        return switch (port.approveJoinRequest(requestId, leaderCharacterId)) {
            case OK -> Result.OK;
            case NOT_FOUND -> Result.NOT_FOUND;
            case TARGET_ALREADY_IN_GUILD -> Result.TARGET_ALREADY_IN_GUILD;
        };
    }

    public enum Result {
        OK,
        NOT_FOUND,
        TARGET_ALREADY_IN_GUILD,
        /** Bang đã đủ người theo cấp bang — đơn còn nguyên, duyệt lại được khi có chỗ. */
        GUILD_FULL
    }
}
