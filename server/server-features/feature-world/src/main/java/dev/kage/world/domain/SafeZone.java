package dev.kage.world.domain;

/**
 * Một vùng an toàn hình chữ nhật trên bản đồ (ADR-046 D3, bảng {@code safe_zone}) — bước vào thì
 * hồi đầy máu.
 *
 * <p>Tên "an toàn" chứ không phải "hồi máu" là có chủ đích: cùng vùng đó sau này là chỗ cấm PvP
 * (STORY §7 — tông môn là đất an toàn). Chưa có cột "vùng này làm gì" vì mới có ĐÚNG một luật, và
 * đoán trước luật thứ hai là đoán sai (ADR-046 Revisit Trigger).
 *
 * <p>Biên là toạ độ thế giới, kể cả biên (xem {@code Aabb.contains}). {@code minY}/{@code maxY} vẫn
 * có mặt dù ADR-033 khoá di chuyển vào trục X: nhân vật có {@code z} khi nhảy, và một vùng chỉ khai
 * trục X sẽ phải đổi hình dạng ngay khi bản đồ có tầng.
 */
public record SafeZone(String code, String worldCode, float minX, float minY, float maxX, float maxY) {

    /** World chính — cùng chuỗi với {@code MonsterSpawnPoint.MAIN_WORLD_CODE}, có test khoá lại. */
    public static final String MAIN_WORLD_CODE = "main";

    public SafeZone {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code không được rỗng");
        }
        if (worldCode == null || worldCode.isBlank()) {
            throw new IllegalArgumentException("worldCode không được rỗng");
        }
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException(
                    "biên vùng lộn ngược: [" + minX + ", " + minY + "] .. [" + maxX + ", " + maxY + "]");
        }
    }
}
