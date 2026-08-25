package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.kage.protocol.AcceptPartyInviteOutcome;
import dev.kage.protocol.CreatePartyOutcome;
import dev.kage.protocol.GetMyPartyResponse;
import dev.kage.protocol.InvitePartyMemberOutcome;
import dev.kage.protocol.KickPartyMemberOutcome;
import dev.kage.protocol.LeavePartyOutcome;
import dev.kage.protocol.ListPartyMembersResponse;
import dev.kage.protocol.ListPendingPartyInvitesResponse;
import dev.kage.protocol.PartyMemberView;
import dev.kage.protocol.PendingPartyInviteView;
import dev.kage.protocol.RejectPartyInviteOutcome;

/**
 * Trạng thái nhóm của chính người chơi (2026-08-25) — thuần Java, không libGDX/GL, test được thẳng
 * (UI.md §4). Cùng hình dạng {@link ChatController}/{@link DialogueController}.
 *
 * <p><b>Toàn bộ trạng thái ở đây là ẢNH CHỤP, không phải nguồn chân lý.</b> party.proto (dải
 * 1300-1399) KHÔNG có một event đẩy nào — khác hẳn Trade ({@code TRADE_REQUESTED_EVENT}) hay Friend
 * ({@code FRIEND_ONLINE_EVENT}). Hệ quả thật, không phải chi tiết cài đặt:
 *
 * <ul>
 *   <li>Bị mời vào nhóm thì <b>không có gì báo</b> — chỉ biết khi tự hỏi
 *       ({@code ListPendingPartyInvitesRequest}).
 *   <li>Bị kick, hoặc leader rời nhóm, thì màn hình vẫn hiện nhóm cũ cho tới lần hỏi tiếp theo.
 * </ul>
 *
 * <p>Vì vậy {@link PartyCoordinator} hỏi lại server sau MỌI lệnh đổi trạng thái, và không lớp nào
 * được phép tự suy ra trạng thái mới từ việc "lệnh vừa trả OK" (Điều 15). Khoảng trống event đẩy này
 * cần chủ dự án quyết — thêm opcode là đổi protocol (§4).
 */
final class PartyController {

    /** Một thành viên. {@code online} do gateway ghép từ registry, không phải dữ liệu feature-party. */
    record Member(long characterId, String name, boolean leader, boolean online) {}

    /** Một lời mời đang chờ mình trả lời. */
    record Invite(long inviteId, long partyId) {}

    private boolean inParty;

    private boolean leader;

    private final List<Member> members = new ArrayList<>();

    private final List<Invite> invites = new ArrayList<>();

    /** Trạng thái nhóm của chính mình ({@code GetMyPartyResponse}). */
    void onMyParty(GetMyPartyResponse response) {
        inParty = response.getInParty();
        leader = response.getInParty() && response.getIsLeader();
        if (!inParty) {
            // Không còn trong nhóm nào ⇒ danh sách thành viên cũ là rác. Không dọn thì màn hình vẫn
            // hiện đủ tên sau khi vừa bị kick, vì server không đẩy gì để đính chính.
            members.clear();
        }
    }

    void onMembers(ListPartyMembersResponse response) {
        members.clear();
        for (PartyMemberView view : response.getMembersList()) {
            // sanitize: tên do NGƯỜI KHÁC đặt, cùng lý do ChatController làm với tin nhắn đến.
            members.add(new Member(
                    view.getCharacterId(),
                    ChatController.sanitize(view.getName()),
                    view.getIsLeader(),
                    view.getOnline()));
        }
    }

    void onInvites(ListPendingPartyInvitesResponse response) {
        invites.clear();
        for (PendingPartyInviteView view : response.getInvitesList()) {
            invites.add(new Invite(view.getInviteId(), view.getPartyId()));
        }
    }

    boolean inParty() {
        return inParty;
    }

    boolean isLeader() {
        return leader;
    }

    List<Member> members() {
        return List.copyOf(members);
    }

    List<Invite> invites() {
        return List.copyOf(invites);
    }

    /** Rời màn hình/mất kết nối — ảnh chụp của một phiên chơi không được sống sót sang phiên sau. */
    void clear() {
        inParty = false;
        leader = false;
        members.clear();
        invites.clear();
    }

    // --- Kết quả từng lệnh -> khoá i18n -------------------------------------------------------
    // Bảng tra chứ không phải switch phủ hết enum: một switch như thế tự nó vượt trần
    // CyclomaticComplexity của PMD (đã dính thật ở ChatController.outcomeMessageKey). Giá trị OK cố
    // tình VẮNG MẶT khỏi mọi bảng — nó là nhánh Optional.empty() ở hàm tra, không phải một lời nhắn.

