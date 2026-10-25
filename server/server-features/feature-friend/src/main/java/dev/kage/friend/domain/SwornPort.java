package dev.kage.friend.domain;

import java.util.List;

/** Kết nghĩa huynh đệ (ADR-110) — hiện thực ở server-database (V90). */
public interface SwornPort {

    /** Mọi thành viên nhóm của {@code characterId} (gồm cả người đó); rỗng nếu chưa kết nghĩa. */
    List<Long> membersOf(long characterId);

    /**
     * Nhận lời — nguyên tử: kiểm lại thân mật, người được mời chưa ở nhóm, nhóm người mời chưa đầy; lập nhóm mới
     * hoặc thêm vào nhóm cũ; trừ vàng người mời ({@link SwornPolicy#FOUND_COST} / {@link SwornPolicy#JOIN_COST}).
     */
    SwornOutcome join(long inviterId, long inviteeId);

    /**
     * Rời nhóm; nhóm còn một người thì giải tán.
     *
     * @return những người CÒN LẠI của nhóm cũ (để báo); rỗng nếu chưa kết nghĩa
     */
    List<Long> leave(long characterId);

    /** Thân mật giữa hai người bạn (0 nếu không là bạn). */
    int intimacyBetween(long firstId, long secondId);
}
