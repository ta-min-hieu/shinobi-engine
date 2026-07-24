package dev.kage.party.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port — DB-agnostic (Điều 23). Implementation thật ({@code PostgresPartyAdapter}) nằm ở
 * server-database. {@link #createParty} và {@link #acceptInvite} chạm nhiều bảng nguyên tử
 * (transaction thủ công trong adapter, khoá dòng {@code party} FOR UPDATE để chống race vượt
 * {@link Party#MAX_MEMBERS} khi nhiều lời mời được chấp nhận đồng thời) — cùng lý do
 * {@code GuildPort} không cần {@code TransactionRunner} riêng.
 */
public interface PartyPort {

    /** Party mà {@code characterId} đang là thành viên (bất kỳ role) — rỗng nếu chưa vào party nào. */
    Optional<PartyMember> findMembershipOf(long characterId);

    List<PartyMember> listMembers(long partyId);

    /** Tạo party mới + thêm {@code leaderCharacterId} làm {@link PartyRole#LEADER}, nguyên tử. */
    Party createParty(long leaderCharacterId);

    /**
     * Lời mời đang chờ CHÍNH {@code characterId} (vai trò invitee) chấp nhận/từ chối — global,
     * không giới hạn 1 party.
     */
    Optional<PartyInvite> findPendingInviteOfInvitee(long characterId);

    List<PartyInvite> listPendingInvitesOfInvitee(long characterId);

    /**
     * @throws DuplicatePartyInviteException nếu vi phạm UNIQUE {@code (party_id, invitee_character_id)}
     */
    PartyInvite invite(long partyId, long inviteeCharacterId);

    /**
     * Chấp nhận — nguyên tử: khoá dòng {@code party}, kiểm sức chứa + đã-ở-party-khác, rồi thêm
     * thành viên + xoá lời mời.
     */
    AcceptResult acceptInvite(long inviteId, long characterId);

    /**
     * Từ chối — xoá dòng PENDING. {@code false} nếu không tồn tại HOẶC {@code characterId} không
     * phải invitee của lời mời đó.
     */
    boolean rejectInvite(long inviteId, long characterId);

    /**
     * Rời party — xử lý CẢ 2 trường hợp nguyên tử trong adapter:
     * MEMBER thường rời → xoá dòng; LEADER rời → tự động thăng thành viên vào SỚM NHẤT còn lại
     * làm leader mới (nếu còn ai), hoặc xoá luôn party nếu đây là người cuối cùng.
     */
    LeaveResult leaveParty(long characterId);

    /** Leader kick 1 thành viên MEMBER khác cùng party (không tự kick chính mình). */
    boolean kickMember(long leaderCharacterId, long targetCharacterId);

    enum AcceptResult {
        OK,
        /** Không tìm thấy lời mời, HOẶC {@code characterId} không phải invitee của lời mời đó. */
        NOT_FOUND,
        /** Invitee đã vào party KHÁC giữa lúc được mời và lúc chấp nhận — lời mời hết hạn, bị xoá. */
        ALREADY_IN_PARTY,
        /** Party đã đủ {@link Party#MAX_MEMBERS} do lời mời khác được chấp nhận trước — lời mời bị xoá. */
        PARTY_FULL
    }

    enum LeaveResult {
        OK,
        NOT_IN_PARTY
    }
}
