package dev.kage.client.core.world;

import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.EffectKind;
import dev.kage.protocol.SkillResultEvent;

/**
 * Dịch {@link AttackResultEvent}/{@link SkillResultEvent} sang lệnh phát animation trên
 * {@link WorldModelRenderer} — tách khỏi {@link WorldScreen} để giữ độ phức tạp lớp đó dưới
 * ngưỡng PMD {@code CyclomaticComplexity}, cùng tinh thần {@link CombatHudController} đã tách state
 * combat ra khỏi orchestration packet.
 *
 * <p>Gói ID chung nhân vật/quái (combat.proto: dương = nhân vật, âm = quái) nên
 * {@code attacker_entity_id}/{@code target_entity_id} của 2 event này có thể là CHÍNH người chơi,
 * người chơi KHÁC, hoặc quái. <b>Cả ba đều được phát animation từ 2026-08-09</b> — quái giờ cũng là
 * model 3D thật ({@code mob_training-dummy} có {@code attack}/{@code hit}/{@code die}), không còn là
 * khối hộp không cử động được.
 *
 * <p>Điều kiện lọc vì vậy không còn là "có phải nhân vật không" mà đúng câu hỏi cần hỏi:
 * {@link WorldModelRenderer#hasModel} — <b>có thứ gì để phát animation lên hay không</b>. Câu hỏi cũ
 * cho kết quả sai theo cả hai chiều kể từ hôm nay: bỏ sót quái đã có model, và vẫn "đồng ý" với một
 * mã quái chưa có art nếu ai đó lỡ tay xếp nó vào nhóm nhân vật.
 */
final class CombatAnimationTrigger {

    private final WorldModelRenderer models;
    private final long localCharacterId;
    private final long localModelId;

    /**
     * @param localCharacterId id THẬT của chính người chơi (khớp {@code attacker_entity_id}/
     *     {@code target_entity_id} do server gửi).
     * @param localModelId khoá sentinel {@link WorldModelRenderer} dùng cho chính người chơi
     *     ({@code WorldScreen.LOCAL_CHARACTER_ID}) — khác {@code localCharacterId} vì server không
     *     biết khái niệm sentinel này.
     */
    CombatAnimationTrigger(WorldModelRenderer models, long localCharacterId, long localModelId) {
        this.models = models;
        this.localCharacterId = localCharacterId;
        this.localModelId = localModelId;
    }

    /** "attack" luôn phát cho người đánh dù trúng hay né — đó là động tác vung đòn, không phải kết quả. */
    void onAttackResult(AttackResultEvent event) {
        if (hasModel(event.getAttackerEntityId())) {
            models.playAttack(modelId(event.getAttackerEntityId()));
        }
        if (!event.getIsMiss() && hasModel(event.getTargetEntityId())) {
            playHitOrDie(event.getTargetEntityId(), event.getTargetDied());
        }
    }

    /**
     * is_crit/is_miss (và phản ứng "hit" của mục tiêu) chỉ có ý nghĩa với
     * {@code EFFECT_KIND_DAMAGE} (combat.proto) — heal/khiên/buff/debuff không làm mục tiêu giật
     * người như bị đánh.
     */
    void onSkillResult(SkillResultEvent event) {
        if (hasModel(event.getAttackerEntityId())) {
            models.playSkillCast(modelId(event.getAttackerEntityId()));
        }
        boolean isDamage = event.getEffectKind() == EffectKind.EFFECT_KIND_UNSPECIFIED
                || event.getEffectKind() == EffectKind.EFFECT_KIND_DAMAGE;
        if (isDamage && !event.getIsMiss() && hasModel(event.getTargetEntityId())) {
            playHitOrDie(event.getTargetEntityId(), event.getTargetDied());
        }
    }

    private void playHitOrDie(long targetEntityId, boolean targetDied) {
        long modelId = modelId(targetEntityId);
        if (targetDied) {
            models.playDie(modelId);
        } else {
            models.playHit(modelId);
        }
    }

    /**
     * Quy đổi id không gian chung nhân vật/quái sang khoá mà {@link WorldModelRenderer} dùng —
     * CHÍNH mình dùng {@link #localModelId}, mọi thứ khác (nhân vật KHÁC, quái) dùng thẳng
     * {@code entity_id} thật, khớp khoá đã dùng lúc {@code addCharacter}/{@code addMonsterOrNpc}.
     */
    private long modelId(long entityId) {
        return entityId == localCharacterId ? localModelId : entityId;
    }

    /** true nếu id này đang có model 3D để phát animation lên. */
    private boolean hasModel(long entityId) {
        return models.hasModel(modelId(entityId));
    }
}
