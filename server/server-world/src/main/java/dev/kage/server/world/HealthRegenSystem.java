package dev.kage.server.world;

import java.util.ArrayList;
import java.util.List;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.Family;
import dev.kage.engine.ecs.IteratingKSystem;
import dev.kage.engine.ecs.KEntity;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.CombatRecencyComponent;
import dev.kage.server.world.component.HealthComponent;

/**
 * Hồi máu ngoài chiến đấu cho NHÂN VẬT (ADR-046 D2) — trước ADR này cả arc 1 không có một nguồn hồi
 * máu nào, và playtest 2026-09-06 chết 3 lần trong ~15 phút vì đúng lý do đó.
 *
 * <p><b>Chỉ nhân vật, không quái.</b> Quái lành lại bằng đường khác: về tới điểm spawn thì đầy máu
 * ({@code AiSystem.tickReturn}, ADR-046 D4). Cho quái dùng chung System này là sai — con quái đang
 * ĐUỔI mà chưa đánh trúng ai vẫn là "ngoài chiến đấu" theo nghĩa đen của {@link
 * CombatRecencyComponent}, nên nó sẽ vừa chạy vừa hồi máu.
 *
 * <p>KHÔNG gọi thẳng {@code WorldOutboundSink} — cùng khuôn {@link CombatSystem}: kết quả tích
 * trong hàng đợi nội bộ, {@link #drainResults()} lấy ra sau {@code ecsWorld.update()} mỗi tick.
 */
final class HealthRegenSystem extends IteratingKSystem {

    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<CombatRecencyComponent> RECENCY =
            ComponentMapper.getFor(CombatRecencyComponent.class);
    private static final ComponentMapper<CharacterIdComponent> CHARACTER_ID =
            ComponentMapper.getFor(CharacterIdComponent.class);

    private final List<HealthChange> pendingResults = new ArrayList<>();

    HealthRegenSystem() {
        super(Family.all(HealthComponent.class, CombatRecencyComponent.class, CharacterIdComponent.class)
                .get());
    }

    List<HealthChange> drainResults() {
        if (pendingResults.isEmpty()) {
            return List.of();
        }
        List<HealthChange> results = List.copyOf(pendingResults);
        pendingResults.clear();
        return results;
    }

    @Override
    protected void processEntity(KEntity entity, float deltaTime) {
        CombatRecencyComponent recency = RECENCY.get(entity);
        // Float.MAX_VALUE + deltaTime vẫn là Float.MAX_VALUE (không tràn) — nhân vật vừa vào world
        // được coi là đã ngoài chiến đấu từ lâu, đúng ý.
        recency.secondsSinceCombat += deltaTime;
        // ADR-050 D3 — đồng hồ "tôi vừa ra đòn" phải chạy ở ĐÂY, trước mọi nhánh thoát sớm; để
        // dưới thì người đầy máu không bao giờ lấy lại được lá chắn vùng an toàn.
        recency.secondsSinceAttackDealt += deltaTime;

        HealthComponent health = HEALTH.get(entity);
        if (health.isDead() || health.current >= health.max) {
            // Đầy máu (hoặc đã chết) là trạng thái THƯỜNG của người chơi ⇒ nhánh này là nhánh chạy
            // nhiều nhất, và nó phát 0 gói. Dọn luôn phần lẻ để lần bị thương sau bắt đầu từ 0.
            recency.healAccumulator = 0f;
            recency.secondsSinceEmit = 0f;
            recency.regenUnannounced = false;
            return;
        }
        if (recency.secondsSinceCombat < CombatConstants.REGEN_DELAY_SECONDS) {
            // Vừa đánh nhau — chưa hồi, đồng hồ phát gói đứng yên. Nhưng phần máu đã hồi mà chưa
            // kịp báo thì báo NGAY: đòn của chính mình không mang máu của mình ra dây
            // (AttackResultEvent chỉ chở máu bên chịu đòn), nên không báo ở đây thì vạch máu kẹt số
            // cũ suốt REGEN_DELAY_SECONDS giây (test shouldAnnounceUnreportedRegen_...).
            if (recency.regenUnannounced) {
                announce(entity, recency, health);
            }
            return;
        }

        recency.healAccumulator += CombatConstants.REGEN_FRACTION_PER_SECOND * health.max * deltaTime;
        int restored = (int) recency.healAccumulator;
        if (restored > 0) {
            recency.healAccumulator -= restored;
            health.current = Math.min(health.max, health.current + restored);
        }

        recency.secondsSinceEmit += deltaTime;
        boolean full = health.current >= health.max;
        // Phát khi đủ nhịp HOẶC khi vừa đầy máu — nhịp cuối cùng luôn tới nơi, không để client kẹt
        // ở "còn thiếu 2 máu" cho tới đòn đánh kế tiếp.
        if (restored > 0) {
            recency.regenUnannounced = true;
            if (full || recency.secondsSinceEmit >= CombatConstants.REGEN_EMIT_INTERVAL_SECONDS) {
                announce(entity, recency, health);
            }
        }
    }

    private void announce(KEntity entity, CombatRecencyComponent recency, HealthComponent health) {
        recency.secondsSinceEmit = 0f;
        recency.regenUnannounced = false;
        pendingResults.add(new HealthChange(CHARACTER_ID.get(entity).characterId, health.current, health.max));
    }
}
