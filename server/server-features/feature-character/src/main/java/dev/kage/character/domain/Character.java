package dev.kage.character.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Aggregate root — một nhân vật. Validate trong compact constructor (Fail Fast, CODING-STANDARDS
 * §6). Chỉ số dẫn xuất (ATK/DEF...) KHÔNG có ở đây — server tính từ {@code character_stat} +
 * trang bị + buff lúc cần, không lưu (GAMEPLAY.md §2, chặn Phase 4 công thức damage).
 *
 * <p>⚠️ Validate tên hiện tại CHỈ độ dài + charset — CHƯA lọc từ cấm/homoglyph (SECURITY.md §12
 * yêu cầu đủ, nhưng dự án cá nhân rủi ro thấp, để trống có chủ đích ở lát cắt này).
 */
public record Character(
        long id,
        long accountId,
        int realmId,
        String name,
        Faction faction,
        int level,
        long exp,
        int rebirthCount,
        Instant createdAt) {

    private static final int NAME_MIN_LENGTH = 2;
    private static final int NAME_MAX_LENGTH = 16;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_]+$");
    private static final int MIN_LEVEL = 1;

    public Character {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(createdAt, "createdAt");
        validateName(name);
        if (level < MIN_LEVEL) {
            throw new IllegalArgumentException("level phải >= " + MIN_LEVEL + ": " + level);
        }
        if (exp < 0) {
            throw new IllegalArgumentException("exp không được âm: " + exp);
        }
        if (rebirthCount < 0) {
            throw new IllegalArgumentException("rebirthCount không được âm: " + rebirthCount);
        }
    }

    /**
     * Validate tên ĐỘC LẬP với việc tạo {@code Character} — dùng ở
     * {@code CreateCharacterUseCase} để fail fast TRƯỚC khi chạm DB (countByAccount/existsByName),
     * không phải chỉ lúc adapter dựng lại đối tượng trả về sau khi đã ghi xong.
     */
    public static void validateName(String name) {
        Objects.requireNonNull(name, "name");
        if (!hasValidLength(name)) {
            throw new IllegalArgumentException(
                    "tên nhân vật phải dài %d-%d ký tự: '%s'".formatted(NAME_MIN_LENGTH, NAME_MAX_LENGTH, name));
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("tên nhân vật chứa ký tự không hợp lệ: '" + name + "'");
        }
    }

    /**
     * Bản không ném của {@link #validateName} — {@code true} nếu {@code name} là tên một nhân vật CÓ
     * THỂ tồn tại. Vì compact constructor áp đúng luật này cho mọi {@code Character} (kể cả dựng lại
     * từ DB), tên trả {@code false} chắc chắn không khớp nhân vật nào.
     */
    public static boolean isValidName(String name) {
        return name != null
                && hasValidLength(name)
                && NAME_PATTERN.matcher(name).matches();
    }

    private static boolean hasValidLength(String name) {
        return name.length() >= NAME_MIN_LENGTH && name.length() <= NAME_MAX_LENGTH;
    }
}
