package dev.kage.character.domain;

import java.text.Normalizer;
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
// AvoidCommonTypeNames: tên trùng java.lang.Character là tên aggregate đang dùng (~50 file ở
// 3 module import nó). Đổi tên là đổi public API của feature-character — việc của chủ dự án,
// không phải của một đợt dọn warning. Trong package này không ai dùng java.lang.Character.
@SuppressWarnings("AvoidCommonTypeNames")
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
    // Đúng MỘT ký tự theo luật của NAME_PATTERN — chỉ dùng để chỉ ra ký tự sai đầu tiên trong thông điệp lỗi.
    private static final Pattern NAME_CHARACTER = Pattern.compile("[\\p{L}\\p{N}_]");
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
        // Thông điệp KHÔNG chép nguyên văn tên (sửa 2026-10-06): gateway ghi nó ra log ở mỗi lượt tạo
        // sai, và tên đến thẳng từ client — khung 1 MiB thành dòng log 1 MiB, ký tự xuống dòng bịa được
        // dòng log giả. Chỉ ghi độ dài và mã của ký tự sai đầu tiên.
        if (!hasValidLength(name)) {
            throw new IllegalArgumentException("tên nhân vật phải dài %d-%d ký tự, nhận %d"
                    .formatted(NAME_MIN_LENGTH, NAME_MAX_LENGTH, name.length()));
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("tên nhân vật chứa ký tự không hợp lệ: " + firstInvalidCharacter(name));
        }
    }

    /**
     * Luật cho tên MỚI (quyết định chủ dự án 2026-10-11): {@link #validateName} cộng thêm "không ký tự
     * vô hình, không dạng tương thích". {@code \p{L}} nhận cả ký tự lấp chỗ Hangul (hiện ra là
     * KHOẢNG TRỐNG — tên vô hình) lẫn chữ khổ toàn phần / chữ toán học (trông y hệt chữ thường — giả
     * mạo tên người khác).
     *
     * <p>CHỈ dùng lúc TẠO nhân vật — không đặt vào compact constructor: nhân vật tạo trước ngày này có
     * thể đang mang đúng loại tên đó, và dựng lại chúng từ DB không được nổ (cùng lý do
     * {@code GuildName} chỉ kiểm lúc tạo bang). {@link #isValidName} vì thế cũng giữ luật rộng.
     *
     * <p>Chưa chặn: chữ khác hệ trông giống nhau (Cyrillic {@code а} với Latin {@code a}) và tên chỉ
     * khác hoa-thường — so trùng kiểu đó cần index mới ở DB.
     */
    public static void validateNewName(String name) {
        // Gọi SAU normalizeNewName: chuỗi NFD tới đây đã thành NFC.
        validateName(name);
        if (!Normalizer.isNormalized(name, Normalizer.Form.NFKC)
                || name.codePoints().anyMatch(Character::isInvisibleLetter)) {
            throw new IllegalArgumentException("tên nhân vật chứa ký tự vô hình hoặc dạng tương thích");
        }
    }

    /**
     * Dạng lưu của một tên MỚI: chuẩn hoá Unicode NFC (chủ dự án chốt 2026-10-14, docs/README.md dòng 42).
     * Bàn phím/IME có thể gửi tiếng Việt dạng NFD — "ê" thành "e" + dấu mũ rời (U+0302, nhóm {@code \p{M}}
     * chứ không phải {@code \p{L}}) — và trước đó tên như vậy bị từ chối dù hiển thị y hệt tên hợp lệ.
     * Chuẩn hoá trước khi kiểm và LƯU dạng NFC, để cùng một tên luôn là cùng một chuỗi byte (UNIQUE ở DB
     * so theo byte). {@code null} đi qua nguyên trạng cho {@link #validateNewName} báo lỗi.
     */
    public static String normalizeNewName(String name) {
        return name == null ? null : Normalizer.normalize(name, Normalizer.Form.NFC);
    }

    /**
     * Bốn ký tự lấp chỗ Hangul: thuộc nhóm chữ cái nên qua được {@code \p{L}}, nhưng trình bày là
     * khoảng trống. U+3164 và U+FFA0 đã bị kiểm NFKC bắt (chúng chuẩn hoá về U+1160); U+115F và
     * U+1160 thì NFKC để nguyên nên phải gọi tên.
     */
    private static boolean isInvisibleLetter(int codePoint) {
        return codePoint == 0x115F || codePoint == 0x1160 || codePoint == 0x3164 || codePoint == 0xFFA0;
    }

    /** {@code U+XXXX tại vị trí i} của ký tự đầu tiên ngoài luật tên — chỉ gọi khi tên đã sai. */
    private static String firstInvalidCharacter(String name) {
        int index = 0;
        while (index < name.length()) {
            int codePoint = name.codePointAt(index);
            String text = new String(java.lang.Character.toChars(codePoint));
            if (!NAME_CHARACTER.matcher(text).matches()) {
                return "U+%04X tại vị trí %d".formatted(codePoint, index);
            }
            index += java.lang.Character.charCount(codePoint);
        }
        return "không xác định";
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
