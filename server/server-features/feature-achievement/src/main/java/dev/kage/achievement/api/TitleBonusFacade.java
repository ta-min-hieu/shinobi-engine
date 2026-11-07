package dev.kage.achievement.api;

import java.util.Map;
import java.util.Optional;

import dev.kage.achievement.domain.TitleBonusPort;

/** API chỉ số cộng của danh hiệu (ADR-210). Bảng đọc MỘT lần ở lần hỏi đầu — đổi SQL thì khởi động lại server. */
public final class TitleBonusFacade {

    /** Không danh hiệu nào cộng gì (composition test). */
    public static final TitleBonusFacade DISABLED = new TitleBonusFacade(Map::of);

    private final TitleBonusPort port;
    private volatile Map<String, TitleBonusPort.TitleBonus> bonuses;

    /** Dựng ở composition root. */
    public TitleBonusFacade(TitleBonusPort port) {
        this.port = port;
    }

    /** Chỉ số cộng của danh hiệu {@code achievementCode}; {@link TitleBonusPort.TitleBonus#NONE} nếu không cộng gì. */
    public TitleBonusPort.TitleBonus bonusOf(String achievementCode) {
        return all().getOrDefault(achievementCode, TitleBonusPort.TitleBonus.NONE);
    }

    /** Chỉ số cộng của danh hiệu đang đeo (rỗng = không đeo gì). */
    public TitleBonusPort.TitleBonus bonusOf(Optional<String> activeTitleCode) {
        return activeTitleCode.map(this::bonusOf).orElse(TitleBonusPort.TitleBonus.NONE);
    }

    private Map<String, TitleBonusPort.TitleBonus> all() {
        Map<String, TitleBonusPort.TitleBonus> loaded = bonuses;
        if (loaded == null) {
            loaded = Map.copyOf(port.bonusesByCode());
            bonuses = loaded;
        }
        return loaded;
    }
}
