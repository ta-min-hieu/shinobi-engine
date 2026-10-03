package dev.kage.tools.assetpipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Hạ mẫu + đổi tên phẳng mọi asset vào 1 thư mục staging cho 1 tier, đúng quy ước region name
 * ART-PIPELINE.md §8.7 (<asset_key>_<action> + index tăng dần cho asset nhiều frame, đúng
 * <asset_key> không hậu tố cho icon tĩnh) — TexturePacker tự nhận số cuối tên file làm index
 * (useIndexes mặc định true), nên chỉ cần đặt đúng tên file, không cần cấu hình gì thêm.
 *
 * <p>Không gọi cho {@link GraphicsTier#HIGH} — HIGH dùng chung ảnh với MEDIUM (xem
 * {@link GraphicsTier}), atlas được sao chép thẳng ở {@link AssetPipelineRunner}, không stage lại.
 */
final class AtlasStager {

    private AtlasStager() {}

    static void stage(List<AssetFolder> assets, GraphicsTier tier, Path stagingDir) throws IOException {
        Files.createDirectories(stagingDir);
        for (AssetFolder asset : assets) {
            if (asset.isStaticIcon()) {
                stageIcon(asset, tier, stagingDir);
            } else {
                stageAnimated(asset, tier, stagingDir);
            }
        }
    }

    private static void stageIcon(AssetFolder asset, GraphicsTier tier, Path stagingDir) throws IOException {
        Path source = asset.folder().resolve("icon.png");
        Path destination = stagingDir.resolve(asset.assetKey() + ".png");
        requireUnclaimed(destination, asset);
        ImageDownscaler.downscaleTo(source, destination, tier.scaleOfMaster);
    }

    private static void stageAnimated(AssetFolder asset, GraphicsTier tier, Path stagingDir) throws IOException {
        for (Map.Entry<String, Integer> action : asset.renderMeta().actions().entrySet()) {
            for (int index = 0; index < action.getValue(); index++) {
                String sourceName = "%s_%02d.png".formatted(action.getKey(), index);
                String destName = "%s_%s_%02d.png".formatted(asset.assetKey(), action.getKey(), index);
                Path source = asset.folder().resolve(sourceName);
                Path destination = stagingDir.resolve(destName);
                requireUnclaimed(destination, asset);
                ImageDownscaler.downscaleTo(source, destination, tier.scaleOfMaster);
            }
        }
    }

    // Thư mục staging luôn mới tinh (createTempDirectory) — file đã có ở đó nghĩa là một asset KHÁC
    // vừa dẹt ra đúng tên này. VD "vfx_a" action "b_c" và "vfx_a_b" action "c" cùng thành
    // "vfx_a_b_c_00.png". Ghi đè thì một asset biến mất khỏi atlas mà build vẫn xanh.
    private static void requireUnclaimed(Path destination, AssetFolder asset) {
        if (Files.exists(destination)) {
            throw new AssetPipelineException("Asset '" + asset.assetKey() + "' dẹt ra tên staging "
                    + destination.getFileName() + " TRÙNG với một asset khác — một trong hai sẽ mất khỏi"
                    + " atlas; đổi asset_key hoặc tên action để tách hai tên region");
        }
    }
}
