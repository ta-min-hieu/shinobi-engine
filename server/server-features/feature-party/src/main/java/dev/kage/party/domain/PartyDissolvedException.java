package dev.kage.party.domain;

/**
 * Ném khi adapter ghi một dòng tham chiếu tới party đã bị giải tán — leader cuối cùng rời nhóm
 * giữa lúc use case kiểm tra quyền và lúc ghi (vi phạm FOREIGN KEY {@code party_id}).
 */
public final class PartyDissolvedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PartyDissolvedException(long partyId) {
        super("Party đã bị giải tán: partyId=" + partyId);
    }
}
