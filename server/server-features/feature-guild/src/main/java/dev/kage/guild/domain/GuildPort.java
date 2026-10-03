package dev.kage.guild.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port — DB-agnostic (Điều 23). Implementation thật ({@code PostgresGuildAdapter}) nằm ở
 * server-database. {@link #createGuild} và {@link #approveJoinRequest} chạm 2 bảng nguyên tử
 * (transaction thủ công trong adapter) — không cần {@code TransactionRunner} riêng vì cả 2 bảng
 * (guild/guild_member, guild_join_request/guild_member) đều thuộc feature-guild, không xuyên
 * feature khác (khác feature-shop/feature-quest cần TransactionRunner do gọi ngược server-world).
 */
public interface GuildPort {

    Optional<Guild> findByName(String name);

    Optional<Guild> findById(long guildId);

    /** Bang mà {@code characterId} đang là thành viên (bất kỳ role) — rỗng nếu chưa vào bang nào. */
    Optional<GuildMember> findMembershipOf(long characterId);

    List<GuildMember> listMembers(long guildId);

    /**
     * Tạo bang mới + thêm {@code leaderCharacterId} làm {@link GuildRole#LEADER}, nguyên tử.
     *
     * @throws DuplicateGuildNameException nếu vi phạm UNIQUE {@code name}
     * @throws DuplicateGuildMembershipException nếu vi phạm UNIQUE {@code guild_member.character_id}
     *     — {@code leaderCharacterId} vừa vào bang khác (race với 1 lệnh tạo/duyệt bang khác của
     *     CHÍNH nhân vật này, chạy gần như đồng thời trên virtual thread khác)
     */
    Guild createGuild(String name, long leaderCharacterId);

    /** Giải tán bang do {@code leaderCharacterId} làm leader — CASCADE xoá member + join request. */
    boolean disbandGuild(long leaderCharacterId);

    /** Rời bang — chỉ áp dụng {@link GuildRole#MEMBER} thường (leader phải {@link #disbandGuild}). */
    boolean leaveGuild(long characterId);

    /** Leader kick 1 thành viên MEMBER khác cùng bang (không tự kick chính mình). */
    boolean kickMember(long leaderCharacterId, long targetCharacterId);

    Optional<GuildJoinRequest> findPendingRequestOfCharacter(long characterId);

    List<GuildJoinRequest> listPendingRequests(long guildId);

    /**
     * Ghi đơn xin gia nhập PENDING.
     *
     * @throws DuplicateGuildJoinRequestException nếu vi phạm UNIQUE {@code (guild_id, character_id)}
     * @throws GuildDisbandedException nếu bang đã bị giải tán (vừa xảy ra, sau lần tra bang của caller)
     */
    GuildJoinRequest sendJoinRequest(long guildId, long characterId);

    /** Duyệt đơn — nguyên tử: thêm {@link GuildMember} + xoá {@link GuildJoinRequest}. */
    ApproveResult approveJoinRequest(long requestId, long leaderCharacterId);

    /** Từ chối — xoá dòng PENDING. {@code false} nếu không tồn tại HOẶC người gọi không phải leader của bang đó. */
    boolean rejectJoinRequest(long requestId, long leaderCharacterId);

    enum ApproveResult {
        OK,
        /** Không tìm thấy đơn, HOẶC {@code leaderCharacterId} không phải leader của bang đơn đó nhắm tới. */
        NOT_FOUND,
        /**
         * Người xin đã vào bang KHÁC giữa lúc gửi đơn và lúc được duyệt (VD tự tạo bang mới) —
         * đơn coi như hết hạn, adapter tự xoá dòng join_request thay vì để lại rác.
         */
        TARGET_ALREADY_IN_GUILD
    }
}
