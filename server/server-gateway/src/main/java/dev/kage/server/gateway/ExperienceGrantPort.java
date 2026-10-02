package dev.kage.server.gateway;

import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.domain.ExperienceProgress;

/**
 * Cổng hẹp để {@link ConnectedCharacterRegistry} cộng EXP mà không phải cầm cả
 * {@code CharacterFacade} (nó chỉ cần đúng một hành động này). Bootstrap nối vào bằng
 * {@code characterFacade::grantExperience}.
 *
 * <p>Khai báo ở phía GỌI đúng tinh thần Dependency Inversion — gateway nói nó cần gì, không đi mượn
 * nguyên API của feature khác.
 */
@FunctionalInterface
interface ExperienceGrantPort {

    /**
     * Cộng EXP cho nhân vật và trả trạng thái cấp sau khi cộng.
     *
     * @param amount {@code <= 0} = chỉ đọc trạng thái hiện tại (xem {@code GrantExperienceUseCase}).
     */
    GrantExperienceUseCase.Result grantExperience(long characterId, long amount);

    /**
     * Bản KHÔNG cộng gì, luôn báo cấp 1 / 0 EXP.
     *
     * <p>⚠️ CHỈ dùng cho test không quan tâm tới EXP — nối cái này vào production nghĩa là người
     * chơi giết quái cả ngày mà vạch không nhúc nhích, và không có lỗi nào để lần ra.
     */
    static ExperienceGrantPort disabled() {
        return (characterId, amount) ->
                new GrantExperienceUseCase.Result(new ExperienceProgress(1, 0L, 0L, 100L), false);
    }
}
