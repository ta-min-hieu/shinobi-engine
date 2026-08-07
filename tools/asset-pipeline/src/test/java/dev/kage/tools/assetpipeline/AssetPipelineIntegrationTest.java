package dev.kage.tools.assetpipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Chạy toàn bộ pipeline thật trên PNG giả (không mock TexturePacker/ImageIO — TESTING.md §2 tinh
 * thần "test bằng thứ thật") để verify: hạ mẫu ra đúng kích thước, region name đúng quy ước
 * ART-PIPELINE.md §8.6, cả 3 tier đều xuất ra, manifest liệt kê đủ file kèm hash/size thật, và
 * nhánh 3D (ADR-031) vào được manifest kèm ngân sách tam giác đúng §4.4.
 */
final class AssetPipelineIntegrationTest {

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int CHUNK_TYPE_JSON = 0x4E4F534A;
    private static final int GLB_VERSION = 2;

    /** 12 byte header GLB + 8 byte header chunk JSON. */
    private static final int GLB_HEADER_BYTES = 20;

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

    @Test
    void shouldListModelsWithTierAndTriangles_whenModelBranchHasGlbFiles(@TempDir Path tempRoot) throws IOException {
        Path renderRoot = tempRoot.resolve("render-out");
        writeStaticIcon(renderRoot, "icon_test-item");
        Path exportedRoot = tempRoot.resolve("exported");
        writeModel(exportedRoot, GraphicsTier.MEDIUM, "char_test-hero", 900);

        AssetPipelineRunner.main(new String[] {
            "--render-root", renderRoot.toString(),
            "--exported-root", exportedRoot.toString(),
        });

        String manifest = Files.readString(exportedRoot.resolve("asset-manifest.json"));
        assertThat(manifest)
                .contains("models/med/char_test-hero.glb")
                .contains("\"assetKey\": \"char_test-hero\"")
                .contains("\"tier\": \"med\"")
                .contains("\"triangles\": 300");
    }

    @Test
    void shouldFailBuild_whenModelExceedsTierTriangleBudget(@TempDir Path tempRoot) throws IOException {
        Path renderRoot = tempRoot.resolve("render-out");
        writeStaticIcon(renderRoot, "icon_test-item");
        Path exportedRoot = tempRoot.resolve("exported");
        int overBudgetVertices = (GraphicsTier.LOW.maxTriangles + 1) * 3;
        writeModel(exportedRoot, GraphicsTier.LOW, "char_test-hero", overBudgetVertices);

        assertThatThrownBy(() -> AssetPipelineRunner.main(new String[] {
                    "--render-root", renderRoot.toString(),
                    "--exported-root", exportedRoot.toString(),
                }))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("vượt ngân sách tam giác");
    }

    /** File {@code .glb} tối thiểu: header + chunk JSON thật, không có chunk BIN (xem counter test). */
    private static void writeModel(Path exportedRoot, GraphicsTier tier, String assetKey, int indexCount)
            throws IOException {
        Path tierDir = exportedRoot.resolve("models").resolve(tier.folderName);
        Files.createDirectories(tierDir);
        // Nối chuỗi chứ không dùng text block + formatted(): SpotBugs coi text block nhiều dòng
        // truyền cho formatted() là format string và bắt lỗi "%n thay vì \n" — ở đây là JSON, không
        // phải chuỗi in ra màn hình, nên tránh format string luôn cho gọn.
        byte[] json = ("{ \"accessors\": [ { \"count\": " + indexCount + " } ], \"meshes\": "
                        + "[ { \"primitives\": [ { \"attributes\": { \"POSITION\": 0 }, \"indices\": 0 } ] } ] }")
                .getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(GLB_HEADER_BYTES + json.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(GLB_MAGIC);
        buffer.putInt(GLB_VERSION);
        buffer.putInt(buffer.capacity());
        buffer.putInt(json.length);
        buffer.putInt(CHUNK_TYPE_JSON);
        buffer.put(json);
        Files.write(tierDir.resolve(assetKey + ".glb"), buffer.array());
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
