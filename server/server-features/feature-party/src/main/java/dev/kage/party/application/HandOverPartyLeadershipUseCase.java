package dev.kage.party.application;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.LongPredicate;

import dev.kage.party.domain.PartyMember;
import dev.kage.party.domain.PartyPort;
import dev.kage.party.domain.PartyRole;

/**
 * Trưởng nhóm vừa mất kết nối — trao quyền cho thành viên vào nhóm SỚM NHẤT đang online (quyết định
 * chủ dự án 2026-10-11).
 *
 * <p>Trước đó trưởng nhóm rớt mạng là cả nhóm kẹt: chỉ trưởng nhóm mời và kick được, không ai kick
 * được trưởng nhóm, và hầm ngục đòi cả nhóm online — nên các thành viên còn lại không vào được hầm
 * ngục mà cũng không gỡ được người vắng mặt, chỉ còn cách cùng rời nhóm.
 *
 * <p>Người cũ VẪN ở trong nhóm với vai thành viên: quay lại thì vẫn là đồng đội, và trưởng nhóm mới
 * kick được họ nếu họ không quay lại. "Ai đang online" là hiểu biết của gateway, nên đi vào bằng một
 * predicate — module này không biết kết nối là gì.
 */
public final class HandOverPartyLeadershipUseCase {

    private final PartyPort port;

    public HandOverPartyLeadershipUseCase(PartyPort port) {
        this.port = port;
    }

    /**
     * Chuyển quyền trưởng nhóm cho thành viên vào sớm nhất đang online.
     *
     * @param isOnline nhân vật nào đang có kết nối
     * @return trưởng nhóm MỚI; rỗng nếu không có gì đổi — người gọi không phải trưởng nhóm, hoặc không
     *     thành viên nào khác đang online (khi đó nhóm giữ nguyên, chờ trưởng nhóm quay lại)
     */
    public Optional<Long> execute(long leaderCharacterId, LongPredicate isOnline) {
        Optional<PartyMember> membership = port.findMembershipOf(leaderCharacterId);
        if (membership.isEmpty() || membership.get().role() != PartyRole.LEADER) {
            return Optional.empty();
        }
        List<PartyMember> heirs = port.listMembers(membership.get().partyId()).stream()
                .filter(member -> member.characterId() != leaderCharacterId)
                .filter(member -> isOnline.test(member.characterId()))
                .sorted(Comparator.comparing(PartyMember::joinedAt).thenComparingLong(PartyMember::characterId))
                .toList();
        for (PartyMember heir : heirs) {
            // false = người này vừa rời/bị kick giữa lúc đọc và lúc ghi — thử người kế.
            if (port.transferLeadership(leaderCharacterId, heir.characterId())) {
                return Optional.of(heir.characterId());
            }
        }
        return Optional.empty();
    }

    /**
     * ADR-191 — trưởng nhóm tự nhường quyền cho {@code targetCharacterId} (cùng nhóm) mà không rời nhóm.
     *
     * @return kết quả; {@link Transfer#OK} nghĩa là đã ghi xong
     */
    public Transfer transferTo(long leaderCharacterId, long targetCharacterId) {
        Optional<PartyMember> membership = port.findMembershipOf(leaderCharacterId);
        if (membership.isEmpty() || membership.get().role() != PartyRole.LEADER) {
            return Transfer.NOT_LEADER;
        }
        if (leaderCharacterId == targetCharacterId) {
            return Transfer.CANNOT_TRANSFER_TO_SELF;
        }
        Optional<PartyMember> target = port.findMembershipOf(targetCharacterId);
        if (target.isEmpty() || target.get().partyId() != membership.get().partyId()) {
            return Transfer.TARGET_NOT_IN_PARTY;
        }
        // false = một trong hai vừa rời / bị kick / mất quyền giữa lúc đọc và lúc ghi.
        return port.transferLeadership(leaderCharacterId, targetCharacterId)
                ? Transfer.OK
                : Transfer.TARGET_NOT_IN_PARTY;
    }

    /** Kết quả {@link #transferTo}. */
    public enum Transfer {
        OK,
        NOT_LEADER,
        TARGET_NOT_IN_PARTY,
        CANNOT_TRANSFER_TO_SELF
    }
}
