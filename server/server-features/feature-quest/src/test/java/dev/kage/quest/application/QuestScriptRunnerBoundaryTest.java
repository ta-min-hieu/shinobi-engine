package dev.kage.quest.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Biên đầu vào của MỌI hàm {@code ctx.*} mà script quest gọi được (rà 2026-10-10).
 *
 * <p><b>Vì sao cần.</b> Các callback đọc thẳng tham số JS bằng {@code asString()}/{@code asInt()}/
 * {@code asLong()} rồi đẩy xuống adapter. {@code give_exp} âm đã từng đẩy {@code character.exp} xuống
 * dưới 0 và làm nhân vật không vào được world; {@code give_item} âm làm đúng việc đó với túi đồ (xem
 * {@code QuestRewardItemBoundsIntegrationTest} ở server-database cho hậu quả đo trên Postgres thật).
 *
 * <p>Luật gác ở đây: một tham số sai kiểu, thiếu, hay ngoài miền PHẢI thành lỗi script
 * ({@link QuestScriptRunner.HookEffects#scriptFailed}) — khi đó cả ba use case đều không ghi gì. Một
 * giá trị sai mà lọt qua thành hiệu ứng là một lần ghi rác xuống DB.
 *
 * <p>Fixture {@code test_fixture_boundary.js} chọn nhánh qua {@code progress_json}.
 */
class QuestScriptRunnerBoundaryTest {

    private static final String BOUNDARY_SCRIPT_REF = "/scripts/quest/test_fixture_boundary.js";
    private static final Map<String, Object> ANY_EVENT = Map.<String, Object>of("type", "npc_talked", "npc_code", "x");

    private final QuestScriptRunner runner = new QuestScriptRunner();

    @ParameterizedTest(name = "on_claim: {0} phải là lỗi script")
    @ValueSource(
            strings = {
                "give_item_negative",
                "give_item_zero",
                "give_item_negative_zero",
                "give_item_fraction",
                "give_item_nan",
                "give_item_infinity",
                "give_item_numeric_string",
                "give_item_beyond_int",
                "give_item_null_quantity",
                "give_item_missing_quantity",
                "give_item_no_arguments",
                "give_item_null_code",
                "give_item_undefined_code",
                "give_item_empty_code",
                "give_item_blank_code",
                "give_item_nul_code",
                "give_item_numeric_code",
                "give_exp_fraction",
                "give_exp_nan",
                "give_exp_infinity",
                "give_exp_numeric_string",
                "give_exp_null",
                "give_exp_no_arguments",
                "give_exp_beyond_long",
                "give_exp_sum_overflows",
                "give_gold_zero",
                "give_gold_negative",
                "give_gold_over_cap",
                "give_gold_fraction",
                "give_gold_numeric_string",
                "give_gold_no_arguments"
            })
    void shouldFailTheScript_whenAClaimCallbackGetsAnOutOfDomainArgument(String caseName) {
        QuestScriptRunner.HookEffects effects = runner.runClaim(BOUNDARY_SCRIPT_REF, progressFor(caseName));

        assertThat(effects.scriptFailed()).as("nhánh %s", caseName).isTrue();
    }

    @ParameterizedTest(name = "on_event: {0} phải là lỗi script")
    @ValueSource(
            strings = {
                "take_item_zero",
                "take_item_fraction",
                "take_item_nan",
                "take_item_numeric_string",
                "take_item_missing_quantity",
                "take_item_null_code",
                "take_item_empty_code",
                "take_item_nul_code",
                "save_progress_null",
                "save_progress_undefined",
                "save_progress_number",
                "save_progress_object",
                "save_progress_no_arguments",
                "save_progress_nul"
            })
    void shouldFailTheScript_whenAnEventCallbackGetsAnOutOfDomainArgument(String caseName) {
        QuestScriptRunner.HookEffects effects = runner.runEvent(BOUNDARY_SCRIPT_REF, progressFor(caseName), ANY_EVENT);

        assertThat(effects.scriptFailed()).as("nhánh %s", caseName).isTrue();
    }

    /**
     * Những giá trị NẰM TRONG miền của tầng script. {@code Integer.MAX_VALUE} hợp lệ ở đây có chủ
     * đích: trần thật là sức chứa túi, và tầng túi đồ mới biết nó (adapter từ chối, lượt lĩnh
     * rollback thành {@code INVENTORY_FULL}).
     */
    @ParameterizedTest(name = "on_claim: {0} hợp lệ")
    @ValueSource(
            strings = {
                "give_item_max_int",
                "give_item_whole_double",
                "give_item_same_code_twice",
                "give_exp_zero",
                "give_gold_cap"
            })
    void shouldAcceptValuesInsideTheDomain(String caseName) {
        QuestScriptRunner.HookEffects effects = runner.runClaim(BOUNDARY_SCRIPT_REF, progressFor(caseName));

        assertThat(effects.scriptFailed()).as("nhánh %s", caseName).isFalse();
    }

    /** Hai lần {@code give_gold} cộng dồn (ADR-055) — tổng đi vào một lần ghi vàng. */
    @Test
    void shouldSumGoldAcrossCalls() {
        QuestScriptRunner.HookEffects effects = runner.runClaim(BOUNDARY_SCRIPT_REF, progressFor("give_gold_twice"));

        assertThat(effects.scriptFailed()).isFalse();
        assertThat(effects.goldGranted()).isEqualTo(50L);
    }

    /** Hai lần trao cùng mã KHÔNG bị cộng dồn ở tầng script — không có phép cộng {@code int} nào để tràn. */
    @Test
    void shouldKeepRepeatedGrantsOfTheSameCodeApart() {
        QuestScriptRunner.HookEffects effects =
                runner.runClaim(BOUNDARY_SCRIPT_REF, progressFor("give_item_same_code_twice"));

        assertThat(effects.itemGrants())
                .containsExactly(
                        new QuestScriptRunner.ItemGrant("kunai", Integer.MAX_VALUE),
                        new QuestScriptRunner.ItemGrant("kunai", Integer.MAX_VALUE));
    }

    @Test
    void shouldAcceptTheLargestTake() {
        QuestScriptRunner.HookEffects effects =
                runner.runEvent(BOUNDARY_SCRIPT_REF, progressFor("take_item_max_int"), ANY_EVENT);

        assertThat(effects.scriptFailed()).isFalse();
        assertThat(effects.itemTakes()).containsExactly(new QuestScriptRunner.ItemTake("kunai", Integer.MAX_VALUE));
    }

    private static String progressFor(String caseName) {
        return "{\"case\":\"" + caseName + "\"}";
    }
}
