package dev.kage.character.domain;

import java.util.Objects;

/**
 * Hệ hiện tại + số lần đã đổi, đọc DƯỚI khoá dòng nhân vật (ADR-066). Hệ phải đọc lại ở đây chứ không tin bản
 * đọc trước transaction: hai lệnh đổi tới sát nhau chạy song song, lệnh sau thấy hệ cũ và sẽ trả một viên đan
 * để "đổi" sang đúng hệ lệnh trước vừa ghi.
 *
 * @param faction hệ đang ghi trong DB
 * @param changes số lần đã chọn/đổi trước lần này
 */
public record FactionChangeState(Faction faction, int changes) {

    public FactionChangeState {
        Objects.requireNonNull(faction, "faction");
        if (changes < 0) {
            throw new IllegalArgumentException("changes phải >= 0, nhận " + changes);
        }
    }
}
