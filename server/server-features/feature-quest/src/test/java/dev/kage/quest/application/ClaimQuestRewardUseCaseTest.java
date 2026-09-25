package dev.kage.quest.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.quest.domain.AuditPort;
import dev.kage.quest.domain.ExpPort;
import dev.kage.quest.domain.IdempotencyKeyReusedException;
import dev.kage.quest.domain.IdempotencyPort;
import dev.kage.quest.domain.QuestInventoryGrantPort;
import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.QuestTemplate;
import dev.kage.quest.domain.QuestTemplatePort;
import dev.kage.quest.domain.Transaction;
import dev.kage.quest.domain.TransactionRunner;

class ClaimQuestRewardUseCaseTest {

    private static final long CHARACTER_ID = 1L;
    private static final long TEMPLATE_ID = 100L;
    private static final String QUEST_CODE = "known_quest";
    private static final String SCRIPT_REF = "/scripts/quest/test_fixture.js";
    private static final String BROKEN_QUEST_CODE = "broken_quest";
    private static final String BROKEN_SCRIPT_REF = "/scripts/quest/test_fixture_broken.js";

    static final class FakeQuestTemplatePort implements QuestTemplatePort {
        @Override
        public Optional<QuestTemplate> findByCode(String questCode) {
            if (QUEST_CODE.equals(questCode)) {
                return Optional.of(QuestTemplate.withoutPrerequisite(TEMPLATE_ID, SCRIPT_REF));
            }
            if (BROKEN_QUEST_CODE.equals(questCode)) {
                // Cùng TEMPLATE_ID: Fake progress port tra theo nhân vật nên hai mã dùng chung một dòng tiến độ.
                return Optional.of(QuestTemplate.withoutPrerequisite(TEMPLATE_ID, BROKEN_SCRIPT_REF));
            }
            return Optional.empty();
        }
    }

    static final class FakeQuestProgressPort implements QuestProgressPort {
        private QuestProgress existing;
        QuestProgress saved;
        // Một lệnh lĩnh KHÁC đã đổi dòng sang CLAIMED giữa lần find và transaction của lệnh này.
        boolean claimedByAnotherRequest;

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

        /**
         * Không sửa {@code existing}: lần {@code find} kế tiếp vẫn thấy COMPLETED, đúng như một
         * lệnh lĩnh chồng lên đọc được trước khi lệnh kia commit.
         */
        @Override
        public boolean markClaimedIfCompleted(Transaction tx, long characterId, long questTemplateId) {
            if (claimedByAnotherRequest || existing == null || existing.status() != QuestStatus.COMPLETED) {
                return false;
            }
            this.saved = new QuestProgress(characterId, questTemplateId, QuestStatus.CLAIMED, existing.progressJson());
            return true;
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

    /** Túi đầy: {@code hasRoomFor} trả false, còn {@code grantItemByCode} sẽ KHÔNG được gọi tới. */
    static final class FullBagInventoryGrantPort implements QuestInventoryGrantPort {
        boolean grantAttempted;

        @Override
        public boolean grantItemByCode(Transaction tx, long characterId, String itemTemplateCode, int quantity) {
            grantAttempted = true;
            return false;
        }

        @Override
        public boolean hasRoomFor(Transaction tx, long characterId, java.util.List<ItemGrantRequest> grants) {
            return false;
        }
    }

    static final class FakeAuditPort implements AuditPort {
        String loggedAction;

        @Override
        public void log(Transaction tx, long characterId, String action, String detailJson) {
            loggedAction = action;
        }
    }

    static final class FakeIdempotencyPort implements IdempotencyPort {
        private final java.util.Set<String> usedKeys = new java.util.HashSet<>();

        @Override
        public void recordOrThrow(Transaction tx, String idempotencyKey) {
            if (!usedKeys.add(idempotencyKey)) {
                throw new IdempotencyKeyReusedException(idempotencyKey);
            }
        }
    }

    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);
    private static final String IDEMPOTENCY_KEY = "key-1";

    private static ClaimQuestRewardUseCase newUseCase(
            QuestProgressPort progressPort,
            FakeExpPort expPort,
            FakeInventoryGrantPort inventoryGrantPort,
            FakeAuditPort auditPort) {
        return newUseCase(
                progressPort, new QuestRewardPorts(expPort, inventoryGrantPort, auditPort, new FakeIdempotencyPort()));
    }

