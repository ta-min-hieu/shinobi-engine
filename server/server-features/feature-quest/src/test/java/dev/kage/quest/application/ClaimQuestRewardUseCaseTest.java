package dev.kage.quest.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.quest.domain.AuditPort;
import dev.kage.quest.domain.ExpPort;
import dev.kage.quest.domain.QuestInventoryGrantPort;
import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.QuestTemplate;
import dev.kage.quest.domain.QuestTemplatePort;
import dev.kage.quest.domain.Transaction;
import dev.kage.quest.domain.TransactionRunner;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClaimQuestRewardUseCaseTest {

    private static final long CHARACTER_ID = 1L;
    private static final long TEMPLATE_ID = 100L;
    private static final String QUEST_CODE = "known_quest";
    private static final String SCRIPT_REF = "/scripts/quest/test_fixture.js";

    static final class FakeQuestTemplatePort implements QuestTemplatePort {
        @Override
        public Optional<QuestTemplate> findByCode(String questCode) {
            return QUEST_CODE.equals(questCode) ? Optional.of(new QuestTemplate(TEMPLATE_ID, SCRIPT_REF))
                    : Optional.empty();
        }
    }

    static final class FakeQuestProgressPort implements QuestProgressPort {
        private QuestProgress existing;
        QuestProgress saved;

        FakeQuestProgressPort(QuestProgress existing) {
            this.existing = existing;
        }

        @Override
        public Optional<QuestProgress> find(long characterId, long questTemplateId) {
            return Optional.ofNullable(existing);
        }

        @Override
        public void save(Transaction tx, QuestProgress progress) {
            this.saved = progress;
        }
    }

    static final class FakeExpPort implements ExpPort {
        long grantedAmount = -1;

        @Override
        public void grantExp(Transaction tx, long characterId, long amount) {
            grantedAmount = amount;
        }
    }

    static final class FakeInventoryGrantPort implements QuestInventoryGrantPort {
        String grantedCode;
        int grantedQuantity;

        @Override
        public boolean grantItemByCode(Transaction tx, long characterId, String itemTemplateCode, int quantity) {
            grantedCode = itemTemplateCode;
            grantedQuantity = quantity;
            return true;
        }
    }

    static final class FakeAuditPort implements AuditPort {
        String loggedAction;

        @Override
        public void log(Transaction tx, long characterId, String action, String detailJson) {
            loggedAction = action;
        }
    }

    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);

    private static ClaimQuestRewardUseCase newUseCase(
            QuestProgressPort progressPort, FakeExpPort expPort, FakeInventoryGrantPort inventoryGrantPort,
            FakeAuditPort auditPort) {
        var rewardPorts = new QuestRewardPorts(expPort, inventoryGrantPort, auditPort);
        return new ClaimQuestRewardUseCase(progressPort, new FakeQuestTemplatePort(), rewardPorts, DIRECT_TX);
    }

    @Test
    void shouldReturnQuestNotFound_whenCodeUnknown() {
        var useCase = newUseCase(
                new FakeQuestProgressPort(null), new FakeExpPort(), new FakeInventoryGrantPort(), new FakeAuditPort());

        var result = useCase.execute(CHARACTER_ID, "unknown_code");

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.QUEST_NOT_FOUND);
    }

    @Test
    void shouldReturnNotAccepted_whenNoProgressRow() {
        var useCase = newUseCase(
                new FakeQuestProgressPort(null), new FakeExpPort(), new FakeInventoryGrantPort(), new FakeAuditPort());

        var result = useCase.execute(CHARACTER_ID, QUEST_CODE);

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.NOT_ACCEPTED);
    }

    @Test
    void shouldReturnNotCompletedYet_whenStillInProgress() {
        var progress = new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.IN_PROGRESS, "{}");
        var useCase = newUseCase(
                new FakeQuestProgressPort(progress), new FakeExpPort(), new FakeInventoryGrantPort(),
                new FakeAuditPort());

        var result = useCase.execute(CHARACTER_ID, QUEST_CODE);

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.NOT_COMPLETED_YET);
    }

    @Test
    void shouldGrantRewardsAndMarkClaimed_whenCompleted() {
        var progress = new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.COMPLETED, "{\"counter\":2}");
        var progressPort = new FakeQuestProgressPort(progress);
        var expPort = new FakeExpPort();
        var inventoryGrantPort = new FakeInventoryGrantPort();
        var auditPort = new FakeAuditPort();
        var useCase = newUseCase(progressPort, expPort, inventoryGrantPort, auditPort);

        var result = useCase.execute(CHARACTER_ID, QUEST_CODE);

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        assertThat(result.expGranted()).isEqualTo(100L);
        assertThat(result.itemsGranted()).containsExactly(
                new ClaimQuestRewardUseCase.ClaimResult.ItemReward("test_item", 3));
        assertThat(expPort.grantedAmount).isEqualTo(100L);
        assertThat(inventoryGrantPort.grantedCode).isEqualTo("test_item");
        assertThat(inventoryGrantPort.grantedQuantity).isEqualTo(3);
        assertThat(auditPort.loggedAction).isEqualTo("QUEST_CLAIM");
        assertThat(progressPort.saved.status()).isEqualTo(QuestStatus.CLAIMED);
    }
}
