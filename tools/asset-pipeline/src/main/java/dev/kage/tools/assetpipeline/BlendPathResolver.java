package dev.kage.tools.assetpipeline;

/**
 * Suy ra {@code blend_path} từ {@code asset_key} theo đúng quy ước thư mục ART-PIPELINE.md §7
 * ({@code assets/raw/blender/characters|monsters|effects|props/}) — quy ước này là thứ đã dùng
 * thật để đặt tên mọi file {@code .blend} hiện có (`tools/blender/build_*.py --out`), không phải
 * suy đoán mới.
 */
final class BlendPathResolver {

    private BlendPathResolver() {}

    static String resolve(String assetKey) {
        String prefix = assetKey.substring(0, assetKey.indexOf('_') + 1);
        String nameWithoutPrefix = assetKey.substring(prefix.length());
        String subfolder =
                switch (prefix) {
                    case "char_" -> "characters";
                    case "mob_" -> "monsters";
                    case "vfx_" -> "effects";
                    case "icon_" -> "props";
                    default ->
                        throw new AssetPipelineException(
                                "Không suy ra được thư mục .blend cho asset_key '" + assetKey + "'");
                };
        return "assets/raw/blender/%s/%s.blend".formatted(subfolder, nameWithoutPrefix);
    }
}
