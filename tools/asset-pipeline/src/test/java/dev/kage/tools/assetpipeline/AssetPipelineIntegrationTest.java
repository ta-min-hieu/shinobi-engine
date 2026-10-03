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
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Chạy toàn bộ pipeline thật trên PNG giả (không mock TexturePacker/ImageIO — TESTING.md §2 tinh
 * thần "test bằng thứ thật") để verify: hạ mẫu ra đúng kích thước, region name đúng quy ước
 * ART-PIPELINE.md §8.7, cả 3 tier đều xuất ra, manifest liệt kê đủ file kèm hash/size thật, và
 * nhánh 3D (ADR-031) vào được manifest kèm ngân sách tam giác đúng §4.4.
 *
 * <p><b>Nhánh registry là KHÔNG</b> — mọi test ở đây truyền {@code Optional.empty()} cho
 * {@link AssetPipelineRunner#run}, tức "chạy pipeline không có Postgres", đúng thứ chúng nó kiểm.
 * Bản cũ gọi {@code AssetPipelineRunner.main} nên nhánh registry do BIẾN MÔI TRƯỜNG của máy chạy
 * quyết định: shell nào có {@code DB_PASSWORD} (VD shell đang chạy server dev) thì {@code ./gradlew
 * build} đỏ, shell sạch thì xanh — lỗi thật ghi ở ROADMAP mục 61. Nhánh CÓ registry được kiểm ở
 * {@link AssetPipelineRegistryBranchIntegrationTest} bằng Postgres throwaway, không bằng may rủi.
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

        AssetPipelineRunner.run(renderRoot, exportedRoot, Optional.empty());

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

        AssetPipelineRunner.run(renderRoot, exportedRoot, Optional.empty());

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

        assertThatThrownBy(() -> AssetPipelineRunner.run(renderRoot, exportedRoot, Optional.empty()))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("vượt ngân sách tam giác");
    }

    /**
     * Trang atlas thừa của lần chạy TRƯỚC không được sống sót ở tier HIGH. TexturePacker tự xoá
     * {@code main*.png} cũ ở thư mục đích của LOW/MED, nhưng HIGH là bản SAO CHÉP từ MED — bản cũ
     * chỉ ghi đè file trùng tên, nên khi số trang giảm (VD 4 → 1) thì {@code high/main4.png} cũ vẫn
     * nằm đó và manifest liệt kê nó kèm hash: patcher tải về một trang mà không {@code .atlas} nào
     * tham chiếu.
     */
    @Test
    void shouldDropStaleHighTierPages_whenPreviousRunLeftMorePages(@TempDir Path tempRoot) throws IOException {
        Path renderRoot = tempRoot.resolve("render-out");
        writeStaticIcon(renderRoot, "icon_test-item");
        Path exportedRoot = tempRoot.resolve("exported");
        Path highDir = exportedRoot.resolve("atlases").resolve("high");
        Files.createDirectories(highDir);
        Path staleHighPage = highDir.resolve("main4.png");
        writePng(staleHighPage, 8, 8);

        AssetPipelineRunner.run(renderRoot, exportedRoot, Optional.empty());

        assertThat(staleHighPage).doesNotExist();
        assertThat(Files.readString(exportedRoot.resolve("asset-manifest.json")))
                .doesNotContain("main4.png");
    }

    /**
     * TexturePacker coi {@code _<số>} ở cuối tên file là chỉ số frame ({@code useIndexes}). Icon
     * tĩnh tên {@code icon_x_2} vì thế ra region {@code icon_x} index 2 — client tìm
     * {@code findRegion("icon_x_2")} không thấy, build vẫn xanh. Phải fail build ngay ở bước tên.
     */
    @Test
    void shouldFailBuild_whenStaticIconKeyEndsWithFrameIndexSuffix(@TempDir Path tempRoot) throws IOException {
        Path renderRoot = tempRoot.resolve("render-out");
        writeStaticIcon(renderRoot, "icon_test-item_2");
        Path exportedRoot = tempRoot.resolve("exported");

        assertThatThrownBy(() -> AssetPipelineRunner.run(renderRoot, exportedRoot, Optional.empty()))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("icon_test-item_2");
    }

    /**
     * Hai asset khác nhau có thể dẹt ra CÙNG tên file staging: {@code vfx_a} action {@code b_c}
     * và {@code vfx_a_b} action {@code c} đều thành {@code vfx_a_b_c_00.png}. Bản cũ ghi đè im lặng
     * — một asset biến mất khỏi atlas, build xanh.
     */
    @Test
    void shouldFailBuild_whenTwoAssetsFlattenToSameStagedFrame(@TempDir Path tempRoot) throws IOException {
        Path renderRoot = tempRoot.resolve("render-out");
        writeAnimatedAsset(renderRoot, "vfx_a", "b_c");
        writeAnimatedAsset(renderRoot, "vfx_a_b", "c");
        Path exportedRoot = tempRoot.resolve("exported");

        assertThatThrownBy(() -> AssetPipelineRunner.run(renderRoot, exportedRoot, Optional.empty()))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("vfx_a_b_c_00.png");
    }

    /**
     * File {@code .glb} tối thiểu: header + chunk JSON thật, không có chunk BIN (xem counter test),
     * KÈM sidecar {@code <asset_key>.model-meta.json} bắt buộc của ART-PIPELINE.md §8.6.
     *
     * <p>Sidecar không dùng tới ở nhánh không-registry, nhưng bản cũ thiếu nó là fixture SAI so với
     * đĩa thật: {@code export_character_lods.py} luôn ghi hai thứ cùng nhau, model {@code .glb}
     * đứng một mình là trạng thái không tồn tại trong dây chuyền. Cùng khuôn
     * {@code OrphanAssetCheckerIntegrationTest#writeModelAsset}.
     */
    private static void writeModel(Path exportedRoot, GraphicsTier tier, String assetKey, int indexCount)
            throws IOException {
        Path modelsRoot = exportedRoot.resolve("models");
        Path tierDir = modelsRoot.resolve(tier.folderName);
        Files.createDirectories(tierDir);
        Files.writeString(
                modelsRoot.resolve(assetKey + ".model-meta.json"),
                "{ \"blend_file\": \"assets/raw/blender/" + assetKey + ".blend\" }");
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
        writeAnimatedAsset(renderRoot, assetKey, "idle");
    }

    private static void writeAnimatedAsset(Path renderRoot, String assetKey, String action) throws IOException {
        Path folder = renderRoot.resolve(assetKey);
        Files.createDirectories(folder);
        Files.writeString(
                folder.resolve("render-meta.json"), "{ \"actions\": { \"" + action + "\": 2 }, \"master_px\": 8 }");
        writePng(folder.resolve(action + "_00.png"), 8, 8);
        writePng(folder.resolve(action + "_01.png"), 8, 8);
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
