package dev.kage.tools.assetpipeline;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** ART-PIPELINE.md §4.4 — ngân sách tam giác là ràng buộc làm fail build, không phải gợi ý. */
final class TriangleBudgetValidatorTest {

    @Test
    void shouldPass_whenModelSitsExactlyOnTierBudget() {
        ScannedModel onBudget = model(GraphicsTier.MEDIUM, GraphicsTier.MEDIUM.maxTriangles);

        assertThatCode(() -> TriangleBudgetValidator.validateAll(List.of(onBudget)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldReject_whenModelExceedsTierBudgetByOneTriangle() {
        ScannedModel overBudget = model(GraphicsTier.LOW, GraphicsTier.LOW.maxTriangles + 1);

        assertThatThrownBy(() -> TriangleBudgetValidator.validateAll(List.of(overBudget)))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("char_test-hero.glb")
                .hasMessageContaining("5001")
                .hasMessageContaining("5000");
    }

    @Test
    void shouldReportEveryViolationAtOnce_whenWholeBatchIsOverBudget() {
        List<ScannedModel> batch = List.of(
                model(GraphicsTier.LOW, GraphicsTier.LOW.maxTriangles + 107),
                model(GraphicsTier.MEDIUM, GraphicsTier.MEDIUM.maxTriangles + 107),
                model(GraphicsTier.HIGH, GraphicsTier.HIGH.maxTriangles));

        assertThatThrownBy(() -> TriangleBudgetValidator.validateAll(batch))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("2/3 model vượt ngân sách")
                .hasMessageContaining("tier low")
                .hasMessageContaining("tier med");
    }

    @Test
    void shouldReject_whenModelPrefixBelongsToSpriteOnlyBranch() {
        // ADR-031: VFX vẫn là sprite 2D — vfx_*.glb nghĩa là ai đó đặt nhầm thư mục.
        ScannedModel wrongBranch =
                new ScannedModel("vfx_fireball", GraphicsTier.MEDIUM, Path.of("vfx_fireball.glb"), 1);

        assertThatThrownBy(() -> NamingValidator.validateModel(wrongBranch))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("prefix danh mục");
    }

    private static ScannedModel model(GraphicsTier tier, int triangleCount) {
        return new ScannedModel(
                "char_test-hero", tier, Path.of("models", tier.folderName, "char_test-hero.glb"), triangleCount);
    }
}
