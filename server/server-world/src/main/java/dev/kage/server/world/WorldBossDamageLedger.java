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
    // Máu còn lại SAU đòn gần nhất — chỉ để kẹp đòn kết liễu (máu sau đòn đó là 0, không cho biết phần thừa).
    private final Map<Long, Integer> hpAfterLastHit = new HashMap<>();

    /** Bắt đầu ghi sổ cho quái {@code monsterId} (id âm) mang mã {@code monsterCode}, máu đầy {@code maxHp}. */
    void track(long monsterId, String monsterCode, int maxHp) {
        codeByMonsterId.put(monsterId, monsterCode);
        damageByMonsterId.put(monsterId, new HashMap<>());
        hpAfterLastHit.put(monsterId, maxHp);
    }

    /**
     * Ghi một đòn — bỏ qua nếu mục tiêu không có sổ, kẻ đánh không phải nhân vật, hoặc không gây sát thương. Đòn kết
     * liễu chỉ được tính phần máu thật còn lại (đòn 76 lên boss còn 4 máu ghi 4) — sổ đo đóng góp, không đo sức đánh.
     *
     * @param targetHpAfter máu mục tiêu SAU đòn này (0 nếu chết)
     */
    void record(long targetEntityId, long attackerEntityId, int damage, int targetHpAfter) {
        Map<Long, Long> ledger = damageByMonsterId.get(targetEntityId);
        if (ledger == null) {
            return;
        }
        int hpBefore = hpAfterLastHit.getOrDefault(targetEntityId, damage);
        hpAfterLastHit.put(targetEntityId, Math.max(0, targetHpAfter));
        int credited = targetHpAfter > 0 ? damage : Math.min(damage, hpBefore);
        if (credited > 0 && attackerEntityId > 0) {
            ledger.merge(attackerEntityId, (long) credited, Long::sum);
        }
    }

    /** Boss vừa chết: trả sổ (nhân vật → sát thương) và gỡ nó; rỗng nếu quái này không phải boss thế giới. */
    Optional<Defeat> close(long monsterId) {
        hpAfterLastHit.remove(monsterId);
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
