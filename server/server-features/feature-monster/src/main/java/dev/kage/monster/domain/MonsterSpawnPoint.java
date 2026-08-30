package dev.kage.monster.domain;

/**
 * Một điểm spawn quái (ADR-037 D2, bảng {@code monster_spawn}) — loài nào, đứng ở đâu, hồi sinh sau
 * bao lâu.
 *
 * <p>Bảng riêng chứ không phải cột trên {@link MonsterTemplate} vì một loài có NHIỀU điểm spawn, và
 * vì đó chính là thứ chặn đường trước ADR-037: bốn điểm spawn hardcode đều tạo cùng một mã, nên
 * thêm loài vào bảng template mà không đụng spawn thì thế giới vẫn y nguyên.
 *
 * <p>{@code posY} luôn 0 (ADR-033 khoá di chuyển vào trục X — quái đặt lệch y là quái người chơi
 * không bao giờ chạm tới); DB có {@code CHECK} bắt buộc điều đó, đây chỉ là hàng rào thứ hai.
 */
public record MonsterSpawnPoint(String monsterCode, String worldCode, float posX, float posY, float respawnSeconds) {

    /** World chính — dungeon/arena chưa đọc bảng spawn ở đợt này (ADR-037 D2). */
    public static final String MAIN_WORLD_CODE = "main";

    public MonsterSpawnPoint {
        if (monsterCode == null || monsterCode.isBlank()) {
            throw new IllegalArgumentException("monsterCode không được rỗng");
        }
        if (worldCode == null || worldCode.isBlank()) {
            throw new IllegalArgumentException("worldCode không được rỗng");
        }
        if (respawnSeconds <= 0f) {
            throw new IllegalArgumentException("respawnSeconds phải > 0: " + respawnSeconds);
        }
    }
}
