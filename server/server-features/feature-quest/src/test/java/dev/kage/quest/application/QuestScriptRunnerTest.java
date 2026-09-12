package dev.kage.quest.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Hai luật của {@code ctx.take_item} nằm ở tầng chạy script, không ở use case — gác riêng ở đây.
 */
class QuestScriptRunnerTest {

    private static final String DELIVERY_SCRIPT_REF = "/scripts/quest/test_fixture_delivery.js";

    private final QuestScriptRunner runner = new QuestScriptRunner();

    /**
     * Số âm đi xuống {@code quantity - ?} của adapter là CỘNG đồ. Nó phải chết ngay trong callback
     * và thành lỗi script — use case bỏ mọi hiệu ứng của một script lỗi, nên không gì lọt tới DB.
     */
    @Test
    void shouldTurnANonPositiveTakeIntoAScriptFailure() {
        QuestScriptRunner.HookEffects effects = runner.runEvent(
                DELIVERY_SCRIPT_REF,
                "{\"delivered\":false}",
                Map.<String, Object>of("type", "npc_talked", "npc_code", "negative_npc"));

        assertThat(effects.scriptFailed()).isTrue();
        assertThat(effects.itemTakes()).isEmpty();
    }

    /**
     * {@code take_item} chỉ tồn tại ở {@code on_event}. Ở {@code on_claim} (và {@code on_accept})
     * gọi nó là lỗi ồn ào — không phải một lời đòi đồ bị âm thầm bỏ qua, vì ClaimQuestRewardUseCase
     * không có đường nào để lấy đồ.
     */
    @Test
    void shouldNotOfferTakeItem_outsideOnEvent() {
        QuestScriptRunner.HookEffects effects = runner.runClaim(DELIVERY_SCRIPT_REF, "{\"delivered\":true}");

        assertThat(effects.scriptFailed()).isTrue();
        assertThat(effects.itemTakes()).isEmpty();
        assertThat(effects.expGranted()).isZero(); // ném trước khi kịp give_exp
    }

    @Test
    void shouldCollectTakes_inTheOrderTheScriptAskedForThem() {
        QuestScriptRunner.HookEffects effects = runner.runEvent(
                DELIVERY_SCRIPT_REF,
                "{\"delivered\":false}",
                Map.<String, Object>of("type", "npc_talked", "npc_code", "senior_sister"));

        assertThat(effects.scriptFailed()).isFalse();
        assertThat(effects.itemTakes())
                .containsExactly(
                        new QuestScriptRunner.ItemTake("sealed_letter", 1),
                        new QuestScriptRunner.ItemTake("spirit_herb", 2));
        assertThat(effects.completed()).isTrue();
    }
}
