package dev.kage.server.gateway;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.CharacterFacade;
import dev.kage.checkin.api.ActivityFacade;
import dev.kage.protocol.BountyProgressEvent;
import dev.kage.protocol.Opcode;
import dev.kage.quest.api.BountyFacade;
import dev.kage.quest.domain.BountyPort;

/**
 * Nối lượt hạ quái vào bảng truy nã (ADR-101) — bước phụ của đường thưởng hạ quái, chỉ người ra đòn cuối. Hoàn thành
 * thì thưởng đã cộng trong giao dịch của adapter; ở đây đồng bộ cấp (EXP cộng thẳng DB như thưởng quest) và báo client.
 */
final class BountyHooks {

    private static final Logger LOG = LoggerFactory.getLogger(BountyHooks.class);

    // volatile, không final: registry dựng TRƯỚC DB — bind() sau, cùng khuôn AchievementHooks.
    private volatile BountyFacade facade = BountyFacade.DISABLED;
    private volatile CharacterFacade characters;
    private volatile ConnectedCharacterRegistry registry;

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(BountyFacade realFacade, CharacterFacade realCharacters, ConnectedCharacterRegistry realRegistry) {
        this.characters = realCharacters;
        this.registry = realRegistry;
        this.facade = realFacade;
    }

    // PMD AvoidCatchingGenericException: việc phụ — lỗi dừng ở đây, phần thưởng chính không suy suyển.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onMonsterKilled(long killerCharacterId, String monsterCode) {
        ConnectedCharacterRegistry target = registry;
        if (target == null) {
            return;
        }
        try {
            Optional<BountyPort.Progress> progress = facade.onKill(killerCharacterId, monsterCode);
            if (progress.isEmpty()) {
                return;
            }
            BountyPort.Progress p = progress.get();
            if (p.completed()) {
                target.sendExperience(killerCharacterId, characters.syncExperienceAfterQuestReward(killerCharacterId));
                target.activity.record(killerCharacterId, ActivityFacade.Activity.BOUNTY); // ADR-106
            }
            target.sendToCharacterIfOnline(
                    killerCharacterId,
                    Opcode.OPCODE_BOUNTY_PROGRESS_EVENT_VALUE,
                    0,
                    BountyProgressEvent.newBuilder()
                            .setBountyCode(p.code())
                            .setProgress(p.progress())
                            .setTarget(p.target())
                            .setCompleted(p.completed())
                            .setRewardGold(p.rewardGold())
                            .setRewardExp(p.rewardExp())
                            .build()
                            .toByteArray());
        } catch (RuntimeException e) {
            LOG.warn("Ghi truy nã lỗi: characterId={}, monster={}", killerCharacterId, monsterCode, e);
        }
    }
}
