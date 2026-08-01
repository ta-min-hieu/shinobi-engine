package dev.kage.tools.assetpipeline;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ART-PIPELINE.md §8.5 "fail build nếu..." — verify pipeline THẬT chặn từng loại vi phạm. */
final class NamingValidatorTest {

    @Test
    void shouldReject_whenAssetKeyHasUppercase(@TempDir Path folder) throws IOException {
        Files.createFile(folder.resolve("icon.png"));
        AssetFolder asset = new AssetFolder("Char_Bad", folder, null);

        assertThatThrownBy(() -> NamingValidator.validate(asset))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("quy ước đặt tên");
    }

    @Test
    void shouldReject_whenAssetKeyPrefixUnknown(@TempDir Path folder) throws IOException {
        Files.createFile(folder.resolve("icon.png"));
        AssetFolder asset = new AssetFolder("banana_test", folder, null);

        assertThatThrownBy(() -> NamingValidator.validate(asset))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("prefix danh mục");
    }

    @Test
    void shouldReject_whenAnimatedFrameMissing(@TempDir Path folder) {
        RenderMeta meta = new RenderMeta(Map.of("idle", 4), 128);
        AssetFolder asset = new AssetFolder("char_incomplete", folder, meta);

        assertThatThrownBy(() -> NamingValidator.validate(asset))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("thiếu frame");
    }

    @Test
    void shouldPass_whenStaticIconNamedCorrectly(@TempDir Path folder) throws IOException {
        Files.createFile(folder.resolve("icon.png"));
        AssetFolder asset = new AssetFolder("icon_ok", folder, null);

        assertThatCode(() -> NamingValidator.validate(asset)).doesNotThrowAnyException();
    }
}