    private static ClaimQuestRewardUseCase newUseCase(QuestProgressPort progressPort, QuestRewardPorts rewardPorts) {
        return new ClaimQuestRewardUseCase(progressPort, new FakeQuestTemplatePort(), rewardPorts, DIRECT_TX);
    }

    @Test
    void shouldReturnQuestNotFound_whenCodeUnknown() {
        ClaimQuestRewardUseCase useCase = newUseCase(
                new FakeQuestProgressPort(null), new FakeExpPort(), new FakeInventoryGrantPort(), new FakeAuditPort());

        ClaimQuestRewardUseCase.ClaimResult result = useCase.execute(CHARACTER_ID, "unknown_code", IDEMPOTENCY_KEY);

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.QUEST_NOT_FOUND);
    }

    @Test
    void shouldReturnNotAccepted_whenNoProgressRow() {
        ClaimQuestRewardUseCase useCase = newUseCase(
                new FakeQuestProgressPort(null), new FakeExpPort(), new FakeInventoryGrantPort(), new FakeAuditPort());

        ClaimQuestRewardUseCase.ClaimResult result = useCase.execute(CHARACTER_ID, QUEST_CODE, IDEMPOTENCY_KEY);

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.NOT_ACCEPTED);
    }

    @Test
    void shouldReturnNotCompletedYet_whenStillInProgress() {
        QuestProgress progress = new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.IN_PROGRESS, "{}");
        ClaimQuestRewardUseCase useCase = newUseCase(
                new FakeQuestProgressPort(progress),
                new FakeExpPort(),
                new FakeInventoryGrantPort(),
                new FakeAuditPort());

        ClaimQuestRewardUseCase.ClaimResult result = useCase.execute(CHARACTER_ID, QUEST_CODE, IDEMPOTENCY_KEY);

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.NOT_COMPLETED_YET);
    }

    @Test
    void shouldGrantRewardsAndMarkClaimed_whenCompleted() {
        QuestProgress progress = new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.COMPLETED, "{\"counter\":2}");
        FakeQuestProgressPort progressPort = new FakeQuestProgressPort(progress);
        FakeExpPort expPort = new FakeExpPort();
        FakeInventoryGrantPort inventoryGrantPort = new FakeInventoryGrantPort();
        FakeAuditPort auditPort = new FakeAuditPort();
        ClaimQuestRewardUseCase useCase = newUseCase(progressPort, expPort, inventoryGrantPort, auditPort);

        ClaimQuestRewardUseCase.ClaimResult result = useCase.execute(CHARACTER_ID, QUEST_CODE, IDEMPOTENCY_KEY);

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        assertThat(result.expGranted()).isEqualTo(100L);
        assertThat(result.itemsGranted())
                .containsExactly(new ClaimQuestRewardUseCase.ClaimResult.ItemReward("test_item", 3));
        assertThat(expPort.grantedAmount).isEqualTo(100L);
        assertThat(inventoryGrantPort.grantedCode).isEqualTo("test_item");
        assertThat(inventoryGrantPort.grantedQuantity).isEqualTo(3);
        assertThat(auditPort.loggedAction).isEqualTo("QUEST_CLAIM");
        assertThat(progressPort.saved.status()).isEqualTo(QuestStatus.CLAIMED);
    }

    @Test
    void shouldReturnDuplicateRequest_whenIdempotencyKeyReused() {
        QuestProgress progress = new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.COMPLETED, "{\"counter\":2}");
        FakeQuestProgressPort progressPort = new FakeQuestProgressPort(progress);
        FakeIdempotencyPort idempotencyPort = new FakeIdempotencyPort();
        QuestRewardPorts rewardPorts = new QuestRewardPorts(
                new FakeExpPort(), new FakeInventoryGrantPort(), new FakeAuditPort(), idempotencyPort);
        ClaimQuestRewardUseCase useCase = newUseCase(progressPort, rewardPorts);
        useCase.execute(CHARACTER_ID, QUEST_CODE, IDEMPOTENCY_KEY);

        ClaimQuestRewardUseCase.ClaimResult result = useCase.execute(CHARACTER_ID, QUEST_CODE, IDEMPOTENCY_KEY);

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.DUPLICATE_REQUEST);
    }

    /**
     * Hai lệnh lĩnh với HAI key khác nhau cùng đọc thấy COMPLETED: lệnh thua không được trao gì, và
     * key của nó KHÔNG bị đốt (nó không ghi gì cả). Bản cũ trao thưởng cho cả hai.
     */
    @Test
    void shouldGrantNothing_whenAnotherClaimWonTheRace() {
        QuestProgress progress = new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.COMPLETED, "{\"counter\":2}");
        FakeQuestProgressPort progressPort = new FakeQuestProgressPort(progress);
        progressPort.claimedByAnotherRequest = true;
        FakeExpPort expPort = new FakeExpPort();
        FakeInventoryGrantPort inventoryGrantPort = new FakeInventoryGrantPort();
        FakeAuditPort auditPort = new FakeAuditPort();
        FakeIdempotencyPort idempotencyPort = new FakeIdempotencyPort();
        ClaimQuestRewardUseCase useCase =
                newUseCase(progressPort, new QuestRewardPorts(expPort, inventoryGrantPort, auditPort, idempotencyPort));

        ClaimQuestRewardUseCase.ClaimResult result = useCase.execute(CHARACTER_ID, QUEST_CODE, IDEMPOTENCY_KEY);

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.NOT_COMPLETED_YET);
        assertThat(expPort.grantedAmount).as("không được trao EXP").isEqualTo(-1L);
        assertThat(inventoryGrantPort.grantedCode).as("không được trao vật phẩm").isNull();
        assertThat(auditPort.loggedAction).isNull();
        assertThat(progressPort.saved).isNull();
        progressPort.claimedByAnotherRequest = false;
        assertThat(useCase.execute(CHARACTER_ID, QUEST_CODE, IDEMPOTENCY_KEY).outcome())
                .as("key không bị đốt")
                .isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
    }

    /**
     * Túi KHÔNG đủ ô ⇒ KHÔNG trao gì, KHÔNG đổi CLAIMED, KHÔNG đốt key (bug đo thật 2026-09-13).
     *
     * <p>Quest 11 trao 6 món khi túi còn 5 ô: người chơi nhận 5, {@code outer_sect_talisman} biến
     * mất, mà server vẫn trả OK với {@code items=6} và {@code idempotency_key} vẫn bị đốt ⇒ MẤT MÓN
     * VĨNH VIỄN. Túi đầy KHÔNG ném exception nên guard {@code scriptFailed} không che được ca này.
     *
     * <p>Fixture gọi {@code give_exp(100)} + {@code give_item} nên MỌI khẳng định dưới đây đều đỏ
     * với bản cũ — kể cả {@code grantAttempted}, thứ chứng minh use case dừng TRƯỚC transaction.
     */
    @Test
    void shouldGrantNothingAndKeepQuestClaimable_whenBagHasNoRoom() {
        QuestProgress progress = new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.COMPLETED, "{\"counter\":2}");
        FakeQuestProgressPort progressPort = new FakeQuestProgressPort(progress);
        FakeExpPort expPort = new FakeExpPort();
        FullBagInventoryGrantPort inventoryGrantPort = new FullBagInventoryGrantPort();
        FakeAuditPort auditPort = new FakeAuditPort();
        FakeIdempotencyPort idempotencyPort = new FakeIdempotencyPort();
        ClaimQuestRewardUseCase useCase =
                newUseCase(progressPort, new QuestRewardPorts(expPort, inventoryGrantPort, auditPort, idempotencyPort));

        ClaimQuestRewardUseCase.ClaimResult result = useCase.execute(CHARACTER_ID, QUEST_CODE, IDEMPOTENCY_KEY);

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.INVENTORY_FULL);
        assertThat(result.itemsGranted()).isEmpty();
        assertThat(result.expGranted()).isZero();
        assertThat(expPort.grantedAmount).as("không được trao EXP").isEqualTo(-1L);
        assertThat(inventoryGrantPort.grantAttempted)
                .as("phải dừng TRƯỚC transaction, không thử trao món nào")
                .isFalse();
        assertThat(auditPort.loggedAction).as("không được ghi audit").isNull();
        assertThat(progressPort.saved).as("quest phải vẫn COMPLETED").isNull();
    }

    /** Key CHƯA bị đốt ⇒ dọn túi xong lĩnh lại bằng ĐÚNG key cũ vẫn phải được — thứ bug cũ phá. */
    @Test
    void shouldStillBeClaimableWithSameKey_afterBagWasFreed() {
        QuestProgress progress = new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.COMPLETED, "{\"counter\":2}");
        FakeQuestProgressPort progressPort = new FakeQuestProgressPort(progress);
        FakeIdempotencyPort idempotencyPort = new FakeIdempotencyPort();
        ClaimQuestRewardUseCase blocked = newUseCase(
                progressPort,
                new QuestRewardPorts(
                        new FakeExpPort(), new FullBagInventoryGrantPort(), new FakeAuditPort(), idempotencyPort));
        blocked.execute(CHARACTER_ID, QUEST_CODE, IDEMPOTENCY_KEY);

        FakeExpPort expPort = new FakeExpPort();
        FakeInventoryGrantPort roomy = new FakeInventoryGrantPort();
        ClaimQuestRewardUseCase afterCleanup =
                newUseCase(progressPort, new QuestRewardPorts(expPort, roomy, new FakeAuditPort(), idempotencyPort));

        ClaimQuestRewardUseCase.ClaimResult result = afterCleanup.execute(CHARACTER_ID, QUEST_CODE, IDEMPOTENCY_KEY);

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        assertThat(expPort.grantedAmount).isEqualTo(100L);
        assertThat(roomy.grantedCode).isEqualTo("test_item");
        assertThat(progressPort.saved.status()).isEqualTo(QuestStatus.CLAIMED);
    }

    /**
     * Script {@code on_claim} ném lỗi ⇒ KHÔNG trao gì, KHÔNG đổi CLAIMED (2026-09-12).
     *
     * <p>Fixture gọi {@code give_exp(100)} và {@code give_item("test_item", 3)} TRƯỚC chỗ lỗi, nên
     * mọi khẳng định dưới đây đều đỏ với bản cũ — đó chính là lỗ mất phần thưởng vĩnh viễn.
     */
    @Test
    void shouldGrantNothingAndKeepQuestClaimable_whenClaimHookThrows() {
        QuestProgress progress = new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.COMPLETED, "{\"counter\":2}");
        FakeQuestProgressPort progressPort = new FakeQuestProgressPort(progress);
        FakeExpPort expPort = new FakeExpPort();
        FakeInventoryGrantPort inventoryGrantPort = new FakeInventoryGrantPort();
        FakeAuditPort auditPort = new FakeAuditPort();
        ClaimQuestRewardUseCase useCase = newUseCase(progressPort, expPort, inventoryGrantPort, auditPort);

        ClaimQuestRewardUseCase.ClaimResult result = useCase.execute(CHARACTER_ID, BROKEN_QUEST_CODE, IDEMPOTENCY_KEY);

        assertThat(result.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.SCRIPT_ERROR);
        assertThat(result.expGranted()).isZero();
        assertThat(result.itemsGranted()).isEmpty();
        assertThat(expPort.grantedAmount).isEqualTo(-1); // -1 = chưa ai gọi grantExp
        assertThat(inventoryGrantPort.grantedCode).isNull();
        assertThat(auditPort.loggedAction).isNull();
        // Vẫn COMPLETED: người chơi lĩnh lại được sau khi script được sửa.
        assertThat(progressPort.saved).isNull();
    }

    /**
     * Và khoá idempotency KHÔNG bị đốt — thứ quyết định "lĩnh lại được" có thật hay chỉ trên giấy.
     *
     * <p>Cùng một {@code idempotencyKey} dùng lại sau lần lỗi phải đi tới cùng: bản cũ ghi key trong
     * transaction TRƯỚC khi trao thưởng, nên lần thử lại sẽ trả {@code DUPLICATE_REQUEST} và người
     * chơi mất thưởng mãi mãi.
     */
    @Test
    void shouldNotBurnTheIdempotencyKey_whenClaimHookThrows() {
        QuestProgress progress = new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.COMPLETED, "{\"counter\":2}");
        FakeQuestProgressPort progressPort = new FakeQuestProgressPort(progress);
        FakeExpPort expPort = new FakeExpPort();
        QuestRewardPorts rewardPorts = new QuestRewardPorts(
                expPort, new FakeInventoryGrantPort(), new FakeAuditPort(), new FakeIdempotencyPort());
        ClaimQuestRewardUseCase useCase = newUseCase(progressPort, rewardPorts);
        useCase.execute(CHARACTER_ID, BROKEN_QUEST_CODE, IDEMPOTENCY_KEY);

        // Script được sửa (mã lành, CÙNG khoá) — lần lĩnh này phải đi tới cùng.
        ClaimQuestRewardUseCase.ClaimResult retry = useCase.execute(CHARACTER_ID, QUEST_CODE, IDEMPOTENCY_KEY);

        assertThat(retry.outcome()).isEqualTo(ClaimQuestRewardUseCase.ClaimResult.Outcome.OK);
        assertThat(expPort.grantedAmount).isEqualTo(100L);
    }
}
