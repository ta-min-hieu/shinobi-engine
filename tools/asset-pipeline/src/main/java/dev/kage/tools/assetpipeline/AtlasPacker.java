package dev.kage.tools.assetpipeline;

import java.nio.file.Path;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;

/**
 * Gọi thẳng {@code TexturePacker} gốc của libGDX (ADR-029) — không tự viết bin-packing, để format
 * {@code .atlas} sinh ra khớp tuyệt đối với {@code TextureAtlas} runtime đọc.
 */
final class AtlasPacker {

    private static final String PACK_FILE_NAME = "main";

    private AtlasPacker() {}

    static void pack(Path stagingDir, Path outputDir) {
        TexturePacker.Settings settings = new TexturePacker.Settings();
        settings.maxWidth = 2048; // RESOURCE.md §5 — trần phần cứng Android tầm trung
        settings.maxHeight = 2048;
        settings.pot = false; // texture NPOT an toàn trên phần cứng mục tiêu hiện tại
        settings.filterMin = Texture.TextureFilter.Linear; // ART-PIPELINE.md §5 "Texture filter: Linear"
        settings.filterMag = Texture.TextureFilter.Linear;
        settings.duplicatePadding = true; // chống bleed giữa các region khít nhau
        // useIndexes mặc định true — TexturePacker tự nhận số cuối tên file làm frame index,
        // gom "asset_key_action_00.png".."_NN.png" thành 1 region "asset_key_action" nhiều frame
        // (ART-PIPELINE.md §8.7), đúng thứ AtlasStager đã đặt tên sẵn.

        TexturePacker.process(settings, stagingDir.toString(), outputDir.toString(), PACK_FILE_NAME);
    }
}