    private static final Map<CreatePartyOutcome, String> CREATE_KEYS =
            Map.of(CreatePartyOutcome.CREATE_PARTY_OUTCOME_ALREADY_IN_PARTY, "party.error.already_in_party");

    private static final Map<InvitePartyMemberOutcome, String> INVITE_KEYS = Map.of(
            InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_NOT_LEADER, "party.error.not_leader",
            InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_CANNOT_INVITE_SELF, "party.error.cannot_invite_self",
            InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_TARGET_ALREADY_IN_PARTY,
                    "party.error.target_already_in_party",
            InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_PARTY_FULL, "party.error.party_full",
            InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_INVITE_ALREADY_PENDING,
                    "party.error.invite_already_pending",
            InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_TARGET_NOT_FOUND, "party.error.target_not_found");

    private static final Map<AcceptPartyInviteOutcome, String> ACCEPT_KEYS = Map.of(
            AcceptPartyInviteOutcome.ACCEPT_PARTY_INVITE_OUTCOME_NOT_FOUND, "party.error.invite_gone",
            AcceptPartyInviteOutcome.ACCEPT_PARTY_INVITE_OUTCOME_ALREADY_IN_PARTY, "party.error.already_in_party",
            AcceptPartyInviteOutcome.ACCEPT_PARTY_INVITE_OUTCOME_PARTY_FULL, "party.error.party_full");

    private static final Map<RejectPartyInviteOutcome, String> REJECT_KEYS =
            Map.of(RejectPartyInviteOutcome.REJECT_PARTY_INVITE_OUTCOME_NOT_FOUND, "party.error.invite_gone");

    private static final Map<LeavePartyOutcome, String> LEAVE_KEYS =
            Map.of(LeavePartyOutcome.LEAVE_PARTY_OUTCOME_NOT_IN_PARTY, "party.error.not_in_party");

    private static final Map<KickPartyMemberOutcome, String> KICK_KEYS = Map.of(
            KickPartyMemberOutcome.KICK_PARTY_MEMBER_OUTCOME_CANNOT_KICK_SELF, "party.error.cannot_kick_self",
            KickPartyMemberOutcome.KICK_PARTY_MEMBER_OUTCOME_TARGET_NOT_FOUND, "party.error.target_not_found",
            // party.proto: NOT_FOUND ở lệnh kick gộp HAI chuyện khác nhau (không phải leader, HOẶC
            // target không cùng nhóm) — không tách được ở client, nên nói cả hai khả năng.
            KickPartyMemberOutcome.KICK_PARTY_MEMBER_OUTCOME_NOT_FOUND, "party.error.kick_not_allowed");

    /** Khoá i18n giải thích kết quả, hoặc rỗng khi lệnh thành công (không có gì để nói thêm). */
    static Optional<String> createOutcomeKey(CreatePartyOutcome outcome) {
        return lookup(outcome, outcome == CreatePartyOutcome.CREATE_PARTY_OUTCOME_OK, CREATE_KEYS);
    }

    static Optional<String> inviteOutcomeKey(InvitePartyMemberOutcome outcome) {
        return lookup(outcome, outcome == InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_OK, INVITE_KEYS);
    }

    static Optional<String> acceptOutcomeKey(AcceptPartyInviteOutcome outcome) {
        return lookup(outcome, outcome == AcceptPartyInviteOutcome.ACCEPT_PARTY_INVITE_OUTCOME_OK, ACCEPT_KEYS);
    }

    static Optional<String> rejectOutcomeKey(RejectPartyInviteOutcome outcome) {
        return lookup(outcome, outcome == RejectPartyInviteOutcome.REJECT_PARTY_INVITE_OUTCOME_OK, REJECT_KEYS);
    }

    static Optional<String> leaveOutcomeKey(LeavePartyOutcome outcome) {
        return lookup(outcome, outcome == LeavePartyOutcome.LEAVE_PARTY_OUTCOME_OK, LEAVE_KEYS);
    }

    static Optional<String> kickOutcomeKey(KickPartyMemberOutcome outcome) {
        return lookup(outcome, outcome == KickPartyMemberOutcome.KICK_PARTY_MEMBER_OUTCOME_OK, KICK_KEYS);
    }

    /**
     * @param succeeded lệnh đã thành công — không có gì để nói thêm
     * @return khoá đã tra, hoặc {@code party.error.unknown} cho {@code UNSPECIFIED}/{@code UNRECOGNIZED}
     *     (giá trị của một server mới hơn client — không liệt kê trước được, chỉ nói ra là không hiểu)
     */
    private static <T> Optional<String> lookup(T outcome, boolean succeeded, Map<T, String> keys) {
        if (succeeded) {
            return Optional.empty();
        }
        return Optional.of(keys.getOrDefault(outcome, "party.error.unknown"));
    }
}
