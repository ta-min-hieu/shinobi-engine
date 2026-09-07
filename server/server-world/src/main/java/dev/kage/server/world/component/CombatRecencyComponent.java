package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * "Lần cuối dính tới chiến đấu là bao lâu rồi" — dữ liệu cho {@code HealthRegenSystem} (ADR-046
 * D2). Mutable + field public: cùng ngoại lệ có chủ đích của Điều 43 như {@link HealthComponent}.
 *
 * <p>{@code CombatSystem}/{@code SkillSystem} đặt {@link #secondsSinceCombat} về 0 cho CẢ bên đánh
 * lẫn bên chịu mỗi khi có damage thật; System hồi máu cộng dồn {@code deltaTime} vào nó.
 *
 * <p>{@link #healAccumulator} tồn tại vì lượng hồi mỗi tick là số THỰC nhỏ (60Hz × ~3,5 máu/giây =
 * ~0,06 máu/tick) trong khi {@link HealthComponent#current} là {@code int}: cộng thẳng rồi ép kiểu
 * mỗi tick thì làm tròn xuống 0 và máu KHÔNG BAO GIỜ hồi. Phần lẻ tích ở đây, chỉ phần nguyên mới
 * đổ sang HP.
 *
 * <p>{@link #secondsSinceEmit} là đồng hồ của TRẦN SỐ GÓI (ADR-046 D2:
 * {@code REGEN_EMIT_INTERVAL_SECONDS}), không phải của gameplay — máu vẫn hồi mỗi tick, chỉ có tin
 * báo về client là gộp lại.
 */
public final class CombatRecencyComponent implements Component {

    /** Giây kể từ lần cuối entity này gây HOẶC chịu damage. Khởi tạo lớn = coi như đã ngoài chiến đấu từ lâu. */
    public float secondsSinceCombat = Float.MAX_VALUE;

    /** Phần máu lẻ chưa đủ 1 điểm để đổ vào {@link HealthComponent#current}. */
    public float healAccumulator;

    /** Giây kể từ lần cuối phát {@code HealthChangedEvent} vì hồi máu. */
    public float secondsSinceEmit;
}
