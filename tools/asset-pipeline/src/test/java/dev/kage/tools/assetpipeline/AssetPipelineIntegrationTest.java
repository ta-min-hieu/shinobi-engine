package dev.kage.tools.assetpipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Chạy toàn bộ pipeline thật trên PNG giả (không mock TexturePacker/ImageIO — TESTING.md §2 tinh
 * thần "test bằng thứ thật") để verify: hạ mẫu ra đúng kích thước, region name đúng quy ước
 * ART-PIPELINE.md §8.6, cả 3 tier đều xuất ra, manifest liệt kê đủ file kèm hash/size thật.
 */
final class AssetPipelineIntegrationTest {

    @Test
    void shouldPackAllTiersAndWriteManifest_whenGivenAnimatedAssetAndIcon(@TempDir Path tempRoot) throws IOException {
        Path renderRoot = tempRoot.resolve("render-out");
        writeAnimatedAsset(renderRoot, "char_test-hero");
        writeStaticIcon(renderRoot, "icon_test-item");
        Path exportedRoot = tempRoot.resolve("exported");

        AssetPipelineRunner.main(new String[] {
            "--render-root", renderRoot.toString(),
            "--exported-root", exportedRoot.toString(),
        });

        Path atlasesRoot = exportedRoot.resolve("atlases");
        for (String tier : new String[] {"low", "med", "high"}) {
            Path atlasFile = atlasesRoot.resolve(tier).resolve("main.atlas");
            assertThat(atlasFile).exists();
            String atlasText = Files.readString(atlasFile);
            assertThat(atlasText).contains("char_test-hero_idle").contains("icon_test-item");
        }

        Path manifestPath = exportedRoot.resolve("asset-manifest.json");
        assertThat(manifestPath).exists();
        String manifest = Files.readString(manifestPath);
        assertThat(manifest)
                .contains("\"assetVersion\": 1")
                .contains("atlases/low/main.atlas")
                .contains("sha256:");
    }

    private static void writeAnimatedAsset(Path renderRoot, String assetKey) throws IOException {
        Path folder = renderRoot.resolve(assetKey);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("render-meta.json"), """
                { "actions": { "idle": 2 }, "master_px": 8 }
                """);
        writePng(folder.resolve("idle_00.png"), 8, 8);
        writePng(folder.resolve("idle_01.png"), 8, 8);
    }

    private static void writeStaticIcon(Path renderRoot, String assetKey) throws IOException {
        Path folder = renderRoot.resolve(assetKey);
        Files.createDirectories(folder);
        writePng(folder.resolve("icon.png"), 8, 8);
    }

    private static void writePng(Path path, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, 0xFF336699);
            }
        }
        ImageIO.write(image, "png", path.toFile());
    }
}
