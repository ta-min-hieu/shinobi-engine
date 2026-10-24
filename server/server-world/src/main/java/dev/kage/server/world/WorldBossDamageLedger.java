package dev.kage.server.world;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sổ sát thương của boss thế giới (ADR-081 D3) — chỉ chạm trên tick thread. Chỉ quái được {@link #track} mới có sổ;
 * đòn lên mọi quái khác là một lần tra map rỗng. Cộng dồn cả đòn thường lẫn chiêu, chỉ từ NHÂN VẬT (id dương).
 *
 * <p>Không phải component ECS: sổ chỉ tồn tại để báo ra ngoài đúng một lần lúc boss chết (cùng loại sổ sách với
 * {@code spawnIndexByMonsterId} của {@link WorldFacade}), không system nào đọc nó mỗi tick.
 */
final class WorldBossDamageLedger {

    private final Map<Long, String> codeByMonsterId = new HashMap<>();
    private final Map<Long, Map<Long, Long>> damageByMonsterId = new HashMap<>();

    /** Bắt đầu ghi sổ cho quái {@code monsterId} (id âm) mang mã {@code monsterCode}. */
    void track(long monsterId, String monsterCode) {
        codeByMonsterId.put(monsterId, monsterCode);
        damageByMonsterId.put(monsterId, new HashMap<>());
    }

    /** Ghi một đòn — bỏ qua nếu mục tiêu không có sổ, kẻ đánh không phải nhân vật, hoặc không gây sát thương. */
    void record(long targetEntityId, long attackerEntityId, int damage) {
        if (damage <= 0 || attackerEntityId <= 0) {
            return;
        }
        Map<Long, Long> ledger = damageByMonsterId.get(targetEntityId);
        if (ledger != null) {
            ledger.merge(attackerEntityId, (long) damage, Long::sum);
        }
    }

    /** Boss vừa chết: trả sổ (nhân vật → sát thương) và gỡ nó; rỗng nếu quái này không phải boss thế giới. */
    Optional<Defeat> close(long monsterId) {
        String code = codeByMonsterId.remove(monsterId);
        Map<Long, Long> ledger = damageByMonsterId.remove(monsterId);
        if (code == null || ledger == null) {
            return Optional.empty();
        }
        return Optional.of(new Defeat(code, Map.copyOf(ledger)));
    }

    /** Một boss thế giới đã chết kèm sổ sát thương. */
    record Defeat(String monsterCode, Map<Long, Long> damageByCharacter) {}
}
