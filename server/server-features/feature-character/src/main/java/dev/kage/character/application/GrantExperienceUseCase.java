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
     * @return trạng thái SAU khi cộng, kèm {@code leveledUp} để client biết có ăn mừng hay không —
     *     xem {@link #raiseStoredLevel} về việc AI được báo khi nhiều lượt chạy song song.
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
        return new Result(after, raiseStoredLevel(characterId, character.level(), after.level(), expAfter));
    }

    /**
     * Đồng bộ vạch EXP SAU khi EXP đã được cộng bằng đường khác use case này — thưởng quest đi thẳng
     * vào {@code character.exp} qua {@code PostgresQuestExpAdapter}. Hoà giải cột {@code level} như
     * {@code execute(id, 0)}, nhưng CÓ báo {@code leveledUp} khi chính lần gọi này nâng được cột
     * {@code level} — cột đó là cấp người chơi đang thấy trước lần lĩnh thưởng này.
     *
     * <p>Vì sao không dùng {@code execute(id, 0)}: nhánh đó trả {@code leveledUp=false} có chủ đích
     * (lúc vào world không có gì để mừng), nên những cú lên cấp lớn nhất arc 1 — đều đến từ thưởng
     * quest — không bao giờ hiện thông báo lên cấp (kiểm sống chuỗi quest, 2026-10-03).
     */
    public Result syncAfterExternalGrant(long characterId) {
        Character character =
                characterPort.findById(characterId).orElseThrow(() -> new CharacterNotFoundException(characterId));
        ExperienceProgress now = ExperienceCurve.progressOf(character.exp());
        return new Result(now, raiseStoredLevel(characterId, character.level(), now.level(), character.exp()));
    }

    /**
     * Hoà giải cột {@code level} và trả lời "lần gọi này có phải là lần LÊN CẤP không".
     *
     * <p><b>Cột {@code level} là trọng tài duy nhất của việc báo lên cấp</b> (sửa 2026-10-10): lên
     * cấp được báo bởi đúng lượt ĐỔI ĐƯỢC cột đó lên cấp cao hơn ({@link CharacterPort#saveLevel}
     * chỉ trả {@code true} cho một lượt). Hai phép so cũ đều sai khi có hai nguồn EXP chạy song song:
     *
     * <ul>
     *   <li>{@code execute} so cấp trước/sau với {@code exp} ĐỌC TỪ TRƯỚC khi cộng. Mọi lượt đọc
     *       trước khi một lượt khác vượt mốc đều tự cho là mình vượt mốc — đo trên Postgres thật, 16
     *       lượt song song quanh MỘT mốc cấp báo lên cấp 9–10 lần
     *       ({@code ExperienceGrantRaceIntegrationTest}).
     *   <li>{@code syncAfterExternalGrant} so với cột {@code level} mà không biết mình có phải là
     *       bên ghi hay không. Một lượt hạ quái chen giữa "thưởng quest commit" và "đồng bộ" hoà giải
     *       cột trước, và khi đó KHÔNG bên nào báo — cú lên cấp biến mất.
     * </ul>
     *
     * @return {@code true} nếu chính lần gọi này nâng cột {@code level} (có thể nhiều cấp một lúc)
     */
    private boolean raiseStoredLevel(long characterId, int storedLevel, int derivedLevel, long derivedFromExp) {
        boolean written = reconcileStoredLevel(characterId, storedLevel, derivedLevel, derivedFromExp);
        return written && derivedLevel > storedLevel;
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
    private boolean reconcileStoredLevel(long characterId, int storedLevel, int derivedLevel, long derivedFromExp) {
        return storedLevel != derivedLevel && characterPort.saveLevel(characterId, derivedLevel, derivedFromExp);
    }

    /**
     * Kết quả một lần {@link #execute}.
     *
     * @param leveledUp true nếu CHÍNH lần gọi này nâng cột {@code level} (có thể nhảy nhiều cấp một
     *     lúc) — mỗi lần lên cấp được báo đúng một lần dù bao nhiêu lượt cộng EXP chạy song song.
     */
    public record Result(ExperienceProgress progress, boolean leveledUp) {}
}
