package dev.kage.character.application;

import dev.kage.character.domain.Character;
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
     * Cộng {@code amount} EXP cho nhân vật rồi trả trạng thái EXP/cấp sau khi cộng.
     *
     * @param amount lượng EXP cộng thêm; {@code <= 0} thì KHÔNG cộng gì — đó là cách gateway lấy số
     *     để gửi vạch EXP lúc vào world và sau khi nhận thưởng quest. ⚠️ Không cộng KHÔNG có nghĩa
     *     là không ghi: nhánh này vẫn hoà giải cột {@code level} nếu nó lệch (xem
     *     {@link #reconcileStoredLevel}).
     * @return trạng thái SAU khi cộng, kèm {@code leveledUp} để client biết có ăn mừng hay không.
     */
    public Result execute(long characterId, long amount) {
        Character character =
                characterPort.findById(characterId).orElseThrow(() -> new CharacterNotFoundException(characterId));
        ExperienceProgress before = ExperienceCurve.progressOf(character.exp());
        if (amount <= 0L) {
            reconcileStoredLevel(characterId, character.level(), before.level(), character.exp());
            return new Result(before, false);
        }

        long expAfter = characterPort.addExperience(characterId, amount);
        ExperienceProgress after = ExperienceCurve.progressOf(expAfter);
        boolean leveledUp = after.level() > before.level();
        reconcileStoredLevel(characterId, character.level(), after.level(), expAfter);
        return new Result(after, leveledUp);
    }

    /**
     * Ghi cột {@code character.level} khi và CHỈ khi nó lệch với cấp suy ra từ {@code exp}.
     *
     * <p><b>Vì sao so với cột ĐANG LƯU chứ không so trước/sau lần cộng này</b> (sửa 2026-09-05):
     * bản cũ chỉ ghi khi {@code after.level() > before.level()}, tức chỉ khi CHÍNH lần cộng này
     * vượt mốc. Nhưng EXP thưởng quest đi thẳng vào {@code character.exp} qua
     * {@code PostgresQuestExpAdapter}, KHÔNG qua use case này — nên cột {@code level} tụt lại và
     * phép so trước/sau **không bao giờ phát hiện ra**. Playtest 2026-09-05: nhận 500 EXP thưởng
     * quest, {@code exp} thành 620 (đúng ra là cấp 4) mà cột {@code level} nằm im ở 2, và chỉ tự
     * nhảy khi một lần giết quái sau đó tình cờ vượt mốc — khi ấy nó NHẢY CÓC, bỏ qua các cấp giữa.
     *
     * <p>Cột lệch không phải chuyện hiển thị suông: {@code CharacterSummary.level} (màn chọn nhân
     * vật) và {@code CharacterLevelComponent} (cổng Tuyệt Kỹ Lv50) đều đọc cột này.
     *
     * <p>Vẫn giữ nguyên tinh thần tối ưu cũ — không UPDATE khi giá trị không đổi — chỉ đổi phép so
     * cho ĐÚNG: so với thứ đang nằm trong DB, không phải với một mốc phái sinh.
     *
     * <p><b>Ghi kèm điều kiện {@code exp} chưa đổi</b> (sửa 2026-09-24): xem
     * {@link CharacterPort#saveLevel} — không có điều kiện đó, hai lần cộng song song về đích ngược
     * thứ tự có thể ghi cột {@code level} LÙI về cấp cũ.
     */
    private void reconcileStoredLevel(long characterId, int storedLevel, int derivedLevel, long derivedFromExp) {
        if (storedLevel != derivedLevel) {
            characterPort.saveLevel(characterId, derivedLevel, derivedFromExp);
        }
    }

    /**
     * Kết quả một lần {@link #execute}.
     *
     * @param leveledUp true nếu CHÍNH lần cộng này làm đổi cấp (có thể nhảy nhiều cấp một lúc).
     */
    public record Result(ExperienceProgress progress, boolean leveledUp) {}
}
