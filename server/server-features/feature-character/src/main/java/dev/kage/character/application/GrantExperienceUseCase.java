package dev.kage.character.application;

import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.ExperienceCurve;
import dev.kage.character.domain.ExperienceProgress;

/**
 * Cộng EXP cho nhân vật và lên cấp nếu đủ (2026-08-09 — trước đó dự án cộng EXP vào
 * {@code character.exp} nhưng KHÔNG nơi nào đổi nó thành cấp; chủ dự án phát hiện khi hỏi vạch
 * kinh nghiệm đâu).
 *
 * <p>Cấp được SUY LẠI từ tổng EXP mỗi lần chứ không cộng dồn từng bước ({@link ExperienceCurve} —
 * nguồn sự thật là cột {@code exp}), nên nhân vật đã tích EXP từ quest trước khi có hệ thống này
 * cũng tự nhảy đúng cấp ngay lần nhận EXP đầu tiên.
 */
public final class GrantExperienceUseCase {

    private final CharacterPort characterPort;

    public GrantExperienceUseCase(CharacterPort characterPort) {
        this.characterPort = characterPort;
    }

    /**
     * @param amount lượng EXP cộng thêm; {@code <= 0} thì chỉ ĐỌC trạng thái hiện tại, không ghi gì
     *     — đó là cách gateway lấy số để gửi vạch EXP lần đầu lúc vào world.
     * @return trạng thái SAU khi cộng, kèm {@code leveledUp} để client biết có ăn mừng hay không.
     */
    public Result execute(long characterId, long amount) {
        ExperienceProgress before = ExperienceCurve.progressOf(characterPort
                .findById(characterId)
                .orElseThrow(() -> new CharacterNotFoundException(characterId))
                .exp());
        if (amount <= 0L) {
            return new Result(before, false);
        }

        ExperienceProgress after = ExperienceCurve.progressOf(characterPort.addExperience(characterId, amount));
        boolean leveledUp = after.level() > before.level();
        if (leveledUp) {
            // Chỉ ghi cột level khi thật sự đổi — mỗi lần giết quái mà UPDATE thêm một cột không
            // đổi giá trị là ghi thừa vào bảng nóng nhất của game (server/CLAUDE.md: item/gold ghi
            // ngay, thứ khác ghi định kỳ).
            characterPort.saveLevel(characterId, after.level());
        }
        return new Result(after, leveledUp);
    }

    /** @param leveledUp true nếu CHÍNH lần cộng này làm đổi cấp (có thể nhảy nhiều cấp một lúc). */
    public record Result(ExperienceProgress progress, boolean leveledUp) {}
}
