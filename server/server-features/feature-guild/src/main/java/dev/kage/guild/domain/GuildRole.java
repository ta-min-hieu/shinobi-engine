package dev.kage.guild.domain;

/**
 * LEADER = bang chủ, duy nhất, phải giải tán bang (hoặc nhường chức, ADR-183) chứ không "rời" ({@link GuildPort}).
 * VICE_LEADER (ADR-184) = phó bang chủ, tối đa {@link #MAX_VICE_LEADERS}: duyệt/từ chối đơn xin vào và đăng thông báo
 * bang; KHÔNG đuổi người, giải tán, đổi tên, nhường chức hay tuyên chiến.
 */
public enum GuildRole {
    LEADER,
    MEMBER,
    VICE_LEADER;

    /** Số phó bang chủ tối đa mỗi bang (ADR-184). PLACEHOLDER (Điều 27). */
    public static final int MAX_VICE_LEADERS = 2;

    /** Được duyệt đơn xin vào bang và đăng thông báo bang: bang chủ hoặc phó bang chủ. */
    public boolean canManageMembership() {
        return this == LEADER || this == VICE_LEADER;
    }
}
