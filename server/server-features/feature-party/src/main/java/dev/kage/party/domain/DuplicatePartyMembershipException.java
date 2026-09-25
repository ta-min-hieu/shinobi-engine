package dev.kage.party.domain;

/**
 * Ném khi {@link PartyPort#createParty} không thêm được leader làm thành viên vì
 * {@code party_member.character_id UNIQUE} — nhân vật vừa vào party KHÁC ở một transaction chạy
 * gần như đồng thời (tạo party 2 lần, hoặc tạo party trong lúc chấp nhận lời mời).
 * {@link PartyPort#findMembershipOf} của application/ không chặn được vì là check-then-act; DB là
 * tuyến phòng thủ cuối. Transaction thua rollback TOÀN BỘ (kể cả dòng {@code party} vừa insert),
 * không để lại party mồ côi 0 thành viên. Cùng khuôn {@code DuplicateGuildMembershipException}.
 */
public final class DuplicatePartyMembershipException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Tạo exception cho nhân vật đã ở party khác.
     *
     * @param characterId nhân vật đã ở party khác
     */
    public DuplicatePartyMembershipException(long characterId) {
        super("Nhân vật đã ở party khác: characterId=" + characterId);
    }
}
