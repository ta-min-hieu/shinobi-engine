package dev.kage.client.core.world;

import java.util.function.LongPredicate;

import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.EffectKind;
import dev.kage.protocol.SkillResultEvent;

/**
 * Dịch {@link AttackResultEvent}/{@link SkillResultEvent} sang lệnh phát animation trên
 * {@link CharacterModelRenderer} — tách khỏi {@link WorldScreen} để giữ độ phức tạp lớp đó dưới
 * ngưỡng PMD {@code CyclomaticComplexity}, cùng tinh thần {@link CombatHudController} đã tách state
 * combat ra khỏi orchestration packet.
 *
 * <p>Gói ID chung nhân vật/quái (combat.proto: dương = nhân vật, âm = quái) nên
 * {@code attacker_entity_id}/{@code target_entity_id} của 2 event này có thể là CHÍNH người chơi,
 * người chơi KHÁC, hoặc quái — chỉ nhân vật mới có model 3D để phát animation
 * ({@code WorldScreen.drawMonsters} vẽ quái bằng hộp phẳng, ngoài phạm vi lớp này).
 */
final class CombatAnimationTrigger {

    private final CharacterModelRenderer models;
    private final long localCharacterId;
    private final long localModelId;
    private final LongPredicate isRemoteCharacter;

    /**
     * @param localCharacterId id THẬT của chính người chơi (khớp {@code attacker_entity_id}/
     *     {@code target_entity_id} do server gửi).
     * @param localModelId khoá sentinel {@link CharacterModelRenderer} dùng cho chính người chơi
     *     ({@code WorldScreen.LOCAL_CHARACTER_ID}) — khác {@code localCharacterId} vì server không
     *     biết khái niệm sentinel này.
     * @param isRemoteCharacter tra {@code characterId} có đang được {@code WorldScreen} render như
     *     nhân vật KHÁC hay không (không phải quái) — {@code LongPredicate} để tránh boxing.
     */
    CombatAnimationTrigger(
            CharacterModelRenderer models, long localCharacterId, long localModelId, LongPredicate isRemoteCharacter) {
        this.models = models;
        this.localCharacterId = localCharacterId;
        this.localModelId = localModelId;
        this.isRemoteCharacter = isRemoteCharacter;
    }

    /** "attack" luôn phát cho người đánh dù trúng hay né — đó là động tác vung đòn, không phải kết quả. */
    void onAttackResult(AttackResultEvent event) {
        if (isPlayerCharacter(event.getAttackerEntityId())) {
            models.playAttack(modelCharacterId(event.getAttackerEntityId()));
        }
        if (!event.getIsMiss() && isPlayerCharacter(event.getTargetEntityId())) {
            playHitOrDie(event.getTargetEntityId(), event.getTargetDied());
        }
    }

    /**
     * is_crit/is_miss (và phản ứng "hit" của mục tiêu) chỉ có ý nghĩa với
     * {@code EFFECT_KIND_DAMAGE} (combat.proto) — heal/khiên/buff/debuff không làm mục tiêu giật
     * người như bị đánh.
     */
    void onSkillResult(SkillResultEvent event) {
        if (isPlayerCharacter(event.getAttackerEntityId())) {
            models.playSkillCast(modelCharacterId(event.getAttackerEntityId()));
        }
        boolean isDamage = event.getEffectKind() == EffectKind.EFFECT_KIND_UNSPECIFIED
                || event.getEffectKind() == EffectKind.EFFECT_KIND_DAMAGE;
        if (isDamage && !event.getIsMiss() && isPlayerCharacter(event.getTargetEntityId())) {
            playHitOrDie(event.getTargetEntityId(), event.getTargetDied());
        }
    }

    private void playHitOrDie(long targetEntityId, boolean targetDied) {
        long modelId = modelCharacterId(targetEntityId);
        if (targetDied) {
            models.playDie(modelId);
        } else {
            models.playHit(modelId);
        }
    }

    /**
     * Quy đổi id không gian chung nhân vật/quái sang khoá mà {@link CharacterModelRenderer} dùng —
     * CHÍNH mình dùng {@link #localModelId}, người khác dùng thẳng {@code character_id} thật (khớp
     * khoá {@code models.addCharacter} ở {@code WorldScreen.onCharacterSpawned}). CHỈ gọi sau khi
     * {@link #isPlayerCharacter} xác nhận true.
     */
    private long modelCharacterId(long entityId) {
        return entityId == localCharacterId ? localModelId : entityId;
    }

    /** true nếu id thuộc một nhân vật (mình hoặc người khác đang render) — false nếu là quái. */
    private boolean isPlayerCharacter(long entityId) {
        return entityId == localCharacterId || isRemoteCharacter.test(entityId);
    }
}
