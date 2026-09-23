package dev.kage.guild.domain;

/**
 * Ném khi adapter phát hiện vi phạm UNIQUE {@code guild_member.character_id} lúc
 * {@link GuildPort#createGuild} thêm leader làm thành viên — race 1 nhân vật {@code createGuild}
 * (tên bang khác nhau) gần như đồng thời ở 2 request: {@link GuildPort#findMembershipOf} của cả
 * hai đều thấy "chưa ở bang nào" trước khi 1 trong 2 transaction commit, nên application/ không
 * chặn được bằng check-then-act; DB là tuyến phòng thủ cuối. Bên thua transaction rollback TOÀN BỘ
 * (kể cả dòng {@code guild} vừa insert — cùng 1 transaction), không để lại rác.
 */
public final class DuplicateGuildMembershipException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateGuildMembershipException(long characterId) {
        super("Nhân vật đã ở bang khác: characterId=" + characterId);
    }
}
