package dev.kage.client.core.world;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.SkillResultEvent;

/**
 * "HUD controller" cho combat (UI.md §4) — đọc packet server, giữ trạng thái HP/Khí/hồi chiêu/mục
 * tiêu để {@link dev.kage.client.core.ui.hud.CombatHud} vẽ, KHÔNG tự vẽ hay động vào mạng
 * (widget/controller tách biệt — UI.md §4, §10). Không phụ thuộc libGDX/GL nên test được thẳng,
 * không cần context đồ hoạ.
 *
 * <p>Server không gửi gói đồng bộ HP/Khí/hồi chiêu nào (GAMEPLAY.md §3) — HP/Khí bắt đầu ở mức đầy
 * và chỉ được SỬA LẠI bằng số thật mỗi khi có {@link AttackResultEvent}/{@link SkillResultEvent}
 * nhắm vào mình; hồi chiêu là đếm ngược cục bộ hoàn toàn dự đoán, có thể lệch với server (chấp nhận
 * được — server luôn tự kiểm tra lại, lệch chỉ khiến nút bật/tắt sai thời điểm, xem
 * {@link CombatConstants}).
 */
final class CombatHudController {

    /** Trạng thái 1 quái đã biết, tối thiểu để vẽ hộp + thanh máu (không phải ECS Component). */
    record MonsterState(String code, float x, float y, int hp, int maxHp) {}

    private final long localCharacterId;
    private final Map<Long, MonsterState> monsters = new HashMap<>();

    private int playerHp = CombatConstants.CHARACTER_MAX_HP;
    private int playerChi = CombatConstants.CHARACTER_MAX_CHI;
    private float skillCooldownRemaining;
    private long currentTargetId;

    /**
     * @param localCharacterId id nhân vật của chính người chơi — dùng để nhận ra
     *     {@code AttackResultEvent}/{@code SkillResultEvent} nào đang nhắm vào MÌNH thay vì quái.
     */
    CombatHudController(long localCharacterId) {
        this.localCharacterId = localCharacterId;
    }

    /**
     * Ghi nhận quái mới, tự chọn làm mục tiêu nếu chưa có mục tiêu nào.
     *
     * @return {@code monster_id} vừa được TỰ ĐỘNG chọn làm mục tiêu (caller phải gửi
     *     {@code SetTargetCommand} với id này), hoặc {@code 0} nếu đã có mục tiêu từ trước nên
     *     không đổi. Chỉ 1 quái tồn tại ở overworld hiện tại nên "chọn quái đầu tiên thấy được" là
     *     đủ — chưa cần chọn "quái gần nhất" trong nhiều lựa chọn.
     */
    long onMonsterSpawned(MonsterSpawnedEvent event) {
        monsters.put(
                event.getMonsterId(),
                new MonsterState(
                        event.getMonsterCode(), event.getX(), event.getY(), event.getCurrentHp(), event.getMaxHp()));
        if (currentTargetId == 0L) {
            currentTargetId = event.getMonsterId();
            return currentTargetId;
        }
        return 0L;
    }

    void onMonsterDespawned(MonsterDespawnedEvent event) {
        monsters.remove(event.getMonsterId());
        if (currentTargetId == event.getMonsterId()) {
            currentTargetId = 0L;
        }
    }

    void onMonsterMoved(MonsterMovedEvent event) {
        MonsterState existing = monsters.get(event.getMonsterId());
        if (existing != null) {
            monsters.put(
                    event.getMonsterId(),
                    new MonsterState(existing.code(), event.getX(), event.getY(), existing.hp(), existing.maxHp()));
        }
    }

    void onAttackResult(AttackResultEvent event) {
        applyHpUpdate(event.getTargetEntityId(), event.getTargetCurrentHp(), event.getTargetDied());
    }

    void onSkillResult(SkillResultEvent event) {
        applyHpUpdate(event.getTargetEntityId(), event.getTargetCurrentHp(), event.getTargetDied());
    }

    // Dùng chung cho AttackResultEvent/SkillResultEvent — cả 2 mang đúng 3 field cần: ai bị nhắm,
    // HP còn lại, có chết không (SkillResultEvent chỉ thêm skill_code/effect_kind, chưa cần cho
    // HUD tối thiểu này). Gọi 2 lần (Despawned tới sau) vẫn an toàn — remove/clear đã idempotent.
    private void applyHpUpdate(long targetEntityId, int currentHp, boolean died) {
        if (targetEntityId == localCharacterId) {
            playerHp = currentHp;
            return;
        }
        MonsterState existing = monsters.get(targetEntityId);
        if (existing == null) {
            return; // Không phải mình cũng không phải quái đang theo dõi — bỏ qua an toàn.
        }
        if (died) {
            monsters.remove(targetEntityId);
            if (currentTargetId == targetEntityId) {
                currentTargetId = 0L;
            }
            return;
        }
        monsters.put(
                targetEntityId,
                new MonsterState(existing.code(), existing.x(), existing.y(), currentHp, existing.maxHp()));
    }

    void tick(float deltaTime) {
        if (skillCooldownRemaining > 0f) {
            skillCooldownRemaining = Math.max(0f, skillCooldownRemaining - deltaTime);
        }
    }

    boolean canUseSkill() {
        return skillCooldownRemaining <= 0f && playerChi >= CombatConstants.FIREBALL_CHI_COST && currentTargetId != 0L;
    }

    /**
     * Gọi khi người chơi bấm nút skill.
     *
     * @return {@code true} nếu hợp lệ CỤC BỘ (caller phải gửi {@code UseSkillCommand} ngay);
     *     {@code false} nếu chưa đủ điều kiện (đang hồi chiêu/thiếu Khí/chưa có mục tiêu) — không
     *     gửi gì, vì server sẽ âm thầm bỏ qua lệnh sai (SECURITY.md), gửi thừa chỉ tốn băng thông.
     */
    boolean useSkill() {
        if (!canUseSkill()) {
            return false;
        }
        skillCooldownRemaining = CombatConstants.FIREBALL_COOLDOWN_SECONDS;
        playerChi -= CombatConstants.FIREBALL_CHI_COST;
        return true;
    }

    int playerHp() {
        return playerHp;
    }

    int playerMaxHp() {
        return CombatConstants.CHARACTER_MAX_HP;
    }

    long currentTargetId() {
        return currentTargetId;
    }

    MonsterState targetState() {
        return monsters.get(currentTargetId);
    }

    Map<Long, MonsterState> monsters() {
        return Collections.unmodifiableMap(monsters);
    }

    float skillCooldownRemaining() {
        return skillCooldownRemaining;
    }
}
